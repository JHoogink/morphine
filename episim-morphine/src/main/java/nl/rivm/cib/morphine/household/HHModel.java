package nl.rivm.cib.morphine.household;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.measure.Quantity;
import javax.measure.quantity.Time;

import org.apache.logging.log4j.Logger;
import org.ujmp.core.Matrix;
import org.ujmp.core.SparseMatrix;
import org.ujmp.core.enums.ValueType;

import com.eaio.uuid.UUID;

import io.coala.bind.InjectConfig;
import io.coala.bind.InjectConfig.Scope;
import io.coala.bind.LocalBinder;
import io.coala.enterprise.Actor;
import io.coala.log.LogUtil;
import io.coala.math.QuantityUtil;
import io.coala.math.Range;
import io.coala.math.Tuple;
import io.coala.random.ConditionalDistribution;
import io.coala.random.ProbabilityDistribution;
import io.coala.random.PseudoRandom;
import io.coala.random.QuantityDistribution;
import io.coala.time.Instant;
import io.coala.time.Scenario;
import io.coala.time.Scheduler;
import io.coala.time.TimeUnits;
import io.reactivex.Observable;
import nl.rivm.cib.epidemes.cbs.json.CBSGender;
import nl.rivm.cib.epidemes.cbs.json.CBSHousehold;
import nl.rivm.cib.episim.model.locate.Region;
import nl.rivm.cib.episim.model.vaccine.attitude.VaxOccasion;
import nl.rivm.cib.morphine.dao.HHStatisticsDao;
import nl.rivm.cib.morphine.json.HesitancyProfileJson;
import nl.rivm.cib.morphine.json.HesitancyProfileJson.VaccineStatus;

/**
 * {@link HHModel} is a simple example {@link Scenario} implementation, of which
 * only one {@link Singleton} instance exists per {@link LocalBinder}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
@Singleton
public class HHModel implements Scenario
{

	/** */
	private static final Logger LOG = LogUtil.getLogger( HHModel.class );

	/** */
	private static final HHAttribute[] CHILD_REF_COLUMN_INDICES = {
			// HHAttribute.REFERENT_REF,
			// HHAttribute.PARTNER_REF,
			HHAttribute.CHILD1_REF
//			, HHAttribute.CHILD2_REF
//			, HHAttribute.CHILD3_REF 
	};

	@InjectConfig( Scope.DEFAULT )
	private transient HHConfig config;

	@Inject
	private transient LocalBinder binder;

	@Inject
	private transient Scheduler scheduler;

	@Inject
	private transient ProbabilityDistribution.Factory distFactory;

	@Inject
	private transient ProbabilityDistribution.Parser distParser;

	/** current (cached) virtual time instant */
	private transient Instant dtInstant = null;
	/** current (cached) date/time */
	private transient LocalDate dtCache = null;
	/** */
	private final AtomicInteger statsIteration = new AtomicInteger();
	/** */
	private Matrix hhAttributes;
	/** */
	private Matrix hhNetwork;
	/** */
	private Matrix ppAttributes;
	/** */
	private final AtomicLong hhCount = new AtomicLong();
	/** */
	private final AtomicLong persons = new AtomicLong();
	/** number of top rows (0..n) in {@link #hhNetwork} reserved for oracles */
	private long attractorCount;
	/** virtual time range of simulation */
//	private transient Range<LocalDate> timeRange = null;
	/** empirical household compositions and referent ages, see CBS 71486 */
//	private transient ConditionalDistribution<Cbs71486json.Category, LocalDate> localHouseholdDist;
	/** zip-codes per borough / ward / municipality / ... */
//	private transient ConditionalDistribution<CbsNeighborhood, Region.ID> hoodDist;

	/** */
	private RegionBroker attractorBroker;
	/** */
	private ProbabilityDistribution<CBSHousehold> hhTypeDist;
	/** */
	private QuantityDistribution<Time> hhRefAgeDist;
	/** */
	private QuantityDistribution<Time> hhMigrateDist;
	/** */
	private Quantity<Time> hhLeaveHomeAge;

	/** */
	private transient ConditionalDistribution<HesitancyProfileJson, HesitancyProfileJson.Category> hesitancyProfileDist;
	/** */
	private transient ProbabilityDistribution<BigDecimal> calculationDist;
	/** */
	private transient ProbabilityDistribution<Boolean> schoolAssortativity;
	/** */
	private transient HHAttitudeEvaluator attitudeEvaluator;
	/** */
	private transient HHAttitudePropagator attitudePropagator;
	/** */
	private transient ConditionalDistribution<Map<HHAttribute, BigDecimal>, HesitancyProfileJson> hesitancyDist;
	/** */
	private transient ProbabilityDistribution<VaxOccasion> vaxOccasionDist;

//	private transient ConditionalDistribution<Quantity<Time>, GenderAge> peerPressureIntervalDist;

	/**
	 * {@link RegionBroker} assigns regions to households
	 */
	@FunctionalInterface
	public interface RegionBroker
	{
		Region.ID next( Long hhIndex );
	}

	/**
	 * {@link GenderAge} wraps a {@link Tuple} in: CBSGender x Instant (birth)
	 */
	public static class GenderAge extends Tuple
	{
		public GenderAge( final CBSGender gender, final BigDecimal ageYears )
		{
			super( Arrays.<Comparable<?>>asList( gender, ageYears ) );
		}

		public CBSGender gender()
		{
			return (CBSGender) values().get( 0 );
		}

		public BigDecimal ageYears()
		{
			return (BigDecimal) values().get( 1 );
		}
	}

	@Override
	public void init() throws ParseException, InstantiationException,
		IllegalAccessException, IOException
	{

		final List<HHAttractor> attractors = this.config
				.hesitancyAttractors( this.binder ).toList().blockingGet();
		this.attractorCount = attractors.size();

		final CBSHousehold hhType = this.config
				.householdTypeDist( this.distParser ).draw(); // assuming constant
		final long ppTotal = this.config.populationSize(),
				hhTotal = ppTotal / hhType.size(),
				edgeTotal = hhTotal + this.attractorCount;
		LOG.trace( "#persons: {}, #hh: {}, #oracles: {}, #edges (max): {}",
				ppTotal, hhTotal, this.attractorCount, edgeTotal );

		// or Matrix.Factory.linkToJDBC(host, port, db, table, user, password)
		// or Matrix.Factory.linkTo().file("hugeCSVFile").asDenseCSV(columnSeparator)
		this.hhAttributes = Matrix.Factory.zeros( ValueType.BIGDECIMAL,
				edgeTotal, HHAttribute.values().length );
		this.ppAttributes = Matrix.Factory.zeros( ValueType.BIGDECIMAL, ppTotal,
				HHMemberAttribute.values().length );
		this.hhNetwork = SparseMatrix.Factory.zeros( //ValueType.BIGDECIMAL,
				edgeTotal, edgeTotal );

		attractors.forEach( attractor ->
		{
			final long index = this.hhCount.getAndIncrement();
			attractor.position().subscribe( map ->
			{
				LOG.info( "t={}, attractor {}: {}", dt(), index, map );
				map.forEach( ( att, val ) -> this.hhAttributes
						.setAsBigDecimal( val, index, att.ordinal() ) );
			}, this::logError );
		} );

//		this.timeRange = Range
//				.upFromAndIncluding( scheduler().offset().toLocalDate() );
//		this.localHouseholdDist = this.config.cbs71486( this.timeRange,
//				this.distFactory );
//		final Region.ID fallbackRegRef = this.config.fallbackRegionRef();
//		this.hoodDist = this.config.neighborhoodDist( this.distFactory,
//				regRef -> fallbackRegRef );
		final Map<Long, Region.ID> regions = new HashMap<>();
		this.attractorBroker = hhIndex -> regions.computeIfAbsent(
				hhIndex % this.attractorCount,
				key -> Region.ID.of( "or" + key ) );
		this.hhTypeDist = this.config.householdTypeDist( this.distParser );
		this.hhRefAgeDist = this.config
				.householdReferentAgeDist( this.distParser );
//		this.peerPressureIntervalDist = this.config
//				.peerPressureInterval( this.distFactory );

		this.hesitancyProfileDist = this.config.hesitancyProfilesGrouped(
				this.distFactory, HesitancyProfileJson::toCategory );
		this.calculationDist = this.config
				.hesitancyCalculationDist( this.distParser );

		this.hhLeaveHomeAge = this.config.householdLeaveHomeAge();
		this.hhMigrateDist = this.config
				.householdReplacementDist( this.distFactory, hhTotal );
		after( this.hhMigrateDist.draw() ).call( this::migrateHousehold );

		final ProbabilityDistribution<Number> vaccinationUtilityDist = this.config
				.vaccinationUtilityDist( this.distParser );
		final ProbabilityDistribution<Number> vaccinationProximityDist = this.config
				.vaccinationProximityDist( this.distParser );
		final ProbabilityDistribution<Number> vaccinationClarityDist = this.config
				.vaccinationClarityDist( this.distParser );
		final ProbabilityDistribution<Number> vaccinationAffinityDist = this.config
				.vaccinationAffinityDist( this.distParser );
		this.vaxOccasionDist = () -> VaxOccasion.of(
				vaccinationUtilityDist.draw(), vaccinationProximityDist.draw(),
				vaccinationClarityDist.draw(), vaccinationAffinityDist.draw() );

		// reference to json indices
		this.hesitancyDist = this.config
				.hesitancyProfileSample( this.distFactory.getStream() );

		// populate households
		for( long time = System.currentTimeMillis(), agPrev = 0; this.persons
				.get() < ppTotal; )
		{
			addHousehold();
			if( System.currentTimeMillis() - time > 1000 )
			{
				time = System.currentTimeMillis();
				long agNow = this.persons.get() + this.hhCount.get();
				LOG.trace(
						"Initialized {} pp ({}%) across {} hh (= +{} actors/sec)",
						this.persons.get(), this.persons.get() * 100 / ppTotal,
						this.hhCount.get(), agNow - agPrev );
				agPrev = agNow;
			}
		}

		LOG.info( "Initialized {} pp ({}%) across {} hh & {} attractor regions",
				this.persons.get(), this.persons.get() * 100 / ppTotal,
				this.hhCount.get() - this.attractorCount, this.attractorCount );

		final PseudoRandom rng = this.distFactory.getStream();
		final HHConnector conn = new HHConnector.WattsStrogatz( rng,
				this.config.hesitancySocialNetworkBeta() );
		final long A = this.attractorCount, N = this.hhCount.get() - A, K = Math
				.min( N - 1, this.config.hesitancySocialNetworkDegree() );

		final double assortativity = this.config.hesitancySocialAssortativity();
		final Supplier<Long> assortK = this.distFactory
				.createExponential( 1.0 / K / assortativity )
				.map( Math::round )::draw;
		final Supplier<Long> dissortK = this.distFactory
				.createExponential( 1.0 / K / (1 - assortativity / (A - 1)) )
				.map( Math::round )::draw;
		final Supplier<BigDecimal> ownW = () -> BigDecimal.ONE,
				assortW = () -> BigDecimal.ONE, dissortW = () -> BigDecimal.ONE;

//		final long assortativeK = A < 2 ? K
//				: (long) (K * this.config.hesitancySocialAssortativity()),
//				dissortativeK = A < 2 ? 0
//						: Math.max( 1, (K - assortativeK) / (A - 1) );
		final Matrix[] assorting = LongStream.range( 0, A )
				.mapToObj( a -> conn.connect( N, assortK, assortW ) )
				.toArray( Matrix[]::new );
		final Matrix[] dissorting = LongStream.range( 0, A )
				.mapToObj( a -> A < 2 ? SparseMatrix.Factory.zeros( N, N )
						: conn.connect( N, dissortK, dissortW ) )
				.toArray( Matrix[]::new );

		LongStream.range( 0, N ).forEach( i ->
		{
			HHConnector.setSymmetric( this.hhNetwork, ownW.get(), A + i );

			final int attr = this.hhAttributes.getAsInt( i,
					HHAttribute.ATTRACTOR_REF.ordinal() );

			if( A < 2 || assortativity >= 1 )
			{
				final long[] assort = HHConnector
						.symmetricCoordinates( assorting[attr], i )
						.mapToLong( x ->
						{
							final Object w = assorting[attr].getAsObject( x );
							this.hhNetwork.setAsObject( w, A + x[0], A + x[1] );
							return x[0] == i ? x[1] : x[0];
						} ).toArray();
				final long[] dissort = IntStream.range( 0, (int) A )
						.filter( a -> a != attr ).mapToObj( a -> a )
						.flatMapToLong( a -> HHConnector
								.symmetricCoordinates( dissorting[a], i )
								.mapToLong( x ->
								{
									// TODO handle overrides?
									final Object w = dissorting[a]
											.getAsObject( x );
									this.hhNetwork.setAsObject( w, A + x[0],
											A + x[1] );
									return x[0] == i ? x[1] : x[0];
								} ) )
						.distinct().toArray();
				LOG.trace( "{}: {}/{}+{}/{}\t={}/{}", i, assort.length,
						assortK.get(), dissort.length, dissortK.get(),
						assort.length + dissort.length, K );
			} else
			{
				HHConnector.symmetricCoordinates( assorting[attr], i )
						.forEach( x -> this.hhNetwork.setAsObject(
								assorting[attr].getAsObject( x ), A + x[0],
								A + x[1] ) );
			}
		} );

		// show final links sample
//		LongStream.range( 0, 10 ).map( i -> i * N / 10 ).forEach( i ->
//		{
//			final Matrix row = this.hhNetwork.selectRows( Ret.LINK, A + i );
//			final int attr = this.hhAttributes.getAsInt( i,
//					HHAttribute.ATTRACTOR_REF.ordinal() );
//			final long[] assort = StreamSupport
//					.stream( row.availableCoordinates().spliterator(), false )
//					.mapToLong( x -> x[colDim] )
//					.filter( j -> this.hhAttributes.getAsInt( j,
//							HHAttribute.ATTRACTOR_REF.ordinal() ) == attr )
//					.toArray();
//			final long[] dissort = StreamSupport
//					.stream( row.availableCoordinates().spliterator(), false )
//					.mapToLong( x -> x[colDim] )
//					.filter( j -> this.hhAttributes.getAsInt( j,
//							HHAttribute.ATTRACTOR_REF.ordinal() ) != attr )
//					.toArray();
//			LOG.trace( "hh {} knows {}+{} ({}<>{}): {}+{}", i, assort.length,
//					dissort.length,
//					(double) assort.length / (assort.length + dissort.length),
//					assortativity, assort, dissort );
//		} );

		this.schoolAssortativity = this.config
				.hesitancySchoolAssortativity( this.distParser );
		// TODO schools

		this.attitudeEvaluator = this.config.attitudeEvaluatorType()
				.newInstance();
		this.attitudePropagator = this.config.attitudePropagatorType()
				.newInstance();
		atEach( this.config.vaccinationRecurrence( scheduler() ) )
				.subscribe( this::vaccinate, this::logError );

		// TODO add expressingRefs from own / neighboring / global placeRef dist

		// final Pathogen measles = this.pathogens.create( "MV-1" );

	}

	public Observable<HHStatisticsDao> statistics()
	{
		final UUID contextRef = this.binder.id().contextRef();
		return Observable.create( sub ->
		{
			scheduler().onReset( scheduler ->
			{
				// TODO copy/move completion trigger to Scheduler
				scheduler.time().lastOrError().subscribe( t -> sub.onComplete(),
						sub::onError );
				final Iterable<Instant> when;
				try
				{
					when = this.config.statisticsRecurrence( scheduler() );
				} catch( final ParseException e )
				{
					sub.onError( e );
					return;
				}
				scheduler.atEach( when ).subscribe( t ->
				{
					final int i = this.statsIteration.getAndIncrement();
					final Matrix hhAttributes = this.hhAttributes.clone();
					final Matrix ppAttributes = this.ppAttributes.clone();
					Observable
							.fromIterable(
									new HashSet<Long>( this.hhIndex.values() ) )
							.map( index -> HHStatisticsDao.create( contextRef,
									t, i, hhAttributes, index, ppAttributes ) )
							.subscribe( sub::onNext, sub::onError );
				}, sub::onError, sub::onComplete );
			} );
		} );
	}

	private void migrateHousehold( final Instant t )
	{
		final long hhIndex = this.attractorCount
				+ this.distFactory.getStream().nextLong(
						this.hhAttributes.getRowCount() - this.attractorCount );
//		replaceHousehold(hhIndex,-1 );// FIXME

		final Quantity<Time> dt = this.hhMigrateDist.draw();
//		LOG.trace( "t={}, replace migrant #{}, next after: {}",
//				t.prettify( scheduler().offset() ), hhIndex,
//				QuantityUtil.toScale( dt, 1 ) );
		after( dt ).call( this::migrateHousehold );
	}

	private void vaccinate( final Instant t )
	{
		final VaxOccasion occ = this.vaxOccasionDist.draw();
		final BigDecimal nowYears = now().to( TimeUnits.ANNUM ).decimal();
		final Range<BigDecimal> birthRange = Range
				.of( nowYears.subtract( BigDecimal.valueOf( 4 ) ), nowYears );
		LOG.info( "t={}, vaccination occasion: {} for susceptibles born {}",
				t.prettify( scheduler().offset() ), occ.asMap().values(),
				birthRange );

		this.attitudePropagator.propagate( this.hhNetwork, this.hhAttributes );
//		this.hhNetwork.setAsBigDecimal( BigDecimal.ONE, 0, 0 );
//		this.hhNetwork.zeros( Ret.ORIG );
//		if( this.hhNetwork.getAsBigDecimal( 0, 0 ).signum() != 0 ) Thrower
//				.throwNew( IllegalStateException::new, () -> "reset failed" );

		// for each households evaluated with a positive attitude
		this.attitudeEvaluator.isPositive( occ, this.hhAttributes )

				// for each child position in the positive household
				.forEach( hhRef ->
				{
					LOG.trace( "positive: {}, kid status: {}",
							this.ppAttributes.getAsInt(
									this.hhAttributes.getAsLong( hhRef,
											HHAttribute.CHILD1_REF.ordinal() ),
									HHMemberAttribute.STATUS.ordinal() ) );
					Arrays.stream( CHILD_REF_COLUMN_INDICES )
							.mapToLong( hhAtt -> this.hhAttributes
									.getAsLong( hhRef, hhAtt.ordinal() ) )

							// if child member: 1. exists
							.filter( ppRef -> ppRef != NA

									// 2. is susceptible
									&& this.ppAttributes.getAsInt( ppRef,
											HHMemberAttribute.STATUS
													.ordinal() ) == HHMemberStatus.SUSCEPTIBLE
															.ordinal()

					// 3. is of vaccination age
									&& birthRange.contains( this.ppAttributes
											.getAsBigDecimal( ppRef,
													HHMemberAttribute.BIRTH
															.ordinal() ) ) )
							// then vaccinate
							.forEach( ppRef ->
							{
								this.ppAttributes.setAsInt(
										HHMemberStatus.IMMUNE.ordinal(), ppRef,
										HHMemberAttribute.STATUS.ordinal() );
								LOG.info(
										"Vax! (pos) hh #{} (sus) pp #{} born {}",
										hhRef, ppRef, birthRange );
							} );
				} );
	}

	private static final long NA = -1L;

	private int addHousehold()
	{
		final long id = this.hhCount.incrementAndGet(); // grow the network
		final Actor.ID hhRef = Actor.ID.of( String.format( "hh%08d", id ),
				this.binder.id() );
		final long hhIndex = this.attractorCount + this.hhIndex
				.computeIfAbsent( hhRef, key -> (long) this.hhIndex.size() );
		return replaceHousehold( hhIndex, id );
	}

	private int replaceHousehold( final long hhIndex, final long id )
	{

//		final Cbs71486json.Category hhCat = this.localHouseholdDist
//				.draw( dt() );
		final Quantity<Time> hhRefAge =
//				hhCat.ageDist( this.distFactory::createUniformContinuous ).draw();
				this.hhRefAgeDist.draw();
		final CBSHousehold hhType =
//				hhCat.hhTypeDist( this.distFactory::createCategorical ).draw();
				this.hhTypeDist.draw();
		final Region.ID attractorRef =
//				Region.ID.of( hhCat.regionRef() );
				this.attractorBroker.next( hhIndex );

		final long attractorIndex = Long
				.valueOf( attractorRef.unwrap().substring( 2 ) );
		final boolean religious = this.hhAttributes.getAsBoolean(
				attractorIndex, HHAttribute.RELIGIOUS.ordinal() );
		final boolean alternative = this.hhAttributes.getAsBoolean(
				attractorIndex, HHAttribute.ALTERNATIVE.ordinal() );
		final HesitancyProfileJson hesProf = this.hesitancyProfileDist.draw(
				new HesitancyProfileJson.Category( religious, alternative ) );

		final HHMemberStatus hhStatus = hesProf.status == VaccineStatus.none
				? HHMemberStatus.SUSCEPTIBLE : HHMemberStatus.IMMUNE;
		final long referentRef = createPerson( hhRefAge, hhStatus );

//		final long partnerRef = hhType.adultCount() < 2 ? NA
//				: createPerson(
//						hhRefAge.subtract(
//								QuantityUtil.valueOf( 3, TimeUnits.ANNUM ) ),
//						hhStatus );
		final Quantity<Time> child1Age = hhRefAge.subtract(
				// TODO from distribution, e.g. 60036ned, 37201
				QuantityUtil.valueOf( 20, TimeUnits.ANNUM ) );
		final long child1Ref = hhType.childCount() < 1 ? NA
				: createPerson( child1Age, hhStatus );
//		final long child2Ref = hhType.childCount() < 2 ? NA
//				: createPerson(
//						hhRefAge.subtract(
//								QuantityUtil.valueOf( 22, TimeUnits.ANNUM ) ),
//						hhStatus );
//		final long child3Ref = hhType.childCount() < 3 ? NA
//				: createPerson(
//						hhRefAge.subtract(
//								QuantityUtil.valueOf( 24, TimeUnits.ANNUM ) ),
//						hhStatus );

		// create non-NA household members pressure network, adults impress all
//		final long[] impressedRefs = Arrays
//				.stream( new long[]
//		{ referentRef, partnerRef, child1Ref, child2Ref, child3Ref } )
//				.filter( ref -> ref != NA ).toArray();
//		final long[] expressingRefs = Arrays
//				.stream( new long[]
//		{ referentRef, partnerRef } ).filter( ref -> ref != NA ).toArray();
//		for( long impressedRef : impressedRefs )
//			for( long expressingRef : expressingRefs )
//				this.hhNetwork.setAsBigDecimal( BigDecimal.ONE, impressedRef,
//						expressingRef );

		final BigDecimal initialCalculation = this.calculationDist.draw();
		final Map<HHAttribute, BigDecimal> initialHesitancy = this.hesitancyDist
				.draw( hesProf );

		// set household attribute values
		this.hhAttributes.setAsLong( id, hhIndex,
				HHAttribute.IDENTIFIER.ordinal() );
		this.hhAttributes.setAsLong( attractorIndex, hhIndex,
				HHAttribute.ATTRACTOR_REF.ordinal() );
		this.hhAttributes.setAsBoolean( religious, hhIndex,
				HHAttribute.RELIGIOUS.ordinal() );
		this.hhAttributes.setAsBoolean( alternative, hhIndex,
				HHAttribute.ALTERNATIVE.ordinal() );
		this.hhAttributes.setAsBigDecimal( initialCalculation, hhIndex,
				HHAttribute.CALCULATION.ordinal() );
		this.hhAttributes.setAsBigDecimal(
				initialHesitancy.get( HHAttribute.CONFIDENCE ), hhIndex,
				HHAttribute.CONFIDENCE.ordinal() );
		this.hhAttributes.setAsBigDecimal(
				initialHesitancy.get( HHAttribute.COMPLACENCY ), hhIndex,
				HHAttribute.COMPLACENCY.ordinal() );
		this.hhAttributes.setAsLong( referentRef, hhIndex,
				HHAttribute.REFERENT_REF.ordinal() );
//		this.hhAttributes.setAsLong( partnerRef, hhIndex,
//				HHAttribute.PARTNER_REF.ordinal() );
		this.hhAttributes.setAsLong( child1Ref, hhIndex,
				HHAttribute.CHILD1_REF.ordinal() );
//		this.hhAttributes.setAsLong( child2Ref, hhIndex,
//				HHAttribute.CHILD2_REF.ordinal() );
//		this.hhAttributes.setAsLong( child3Ref, hhIndex,
//				HHAttribute.CHILD3_REF.ordinal() );

		after( this.hhLeaveHomeAge.subtract( child1Age ) ).call( t ->
		{
//			LOG.trace( "t={}, replace home leaver #{}",
//					t.prettify( scheduler().offset() ), hhIndex );
			// FIXME replace persons too! 
			// replaceHousehold( hhIndex, id );
		} );

		return hhType.size();
	}

	private long createPerson( final Quantity<Time> initialAge,
		final HHMemberStatus status )
	{
		final long id = this.persons.incrementAndGet();
		final Actor.ID memberRef = Actor.ID.of( String.format( "pp%08d", id ),
				this.binder.id() );
		final long index = toMemberIndex( memberRef );
		this.ppAttributes.setAsBigDecimal(
				now().to( TimeUnits.ANNUM ).subtract( initialAge ).decimal(),
				index, HHMemberAttribute.BIRTH.ordinal() );
		this.ppAttributes.setAsInt( status.ordinal(), index,
				HHMemberAttribute.STATUS.ordinal() );
		this.ppAttributes.setAsInt( HHMemberBehavior.NORMAL.ordinal(), index,
				HHMemberAttribute.BEHAVIOR.ordinal() );
		return index;
	}

	@Override
	public Scheduler scheduler()
	{
		return this.scheduler;
	}

	protected LocalDate dt()
	{
		// FIXME fix daylight savings adjustment, seems to adjust the wrong way
		return now().equals( this.dtInstant ) ? this.dtCache
				: (this.dtCache = (this.dtInstant = now())
						.toJava8( scheduler().offset().toLocalDate() ));
	}

	private final Map<Actor.ID, Long> hhIndex = new HashMap<>();

	private final Map<Actor.ID, Long> ppIndex = new HashMap<>();

	private long toMemberIndex( final Actor.ID hhRef )
	{
		return this.ppIndex.computeIfAbsent( hhRef,
				key -> (long) this.ppIndex.size() );
	}

	private void logError( final Throwable e )
	{
		LOG.error( "Problem", e );
	}
}
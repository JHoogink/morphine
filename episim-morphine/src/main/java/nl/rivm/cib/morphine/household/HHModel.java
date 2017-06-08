package nl.rivm.cib.morphine.household;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.measure.Quantity;
import javax.measure.quantity.Time;
import javax.persistence.EntityManagerFactory;

import org.apache.logging.log4j.Logger;
import org.ujmp.core.Matrix;
import org.ujmp.core.SparseMatrix;
import org.ujmp.core.enums.ValueType;

import io.coala.bind.InjectConfig;
import io.coala.bind.InjectConfig.Scope;
import io.coala.bind.LocalBinder;
import io.coala.enterprise.Actor;
import io.coala.log.LogUtil;
import io.coala.math.DecimalUtil;
import io.coala.math.QuantityUtil;
import io.coala.math.Range;
import io.coala.math.Tuple;
import io.coala.persist.JPAUtil;
import io.coala.random.ConditionalDistribution;
import io.coala.random.ProbabilityDistribution;
import io.coala.random.QuantityDistribution;
import io.coala.time.Instant;
import io.coala.time.Scenario;
import io.coala.time.Scheduler;
import io.coala.time.TimeUnits;
import nl.rivm.cib.epidemes.cbs.json.CBSGender;
import nl.rivm.cib.epidemes.cbs.json.CBSHousehold;
import nl.rivm.cib.episim.model.locate.Region;
import nl.rivm.cib.episim.model.vaccine.attitude.VaxOccasion;
import nl.rivm.cib.morphine.dao.HHStatisticsDao;
import nl.rivm.cib.morphine.profile.HesitancyProfileJson;
import nl.rivm.cib.morphine.profile.HesitancyProfileJson.VaccineStatus;

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
			HHAttribute.CHILD1_REF, HHAttribute.CHILD2_REF,
			HHAttribute.CHILD3_REF };

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

	@Inject
	private transient EntityManagerFactory emf;

	/** current (cached) virtual time instant */
	private transient Instant dtInstant = null;
	/** current (cached) date/time */
	private transient LocalDate dtCache = null;
	/** */
	private Matrix hhAttributes;
	/** */
	private Matrix hhNetwork;
	/** */
	private Matrix ppAttributes;
	/** */
	private final AtomicLong edges = new AtomicLong();
	/** */
	private final AtomicLong persons = new AtomicLong();
	/** number of top rows (0..n) in {@link #hhNetwork} reserved for oracles */
	private long oracleCount;
	/** virtual time range of simulation */
//	private transient Range<LocalDate> timeRange = null;
	/** empirical household compositions and referent ages, see CBS 71486 */
//	private transient ConditionalDistribution<Cbs71486json.Category, LocalDate> localHouseholdDist;
	/** zip-codes per borough / ward / municipality / ... */
//	private transient ConditionalDistribution<CbsNeighborhood, Region.ID> hoodDist;

	/** */
	private RegionBroker oracleRegionBroker;
	/** */
	private ProbabilityDistribution<CBSHousehold> hhTypeDist;
	/** */
	private QuantityDistribution<Time> hhRefAgeDist;
	/** */
	private QuantityDistribution<Time> hhReplaceDist;
	/** */
	private Quantity<Time> hhAgeMax;

	/** */
	private transient ConditionalDistribution<HesitancyProfileJson, HesitancyProfileJson.Category> hesitancyProfileDist;
	/** */
	private transient ProbabilityDistribution<BigDecimal> calculationDist;
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

		final List<HHOracle> oracles = this.config
				.hesitancyOracles( this.binder ).toList().blockingGet();
		this.oracleCount = oracles.size();

		final CBSHousehold hhType = this.config
				.householdTypeDist( this.distParser ).draw(); // assuming constant
		final long ppTotal = this.config.populationSize(),
				hhTotal = ppTotal / hhType.size(),
				edgeTotal = hhTotal + this.oracleCount;
		LOG.trace( "#persons: {}, #hh: {}, #oracles: {}, #edges (max): {}",
				ppTotal, hhTotal, this.oracleCount, edgeTotal );

		// or Matrix.Factory.linkToJDBC(host, port, db, table, user, password)
		// or Matrix.Factory.linkTo().file("hugeCSVFile").asDenseCSV(columnSeparator)
		this.hhAttributes = Matrix.Factory.zeros( ValueType.BIGDECIMAL,
				edgeTotal, HHAttribute.values().length );
		this.ppAttributes = Matrix.Factory.zeros( ValueType.BIGDECIMAL, ppTotal,
				HHMemberAttribute.values().length );
		this.hhNetwork = SparseMatrix.Factory.zeros( //ValueType.BIGDECIMAL,
				edgeTotal, edgeTotal );

		oracles.forEach( oracle ->
		{
			final long index = this.edges.getAndIncrement();
			oracle.position().subscribe( map ->
			{
				LOG.info( "t={}, oracle {}: {}", dt(), index, map );
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
		this.oracleRegionBroker = hhIndex -> regions.computeIfAbsent(
				hhIndex % this.oracleCount, key -> Region.ID.of( "or" + key ) );
		this.hhTypeDist = this.config.householdTypeDist( this.distParser );
		this.hhRefAgeDist = this.config
				.householdReferentAgeDist( this.distParser );
//		this.peerPressureIntervalDist = this.config
//				.peerPressureInterval( this.distFactory );

		this.hesitancyProfileDist = this.config.hesitancyProfilesGrouped(
				this.distFactory, HesitancyProfileJson::toCategory );
		this.calculationDist = this.config
				.hesitancyCalculationDist( this.distParser );

		this.hhAgeMax = this.config.householdReplacementAge();
		this.hhReplaceDist = this.config
				.householdReplacementDist( this.distFactory, hhTotal );
		after( this.hhReplaceDist.draw() ).call( this::migrateHousehold );

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
		for( long time = System
				.currentTimeMillis(), i = 0, agPrev = 0, pp = 0; i < ppTotal; i += pp )
		{
			pp = addHousehold();
			if( System.currentTimeMillis() - time > 1000 )
			{
				time = System.currentTimeMillis();
				long agNow = this.persons.get() + this.edges.get();
				LOG.info(
						"Initialized {} pp ({}%) across {} hh (= +{} actors/sec)",
						this.persons.get(), this.persons.get() * 100 / ppTotal,
						this.edges.get(), agNow - agPrev );
				agPrev = agNow;
			}
		}

		this.attitudeEvaluator = this.config.attitudeEvaluatorType()
				.newInstance();
		this.attitudePropagator = this.config.attitudePropagatorType()
				.newInstance();
		atEach( this.config.vaccinationRecurrence( scheduler() ) )
				.subscribe( this::vaccinate, this::logError );

		// TODO add expressingRefs from own / neighboring / global placeRef dist

		LOG.info( "Initialized {} pp ({}%) across {} hh in {} oracles/regions",
				this.persons.get(), this.persons.get() * 100 / ppTotal,
				this.edges.get() - this.oracleCount, this.oracleCount );

		// final Pathogen measles = this.pathogens.create( "MV-1" );

		atEach( this.config.statisticsRecurrence( scheduler() ) )
				.subscribe( this::exportStatistics, this::logError );
	}

	private void exportStatistics( final Instant t )
	{
		final long start = System.currentTimeMillis();
		JPAUtil.session( this.emf ).subscribe(
				em -> this.hhIndex
						.forEach( ( id,
							index ) -> em.persist(
									HHStatisticsDao.create( id.contextRef(), t,
											this.hhAttributes, index,
											this.ppAttributes ) ) ),
				this::logError,
				() -> LOG.info( "t={}, persisted {} households in {}s",
						t.prettify( scheduler().offset() ), this.hhIndex.size(),
						DecimalUtil.toScale(
								(System.currentTimeMillis() - start) / 1000.,
								1 ) ) );
	}

	private void migrateHousehold( final Instant t )
	{
		final long hhIndex = this.oracleCount + this.distFactory.getStream()
				.nextLong( this.hhAttributes.getRowCount() - this.oracleCount );
		LOG.trace( "t={}, replace migraters #{}",
				t.prettify( scheduler().offset() ), hhIndex ); // FIXME
		after( this.hhReplaceDist.draw() ).call( this::migrateHousehold );
	}

	private void vaccinate( final Instant t )
	{
//		this.attitudePropagator.propagate( this.hhNetwork, this.hhAttributes );
//		this.hhNetwork.setAsBigDecimal( BigDecimal.ONE, 0, 0 );
//		this.hhNetwork.zeros( Ret.ORIG );
//		if( this.hhNetwork.getAsBigDecimal( 0, 0 ).signum() != 0 ) Thrower
//				.throwNew( IllegalStateException::new, () -> "reset failed" );

		final VaxOccasion occ = this.vaxOccasionDist.draw();
		final BigDecimal nowYears = now().to( TimeUnits.ANNUM ).decimal();
		final Range<BigDecimal> birthRange = Range
				.of( nowYears.subtract( BigDecimal.valueOf( 4 ) ), nowYears );
		LOG.info( "t={}, vaccination occasion: {} for susceptibles born {}",
				t.prettify( scheduler().offset() ), occ, birthRange );

		// for each households evaluated with a positive attitude
		this.attitudeEvaluator.isPositive( occ, this.hhAttributes )

				// for each child position in the positive household
				.forEach( hhRef -> Arrays.stream( CHILD_REF_COLUMN_INDICES )
						.mapToLong( hhAtt -> this.hhAttributes.getAsLong( hhRef,
								hhAtt.ordinal() ) )

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
							LOG.info( "Vax! (pos) hh #{} (sus) pp #{} born {}",
									hhRef, ppRef, birthRange );
						} ) );
	}

	private static final long NA = -1L;

	private int addHousehold()
	{
		final long id = this.edges.incrementAndGet();
		final Actor.ID hhRef = Actor.ID.of( String.format( "hh%08d", id ),
				this.binder.id() );
		final long hhIndex = toHouseholdIndex( hhRef );
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
		final Region.ID placeRef =
//				Region.ID.of( hhCat.regionRef() );
				this.oracleRegionBroker.next( hhIndex );

		// TODO from localized dist
		final boolean religious = true;
		final VaccineStatus status = VaccineStatus.all;
		final HesitancyProfileJson hesProf = this.hesitancyProfileDist
				.draw( new HesitancyProfileJson.Category( religious, // alternative, 
						status ) );

		final long placeIndex = toPlaceIndex( placeRef );
		final HHMemberStatus hhStatus = status != VaccineStatus.none
				? HHMemberStatus.IMMUNE : HHMemberStatus.SUSCEPTIBLE;
		final long referentRef = createPerson( hhRefAge, hhStatus );

		// TODO draw age differences empirically (e.g. CBS: 60036ned, 37201)
		final long partnerRef = hhType.adultCount() < 2 ? NA
				: createPerson(
						hhRefAge.subtract(
								QuantityUtil.valueOf( 3, TimeUnits.ANNUM ) ),
						hhStatus );
		final long child1Ref = hhType.childCount() < 1 ? NA
				: createPerson(
						hhRefAge.subtract(
								QuantityUtil.valueOf( 20, TimeUnits.ANNUM ) ),
						hhStatus );
		final long child2Ref = hhType.childCount() < 2 ? NA
				: createPerson(
						hhRefAge.subtract(
								QuantityUtil.valueOf( 22, TimeUnits.ANNUM ) ),
						hhStatus );
		final long child3Ref = hhType.childCount() < 3 ? NA
				: createPerson(
						hhRefAge.subtract(
								QuantityUtil.valueOf( 24, TimeUnits.ANNUM ) ),
						hhStatus );

		// create non-NA household members pressure network, adults impress all
		final long[] impressedRefs = Arrays
				.stream( new long[]
		{ referentRef, partnerRef, child1Ref, child2Ref, child3Ref } )
				.filter( ref -> ref != NA ).toArray();
		final long[] expressingRefs = Arrays
				.stream( new long[]
		{ referentRef, partnerRef } ).filter( ref -> ref != NA ).toArray();
		for( long impressedRef : impressedRefs )
			for( long expressingRef : expressingRefs )
				this.hhNetwork.setAsBigDecimal( BigDecimal.ONE, impressedRef,
						expressingRef );

		final BigDecimal initialCalculation = this.calculationDist.draw();
		final Map<HHAttribute, BigDecimal> initialHesitancy = this.hesitancyDist
				.draw( hesProf );

		// set household attribute values
		this.hhAttributes.setAsLong( id, hhIndex,
				HHAttribute.IDENTIFIER.ordinal() );
		this.hhAttributes.setAsLong( placeIndex, hhIndex,
				HHAttribute.HOME_REF.ordinal() );
		this.hhAttributes.setAsBoolean( religious, hhIndex,
				HHAttribute.RELIGIOUS.ordinal() );
		this.hhAttributes.setAsBoolean( hesProf.alternative, hhIndex,
				HHAttribute.ALTERNATIVE.ordinal() );
		this.hhAttributes.setAsBoolean( hhType.registered(), hhIndex,
				HHAttribute.REGISTERED.ordinal() );
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
		this.hhAttributes.setAsLong( partnerRef, hhIndex,
				HHAttribute.PARTNER_REF.ordinal() );
		this.hhAttributes.setAsLong( child1Ref, hhIndex,
				HHAttribute.CHILD1_REF.ordinal() );
		this.hhAttributes.setAsLong( child2Ref, hhIndex,
				HHAttribute.CHILD2_REF.ordinal() );
		this.hhAttributes.setAsLong( child3Ref, hhIndex,
				HHAttribute.CHILD3_REF.ordinal() );

//		after( Duration.ZERO ).call( t -> peerPressure( hhIndex ) );

		// evaluate current barrier value
		// this.hhAttributes.setAsBigDecimal(
		// this.barrierEvaluator.barrierOf(
		// this.hhAttributes.selectRows( Ret.LINK, hhIndex ) ),
		// hhIndex, HHAttribute.BARRIER.ordinal() );

		after( this.hhAgeMax.subtract( hhRefAge ) ).call( t ->
		{
			LOG.trace( "t={}, replace home leaver #{}",
					t.prettify( scheduler().offset() ), hhIndex );
			// FIXME replace persons too! 
			// replaceHousehold( hhIndex, id );
		} );

		return hhType.size();
	}

//	private void peerPressure( final long hhIndex )
//	{
//		final long refRef = this.hhAttributes.getAsLong( hhIndex,
//				HHAttribute.REFERENT_REF.ordinal() );
//		final BigDecimal age = now().to( TimeUnits.ANNUM ).decimal()
//				.subtract( this.ppAttributes.getAsBigDecimal( refRef,
//						HHMemberAttribute.BIRTH.ordinal() ) );
//		final CBSGender gender = this.ppAttributes.getAsBoolean( refRef,
//				HHMemberAttribute.MALE.ordinal() ) ? CBSGender.MALE
//						: CBSGender.FEMALE;
//		after( this.peerPressureIntervalDist
//				.draw( new GenderAge( gender, age ) ) )
//						.call( t -> peerPressure( hhIndex ) );
//	}

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

	private long toHouseholdIndex( final Actor.ID hhRef )
	{
		return this.hhIndex.computeIfAbsent( hhRef,
				key -> (long) this.hhIndex.size() );
	}

	private final Map<Actor.ID, Long> ppIndex = new HashMap<>();

	private long toMemberIndex( final Actor.ID hhRef )
	{
		return this.ppIndex.computeIfAbsent( hhRef,
				key -> (long) this.ppIndex.size() );
	}

	// private final Map<Region.ID, Long> placeIndex = new HashMap<>();

	private long toPlaceIndex( final Region.ID placeRef )
	{
		return Long.valueOf( placeRef.unwrap().substring( 2 ) );
		// return this.placeIndex.computeIfAbsent( placeRef,
		// key -> (long) this.placeIndex.size() );
	}

	private void logError( final Throwable e )
	{
		LOG.error( "Problem", e );
	}
}
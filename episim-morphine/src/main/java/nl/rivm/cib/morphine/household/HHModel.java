package nl.rivm.cib.morphine.household;

import java.math.BigDecimal;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
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
import org.ujmp.core.calculation.Calculation.Ret;

import io.coala.bind.InjectConfig;
import io.coala.bind.InjectConfig.Scope;
import io.coala.bind.LocalBinder;
import io.coala.enterprise.Actor;
import io.coala.log.LogUtil;
import io.coala.math.DecimalUtil;
import io.coala.math.QuantityUtil;
import io.coala.math.Range;
import io.coala.persist.JPAUtil;
import io.coala.random.ConditionalDistribution;
import io.coala.random.ProbabilityDistribution;
import io.coala.time.Instant;
import io.coala.time.Scenario;
import io.coala.time.Scheduler;
import io.coala.time.TimeUnits;
import nl.rivm.cib.epidemes.cbs.json.CBSHousehold;
import nl.rivm.cib.epidemes.cbs.json.Cbs71486json;
import nl.rivm.cib.episim.model.locate.Region;
import nl.rivm.cib.episim.model.vaccine.attitude.VaxOccasion;
import nl.rivm.cib.morphine.pienter.HesitancyProfileJson;
import nl.rivm.cib.morphine.pienter.HesitancyProfileJson.HesitancyDimension;
import nl.rivm.cib.morphine.pienter.HesitancyProfileJson.VaccineStatus;

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

	/** virtual time range of simulation */
	private transient Range<LocalDate> timeRange = null;
	/** current (cached) virtual time instant */
	private transient Instant dtInstant = null;
	/** current (cached) date/time */
	private transient LocalDate dtCache = null;

	private Matrix hhAttributes;
	private Matrix hhPressure;
	private Matrix ppAttributes;
	private final AtomicLong households = new AtomicLong();
	private final AtomicLong persons = new AtomicLong();
	private long oracleCount;

	/** empirical household compositions and referent ages, see CBS 71486 */
	private transient ConditionalDistribution<Cbs71486json.Category, LocalDate> localHouseholdDist;

	private transient ProbabilityDistribution<HesitancyProfileJson> hesitancyDist;
	private transient ProbabilityDistribution<BigDecimal> calculationDist;
	private transient HHAttitudeEvaluator attitudeEvaluator;
	private transient HHAttitudePropagator attitudePropagator;

	private transient ProbabilityDistribution<VaxOccasion> vaxOccasionDist;

	@Override
	public void init()
		throws ParseException, InstantiationException, IllegalAccessException
	{
		final int n = this.config.populationSize();
		this.hhAttributes = SparseMatrix.Factory.zeros( n,
				HHAttribute.values().length );
		this.ppAttributes = SparseMatrix.Factory.zeros( 3 * n,
				HHMemberAttribute.values().length );
		this.hhPressure = SparseMatrix.Factory.zeros( 3 * n, 3 * n );

		this.timeRange = Range
				.upFromAndIncluding( scheduler().offset().toLocalDate() );
		this.localHouseholdDist = ConditionalDistribution.of(
				this.distFactory::createCategorical,
				this.config.cbs71486( this.timeRange ) );

		this.hesitancyDist = this.config.hesitancyProfiles( this.distFactory );
		this.calculationDist = this.config
				.hesitancyCalculationDist( this.distParser );

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

//		final HHOracle.Factory oracleFact = 
		this.config.hesitancyOracles( this.binder ).forEach( oracle ->
		{
			final long index = this.households.getAndIncrement();
			oracle.position().subscribe( map ->
			{
				map.forEach( ( att, val ) -> this.hhAttributes
						.setAsBigDecimal( val, index, att.ordinal() ) );
				LOG.info( "t={}, oracle {}: {}", dt(), index, map );
			}, this::logError );
		} );
		this.oracleCount = this.households.get();

		// populate households
		for( long time = System
				.currentTimeMillis(), i = 0, agPrev = 0; i < n; i++ )
		{
			createHousehold();
			if( System.currentTimeMillis() - time > 1000 )
			{
				time = System.currentTimeMillis();
				long agNow = this.persons.get() + this.households.get();
				LOG.info(
						"Initialized {} pp ({}%) across {} hh (= +{} actors/sec)",
						this.persons.get(), this.persons.get() * 100 / n,
						this.households.get(), agNow - agPrev );
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

		LOG.info( "Initialized {} pp ({}%) across {} hh", this.persons.get(),
				this.persons.get() * 100 / n, this.households.get() );

//		final Pathogen measles = this.pathogens.create( "MV-1" );

		atEach( this.config.statisticsRecurrence( scheduler() ) )
				.subscribe( this::exportStatistics, this::logError );
	}

	private void impress( final Instant t, final long... hhFilter )
	{
		this.attitudePropagator.propagate(
				hhFilter == null ? // impress all  
						this.hhPressure
						// impress selected only
						: this.hhPressure.selectRows( Ret.LINK, hhFilter ),
				this.hhAttributes );
	}

	private static final HHAttribute[] CHILD_REF_COLUMN_INDICES = {
//			HHAttribute.REFERENT_REF, 
//			HHAttribute.PARTNER_REF, 
			HHAttribute.CHILD1_REF, HHAttribute.CHILD2_REF,
			HHAttribute.CHILD3_REF };

	private void vaccinate( final Instant t )
	{
		final VaxOccasion occ = this.vaxOccasionDist.draw();
		final BigDecimal nowYears = now().to( TimeUnits.ANNUM ).decimal();
		final Range<BigDecimal> birthRange = Range
				.of( nowYears.subtract( BigDecimal.valueOf( 4 ) ), nowYears );
		LOG.info( "t={}, vax members positive on {}, susceptible, born {}",
				t.prettify( scheduler().offset().toLocalDate() ), occ,
				birthRange );

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
						.forEach( ppRef -> vaccinate( hhRef, ppRef,
								birthRange ) ) );
	}

	private void vaccinate( final long hhRef, final long ppRef,
		final Range<BigDecimal> birthRange )
	{
		LOG.info( "Vax in (positive) hh #{} (susceptible) pp #{} born {}...",
				hhRef, ppRef, birthRange );
	}

	private static final long NA = -1L;

	private int createHousehold()
	{
		final HesitancyProfileJson hesProf = this.hesitancyDist.draw();
		final Cbs71486json.Category hhCat = this.localHouseholdDist
				.draw( dt() );
		final Quantity<Time> hhRefAge = hhCat
				.ageDist( this.distFactory::createUniformContinuous ).draw();
		final CBSHousehold hhType = hhCat
				.hhTypeDist( this.distFactory::createCategorical ).draw();

		final long id = this.households.incrementAndGet();
		final Actor.ID hhRef = Actor.ID.of( String.format( "hh%08d", id ),
				this.binder.id() );
		final long hhIndex = this.toHouseholdIndex( hhRef );

		final Region.ID placeRef = Region.ID.of( hhCat.regionRef() );
		final long placeIndex = toPlaceIndex( placeRef );
		final HHMemberStatus hhStatus = hesProf.status != VaccineStatus.none
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
				this.hhPressure.setAsBigDecimal( BigDecimal.ONE, impressedRef,
						expressingRef );

		final BigDecimal initialCalculation = this.calculationDist.draw();
		final BigDecimal initialConfidence = DecimalUtil.valueOf(
				hesProf.distParams.get( HesitancyDimension.confidence )
						.createDist( this.distFactory ).draw() );
		final BigDecimal initialComplacency = DecimalUtil.valueOf(
				hesProf.distParams.get( HesitancyDimension.complacency )
						.createDist( this.distFactory ).draw() );

		// set household attribute values
		this.hhAttributes.setAsLong( id, hhIndex,
				HHAttribute.IDENTIFIER.ordinal() );
		this.hhAttributes.setAsLong( placeIndex, hhIndex,
				HHAttribute.HOME_REF.ordinal() );
		this.hhAttributes.setAsBoolean( hesProf.religious, hhIndex,
				HHAttribute.RELIGIOUS.ordinal() );
		this.hhAttributes.setAsBoolean( hesProf.alternative, hhIndex,
				HHAttribute.ALTERNATIVE.ordinal() );
		this.hhAttributes.setAsBoolean( hhType.registered(), hhIndex,
				HHAttribute.REGISTERED.ordinal() );
		this.hhAttributes.setAsBigDecimal( initialCalculation, hhIndex,
				HHAttribute.CALCULATION.ordinal() );
		this.hhAttributes.setAsBigDecimal( initialConfidence, hhIndex,
				HHAttribute.CONFIDENCE.ordinal() );
		this.hhAttributes.setAsBigDecimal( initialComplacency, hhIndex,
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

		// evaluate current barrier value
//		this.hhAttributes.setAsBigDecimal(
//				this.barrierEvaluator.barrierOf(
//						this.hhAttributes.selectRows( Ret.LINK, hhIndex ) ),
//				hhIndex, HHAttribute.BARRIER.ordinal() );

		return hhType.size();
	}

	private long createPerson( final Quantity<Time> initialAge,
		final HHMemberStatus status )
	{
		final long id = this.persons.incrementAndGet();
		final Actor.ID memberRef = Actor.ID.of( String.format( "ind%08d", id ),
				this.binder.id() );
		final long index = this.toMemberIndex( memberRef );
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

//	private final Map<Region.ID, Long> placeIndex = new HashMap<>();

	private long toPlaceIndex( final Region.ID placeRef )
	{
		return Long.valueOf( placeRef.unwrap().substring( 2 ) );
//		return this.placeIndex.computeIfAbsent( placeRef,
//				key -> (long) this.placeIndex.size() );
	}

	private void exportStatistics( final Instant t )
	{
		JPAUtil.session( this.emf ).subscribe(
				em -> this.hhIndex.forEach( ( id,
					index ) -> em.persist( HHStatisticsDao.create(
							id.contextRef(), t, this.hhAttributes, index,
							this.ppAttributes ) ) ),
				this::logError,
				() -> LOG.info( "t = {} ({}), persisted {} households",
						t.prettify( TimeUnits.DAYS, 1 ),
						t.prettify( scheduler().offset() ),
						this.hhIndex.size() ) );
	}

	private void logError( final Throwable e )
	{
		LOG.error( "Problem", e );
	}
}
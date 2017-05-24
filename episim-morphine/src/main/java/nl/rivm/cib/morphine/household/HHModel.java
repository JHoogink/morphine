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

import org.apache.logging.log4j.Logger;
import org.ujmp.core.Matrix;
import org.ujmp.core.SparseMatrix;

import io.coala.bind.InjectConfig;
import io.coala.bind.InjectConfig.Scope;
import io.coala.bind.LocalBinder;
import io.coala.enterprise.Actor;
import io.coala.log.LogUtil;
import io.coala.math.DecimalUtil;
import io.coala.math.QuantityUtil;
import io.coala.math.Range;
import io.coala.random.ConditionalDistribution;
import io.coala.random.ProbabilityDistribution;
import io.coala.time.Instant;
import io.coala.time.Scenario;
import io.coala.time.Scheduler;
import io.coala.time.TimeUnits;
import io.coala.time.Timing;
import nl.rivm.cib.epidemes.cbs.json.CBSHousehold;
import nl.rivm.cib.epidemes.cbs.json.Cbs71486json;
import nl.rivm.cib.episim.model.locate.Region;
import nl.rivm.cib.episim.model.vaccine.attitude.VaxHesitancy;
import nl.rivm.cib.morphine.pienter.HesitancyProfile;
import nl.rivm.cib.morphine.pienter.HesitancyProfile.HesitancyDimension;

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
	private transient Scheduler scheduler;

	/** virtual time range of simulation */
	private transient Range<LocalDate> timeRange = null;
	/** current (cached) virtual time instant */
	private transient Instant dtInstant = null;
	/** current (cached) date/time */
	private transient LocalDate dtCache = null;

	@Inject
	private transient ProbabilityDistribution.Factory distFactory;

	private Matrix hhAttributes;
	private Matrix hhPressure;
	private Matrix ppAttributes;
	private final AtomicLong households = new AtomicLong();
	private final AtomicLong persons = new AtomicLong();

	/** empirical household compositions and referent ages, see CBS 71486 */
	private transient ConditionalDistribution<Cbs71486json.Category, LocalDate> localHouseholdDist;

	private transient ProbabilityDistribution<HesitancyProfile> hesitancyDist;

	@Override
	public void init() throws ParseException
	{
		final int n = this.config.populationSize();
		this.hhAttributes = SparseMatrix.Factory.zeros( n,
				HHAttribute.values().length );
		this.ppAttributes = SparseMatrix.Factory.zeros( n,
				HHMemberAttribute.values().length );
		this.hhPressure = SparseMatrix.Factory.zeros( n, n );

		this.timeRange = Range
				.upFromAndIncluding( scheduler().offset().toLocalDate() );
		this.localHouseholdDist = ConditionalDistribution.of(
				this.distFactory::createCategorical,
				this.config.cbs71486( this.timeRange ) );

		this.hesitancyDist = this.distFactory
				.createCategorical( this.config.hesitancyProfiles() );

		// populate households
		for( long time = System
				.currentTimeMillis(), i = 0, agPrev = 0; i < n; i += createHousehold() )
		{
			if( System.currentTimeMillis() - time > 1000 )
			{
				time = System.currentTimeMillis();
				long agNow = this.persons.get() + this.households.get();
				LOG.info( "Added #pp: {} ({}%) across {} hh (= +{} actors/sec)",
						this.persons.get(), this.persons.get() * 100 / n,
						this.households.get(), agNow - agPrev );
				agPrev = agNow;
			}
		}
		LOG.info( "Initialized #pp: {} across {} hh", this.persons.get(), n,
				this.households.get() );

//		final Pathogen measles = this.pathogens.create( "MV-1" );

		atEach( Timing.of( this.config.statisticsRecurrence() )
				.offset( scheduler().offset() ).iterate() )
						.subscribe( this::exportStatistics, this::logError );
	}

	private static final long NA = -1L;

	private int createHousehold()
	{
		final HesitancyProfile hesProf = this.hesitancyDist.draw();
		final Cbs71486json.Category hhCat = this.localHouseholdDist
				.draw( dt() );
		final Quantity<Time> hhRefAge = hhCat
				.ageDist( this.distFactory::createUniformContinuous ).draw();
		final CBSHousehold hhType = hhCat
				.hhTypeDist( this.distFactory::createCategorical ).draw();

		final long id = this.households.incrementAndGet();
		final Actor.ID hhRef = Actor.ID.of( String.format( "hh%08d", id ),
				null );
		final long hhIndex = this.toHouseholdIndex( hhRef );

		final Region.ID placeRef = Region.ID.of( hhCat.regionRef() );
		final long placeIndex = toIndex( placeRef );

		final long referentRef = createPerson( hhRefAge );
		// TODO draw age differences empirically (e.g. CBS: 60036ned, 37201)
		final long partnerRef = hhType.adultCount() < 2 ? NA
				: createPerson( hhRefAge.subtract(
						QuantityUtil.valueOf( 3, TimeUnits.ANNUM ) ) );
		final long child1Ref = hhType.childCount() < 1 ? NA
				: createPerson( hhRefAge.subtract(
						QuantityUtil.valueOf( 20, TimeUnits.ANNUM ) ) );
		final long child2Ref = hhType.childCount() < 2 ? NA
				: createPerson( hhRefAge.subtract(
						QuantityUtil.valueOf( 22, TimeUnits.ANNUM ) ) );
		final long child3Ref = hhType.childCount() < 3 ? NA
				: createPerson( hhRefAge.subtract(
						QuantityUtil.valueOf( 24, TimeUnits.ANNUM ) ) );

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
		// TODO add expressingRefs from own / neighboring / global placeRef dist

		final BigDecimal initialCalculation = // TODO draw
				DecimalUtil.ONE_HALF;
		final BigDecimal initialConfidence = DecimalUtil.valueOf(
				hesProf.distParams.get( HesitancyDimension.confidence )
						.createDist( this.distFactory ).draw() );
		final BigDecimal initialComplacency = DecimalUtil.valueOf(
				hesProf.distParams.get( HesitancyDimension.complacency )
						.createDist( this.distFactory ).draw() );
		final BigDecimal initialBarrier = VaxHesitancy // TODO from config
				.averageBarrier( initialConfidence, initialComplacency );

		this.hhAttributes.setAsLong( id, hhIndex,
				HHAttribute.IDENTIFIER.ordinal() );
		this.hhAttributes.setAsLong( placeIndex, hhIndex,
				HHAttribute.PLACE_REF.ordinal() );
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
		this.hhAttributes.setAsBigDecimal( initialBarrier, hhIndex,
				HHAttribute.BARRIER.ordinal() );
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

		return hhType.size();
	}

	private long createPerson( final Quantity<Time> initialAge )
	{
		final long id = this.persons.incrementAndGet();
		final Actor.ID memberRef = Actor.ID.of( String.format( "ind%08d", id ),
				null );
		final long index = this.toMemberIndex( memberRef );
		this.ppAttributes.setAsBigDecimal(
				now().subtract( initialAge ).decimal(), index,
				HHMemberAttribute.BIRTH.ordinal() );
		this.ppAttributes.setAsInt( HHMemberStatus.SUSCEPTIBLE.ordinal(), index,
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

	private final Map<Region.ID, Long> placeIndex = new HashMap<>();

	private long toIndex( final Region.ID placeRef )
	{
		return this.placeIndex.computeIfAbsent( placeRef,
				key -> (long) this.placeIndex.size() );
	}

	private void exportStatistics( final Instant t )
	{
		// FIXME output to database, CSV or other export format
		LOG.info( "t = {} ({})", t.prettify( TimeUnits.DAY, 1 ),
				t.prettify( scheduler().offset() ) );
	}

	private void logError( final Throwable e )
	{
		LOG.error( "Problem", e );
	}
}
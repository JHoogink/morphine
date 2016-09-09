package nl.rivm.cib.morphine;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.measure.quantity.Frequency;
import javax.measure.unit.NonSI;
import javax.measure.unit.Unit;

import org.aeonbits.owner.ConfigCache;
import org.apache.logging.log4j.Logger;
import org.jscience.physics.amount.Amount;

import io.coala.bind.LocalConfig;
import io.coala.config.GlobalConfig;
import io.coala.config.InjectConfig;
import io.coala.dsol3.Dsol3Scheduler;
import io.coala.guice4.Guice4LocalBinder;
import io.coala.log.LogUtil;
import io.coala.math3.Math3ProbabilityDistribution;
import io.coala.math3.Math3PseudoRandom;
import io.coala.random.AmountDistribution;
import io.coala.random.DistributionParser;
import io.coala.random.ProbabilityDistribution;
import io.coala.random.PseudoRandom;
import io.coala.rx.RxCollection;
import io.coala.time.Duration;
import io.coala.time.Instant;
import io.coala.time.ReplicateConfig;
import io.coala.time.Scheduler;
import io.coala.time.Units;
import net.jodah.concurrentunit.Waiter;
import nl.rivm.cib.episim.model.Gender;
import nl.rivm.cib.episim.model.disease.Condition;
import nl.rivm.cib.episim.model.disease.Disease;
import nl.rivm.cib.episim.model.disease.infection.ContactIntensity;
import nl.rivm.cib.episim.model.disease.infection.Infection;
import nl.rivm.cib.episim.model.disease.infection.TransmissionRoute;
import nl.rivm.cib.episim.model.person.Household;
import nl.rivm.cib.episim.model.person.HouseholdPopulation;
import nl.rivm.cib.episim.model.scenario.Scenario;

/**
 * {@link SimpleModel}
 * 
 * @version $Id$
 * @author hooginkj
 */
@Singleton
public class SimpleModel implements Scenario
{
	/** */
	private static final Logger LOG = LogUtil.getLogger( SimpleModel.class );

	/**
	 * {@link SimpleConfig}
	 * 
	 * @version $Id$
	 * @author Rick van Krevelen
	 */
	public static interface SimpleConfig extends GlobalConfig
	{
		@DefaultValue( "10 h" )
		Duration contactPeriod();

		@DefaultValue( "uniform-discrete(-5;0)" )
		String birthDist();

		@DefaultValue( "2 day" )
		Duration latentPeriodConst();

		@DefaultValue( "5 day" )
		Duration recoverPeriodConst();

		@DefaultValue( "9999 day" )
		Duration wanePeriodConst();

		@DefaultValue( "3 day" )
		Duration onsetPeriodConst();

		@DefaultValue( "7 day" )
		Duration symptomPeriodConst();
	}

	@InjectConfig
	private SimpleConfig config;

	@Inject
	private ProbabilityDistribution.Factory distFactory;

	@Inject
	private DistributionParser distParser;

	// TODO @InjectDist
	private ProbabilityDistribution<Instant> birthDist;

	private final Scheduler scheduler;

	@Inject
	public SimpleModel( final Scheduler scheduler )
	{
		this.scheduler = scheduler;
		scheduler.onReset( this::init );
	}

	@Override
	public Scheduler scheduler()
	{
		return this.scheduler;
	}

	public void init() throws Exception
	{
		// final Set<Individual> pop = new HashSet<>();
		final int n_pop = 10;
		// final Set<Location> homes = new HashSet<>();
		// final int n_homes = 6000000;
		// final Set<Location> offices = new HashSet<>();
		// final int n_offices = 3000000;
		final Infection measles = new Infection.Simple(
				Amount.valueOf( 1, Units.DAILY ),
				this.config.latentPeriodConst(),
				this.config.recoverPeriodConst(), this.config.wanePeriodConst(),
				this.config.onsetPeriodConst(),
				this.config.symptomPeriodConst() );

		final Collection<ContactIntensity> contactTypes = Collections
				.singleton( new ContactIntensity()
				{
					private String id = "family";

					@Override
					public String unwrap()
					{
						return this.id;
					}

					@Override
					public ContactIntensity wrap( final String value )
					{
						this.id = value;
						return this;
					}

					@Override
					public String toString()
					{
						return this.getFactor().toString();
					}
				} );
		final Amount<Frequency> force = measles.getForceOfInfection(
				Collections.singleton( TransmissionRoute.AIRBORNE ),
				contactTypes );
		final Duration contactPeriod = this.config.contactPeriod();
		final double infectLikelihood = force.times( contactPeriod.toAmount() )
				.to( Unit.ONE ).getEstimatedValue();
		LOG.trace( "Infection likelihood: {} * {} * {} = {}", force,
				contactPeriod, contactTypes, infectLikelihood );

		final ProbabilityDistribution<Boolean> infectDist = this.distFactory
				.createBernoulli( infectLikelihood );
		final ProbabilityDistribution<Gender> genderDist = this.distFactory
				.createUniformCategorical( Gender.MALE, Gender.FEMALE );

		final ProbabilityDistribution<Boolean> effectiveDist = this.distFactory
				.createBernoulli( 0.5 );

		final AmountDistribution<?> dist = this.distParser
				.parse( this.config.birthDist(), Integer.class )
				.toAmounts( NonSI.DAY );
		this.birthDist = () ->
		{
			return Instant.of( dist.draw() );
		};
		final HouseholdPopulation<SimpleIndividual> pop = HouseholdPopulation
				.of( "pop1", RxCollection.of( new HashSet<>() ),
						RxCollection.of( new HashSet<>() ), scheduler );
		for( int i = 1; i <= n_pop; i++ )
		{
			final Gender gender = genderDist.draw();
			final Instant birth = this.birthDist.draw();
			final Boolean effective = effectiveDist.draw();
			LOG.trace( "#{} - gender: {}, birth: {}, effective: {}", i, gender,
					birth.prettify( NonSI.DAY, 1 ), effective );
			final RxCollection<SimpleIndividual> members = RxCollection
					.of( new HashSet<>() );
			final Household<SimpleIndividual> hh = Household.of( "hh" + i, pop,
					members );
			final Map<Disease, Condition> afflictions = new HashMap<>();
			final SimpleIndividual ind = SimpleIndividual.of( hh, birth, gender,
					afflictions );
			ind.with( SimpleCondition.of( ind, measles ) );
			// pop.add( ind );
			final int nr = i;
			ind.afflictions().get( measles ).transitions().subscribe( tran ->
			{
				LOG.trace( "Transition for #{} at t={}: {}", nr,
						scheduler.now().prettify( NonSI.HOUR, 1 ), tran );
			}, e ->
			{
				LOG.warn( "Problem in transition", e );
			} );
			if( infectDist.draw() )
			{
				LOG.trace( "INFECTED #{}", i );
				ind.after( Duration.of( "30 min" ) )
						.call( ind.afflictions().get( measles )::infect );
			}
		}
	}

	/**
	 * @param args sdfdsf
	 * @throws Exception
	 */
	public static void main( final String[] args ) throws Exception
	{
		// configure replication FIXME via LocalConfig?
		ConfigCache.getOrCreate( ReplicateConfig.class, Collections
				.singletonMap( ReplicateConfig.DURATION_KEY, "" + 500 ) );

		// configure tooling
		final LocalConfig config = LocalConfig.builder().withId( "ecosysSim" )
				.withProvider( Scheduler.class, Dsol3Scheduler.class )
				.withProvider( PseudoRandom.Factory.class,
						Math3PseudoRandom.MersenneTwisterFactory.class )
				.withProvider( ProbabilityDistribution.Factory.class,
						Math3ProbabilityDistribution.Factory.class )
				.withProvider( ProbabilityDistribution.Parser.class,
						DistributionParser.class )
				.build();
		LOG.trace( "Starting MORPHINE replication, config: {}",
				config.toYAML() );

		// create binder and inject the model including a local scheduler
		final Scheduler scheduler = Guice4LocalBinder.of( config )
				.inject( SimpleModel.class ).scheduler();

		final Waiter waiter = new Waiter();
		scheduler.time().subscribe( time ->
		{
			// virtual time passes...
		}, error ->
		{
			waiter.rethrow( error );
		}, () ->
		{
			waiter.resume();
		} );

		// go go go!
		scheduler.resume();
		waiter.await( 3, TimeUnit.SECONDS );

		LOG.info( "completed, t={}", scheduler.now() );
	}

}
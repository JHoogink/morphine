package nl.rivm.cib.morphine;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.measure.quantity.Frequency;
import javax.measure.unit.NonSI;
import javax.measure.unit.Unit;

import org.apache.logging.log4j.Logger;
import org.jscience.physics.amount.Amount;

import io.coala.bind.LocalBinder;
import io.coala.bind.LocalConfig;
import io.coala.config.GlobalConfig;
import io.coala.config.InjectConfig;
import io.coala.dsol3.Dsol3Scheduler;
import io.coala.guice4.Guice4LocalBinder;
import io.coala.log.LogUtil;
import io.coala.math3.Math3ProbabilityDistribution;
import io.coala.math3.Math3PseudoRandom;
import io.coala.random.DistributionParser;
import io.coala.random.ProbabilityDistribution;
import io.coala.random.PseudoRandom;
import io.coala.time.Duration;
import io.coala.time.Instant;
import io.coala.time.Scheduler;
import io.coala.time.Units;
import nl.rivm.cib.episim.model.Gender;
import nl.rivm.cib.episim.model.Store;
import nl.rivm.cib.episim.model.disease.Condition;
import nl.rivm.cib.episim.model.disease.Disease;
import nl.rivm.cib.episim.model.disease.infection.ContactIntensity;
import nl.rivm.cib.episim.model.disease.infection.Infection;
import nl.rivm.cib.episim.model.disease.infection.TransmissionRoute;
import nl.rivm.cib.episim.model.populate.family.Household;
import nl.rivm.cib.episim.model.populate.family.HouseholdPopulation;
import nl.rivm.cib.episim.model.scenario.Scenario;
import nl.tudelft.simulation.dsol.simulators.Simulator;

/**
 * {@link SimpleModel}
 * 
 * @version $Id$
 * @author hooginkj
 */
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

	// FIXME @Inject functionality added in next coala release
	private DistributionParser distParser;

	// provided anew on each replication's init() call
	private Scheduler scheduler;

	private ProbabilityDistribution<Instant> birthDist;

	@Override
	public Scheduler scheduler()
	{
		return this.scheduler;
	}

	@Override
	public void init( final Scheduler scheduler ) throws Exception
	{
		this.scheduler = scheduler;
		this.distParser = new DistributionParser( this.distFactory );

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

		this.birthDist = Instant.of(
				this.distParser.parse( this.config.birthDist(), Integer.class ),
				NonSI.DAY );
		final Store<SimpleIndividual> inds = Store.of( scheduler,
				new HashSet<>() );
		final Store<Household<SimpleIndividual>> hhs = Store.of( scheduler,
				new HashSet<>() );
		final HouseholdPopulation<SimpleIndividual> pop = HouseholdPopulation
				.of( "pop1", inds, hhs );
		for( int i = 1; i <= n_pop; i++ )
		{
			final Gender gender = genderDist.draw();
			final Instant birth = this.birthDist.draw();
			final Boolean effective = effectiveDist.draw();
			LOG.trace( "#{} - gender: {}, birth: {}, effective: {}", i, gender,
					birth.prettify( NonSI.DAY, 1 ), effective );
			final Store<SimpleIndividual> members = Store.of( scheduler,
					new HashSet<>() );
			final Household<SimpleIndividual> hh = Household.of( "hh" + i, pop,
					members );
			final Map<Disease, Condition> afflictions = new HashMap<>();
			final SimpleIndividual ind = SimpleIndividual.of( hh, birth, gender,
					afflictions );
			ind.with( SimpleCondition.of( ind, measles ) );
			// pop.add( ind );
			final int nr = i;
			ind.afflictions().get( measles ).emitTransitions()
					.subscribe( tran ->
					{
						LOG.trace( "Transition for #{} at t={}: {}", nr,
								scheduler.now().prettify( NonSI.HOUR, 1 ),
								tran );
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
		LOG.trace( "Starting scenario..." );

		Units.DAILY.toString();
		final long seed = 1234L;

		@SuppressWarnings( "serial" )
		final LocalBinder binder = Guice4LocalBinder.of( LocalConfig.builder()
				.withProvider( Scenario.class, SimpleModel.class )
				.withProvider( ProbabilityDistribution.Factory.class,
						Math3ProbabilityDistribution.Factory.class )
				.build(), new HashMap<Class<?>, Object>()
				{
					{
						put( PseudoRandom.class, Math3PseudoRandom.Factory
								.ofMersenneTwister().create( "rng", seed ) );
					}
				} );

		// TODO initiate scheduler through (replication-specific) binder
		final Scenario scen = binder.inject( Scenario.class );
		final Scheduler scheduler = Dsol3Scheduler.of( "morphine",
				Instant.of( "0 day" ), Duration.of( "100 day" ), scen::init );

		// go go go
		final CountDownLatch latch = new CountDownLatch( 1 );
		scheduler.time().subscribe( time ->
		{
			LOG.trace( "t = {}", time.prettify( NonSI.DAY, 1 ) );
		}, error ->
		{
			LOG.warn( "Problem in scheduler", error );
			latch.countDown();
		}, () ->
		{
			latch.countDown();
		} );
		scheduler.resume();
		latch.await( 3, TimeUnit.SECONDS );

		// FIXME call cleanup in Scheduler implementation instead
		final Field field = Dsol3Scheduler.class
				.getDeclaredField( "scheduler" );
		field.setAccessible( true );
		((Simulator<?, ?, ?>) field.get( scheduler )).cleanUp();
	}

}
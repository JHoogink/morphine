package nl.rivm.cib.morphine;

import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.aeonbits.owner.ConfigCache;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import io.coala.bind.InjectConfig;
import io.coala.bind.LocalConfig;
import io.coala.dsol3.Dsol3Scheduler;
import io.coala.log.LogUtil;
import io.coala.math3.Math3ProbabilityDistribution;
import io.coala.math3.Math3PseudoRandom;
import io.coala.random.DistributionParser;
import io.coala.random.ProbabilityDistribution;
import io.coala.random.PseudoRandom;
import io.coala.rx.RxCollection;
import io.coala.time.Instant;
import io.coala.time.ReplicateConfig;
import io.coala.time.Scenario;
import io.coala.time.Scheduler;
import io.coala.time.TimeUnits;
import io.coala.time.Timing;
import nl.rivm.cib.episim.geard.HouseholdPopulation;
import nl.rivm.cib.episim.model.disease.infection.ContactIntensity;
import nl.rivm.cib.episim.model.disease.infection.Pathogen;
import nl.rivm.cib.episim.model.person.Gender;

/**
 * {@link SimpleModel} is a simple example {@link Scenario} implementation, of
 * which only one {@link Singleton} instance exists per {@link LocalBinder}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
@Singleton
public class SimpleModel implements Scenario
{
	/** */
	private static final Logger LOG = LogUtil.getLogger( SimpleModel.class );

	@InjectConfig
	private transient SimpleConfig config;

	@Inject
	private transient ProbabilityDistribution.Factory distFactory;

	@Inject
	private transient DistributionParser distParser;

	@Inject
	private transient Scheduler scheduler;

	@Inject
	private transient Pathogen.Factory pathogens;

	@Override
	public Scheduler scheduler()
	{
		return this.scheduler;
	}

	private void exportStatistics()
	{
		LOG.trace( "t = {}", now().prettify( TimeUnits.DAY, 1 ) );
	}

	@Override
	public void init() throws ParseException
	{
		// final Set<Individual> pop = new HashSet<>();
		final int n_pop = 10;
		// final Set<Location> homes = new HashSet<>();
		// final int n_homes = 6000000;
		// final Set<Location> offices = new HashSet<>();
		// final int n_offices = 3000000;
		final Pathogen measles = this.pathogens.create( "MV-1a" );

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
//		final Quantity<Frequency> force = measles.getForceOfInfection(
//				Collections.singleton( TransmissionRoute.AIRBORNE ),
//				contactTypes );
//		final Duration contactPeriod = this.config.contactPeriod();
//		final double infectLikelihood = QuantityUtil.doubleValue(
//				force.multiply( contactPeriod.toQuantity() ),
//				QuantityUtil.PURE );
//		LOG.trace( "Infection likelihood: {} * {} * {} = {}", force,
//				contactPeriod, contactTypes, infectLikelihood );

//		final ProbabilityDistribution<Boolean> infectDist = this.distFactory
//				.createBernoulli( infectLikelihood );
		final ProbabilityDistribution<Gender> genderDist = this.distFactory
				.createUniformCategorical( Gender.MALE, Gender.FEMALE );

		final ProbabilityDistribution<Boolean> effectiveDist = this.distFactory
				.createBernoulli( 0.5 );

		final ProbabilityDistribution<Instant> birthDist = this.distParser
				.parse( this.config.birthDist(), Integer.class )
				.toQuantities( TimeUnits.DAY ).map( Instant::of );

		final HouseholdPopulation<SimpleIndividual> pop = HouseholdPopulation
				.of( "pop1", RxCollection.of( new HashSet<>() ),
						RxCollection.of( new HashSet<>() ), this.scheduler );
		for( int i = 1; i <= n_pop; i++ )
		{
//			final Gender gender = genderDist.draw();
//			final Instant birth = birthDist.draw();
//			final Boolean effective = effectiveDist.draw();
//			LOG.trace( "#{} - gender: {}, birth: {}, effective: {}", i, gender,
//					birth.prettify( TimeUnits.DAY, 1 ), effective );
//			final RxCollection<SimpleIndividual> members = RxCollection
//					.of( new HashSet<>() );
//			final Household<SimpleIndividual> hh = Household.of( "hh" + i, pop,
//					members );
//			final Map<Disease, Condition> afflictions = new HashMap<>();
//			final SimpleIndividual ind = SimpleIndividual.of( hh, birth, gender,
//					afflictions );
//			final Condition cond = SimpleCondition.of( ind, measles );
//			LOG.trace( "cond: {}", cond);
//			ind.with( cond );
//			// pop.add( ind );
//			final int nr = i;
//			ind.afflictions().get( measles ).transitions().subscribe(
//					tran -> LOG.trace( "Transition for #{} at t={}: {}", nr,
//							now().prettify( TimeUnits.HOUR, 1 ), tran ),
//					e -> LOG.warn( "Problem in transition", e ) );
//			if( infectDist.draw() )
//			{
//				LOG.trace( "INFECTED #{}", i );
//				ind.after( Duration.of( "30 min" ) )
//						.call( ind.afflictions().get( measles )::infect );
//			}
		}

		atEach( Timing.of( this.config.statisticsRule() )
				.offset( DateTime.parse( this.config.offsetDate() ) )
				.iterate() ).subscribe( t ->
				{
					// TODO save model statistics to database
					LOG.trace( "saving model statistics to database, t={}", t );
					exportStatistics();
				}, e -> LOG.error( "Problem", e ) );
	}

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main( final String[] args ) throws Exception
	{
		// configure replication FIXME via LocalConfig?
		ConfigCache.getOrCreate( ReplicateConfig.class, Collections
				.singletonMap( ReplicateConfig.DURATION_KEY, "" + 100 ) );

		// configure tooling
		final LocalConfig config = LocalConfig.builder().withId( "morphine" )
				
				// configure simulator
				.withProvider( Scheduler.class, Dsol3Scheduler.class )
				
				// configure randomness
				.withProvider( PseudoRandom.Factory.class,
						Math3PseudoRandom.MersenneTwisterFactory.class )
				.withProvider( ProbabilityDistribution.Factory.class,
						Math3ProbabilityDistribution.Factory.class )
				.withProvider( ProbabilityDistribution.Parser.class,
						DistributionParser.class )

				// configure scenario
				.withProvider( Pathogen.Factory.class,
						Pathogen.SimpleSEIR.Factory.class )

				.build();

		LOG.trace( "Starting MORPHINE replication, config: {}",
				config.toYAML() );

		// create binder and inject the model including a local scheduler
		config.createBinder( Collections.emptyMap() )
				.inject( SimpleModel.class ).run();

		LOG.info( "Completed MORPHINE replication" );
	}

}
package nl.rivm.cib.morphine;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.measure.quantity.Frequency;
import javax.measure.unit.NonSI;
import javax.measure.unit.Unit;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.logging.log4j.Logger;
import org.jscience.physics.amount.Amount;

import io.coala.dsol3.Dsol3Scheduler;
import io.coala.exception.ExceptionFactory;
import io.coala.log.LogUtil;
import io.coala.math3.Math3ProbabilityDistribution;
import io.coala.math3.Math3PseudoRandom;
import io.coala.random.DistributionParser;
import io.coala.random.ProbabilityDistribution;
import io.coala.time.Duration;
import io.coala.time.Instant;
import io.coala.time.Scheduler;
import io.coala.time.Units;
import nl.rivm.cib.episim.model.Gender;
import nl.rivm.cib.episim.model.Individual;
import nl.rivm.cib.episim.model.Store;
import nl.rivm.cib.episim.model.TransitionEvent;
import nl.rivm.cib.episim.model.disease.Afflicted;
import nl.rivm.cib.episim.model.disease.Condition;
import nl.rivm.cib.episim.model.disease.Disease;
import nl.rivm.cib.episim.model.disease.SymptomPhase;
import nl.rivm.cib.episim.model.disease.SymptomPhase.SymptomEvent;
import nl.rivm.cib.episim.model.disease.TreatmentStage;
import nl.rivm.cib.episim.model.disease.TreatmentStage.TreatmentEvent;
import nl.rivm.cib.episim.model.disease.infection.ContactIntensity;
import nl.rivm.cib.episim.model.disease.infection.EpidemicCompartment;
import nl.rivm.cib.episim.model.disease.infection.EpidemicCompartment.CompartmentEvent;
import nl.rivm.cib.episim.model.disease.infection.Infection;
import nl.rivm.cib.episim.model.populate.Population;
import nl.rivm.cib.episim.model.populate.family.Household;
import nl.rivm.cib.episim.model.populate.family.HouseholdParticipant;
import nl.rivm.cib.episim.model.populate.family.HouseholdPopulation;
import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

/**
 * {@link SimpleModel}
 * 
 * @version $Id$
 * @author hooginkj
 */
public class SimpleModel
{
	/** */
	private static final Logger LOG = LogUtil.getLogger( SimpleModel.class );

	public interface SimpleIndividual
		extends Individual, HouseholdParticipant, Afflicted
	{

		static SimpleIndividual of( final Household<SimpleIndividual> household,
			final Instant birth, final Gender gender,
			final Map<Disease, Condition> afflictions )
		{
			return new SimpleIndividual()
			{

				@Override
				public Instant birth()
				{
					return birth;
				}

				@Override
				public Gender gender()
				{
					return gender;
				}

				@Override
				public Scheduler scheduler()
				{
					return household.scheduler();
				}

				@Override
				public Household<SimpleIndividual> household()
				{
					return household;
				}

				@Override
				public Population<SimpleIndividual> population()
				{
					return household.population();
				}

				@Override
				public Map<Disease, Condition> afflictions()
				{
					return afflictions;
				}
			};
		}
	}

	/**
	 * {@link Simple} implementation of {@link Condition}
	 * 
	 * @version $Id: 0d19d6801cb9fefc090fe46d46fc23c2d3afc275 $
	 * @author Rick van Krevelen
	 */
	public static class SimpleCondition implements Condition
	{

		/**
		 * @param individual the {@link Individual}
		 * @param infection the {@link Infection}
		 * @return a {@link Simple} instance of {@link Condition}
		 */
		public static SimpleCondition of( final Individual individual,
			final Infection infection )
		{
			return of( individual, infection,
					EpidemicCompartment.Simple.SUSCEPTIBLE,
					SymptomPhase.ASYMPTOMATIC, TreatmentStage.UNTREATED );
		}

		/**
		 * @param individual the {@link Individual}
		 * @param infection the {@link Infection}
		 * @param compartment the {@link EpidemicCompartment}
		 * @param symptoms the {@link SymptomPhase}
		 * @param treatment the {@link TreatmentStage}
		 * @return a {@link Simple} instance of {@link Condition}
		 */
		public static SimpleCondition of( final Individual individual,
			final Infection infection, final EpidemicCompartment compartment,
			final SymptomPhase symptoms, final TreatmentStage treatment )
		{
			return new SimpleCondition( individual, infection, compartment,
					symptoms, treatment );
		}

		private final Subject<TransitionEvent<?>, TransitionEvent<?>> transitions = PublishSubject
				.create();

		private final Individual individual;

		private final Infection infection;

		private EpidemicCompartment compartment;

		private SymptomPhase symptoms;

		private TreatmentStage treatment;

		/**
		 * {@link Simple} constructor
		 * 
		 * @param individual the {@link Individual}
		 * @param infection the {@link Infection}
		 * @param compartment the {@link EpidemicCompartment}
		 * @param symptoms the {@link SymptomPhase}
		 * @param treatment the {@link TreatmentStage}
		 */
		public SimpleCondition( final Individual individual,
			final Infection infection, final EpidemicCompartment compartment,
			final SymptomPhase symptoms, final TreatmentStage treatment )
		{
			Objects.requireNonNull( infection );
			this.individual = individual;
			this.infection = infection;
			this.compartment = compartment;
			this.symptoms = symptoms;
			this.treatment = treatment;
		}

		protected void setCompartment( final EpidemicCompartment compartment )
		{
			this.transitions.onNext( CompartmentEvent.of( this, compartment ) );
			this.compartment = compartment;
		}

		protected void setTreatmentStage( final TreatmentStage treatment )
		{
			this.transitions.onNext( TreatmentEvent.of( this, treatment ) );
			this.treatment = treatment;
		}

		protected void setSymptomPhase( final SymptomPhase symptoms )
		{
			this.transitions.onNext( SymptomEvent.of( this, symptoms ) );
			this.symptoms = symptoms;
		}

		@Override
		public Individual host()
		{
			return this.individual;
		}

		@Override
		public Scheduler scheduler()
		{
			return host().scheduler();
		}

		@Override
		public Infection disease()
		{
			return this.infection;
		}

		@Override
		public EpidemicCompartment getCompartment()
		{
			return this.compartment;
		}

		@Override
		public TreatmentStage getTreatmentStage()
		{
			return this.treatment;
		}

		@Override
		public SymptomPhase getSymptomPhase()
		{
			return this.symptoms;
		}

		@Override
		public Observable<TransitionEvent<?>> emitTransitions()
		{
			return this.transitions.asObservable();
		}

		@Override
		public void infect()
		{
			if( !getCompartment().isSusceptible() )
				throw ExceptionFactory.createUnchecked(
						"Can't become exposed when: {}", getCompartment() );

			setCompartment( EpidemicCompartment.Simple.EXPOSED );

			after( disease().drawLatentPeriod() )
					.call( this::setCompartment,
							EpidemicCompartment.Simple.INFECTIVE )
					.thenAfter( disease().drawRecoverPeriod() )
					.call( this::setCompartment,
							EpidemicCompartment.Simple.RECOVERED )
					.thenAfter( disease().drawWanePeriod() )
					.call( this::setCompartment,
							EpidemicCompartment.Simple.SUSCEPTIBLE );
			after( disease().drawOnsetPeriod() )
					.call( this::setSymptomPhase, SymptomPhase.SYSTEMIC )
					.thenAfter( disease().drawSymptomPeriod() )
					.call( this::setSymptomPhase, SymptomPhase.ASYMPTOMATIC );
		}
	}

	/**
	 * @param args sdfdsf
	 * @throws Exception
	 */
	public static void main( final String[] args ) throws Exception
	{
		LOG.trace( "Starting scenario..." );

		final Scheduler scheduler = Dsol3Scheduler.of( "dsol3Test",
				Instant.of( "0 day" ), Duration.of( "100 day" ), s ->
				{
					LOG.trace( "initialized, t={}",
							s.now().prettify( NonSI.DAY, 1 ) );
				} );

		// final Set<Individual> pop = new HashSet<>();
		final int n_pop = 10;
		// final Set<Location> homes = new HashSet<>();
		// final int n_homes = 6000000;
		// final Set<Location> offices = new HashSet<>();
		// final int n_offices = 3000000;
		final Infection measles = new Infection.Simple(
				Amount.valueOf( 1, Units.DAILY ), Duration.of( "2 day" ),
				Duration.of( "5 day" ), Duration.of( "9999 day" ),
				Duration.of( "3 day" ), Duration.of( "7 day" ) );

//		final TransmissionRoute route = TransmissionRoute.AIRBORNE;
//		final TransmissionSpace space = TransmissionSpace.of( scheduler,
//				route );
//		final Place rivm = Place.Simple.of( Place.RIVM_POSITION, Place.NO_ZIP,
//				space );

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
				} );
		final Amount<Frequency> force = measles.getForceOfInfection(
//				rivm.getSpace().getTransmissionRoutes()
				Collections.emptySet(), contactTypes );
		final Duration contactPeriod = Duration.of( "10 h" );
		final double infectLikelihood = force.times( contactPeriod.toAmount() )
				.to( Unit.ONE ).getEstimatedValue();
		LOG.trace( "Infection likelihood: {} * {} * {} = {}", force,
				contactPeriod, Arrays.asList( contactTypes ),
				infectLikelihood );

		final DistributionParser distParser = new DistributionParser(
				Math3ProbabilityDistribution.Factory.of(
						Math3PseudoRandom.Factory.of( MersenneTwister.class )
								.create( "MAIN", 1234L ) ) );
		final ProbabilityDistribution<Gender> genderDist = distParser
				.getFactory()
				.createUniformCategorical( Gender.MALE, Gender.FEMALE );

		final ProbabilityDistribution<Boolean> effectiveDist = distParser
				.getFactory().createBernoulli( 0.5 );

		final ProbabilityDistribution<Instant> birthDist = Instant.of(
				distParser.parse( "uniform-discrete(-5;0)", Integer.class ),
				NonSI.DAY );
		final Store<SimpleIndividual> inds = Store.of( scheduler,
				new HashSet<>() );
		final Store<Household<SimpleIndividual>> hhs = Store.of( scheduler,
				new HashSet<>() );
		final CountDownLatch latch = new CountDownLatch( 1 );
		final HouseholdPopulation<SimpleIndividual> pop = HouseholdPopulation
				.of( "pop1", inds, hhs );
		for( int i = 1; i <= n_pop; i++ )
		{
			final Gender gender = genderDist.draw();
			final Instant birth = birthDist.draw();
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
			ind.afflictions().get( measles ).emitTransitions().subscribe( t ->
			{
				LOG.trace( "Transition for #{} at t={}: {}", nr,
						scheduler.now().prettify( NonSI.HOUR, 1 ), t );
			}, e ->
			{
				LOG.warn( "Problem in transition", e );
			}, () ->
			{
				latch.countDown();
			} );
			if( distParser.getFactory().getStream()
					.nextDouble() < infectLikelihood )
			{
				LOG.trace( "INFECTED #{}", i );
				ind.after( Duration.of( "30 min" ) )
						.call( ind.afflictions().get( measles )::infect );
			}
		}
		scheduler.time().subscribe( ( Instant t ) ->
		{
			LOG.trace( "t = {}", t.prettify( NonSI.DAY, 1 ) );
		}, ( Throwable e ) ->
		{
			LOG.warn( "Problem in scheduler", e );
		}, () ->
		{
			latch.countDown();
		} );
		scheduler.resume();
		latch.await( 3, TimeUnit.SECONDS );
	}

}
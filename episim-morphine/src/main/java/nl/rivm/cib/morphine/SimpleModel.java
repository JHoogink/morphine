package nl.rivm.cib.morphine;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.measure.quantity.Frequency;
import javax.measure.unit.NonSI;
import javax.measure.unit.Unit;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.logging.log4j.Logger;
import org.jscience.physics.amount.Amount;

import io.coala.log.LogUtil;
import io.coala.math3.Math3ProbabilityDistribution;
import io.coala.math3.Math3RandomNumberStream;
import io.coala.random.ProbabilityDistribution;
import io.coala.time.x.Duration;
import io.coala.time.x.Instant;
import nl.rivm.cib.episim.model.Condition;
import nl.rivm.cib.episim.model.ContactIntensity;
import nl.rivm.cib.episim.model.Gender;
import nl.rivm.cib.episim.model.Household;
import nl.rivm.cib.episim.model.Individual;
import nl.rivm.cib.episim.model.Infection;
import nl.rivm.cib.episim.model.Place;
import nl.rivm.cib.episim.model.Population;
import nl.rivm.cib.episim.model.TransmissionRoute;
import nl.rivm.cib.episim.model.TransmissionSpace;
import nl.rivm.cib.episim.model.Units;
import nl.rivm.cib.episim.time.Scheduler;
import nl.rivm.cib.episim.time.dsol3.Dsol3Scheduler;

/**
 * {@link SimpleModel}
 * 
 * @version $Id$
 * @author hooginkj
 */
public class SimpleModel
{
	/** */
	private static final Logger LOG = LogUtil.getLogger(SimpleModel.class);

	/**
	 * @param args sdfdsf
	 * @throws Exception
	 */
	public static void main(final String[] args) throws Exception
	{
		LOG.trace("Starting scenario...");

		final Scheduler scheduler = Dsol3Scheduler.of("dsol3Test",
				Instant.of("0 day"), Duration.of("100 day"),
				(Scheduler s) -> {
					LOG.trace("initialized, t={}",
							s.now().prettify(NonSI.DAY, 1));
				});

		// final Set<Individual> pop = new HashSet<>();
		final int n_pop = 10;
		// final Set<Location> homes = new HashSet<>();
		// final int n_homes = 6000000;
		// final Set<Location> offices = new HashSet<>();
		// final int n_offices = 3000000;
		final Infection measles = new Infection.Simple(
				Amount.valueOf(1, Units.DAILY), Duration.of("2 day"),
				Duration.of("5 day"), Duration.of("9999 day"),
				Duration.of("3 day"), Duration.of("7 day"));

		
		final TransmissionRoute route = TransmissionRoute.AIRBORNE;
		final TransmissionSpace space = TransmissionSpace.of(scheduler, route);
		final Place rivm = Place.Simple.of(Place.RIVM_POSITION, Place.NO_ZIP,
				space);

		final Collection<ContactIntensity> contactTypes = Collections
				.singleton(ContactIntensity.FAMILY);
		final Amount<Frequency> force = measles.getForceOfInfection(
				rivm.getSpace().getTransmissionRoutes(), contactTypes);
		final Duration contactPeriod = Duration.of("10 h");
		final double infectLikelihood = force.times(contactPeriod.toAmount())
				.to(Unit.ONE).getEstimatedValue();
		LOG.trace("Infection likelihood: {} * {} * {} = {}", force,
				contactPeriod, Arrays.asList(contactTypes), infectLikelihood);

		final ProbabilityDistribution.Parser distParser = new ProbabilityDistribution.Parser(
				Math3ProbabilityDistribution.Factory
						.of(Math3RandomNumberStream.Factory
								.of(MersenneTwister.class)
								.create("MAIN", 1234L)));
		final ProbabilityDistribution<Gender> genderDist = distParser
				.getFactory()
				.createUniformCategorical(Gender.MALE, Gender.FEMALE);

		final ProbabilityDistribution<Boolean> effectiveDist= distParser
				.getFactory()
				.createBernoulli(0.5); 
					
		/*final ProbabilitDistribution<Vaccine> va
		/*
		 * FIXME RandomDistribution. Util .valueOf( "uniform(male;female)",
		 * distParser, Gender.class );
		 */
		final ProbabilityDistribution<Instant> birthDist = Instant
				.of( /* distFactory.getUniformInteger( rng, -5, 0 ) */
						distParser.parse("uniform-discrete(-5;0)",
								Integer.class),
						NonSI.DAY);
		final CountDownLatch latch = new CountDownLatch(1);
		final Population pop = Population.Simple.of(scheduler);
		for (int i = 1; i <= n_pop; i++)
		{
			final Gender gender = genderDist.draw();
			final Instant birth = birthDist.draw();
			final Boolean effective= effectiveDist.draw();
			LOG.trace("#{} - gender: {}, birth: {}, effective: {}", i, gender,
					birth.prettify(NonSI.DAY, 1), effective);
			final Individual ind = Individual.Simple.of(
					Household.Simple.of(pop, rivm), birth, gender,
					rivm.getSpace());
			ind.with(Condition.Simple.of(ind, measles));
			// pop.add( ind );
			final int nr = i;
			ind.getConditions().get(measles).emitTransitions()
					.subscribe((t) -> {
						LOG.trace("Transition for #{} at t={}: {}", nr,
								scheduler.now().prettify(NonSI.HOUR, 1), t);
					}, (e) -> {
						LOG.warn("Problem in transition", e);
					}, () -> {
						latch.countDown();
					});
			if (distParser.getFactory().getStream()
					.nextDouble() < infectLikelihood)
			{
				LOG.trace("INFECTED #{}", i);
				ind.after(Duration.of("30 min"))
						.call(ind.getConditions().get(measles)::infect);
			}
		}
		scheduler.time().subscribe((Instant t) -> {
			LOG.trace("t = {}", t.prettify(NonSI.DAY, 1));
		}, (Throwable e) -> {
			LOG.warn("Problem in scheduler", e);
		}, () -> {
			latch.countDown();
		});
		scheduler.resume();
		latch.await(3, TimeUnit.SECONDS);
	}

}
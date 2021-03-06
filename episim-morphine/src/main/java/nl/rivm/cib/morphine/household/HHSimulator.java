/* $Id$
 * 
 * Part of ZonMW project no. 50-53000-98-156
 * 
 * @license
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * Copyright (c) 2016 RIVM National Institute for Health and Environment 
 */
package nl.rivm.cib.morphine.household;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import javax.persistence.EntityManagerFactory;

import org.aeonbits.owner.ConfigCache;
import org.aeonbits.owner.ConfigFactory;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.yaml.YamlConfiguration;
import org.hibernate.cfg.AvailableSettings;

import io.coala.bind.LocalBinder;
import io.coala.bind.LocalConfig;
import io.coala.config.ConfigUtil;
import io.coala.dsol3.Dsol3Scheduler;
import io.coala.log.LogUtil;
import io.coala.log.LogUtil.Pretty;
import io.coala.math.DecimalUtil;
import io.coala.math3.Math3ProbabilityDistribution;
import io.coala.math3.Math3PseudoRandom;
import io.coala.persist.HikariHibernateJPAConfig;
import io.coala.persist.JPAUtil;
import io.coala.random.DistributionParser;
import io.coala.random.ProbabilityDistribution;
import io.coala.random.PseudoRandom;
import io.coala.time.ReplicateConfig;
import io.coala.time.Scheduler;
import io.coala.util.FileUtil;
import io.coala.util.MapBuilder;
import io.reactivex.schedulers.Schedulers;
import nl.rivm.cib.episim.cbs.TimeUtil;

/**
 * {@link HHSimulator}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public class HHSimulator
{

	/**
	 * @param args arguments from the command line
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void main( final String[] args )
		throws IOException, InterruptedException
	{
		final HHConfig hhConfig = HHConfig.getOrCreate( args );

		if( System.getProperty(
				ConfigurationFactory.CONFIGURATION_FILE_PROPERTY ) == null )
			try( final InputStream is = FileUtil
					.toInputStream( hhConfig.configBase() + "log4j2.yaml" ) )
			{
			// see https://stackoverflow.com/a/42524443
			final LoggerContext ctx = LoggerContext.getContext( false );
			ctx.start( new YamlConfiguration( ctx, new ConfigurationSource( is ) ) );
			} catch( final IOException ignore )
			{
			}

		final Logger LOG = LogUtil.getLogger( HHSimulator.class );
		LOG.info( "Starting {}, args: {} -> config: {}",
				HHSimulator.class.getSimpleName(), args,
				hhConfig.toJSON( HHConfig.MORPHINE_BASE ) );

		// FIXME move binder configuration to morphine.yaml
		final LocalConfig binderConfig = LocalConfig.builder()
				.withId( hhConfig.setupName() ) // replication name, sets random seeds

				// configure event scheduler
				.withProvider( Scheduler.class, Dsol3Scheduler.class )

				// configure randomness
				.withProvider( ProbabilityDistribution.Parser.class,
						DistributionParser.class )

				// FIXME skip until work-around is no longer needed
//				.withProvider( ProbabilityDistribution.Factory.class,
//						Math3ProbabilityDistribution.class )
//				.withProvider( PseudoRandom.Factory.class,
//						Math3PseudoRandom.MersenneTwisterFactory.class )

				.build();

		// FIXME workaround until scheduler becomes configurable in coala binder
		final ZonedDateTime offset = hhConfig.offset()
				.atStartOfDay( TimeUtil.NL_TZ );
		final long durationDays = Duration
				.between( offset, offset.plus( hhConfig.duration() ) ).toDays();
		ConfigCache.getOrCreate( ReplicateConfig.class, MapBuilder.unordered()
				.put( ReplicateConfig.ID_KEY, "" + binderConfig.rawId() )
				.put( ReplicateConfig.OFFSET_KEY, "" + offset )
				.put( ReplicateConfig.DURATION_KEY, "" + durationDays )
				.build() );

		// FIXME workaround until seed becomes configurable in coala
		final LocalBinder binder = binderConfig
				.createBinder( MapBuilder.<Class<?>, Object>unordered()
						.put( ProbabilityDistribution.Factory.class,
								new Math3ProbabilityDistribution.Factory(
										new Math3PseudoRandom.MersenneTwisterFactory()
												.create( PseudoRandom.Config.NAME_DEFAULT,
														hhConfig.randomSeed() ) ) )
						.build() );

		final HHModel model = binder.inject( HHModel.class );

		// persist statistics
		final boolean jpa = hhConfig.dbEnabled();
		final CountDownLatch dbLatch = new CountDownLatch( jpa ? 1 : 0 );
		if( jpa ) try
		{
			// trade-off; see https://stackoverflow.com/a/30347287/1418999
			final int jdbcBatchSize = 25;
			// trade-off; 50K+ are postponed until sim ends, flooding the stack
			final int rowsPerTx = 10000;

			final Pattern pattern = Pattern.compile(
					"^(" + Pattern.quote( "javax.persistence" ) + ").*" );
			// JPA config with vendor (i.e. Hibernate)-specific settings
			final EntityManagerFactory emf = ConfigFactory.create(
					HikariHibernateJPAConfig.class,
					ConfigUtil.join( hhConfig.export( pattern ), MapBuilder
							.unordered()
							.put( AvailableSettings.STATEMENT_BATCH_SIZE,
									"" + jdbcBatchSize )
							.put( AvailableSettings.BATCH_VERSIONED_DATA,
									"" + true )
							.put( AvailableSettings.ORDER_INSERTS, "" + true )
							.put( AvailableSettings.ORDER_UPDATES, "" + true )
							.build() ) )
					.createEMF();

			// shared between threads generating (sim) and flushing (db) rows
			final AtomicLong rowsPending = new AtomicLong();

			model.statistics().doOnNext( dao -> rowsPending.incrementAndGet() )
					.buffer( 10, TimeUnit.SECONDS, rowsPerTx )
					.observeOn(
							// Schedulers.from( Executors.newFixedThreadPool( 4 ) )
							Schedulers.io() ) // TODO is (unlimited) I/O smart?
					.subscribe( buffer ->
					{
						// TODO hold simulator while pending exceeds a maximum ?
						final long start = System.currentTimeMillis();
						final long n = rowsPending.addAndGet( -buffer.size() );
						JPAUtil.session( emf ).subscribe( em ->
						{
							final AtomicLong it = new AtomicLong();
							buffer.forEach( dao ->
							{
								dao.persist( em, binder.id() );
								if( it.incrementAndGet() % jdbcBatchSize == 0 )
								{
									em.flush();
//									em.clear();
								}
							} );
						}, e -> LOG.error( "Problem persisting stats", e ),
								() -> LOG.trace(
										"Persisted {} rows in {}s, {} pending",
										buffer.size(), Pretty
												.of( () -> DecimalUtil.toScale(
														(System.currentTimeMillis()
																- start)
																/ 1000.,
														1 ) ),
										n ) );
					}, e ->
					{
						LOG.error( "Problem generating household stats", e );
						emf.close(); // clean up connections
						dbLatch.countDown();
					}, () ->
					{
						LOG.trace( "Database persistence completed" );
						emf.close(); // clean up connections
						dbLatch.countDown();
					} );
		} catch( final Exception e )
		{
			LOG.error( "Could not start database", e );
			dbLatch.countDown();
		}
//		model.network().subscribe( e -> LOG.trace( "change: {}", e ) );

		// run injected (Singleton) model; start generating the statistics
		model.run();
		LOG.info( "{} completed...",
				model.scheduler().getClass().getSimpleName() );

		// wait until all statistics persisted
		dbLatch.await();

		LOG.info( "Completed {}", model.getClass().getSimpleName() );
	}
}

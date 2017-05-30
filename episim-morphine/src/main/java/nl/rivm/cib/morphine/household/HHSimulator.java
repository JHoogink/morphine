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
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;

import javax.naming.NamingException;
import javax.persistence.EntityManagerFactory;

import org.aeonbits.owner.ConfigCache;
import org.aeonbits.owner.ConfigFactory;
import org.apache.logging.log4j.Logger;
import org.hibernate.cfg.AvailableSettings;
import org.hsqldb.jdbc.JDBCDataSource;

import io.coala.bind.LocalConfig;
import io.coala.dsol3.Dsol3Scheduler;
import io.coala.log.LogUtil;
import io.coala.math3.Math3ProbabilityDistribution;
import io.coala.math3.Math3PseudoRandom;
import io.coala.name.JndiUtil;
import io.coala.persist.HibernateJPAConfig;
import io.coala.persist.JPAConfig;
import io.coala.random.DistributionParser;
import io.coala.random.ProbabilityDistribution;
import io.coala.random.PseudoRandom;
import io.coala.time.ReplicateConfig;
import io.coala.time.Scheduler;
import io.coala.util.MapBuilder;
import nl.rivm.cib.episim.cbs.TimeUtil;

/**
 * {@link HHSimulator}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public class HHSimulator
{

	/** */
	private static final Logger LOG = LogUtil.getLogger( HHSimulator.class );

	public static final String CONF_ARG = "conf";

	/**
	 * @param args arguments from the command line
	 * @throws NamingException
	 * @throws IOException
	 */
	public static void main( final String[] args )
		throws NamingException, IOException
	{
		final HHConfig hhConfig = HHConfig.getOrCreate( args );
		LOG.info( "Starting {}, args: {} -> config: {}",
				HHSimulator.class.getSimpleName(), args, hhConfig );

		// bind a local HSQL data source for exporting statistics
		JndiUtil.bindLocally( HHConfig.DATASOURCE_JNDI, '/', () ->
		{
			final JDBCDataSource ds = new JDBCDataSource();
			ds.setUrl( hhConfig.hsqlUrl() );
			ds.setUser( hhConfig.hsqlUser() );
			ds.setPassword( hhConfig.hsqlPassword() );
			return ds;
		} );

		// (re)configure replication run length
		final ZonedDateTime offset = hhConfig.offset()
				.atStartOfDay( TimeUtil.NL_TZ );
		final long durationDays = Duration
				.between( offset, offset.plus( hhConfig.duration() ) ).toDays();
		ConfigCache.getOrCreate( ReplicateConfig.class,
				MapBuilder.unordered()
						.put( ReplicateConfig.OFFSET_KEY, "" + offset )
						.put( ReplicateConfig.DURATION_KEY, "" + durationDays )
						.build() );

		// configure tooling
		final LocalConfig binderConfig = LocalConfig.builder()
				.withId( "morphine" ) // replication name, sets random seeds

				// configure simulator
				.withProvider( Scheduler.class, Dsol3Scheduler.class )

				// configure randomness
				.withProvider( PseudoRandom.Factory.class,
						Math3PseudoRandom.MersenneTwisterFactory.class )
				.withProvider( ProbabilityDistribution.Factory.class,
						Math3ProbabilityDistribution.Factory.class )
				.withProvider( ProbabilityDistribution.Parser.class,
						DistributionParser.class )

				.build();

		final Map<?, ?> jpaConfig = MapBuilder.unordered()
				.put( AvailableSettings.DATASOURCE, HHConfig.DATASOURCE_JNDI )
				// match unit name from persistence.xml
				.put( JPAConfig.JPA_UNIT_NAMES_KEY, "hh_pu" ).build();

		binderConfig// create binder from config(s)
				.createBinder( MapBuilder.<Class<?>, Object>unordered()
						.put(
								// bind singleton EntityManagerFactory
								EntityManagerFactory.class,
								ConfigFactory.create( HibernateJPAConfig.class,
										jpaConfig ).createEMF() )
						.build() )
				.inject( HHModel.class ) // inject model with configured tooling
				.run(); // run injected (Singleton) model

		LOG.info( "Completed {}", HHSimulator.class.getSimpleName() );
	}

}

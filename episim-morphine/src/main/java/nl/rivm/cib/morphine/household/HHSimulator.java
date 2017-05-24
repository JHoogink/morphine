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

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.aeonbits.owner.ConfigCache;
import org.apache.logging.log4j.Logger;

import io.coala.bind.LocalConfig;
import io.coala.dsol3.Dsol3Scheduler;
import io.coala.log.LogUtil;
import io.coala.math3.Math3ProbabilityDistribution;
import io.coala.math3.Math3PseudoRandom;
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

	/**
	 * @param args arguments from the command line
	 */
	public static void main( final String[] args )
	{
		// convert command-line arguments to map
		final Map<?, ?> argMap = Arrays.stream( args )
				.filter( arg -> arg.contains( "=" ) )
				.map( arg -> arg.split( "=" ) ).filter( arr -> arr.length == 2 )
				.collect( Collectors.toMap( arr -> arr[0], arr -> arr[1] ) );

		// merge arguments into configuration imported from YAML file
		HHConfig.YamlLoader.register();
		final HHConfig hhConfig = ConfigCache.getOrCreate( HHConfig.class,
				argMap );
		LOG.info( "Starting {}, args: {} -> config: {}",
				HHSimulator.class.getSimpleName(), argMap, hhConfig );

		// configure replication run length
		final ZonedDateTime offset = hhConfig.offset()
				.atStartOfDay( TimeUtil.NL_TZ );
		final long durationDays = Duration
				.between( offset, offset.plus( hhConfig.duration() ) ).toDays();
		ConfigCache.getOrCreate( ReplicateConfig.class,
				MapBuilder.unordered()
						.put( ReplicateConfig.OFFSET_KEY, "" + offset )
						.put( ReplicateConfig.DURATION_KEY, "" + durationDays )
						.build() );

		// configure tooling (TODO from MORPHINE config file?)
		final LocalConfig binderConfig = LocalConfig.builder()
				.withId( "morphine" ) // replication name, sets random seeds

				// configure simulator
				.withProvider( Scheduler.class, Dsol3Scheduler.class )

				// configure randomness
				.withProvider( PseudoRandom.Factory.class,
						Math3PseudoRandom.MersenneTwisterFactory.class )
				.withProvider( ProbabilityDistribution.Factory.class,
						Math3ProbabilityDistribution.Factory.class )

				.build();

		binderConfig.createBinder() // create binder
				.inject( HHModel.class ) // inject model with specified tooling
				.run(); // run model

		LOG.info( "Completed {}", HHSimulator.class.getSimpleName() );
	}

}

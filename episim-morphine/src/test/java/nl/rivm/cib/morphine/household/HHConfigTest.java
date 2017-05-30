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

import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import io.coala.bind.LocalBinder;
import io.coala.bind.LocalConfig;
import io.coala.dsol3.Dsol3Scheduler;
import io.coala.log.LogUtil;
import io.coala.time.Scheduler;
import io.coala.time.TimeUnits;

/**
 * {@link HHConfigTest} tests {@link HHConfig}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public class HHConfigTest
{

	/** */
	private static final Logger LOG = LogUtil.getLogger( HHConfigTest.class );

	private LocalBinder binder;

	/** init binder */
	@Before
	public void initLocalBinder()
	{
		this.binder = LocalConfig.builder().withId( "hhTest" )
				.withProvider( Scheduler.class, Dsol3Scheduler.class ).build()
				.createBinder();
	}

	@Test
	public void configTest()
		throws InstantiationException, IllegalAccessException, IOException
	{
		LOG.info( "{} started", HHConfigTest.class.getSimpleName() );
		final HHConfig conf = HHConfig.getOrCreate();
		final Scheduler scheduler = this.binder.inject( Scheduler.class );
		scheduler.onReset( () -> conf.hesitancyOracles( this.binder )
				.blockingSubscribe( oracle -> oracle.position()
						.subscribe( pos -> LOG.trace( "t={}, oracle {} {}",
								scheduler.now().prettify( TimeUnits.DAYS, 1 ),
								oracle, pos ) ) ) );
		scheduler.run();
		LOG.info( "{} completed", HHConfigTest.class.getSimpleName() );
	}
}

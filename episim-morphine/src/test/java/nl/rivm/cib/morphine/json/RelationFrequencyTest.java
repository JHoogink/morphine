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
package nl.rivm.cib.morphine.json;

import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import io.coala.bind.LocalBinder;
import io.coala.bind.LocalConfig;
import io.coala.json.JsonUtil;
import io.coala.log.LogUtil;
import io.coala.math.QuantityUtil;
import io.coala.math3.Math3ProbabilityDistribution;
import io.coala.math3.Math3PseudoRandom;
import io.coala.random.ProbabilityDistribution;
import io.coala.random.PseudoRandom;
import io.coala.util.FileUtil;
import nl.rivm.cib.morphine.json.RelationFrequencyJson;

/**
 * {@link RelationFrequencyTest} tests {@link RelationFrequencyJson}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public class RelationFrequencyTest
{
	/** */
	private static final Logger LOG = LogUtil
			.getLogger( RelationFrequencyTest.class );

	private static final String FREQUENCIES_FILE = "conf/relation-frequency.json";

	private LocalBinder binder;

	/** init binder */
	@Before
	public void initLocalBinder()
	{
		final LocalConfig conf = LocalConfig.builder().withId( "hes" )
				// set pseudo-random number generator factory
				.withProvider( PseudoRandom.Factory.class,
						Math3PseudoRandom.MersenneTwisterFactory.class )
				// set probability distribution factory
				.withProvider( ProbabilityDistribution.Factory.class,
						Math3ProbabilityDistribution.Factory.class )
				.build();
		LOG.trace( "Start with config: {}", conf.toYAML() );
		this.binder = conf.createBinder();
	}

	@Test
	public void testJsonParsing() throws IOException
	{
		LOG.info( "Testing {}", RelationFrequencyJson.class.getSimpleName() );
		final ProbabilityDistribution.Factory distFact = this.binder
				.inject( ProbabilityDistribution.Factory.class );

		final List<RelationFrequencyJson> json = JsonUtil.readArrayAsync(
				() -> FileUtil.toInputStream( FREQUENCIES_FILE ),
				RelationFrequencyJson.class ).toList().blockingGet();

		for( RelationFrequencyJson cat : json )
			for( int i = 0; i < 10; i++ )
				LOG.trace( "cat: {}, draw #{}: {}", cat, i, QuantityUtil
						.pretty( cat.intervalDist( distFact ).draw(), 1 ) );
	}
}

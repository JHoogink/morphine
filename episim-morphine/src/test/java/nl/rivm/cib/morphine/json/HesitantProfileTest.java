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
import io.coala.math.DecimalUtil;
import io.coala.math.WeightedValue;
import io.coala.math3.Math3ProbabilityDistribution;
import io.coala.math3.Math3PseudoRandom;
import io.coala.random.ProbabilityDistribution;
import io.coala.random.PseudoRandom;
import io.coala.util.FileUtil;
import nl.rivm.cib.morphine.household.HHConfig;
import nl.rivm.cib.morphine.json.HesitancyProfileJson;
import nl.rivm.cib.morphine.json.HesitancyProfileJson.HesitancyDimension;
import nl.rivm.cib.morphine.json.HesitancyProfileJson.VaccineStatus;

/**
 * {@link HesitantProfileTest} tests {@link HesitancyProfileJson}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public class HesitantProfileTest
{
	/** */
	private static final Logger LOG = LogUtil
			.getLogger( HesitantProfileTest.class );

	private static final String PROFILES_FILE = HHConfig.CONFIG_BASE_DIR
			+ "hesitancy-univariate.json";

	private static final String HESITANCY_FILE = HHConfig.CONFIG_BASE_DIR
			+ "hesitancy-initial.json";

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
		LOG.info( "Testing {}", HesitancyProfileJson.class.getSimpleName() );
		final ProbabilityDistribution.Factory distFact = this.binder
				.inject( ProbabilityDistribution.Factory.class );

		final List<WeightedValue<HesitancyProfileJson>> profileDensity = HesitancyProfileJson
				.parse( PROFILES_FILE ).toList().blockingGet();
		LOG.trace( "Parsed dists/weights: {}", profileDensity );
		final ProbabilityDistribution<HesitancyProfileJson> profileDist = distFact
				.createCategorical( profileDensity );

		final float[][] initial = JsonUtil.valueOf(
				FileUtil.toInputStream( HESITANCY_FILE ), float[][].class );
		final VaccineStatus reportStatus = VaccineStatus.all;
		int i = 0, j = 0, k = 0, l = 0, n = 100000;
		for( ; i < n; i++ )
		{
			// step 1. draw profile (empirical)
			final HesitancyProfileJson hes = profileDist.draw();
			// step 2. draw values for each (univariate) distribution
			int confCol = hes.indices.get( HesitancyDimension.confidence ) - 1;
			int compCol = hes.indices.get( HesitancyDimension.complacency ) - 1;
			int row = distFact.getStream().nextInt( initial.length );

			final Double conf = (double) initial[row][confCol];//hes.distParams.get(HesitancyDimension.confidence).createDist(distFact).draw();
			final Double comp = (double) initial[row][compCol];//hes.distParams.get(HesitancyDimension.complacency).createDist(distFact).draw();
			// step 3. calculate the attitude: (conf + (1-comp))/2
			final Double att = (conf + 1 - comp) / 2;
			// step 4. update stats
			if( hes.status == reportStatus )
			{
				j++;
				if( att >= .5 ) k++; // is positive attitude?
				if( hes.religious ) l++;
			}
		}
		LOG.info(
				"Of n={} profile draws, {} ({}%) have status '{}'"
						+ ", of which {} ({}%) have a 'positive' attitude and of which {} person are religious",
				n, j,
				DecimalUtil.toScale( DecimalUtil.multiply( j, 100. / i ), 1 ),
				reportStatus, k,
				DecimalUtil.toScale( DecimalUtil.multiply( k, 100. / j ), 1 ),
				l );
	}
}

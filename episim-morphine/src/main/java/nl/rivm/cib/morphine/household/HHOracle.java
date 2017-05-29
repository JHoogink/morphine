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

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.Logger;
import org.ujmp.core.Matrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.coala.bind.LocalBinder;
import io.coala.log.LogUtil;

/**
 * {@link HHOracle} adds special proactive entities acting as oracle households,
 * representing the nationally or locally communicated positions of e.g. public
 * health, religious, alternative medicinal authorities, or socially observed
 * disease or adverse events
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
@FunctionalInterface
public interface HHOracle
{
	Function<Matrix, BigDecimal> setAttributes( Matrix oracleAttributes );

	static HHOracle of( final BigDecimal confidence,
		final BigDecimal complacency,
		final Function<Matrix, BigDecimal> weighter )
	{
		return oracleAttributes ->
		{
			oracleAttributes.setAsBigDecimal( confidence, 0,
					HHAttribute.CONFIDENCE.ordinal() );
			oracleAttributes.setAsBigDecimal( complacency, 0,
					HHAttribute.COMPLACENCY.ordinal() );
			return weighter;
		};
	}

	interface Factory
	{

		HHOracle create( JsonNode config, LocalBinder binder );

		default List<HHOracle> createAll( final ArrayNode config,
			final LocalBinder binder )
		{
			return StreamSupport.stream( config.spliterator(), true )
					.map( node -> create( node, binder ) )
					.collect( Collectors.toList() );
		}

		/** */
		Logger LOG = LogUtil.getLogger( HHOracle.class );

		class Confident implements Factory
		{
			@Override
			public HHOracle create( final JsonNode config,
				final LocalBinder binder )
			{
				LOG.info( "Creating {}, ignoring config: {}",
						Confident.class.getSimpleName(), config );
				return of( BigDecimal.ONE, BigDecimal.ZERO,
						hhAttributes -> BigDecimal.TEN );
			}
		}

		class ConfidentHalved implements Factory
		{

			@Override
			public HHOracle create( final JsonNode config,
				final LocalBinder binder )
			{
				LOG.info( "Creating {}, ignoring config: {}",
						ConfidentHalved.class.getSimpleName(), config );
				return of( BigDecimal.ONE, BigDecimal.ZERO, hhAttributes ->
				{
					BigDecimal weight = BigDecimal.TEN;
					if( hhAttributes.getAsBoolean( 0,
							HHAttribute.RELIGIOUS.ordinal() ) )
						weight = weight.divide( BigDecimal.valueOf( 2 ) );
					if( hhAttributes.getAsBoolean( 0,
							HHAttribute.ALTERNATIVE.ordinal() ) )
						weight = weight.divide( BigDecimal.valueOf( 2 ) );
					return weight;
				} );
			}

		}
	}
}

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
package nl.rivm.cib.morphine.pienter;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.coala.json.JsonUtil;
import io.coala.math.Range;
import io.coala.math.WeightedValue;
import io.coala.name.Identified;
import io.coala.random.ProbabilityDistribution;
import io.coala.util.FileUtil;
import io.reactivex.Observable;

/**
 * {@link HesitancyProfile}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public class HesitancyProfile extends Identified.SimpleOrdinal<String>
{
	public static Observable<WeightedValue<HesitancyProfile>>
		parse( final String fileName )
	{
		return JsonUtil
				.readArrayAsync( () -> FileUtil.toInputStream( fileName ),
						HesitancyProfile.class )
				.map( p -> WeightedValue.of( p, p.fraction ) );
	}

	/** the initial religious persuasion */
	public boolean religious;

	/** the initial alternative medicine persuasion */
	public boolean alternative;

	/** the initial vaccination status */
	public VaccineStatus status;

	/** the original profile N */
	public int count;

	/** the original profile density/fraction */
	public BigDecimal fraction;

	/** the initial attitude distributions per hesitancy dimension */
	@JsonProperty( "distributions" )
	public EnumMap<HesitancyDimension, DistParams> distParams;

	@Override
	public String id()
	{
		return this.id == null ? (this.id = (this.religious ? "Reli" : "Sec")
				+ "|" + (this.alternative ? "Alto" : "Reg") + "|" + this.status)
				: this.id;
	}

	/**
	 * {@link HesitancyDimension} is a 3C/4C dimension of attitude
	 */
	public enum HesitancyDimension
	{
		complacency, confidence, attitude, calculation;
	}

	/**
	 * {@link VaccineStatus} is a possible vaccination status
	 */
	public static enum VaccineStatus
	{
		all, some, none;
	}

	/**
	 * {@link DistType} is a type of (R-fitted) distribution
	 */
	public static enum DistType
	{
		weibull;
	}

	/**
	 * {@link DistParam} is a (R-fitted) distribution parameter name
	 */
	public static enum DistParam
	{
		shape, scale;
	}

	/**
	 * {@link DistParams} describes and instantiates a (R-fitted) distribution
	 * of attitude values
	 */
	public static class DistParams
	{
		/** the fitted distribution type */
		public HesitancyProfile.DistType type;

		/** the minimum observable value */
		public Double min;

		/** the maximum observable value */
		public Double max;

		/** the distribution parameter estimate */
		public Map<HesitancyProfile.DistParam, BigDecimal> est;

		/** the distribution parameter error (standard deviation) */
		public Map<HesitancyProfile.DistParam, BigDecimal> sd;

		private ProbabilityDistribution<Double> distCache;

		/**
		 * @param distFact a {@link ProbabilityDistribution.Factory}
		 * @return a (cached) {@link ProbabilityDistribution} of {@link Double}s
		 */
		public ProbabilityDistribution<Double>
			createDist( final ProbabilityDistribution.Factory distFact )
		{
			if( this.distCache == null )
			{
				// avoid compl==conf, e.g. min: .5 -> .505,  max: .5 -> .497
				final Range<Double> range = Range.of( this.min * 1.01,
						Math.pow( this.max, 1.01 ) );
				switch( this.type )
				{
				default:
					// TODO map other distribution types
				case weibull:
					this.distCache = distFact
							.createWeibull( this.est.get( DistParam.shape ),
									this.est.get( DistParam.scale ) )
							.map( range::crop );
				}
			}
			return this.distCache;
		}
	}

	@Override
	public String toString()
	{
		return JsonUtil.stringify( this );
	};
}
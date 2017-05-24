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

import org.ujmp.core.Matrix;

import nl.rivm.cib.episim.model.vaccine.attitude.VaxHesitancy;

/**
 * {@link HHBarrierEvaluator} : [{@link HHAttribute}] &rarr; [0,1]
 */
public interface HHBarrierEvaluator
{
	/**
	 * @param attributeRow a row vector (1 &times; n {@link Matrix}) of
	 *            {@link HHAttribute} values
	 * @return {@link BigDecimal} barrier &isin; [0,1]
	 */
	BigDecimal barrierOf( Matrix attributeRow );

	// examples

	/** {@link Average} wraps {@link VaxHesitancy#averageBarrier} */
	class Average implements HHBarrierEvaluator
	{
		@Override
		public BigDecimal barrierOf( final Matrix attributes )
		{
			return VaxHesitancy.averageBarrier(
					attributes.getAsBigDecimal( 0,
							HHAttribute.CONFIDENCE.ordinal() ),
					attributes.getAsBigDecimal( 0,
							HHAttribute.COMPLACENCY.ordinal() ) );
		}
	}

	/** {@link Difference} wraps {@link VaxHesitancy#differenceBarrier} */
	class Difference implements HHBarrierEvaluator
	{
		@Override
		public BigDecimal barrierOf( final Matrix attributes )
		{
			return VaxHesitancy.differenceBarrier(
					attributes.getAsBigDecimal( 0,
							HHAttribute.CONFIDENCE.ordinal() ),
					attributes.getAsBigDecimal( 0,
							HHAttribute.COMPLACENCY.ordinal() ) );
		}
	}
}
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
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.ujmp.core.Matrix;
import org.ujmp.core.calculation.Calculation.Ret;

import io.coala.math.MatrixUtil;
import nl.rivm.cib.episim.model.vaccine.attitude.VaxHesitancy;

/**
 * {@link HHAttitudePropagator} : [{@link HHAttribute}] &rarr; [0,1]
 */
public interface HHAttitudePropagator
{

	BigDecimal filteredAppreciation( final BigDecimal appreciation,
		final BigDecimal calculationLevel );

	/**
	 * propagate the new weighted averages of default social attributes:
	 * {@link HHAttribute#CONFIDENCE} and {@link HHAttribute#COMPLACENCY}
	 * 
	 * @param hhPressure an m &times; n {@link Matrix} containing for <em>m</em>
	 *            households (rows) their respective appreciation weight values
	 *            of all <em>n</em> households (columns)
	 * @param hhAttributes an n &times; k {@link Matrix} containing for all
	 *            <em>n</em> households (rows) their respective <em>k</em>
	 *            attribute values (columns)
	 */
	default void propagate( final Matrix hhPressure, final Matrix hhAttributes )
	{
		propagate( hhPressure, hhAttributes, HHAttribute.CONFIDENCE.ordinal(),
				HHAttribute.COMPLACENCY.ordinal() );
	}

	/**
	 * @param hhPressure an m &times; n {@link Matrix} containing for <em>m</em>
	 *            households (rows) their respective appreciation weight values
	 *            of all <em>n</em> households (columns)
	 * @param hhAttributes an n &times; k {@link Matrix} containing for all
	 *            <em>n</em> households (rows) their respective <em>k</em>
	 *            attribute values (columns)
	 * @param attributePressuredCols the indices of {@link HHAttribute} values
	 *            to replace by their respective newly weighted average
	 */
	default void propagate( final Matrix hhPressure, final Matrix hhAttributes,
		final long... attributePressuredCols )
	{
		final Matrix newAttributes = Matrix.Factory.zeros(
				hhPressure.getRowCount(),
				Objects.requireNonNull( attributePressuredCols ).length );

		// apply calculation threshold function
		final Matrix weights = Matrix.Factory.zeros( hhPressure.getSize() );
		MatrixUtil.coordinateStream( hhPressure.getSize() ).parallel().forEach(
				coords -> weights.setAsBigDecimal( filteredAppreciation(
						hhPressure.getAsBigDecimal( coords ),
						hhAttributes.getAsBigDecimal( coords[0],
								HHAttribute.CALCULATION.ordinal() ) ) ) );

		// calculate new attributes based on all current (weighted) information
		LongStream.range( 0, hhPressure.getRowCount() ).parallel()
				.forEach( row ->
				{
					final Matrix rowPressure = weights.selectRows( Ret.LINK,
							row );
					final Matrix rowAttributes = hhAttributes
							.selectRows( Ret.LINK, row )
							.selectColumns( Ret.LINK, attributePressuredCols );
					final double sum = rowPressure.getValueSum();
					MatrixUtil.insertBigDecimal( newAttributes, sum > 0
							? rowPressure.mtimes( rowAttributes ).divide( sum )
							: rowAttributes, row, 0 );
				} );

		// update all attributes at once
		IntStream.range( 0, attributePressuredCols.length ).parallel()
				.forEach( col -> MatrixUtil.insertBigDecimal( hhAttributes,
						newAttributes.selectColumns( Ret.LINK, col ), 0,
						attributePressuredCols[col] ) );
	}

	// examples

	/** {@link Threshold} wraps {@link VaxHesitancy#thresholdAppreciation} */
	class Threshold implements HHAttitudePropagator
	{
		@Override
		public BigDecimal filteredAppreciation( final BigDecimal appreciation,
			final BigDecimal calculationLevel )
		{
			return VaxHesitancy.thresholdAppreciation( appreciation,
					calculationLevel );
		}
	}

	/** {@link Shifted} wraps {@link VaxHesitancy#shiftedAppreciation} */
	class Shifted implements HHAttitudePropagator
	{
		@Override
		public BigDecimal filteredAppreciation( final BigDecimal appreciation,
			final BigDecimal calculationLevel )
		{
			return VaxHesitancy.shiftedAppreciation( appreciation,
					calculationLevel );
		}
	}
}
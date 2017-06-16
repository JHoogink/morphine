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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.apache.logging.log4j.Logger;
import org.ujmp.core.Matrix;
import org.ujmp.core.SparseMatrix;
import org.ujmp.core.calculation.Calculation.Ret;

import io.coala.log.LogUtil;
import io.coala.math.DecimalUtil;
import io.coala.math.MatrixUtil;
import nl.rivm.cib.episim.model.vaccine.attitude.VaxHesitancy;

/**
 * {@link HHAttitudePropagator} : [{@link HHAttribute}] &rarr; [0,1]
 */
public interface HHAttitudePropagator
{

	BigDecimal filteredAppreciation( BigDecimal appreciation,
		BigDecimal calculationLevel );

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
		Objects.requireNonNull( hhPressure, "network null" );
		Objects.requireNonNull( hhAttributes, "attributes null" );
		final long hhTotal = hhAttributes.getRowCount();

		final AtomicLong duration = new AtomicLong(
				System.currentTimeMillis() ), hhCount = new AtomicLong();
		final Logger LOG = LogUtil.getLogger( getClass() );

		// calculate new attributes based on all current (weighted) information
		final Matrix newAttributes = Matrix.Factory.zeros( hhTotal, Objects
				.requireNonNull( attributePressuredCols, "cols null" ).length );

		LongStream.range( 0, hhTotal ).parallel().forEach( i ->
		{
			hhCount.incrementAndGet();
			if( System.currentTimeMillis() - duration.get() > 1000 )
			{
				duration.set( System.currentTimeMillis() );
				LOG.trace( "Propagating; {} of {}", hhCount.get(), hhTotal );
			}

			final Matrix colV = hhAttributes.selectColumns( Ret.LINK,
					attributePressuredCols );
			final long attr = hhAttributes.getAsLong( i,
					HHAttribute.ATTRACTOR_REF.ordinal() );
			if( attr == i )
			{
				MatrixUtil.insertBigDecimal( newAttributes, colV, i, 0 );
				return;
			}
			final Matrix rowW = SparseMatrix.Factory.zeros( 1, hhTotal );

			final BigDecimal calc = hhAttributes.getAsBigDecimal( i,
					HHAttribute.CALCULATION.ordinal() );
			final AtomicReference<BigDecimal> sumW = new AtomicReference<>(
					BigDecimal.ZERO );
			HHConnector.symmetricCoordinates( hhPressure, i ).forEach( x ->
			{
				// apply calculation threshold function
				final BigDecimal w = filteredAppreciation(
						hhPressure.getAsBigDecimal( x ), calc );
				sumW.getAndUpdate( s -> s.add( w ) );
				final long j = x[0] == i ? x[1] : x[0];
				rowW.setAsBigDecimal( w, 0, j );
			} );

			if( sumW.get().signum() < 1 ) return;

			// add attractor weight, matched using (calculation) factor
			final BigDecimal attractorWeightFactor = hhAttributes
					.getAsBigDecimal( attr, HHAttribute.CALCULATION.ordinal() );
			final BigDecimal attractorWeight = DecimalUtil
					.multiply( attractorWeightFactor, sumW.get() );
			rowW.setAsBigDecimal( attractorWeight, 0, attr );

			// get current hesitancy values and insert transformed
			final Matrix prod = rowW.mtimes( colV )
					.divide( sumW.get().add( attractorWeight ).doubleValue() );
			final Matrix res = sumW.get().signum() != 0 ? prod : colV;
			MatrixUtil.insertBigDecimal( newAttributes, res, i, 0 );

			final int s = 4;
			LOG.trace( "{} [{},{}] -> [{},{}] -> [{},{}]", i,
					DecimalUtil.toScale( hhAttributes.getAsBigDecimal( i,
							attributePressuredCols[0] ), s ),
					DecimalUtil.toScale( hhAttributes.getAsBigDecimal( i,
							attributePressuredCols[1] ), s ),
					DecimalUtil.toScale( newAttributes.getAsBigDecimal( i, 0 ),
							s ),
					DecimalUtil.toScale( newAttributes.getAsBigDecimal( i, 1 ),
							s ),
					DecimalUtil.toScale( hhAttributes.getAsBigDecimal( attr,
							attributePressuredCols[0] ), 1 ),
					DecimalUtil.toScale( hhAttributes.getAsBigDecimal( attr,
							attributePressuredCols[1] ), 1 ) );
		} );

		// update all attributes at once afterwards
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
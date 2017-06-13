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
import java.util.stream.StreamSupport;

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
		final Matrix newAttributes = Matrix.Factory.zeros( hhTotal, Objects
				.requireNonNull( attributePressuredCols, "cols null" ).length );

		// calculate new attributes based on all current (weighted) information
		final AtomicLong duration = new AtomicLong(
				System.currentTimeMillis() ), hhCount = new AtomicLong();
		final Logger LOG = LogUtil.getLogger( getClass() );
		LongStream.range( 0, hhTotal ) //
				.parallel() // really?
				.forEach( row ->
				{
					hhCount.incrementAndGet();
					if( System.currentTimeMillis() - duration.get() > 1000 )
					{
						duration.set( System.currentTimeMillis() );
						LOG.trace( "Propagating; {} of {}", hhCount.get(),
								hhTotal );
					}

					// apply calculation threshold function
					final Matrix hhPressureRow = hhPressure
							.selectRows( Ret.LINK, row );
					if( hhPressureRow == null ) // e.g. isolated attractors
						return;

					final BigDecimal calc = hhAttributes.getAsBigDecimal( row,
							HHAttribute.CALCULATION.ordinal() );
					final long attractorIndex = hhAttributes.getAsLong( row,
							HHAttribute.ATTRACTOR_REF.ordinal() );
					final AtomicLong edges = new AtomicLong();
					final Matrix hhWeights = SparseMatrix.Factory.zeros( 1,
							hhTotal );

					// reset household's attractor weight to zero
//					hhWeights.setAsBigDecimal( BigDecimal.ZERO, 0,
//							attractorIndex );

					final AtomicReference<BigDecimal> sum = new AtomicReference<>(
							BigDecimal.ZERO );
					StreamSupport.stream(
							hhPressureRow.availableCoordinates().spliterator(),
							true ).forEach( rowCoords ->
							{
								final long hhIndex = hhPressureRow.isRowVector()
										? rowCoords[1] : rowCoords[0];
								if( hhIndex == attractorIndex ) return;
								final BigDecimal v = filteredAppreciation(
										hhPressureRow.getAsBigDecimal( 0,
												hhIndex ),
										calc );
								if( v.signum() == 0 ) return;
								sum.getAndUpdate( s -> s.add( v ) );
								hhWeights.setAsBigDecimal( v, 0, hhIndex );
								edges.incrementAndGet();
							} );

					// add attractor weight, matched using (calculation) factor
					final BigDecimal attractorWeightFactor = hhAttributes
							.getAsBigDecimal( attractorIndex,
									HHAttribute.CALCULATION.ordinal() );
					final BigDecimal attractorWeight = DecimalUtil
							.multiply( attractorWeightFactor, sum.get() );
					hhWeights.setAsBigDecimal( attractorWeight, 0,
							attractorIndex );

					// get current hesitancy values and insert transformed
					final Matrix rowAttributes = hhAttributes
							.selectColumns( Ret.LINK, attributePressuredCols );
					final Matrix res = sum.get().signum() != 0 ? hhWeights
							.mtimes( rowAttributes ).divide( sum.get()
									.add( attractorWeight ).doubleValue() )
							: rowAttributes;
					MatrixUtil.insertBigDecimal( newAttributes, res, row, 0 );
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
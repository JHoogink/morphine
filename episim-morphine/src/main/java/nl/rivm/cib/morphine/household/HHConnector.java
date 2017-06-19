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
import java.util.function.Supplier;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.ujmp.core.Matrix;
import org.ujmp.core.SparseMatrix;
import org.ujmp.core.calculation.Calculation.Ret;

import io.coala.random.PseudoRandom;

/**
 * {@link HHConnector}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public interface HHConnector
{

	Matrix connect( long size, Supplier<Long> degree,
		Supplier<BigDecimal> initialWeight );

	/**
	 * utility method
	 * 
	 * @param x
	 * @return
	 */
	static long[] top( final long... x )
	{
		return x[0] > x[1] ? new long[] { x[1], x[0] } : x;
	}

	static BigDecimal putSymmetric( final Matrix W, final BigDecimal wNew,
		final long... x )
	{
		final long[] x_top = HHConnector.top( x );
		final BigDecimal wOld = getSymmetric( W, x_top );
		setSymmetric( W, wNew, x_top );
		return wOld;
	}

	static void setSymmetric( final Matrix W, final Object w, final long i )
	{
		W.setAsObject( w, i, i );
	}

	static void setSymmetric( final Matrix W, final Object w, final long... x )
	{
		W.setAsObject( w, x[0], x[1] );
		W.setAsObject( w, x[1], x[0] );
	}

	static BigDecimal getSymmetric( final Matrix W, final long... x )
	{
		return W.getAsBigDecimal( HHConnector.top( x ) );
	}

	static Stream<long[]> availableCoordinates( final Matrix W )
	{
		return W == null ? Stream.empty()
				: StreamSupport.stream( W.availableCoordinates().spliterator(),
						false );
	}

	static Stream<long[]> availableCoordinates( final Matrix W, final long i )
	{
		// FIXME don't go through ALL coordinates, rather just relevant row/col
		return HHConnector.availableCoordinates( W )
				.filter( x -> x[0] == i || x[1] == i );

		// bug 1: W.selectColumns( Ret.LINK, i ).availableCoordinates() does not LINK but creates new
		// bug 2: W.selectRows( Ret.LINK, i ).transpose() fails
//		Stream.concat(
//				availableCoordinates( W.selectColumns( Ret.LINK, i ) )
//						.filter( x -> x[0] < i ) // avoids duplicate self
//						.map( x -> top( x[0], i ) ),
//				availableCoordinates( W.selectRows( Ret.LINK, i ) )
//						.filter( x -> i <= x[0] ).map( x -> top( i, x[0] ) ) );
	}

	class WattsStrogatz implements HHConnector
	{
		private final PseudoRandom rng;
		private final double beta;

		public WattsStrogatz( final PseudoRandom rng, final double beta )
		{
			this.rng = rng;
			this.beta = beta;
		}

		@Override
		public Matrix connect( final long size, final Supplier<Long> degree,
			final Supplier<BigDecimal> weight )
		{
			final Matrix result = SparseMatrix.Factory.zeros( size, size );

			// step 1: setup lattice: link self + degree-1 lattice 'neighbors'
			// FIXME parallelized rows may contain just 1 value, not thread-safe?
			for( long i = 0; i < size; i++ )
			{
				final long K = Math.min( size / 4, // need room to shuffle j's
						degree.get() );

				for( long di = 0; di < K; di++ )
					result.setAsBigDecimal( weight.get(),
							HHConnector.top( i, (i + 1 + di) % size ) );
			}

			// step 2: perturb lattice
			// FIXME parallelized row selection causes NPE, not Thread safe?
			LongStream.range( 0, size - 1 )// last row in triangle is empty
					.forEach( i -> HHConnector
							.availableCoordinates(
									result.selectRows( Ret.LINK, i ) )
							.forEach( x ->
							{
								long j = x[0];
								if( this.rng.nextDouble() < this.beta )
								{
									final long[] i_k = { i, j };

									// shuffle until : non-self and non-used
									while( i_k[1] == i || result
											.getAsBigDecimal(
													HHConnector.top( i_k ) )
											.signum() != 0 )
										i_k[1] = this.rng.nextLong( size );

									// weight to move from i,j to i,k
									final Object w = result.getAsObject( i, j );
									// reset old position
									result.setAsObject( null, i, j );
									// set new position
									result.setAsObject( w,
											HHConnector.top( i_k ) );
								}
							} ) );

			return result;
		}

	}

}

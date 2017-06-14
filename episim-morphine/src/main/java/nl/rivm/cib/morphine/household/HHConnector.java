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
import java.util.stream.LongStream;
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

	Matrix connect( long size, long degree, BigDecimal initialWeight );

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
		public Matrix connect( final long size, final long degree,
			final BigDecimal weight )
		{
			final Matrix result = SparseMatrix.Factory.zeros( size, size );

			// step 1: setup lattice: link self + degree-1 lattice 'neighbors'
			// FIXME parallelized rows may contain just 1 value, not thread-safe?
			LongStream.range( 0, size )
					.forEach( i -> LongStream.range( 0, degree )
							.forEach( di -> result.setAsBigDecimal( weight,
									top( i, (i + di) % size ) ) ) );

			// step 2: perturb lattice
			// FIXME parallelized row selection causes NPE, not Thread safe?
			LongStream.range( 0, size ).forEach( i -> StreamSupport
					.stream( result.selectRows( Ret.LINK, i )
							.availableCoordinates().spliterator(),
							// algorithm is sequential! (+ no thread-safety)
							false )
					// FIXME ujmp bug: row=col
					.mapToLong( coords -> coords[0] )
					.filter( j -> i < j && this.rng.nextDouble() < this.beta )
					.forEach( j ->
					{
						final long[] i_k = { i, j };

						// shuffle until : non-self and non-used
						while( i_k[1] == i || result
								.getAsBigDecimal( top( i_k ) ).signum() != 0 )
							i_k[1] = this.rng.nextLong( size );

						final Object w = result.getAsObject( i, j );
						result.setAsObject( w, top( i_k ) ); // set new position
						result.setAsObject( null, i, j ); // reset old position
					} ) );

			// make symmetric : w_j,i <- w_i,j
			StreamSupport
					.stream( result.availableCoordinates().spliterator(),
							false )
					.forEach( coords -> result.setAsObject(
							result.getAsObject( coords ), coords[1],
							coords[0] ) );

			return result;
		}

	}

}

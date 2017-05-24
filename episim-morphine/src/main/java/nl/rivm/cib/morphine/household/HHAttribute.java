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

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.ujmp.core.Matrix;

/**
 * {@link HHAttribute}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public enum HHAttribute
{
	IDENTIFIER,

	PLACE_REF,

	REGISTERED,

	RELIGIOUS,

	ALTERNATIVE,

	CALCULATION,

	CONFIDENCE,

	COMPLACENCY,

	BARRIER,

	REFERENT_REF,

	PARTNER_REF,

	CHILD1_REF,

	CHILD2_REF,

	CHILD3_REF,

	;

	public Object get( final Matrix data, final long hhIndex )
	{
		return data.getAsObject( hhIndex, ordinal() );
	}

	public static Map<HHAttribute, Object> get( final Matrix data,
		final long hhIndex, final HHAttribute... attribute )
	{
		return Arrays.stream( Objects.requireNonNull( attribute ) )
				.collect( Collectors.toMap( att -> att,
						att -> att.get( data, hhIndex ), ( att1, att2 ) -> att1,
						() -> new EnumMap<>( HHAttribute.class ) ) );
	}
}

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

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * {@link HHAttribute}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public enum HHAttribute
{
	/** population-unique identifier (may be replaced upon death/emigration) */
	IDENTIFIER,

	/** in-group identifier determines oracle/authority/attractor */
	ATTRACTOR_REF,

	/** {@link Boolean} determines hesitancy profile */
	RELIGIOUS,

	/** {@link Boolean} determines hesitancy profile */
	ALTERNATIVE,

	/**
	 * social <a
	 * href=https://www.wikiwand.com/en/Assortativity>assortativity</a> &isin;
	 * [0,1] representing <a
	 * href=https://www.wikiwand.com/en/Homophily>homophily</a> in peer pressure
	 */
	SOCIAL_ASSORTATIVITY,

	/**
	 * school <a
	 * href=https://www.wikiwand.com/en/Assortativity>assortativity</a> &isin;
	 * [0,1] representing <a
	 * href=https://www.wikiwand.com/en/Homophily>homophily</a> in transmission
	 */
	SCHOOL_ASSORTATIVITY,

	/** {@link Matrix} hh indices {@link Matrix#getAsLong} */
	SOCIAL_NETWORK,

	/** {@link BigDecimal} &isin; [0,1] */
	CALCULATION,

	/** {@link BigDecimal} &isin; [0,1] */
	CONFIDENCE,

	/** {@link BigDecimal} &isin; [0,1] */
	COMPLACENCY,

//	BARRIER,

	/** {@link Long} member row-index */
	REFERENT_REF,

//	/** {@link Long} member row-index, or -1 for N/A */
//	PARTNER_REF,

	/** {@link Long} member row-index, or -1 for N/A */
	CHILD1_REF,

//	/** {@link Long} member row-index, or -1 for N/A */
//	CHILD2_REF,
//
//	/** {@link Long} member row-index, or -1 for N/A */
//	CHILD3_REF,

	;

	public Object get( final Matrix data, final long hhIndex )
	{
		return data.getAsObject( hhIndex, ordinal() );
	}

	public static Map<HHAttribute, Object> toMap( final Matrix data,
		final long hhIndex, final HHAttribute... attribute )
	{
		return Arrays.stream( Objects.requireNonNull( attribute ) )
				.collect( Collectors.toMap( att -> att,
						att -> att.get( data, hhIndex ), ( att1, att2 ) -> att1,
						() -> new EnumMap<>( HHAttribute.class ) ) );
	}

	private String json = null;

	@JsonValue
	public String jsonValue()
	{
		return this.json == null ? (this.json = name().toLowerCase())
				: this.json;
	}
}

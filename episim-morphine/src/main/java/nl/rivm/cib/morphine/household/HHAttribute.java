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
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.ujmp.core.Matrix;

import nl.rivm.cib.morphine.json.HHJsonifiable;
import nl.rivm.cib.morphine.json.RelationFrequencyJson;

/**
 * {@link HHAttribute}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public enum HHAttribute implements HHJsonifiable
{
	/** population-unique identifier (may be replaced upon death/emigration) */
	IDENTIFIER,

	/** in-group identifier determines oracle/authority/attractor */
	ATTRACTOR_REF,

	/** {@link Boolean} determines hesitancy profile */
//	RELIGIOUS,

	/** {@link Boolean} determines hesitancy profile */
//	ALTERNATIVE,

	/** {@link Matrix} hh indices {@link Matrix#getAsLong} */
	SOCIAL_NETWORK_SIZE,

	/** drawn from CBS social contact profile {@link RelationFrequencyJson} */
	IMPRESSION_DAYS, 

	/** in-group peer pressure */
	IMPRESSION_INPEER,

	/** out-group peer pressure */
	IMPRESSION_OUTPEER,

	/** own resolve */
	IMPRESSION_SELF,

	/** coherence */
	IMPRESSION_ATTRACTOR,

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

	public static <T> Map<HHAttribute, T> toMap(
		final Function<Integer, T> data, final HHAttribute... attributeFilter )
	{
		return Arrays
				.stream( attributeFilter == null || attributeFilter.length == 0
						? values() : attributeFilter )
				.collect( Collectors.toMap( att -> att,
						att -> data.apply( att.ordinal() ),
						( att1, att2 ) -> att1,
						() -> new EnumMap<>( HHAttribute.class ) ) );
	}

	private String json = null;

	@Override
	public String jsonValue()
	{
		return this.json == null
				? (this.json = name().toLowerCase().replace( '_', '-' ))
				: this.json;
	}
}

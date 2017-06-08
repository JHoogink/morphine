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
package nl.rivm.cib.morphine.dao;

import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.ujmp.core.Matrix;

import com.eaio.uuid.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;

import io.coala.persist.Persistable;
import io.coala.persist.UUIDToByteConverter;
import io.coala.time.Instant;
import io.coala.time.TimeUnits;
import nl.rivm.cib.morphine.household.HHAttribute;
import nl.rivm.cib.morphine.household.HHMemberAttribute;

/**
 * {@link HHStatisticsDao} with JPA MetaModel in {@link HouseholdDao_}?
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
@Entity
@Table( name = "HOUSEHOLDS" )
public class HHStatisticsDao implements Persistable.Dao
{

	private static final String TIME_COL_DEF = "DECIMAL(10,4)";

	private static final String ATTITUDE_COL_DEF = "DECIMAL(10,8)";

	/**
	 * @param now current virtual time {@link Instant} for calculating age
	 * @param households household data {@link Matrix} per {@link HHAttribute}
	 * @param rowIndex the household's respective row index
	 * @param members member data {@link Matrix} per {@link HHMemberAttribute}
	 * @return a {@link MemberDao}
	 */
	public static HHStatisticsDao create( final UUID context, final Instant now,
		final Matrix households, final long rowIndex, final Matrix members )
	{
		final HHStatisticsDao result = new HHStatisticsDao();
		result.context = context;
		result.hh = households.getAsInt( rowIndex,
				HHAttribute.IDENTIFIER.ordinal() );
		result.time = now.to( TimeUnits.DAYS ).decimal();
		result.homeRef = households.getAsInt( rowIndex,
				HHAttribute.HOME_REF.ordinal() );
		result.religious = households.getAsBoolean( rowIndex,
				HHAttribute.RELIGIOUS.ordinal() );
		result.alternative = households.getAsBoolean( rowIndex,
				HHAttribute.ALTERNATIVE.ordinal() );
		result.calculation = households.getAsBigDecimal( rowIndex,
				HHAttribute.CALCULATION.ordinal() );
		result.confidence = households.getAsBigDecimal( rowIndex,
				HHAttribute.CONFIDENCE.ordinal() );
		result.complacency = households.getAsBigDecimal( rowIndex,
				HHAttribute.COMPLACENCY.ordinal() );
//		result.barrier = households.getAsBigDecimal( rowIndex,
//				HHAttribute.BARRIER.ordinal() );
		result.referent = MemberDao.create( now, members, households
				.getAsLong( rowIndex, HHAttribute.REFERENT_REF.ordinal() ) );
		result.partner = MemberDao.create( now, members, households
				.getAsLong( rowIndex, HHAttribute.PARTNER_REF.ordinal() ) );
		result.child1 = MemberDao.create( now, members, households
				.getAsLong( rowIndex, HHAttribute.CHILD1_REF.ordinal() ) );
		result.child2 = MemberDao.create( now, members, households
				.getAsLong( rowIndex, HHAttribute.CHILD2_REF.ordinal() ) );
		result.child3 = MemberDao.create( now, members, households
				.getAsLong( rowIndex, HHAttribute.CHILD3_REF.ordinal() ) );
		return result;
	}

	/**
	 * {@link MemberDao} is an {@link Embeddable} member data access object
	 */
	@Embeddable
	public static class MemberDao implements Persistable.Dao
	{
		/**
		 * @param now current virtual time {@link Instant} for calculating age
		 * @param data member data {@link Matrix} per {@link HHMemberAttribute}
		 * @param rowIndex the member's respective row index
		 * @return a {@link MemberDao}
		 */
		public static MemberDao create( final Instant now, final Matrix data,
			final long rowIndex )
		{
			if( rowIndex < 0 ) return null;
			final MemberDao result = new MemberDao();
			result.age = now.to( TimeUnits.ANNUM ).decimal()
					.subtract( data.getAsBigDecimal( rowIndex,
							HHMemberAttribute.BIRTH.ordinal() ) );
			result.male = data.getAsBoolean( rowIndex,
					HHMemberAttribute.MALE.ordinal() );
			result.status = data.getAsInt( rowIndex,
					HHMemberAttribute.STATUS.ordinal() );
//			result.behavior = data.getAsInt( rowIndex,
//					HHMemberAttribute.BEHAVIOR.ordinal() );
			return result;
		}

		public static final String AGE_ATTR = "age";

		public static final String STATUS_ATTR = "status";

		public static final String MALE_ATTR = "male";

//		public static final String BEHAVIOR_ATTR = "behavior";

		@Column
		protected BigDecimal age;

		@Column
		protected int status;

		@Column
		protected boolean male;

//		@Column
//		protected int behavior;

	}

	@Id
	@GeneratedValue
	@Column( name = "PK", nullable = false, updatable = false )
	protected Integer pk = null;

	/** time stamp of insert, as per http://stackoverflow.com/a/3107628 */
	@Temporal( TemporalType.TIMESTAMP )
	@Column( name = "CREATED_TS", insertable = false, updatable = false,
		columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP" )
	@JsonIgnore
	protected Date created = null;

	@Column( name = "CONTEXT", nullable = false, updatable = false, length = 16,
		columnDefinition = "BINARY(16)" )
	@Convert( converter = UUIDToByteConverter.class )
	protected UUID context;

	@Column( name = "HH", nullable = false, updatable = false )
	protected int hh;

	@Column( name = "TIME", nullable = false, updatable = false,
		columnDefinition = TIME_COL_DEF )
	protected BigDecimal time;

	@Column( name = "HOME_REF", nullable = false, updatable = false )
	protected int homeRef;

	@Column( name = "RELIGIOUS", nullable = false, updatable = false )
	protected boolean religious;

	@Column( name = "ALTERNATIVE", nullable = false, updatable = false )
	protected boolean alternative;

	@Column( name = "CALCULATION", nullable = false, updatable = false,
		columnDefinition = ATTITUDE_COL_DEF )
	protected BigDecimal calculation;

	@Column( name = "CONFIDENCE", nullable = false, updatable = false,
		columnDefinition = ATTITUDE_COL_DEF )
	protected BigDecimal confidence;

	@Column( name = "COMPLACENCY", nullable = false, updatable = false,
		columnDefinition = ATTITUDE_COL_DEF )
	protected BigDecimal complacency;

//	@Column( name = "BARRIER", nullable = true, updatable = false,
//		columnDefinition = ATTITUDE_COL_DEF )
//	protected BigDecimal barrier;

	@AttributeOverrides( {
			@AttributeOverride( name = MemberDao.AGE_ATTR,
				column = @Column( name = "REFERENT_AGE", nullable = false,
					updatable = false, columnDefinition = TIME_COL_DEF ) ),
			@AttributeOverride( name = MemberDao.STATUS_ATTR,
				column = @Column( name = "REFERENT_STATUS", nullable = false,
					updatable = false ) ),
			@AttributeOverride( name = MemberDao.MALE_ATTR,
				column = @Column( name = "REFERENT_MALE", nullable = false,
					updatable = false ) ),
//			@AttributeOverride( name = MemberDao.BEHAVIOR_ATTR,
//				column = @Column( name = "REFERENT_BEHAVIOR", nullable = false,
//					updatable = false ) )
	} )
	@Embedded
	protected MemberDao referent;

	@AttributeOverrides( {
			@AttributeOverride( name = MemberDao.AGE_ATTR,
				column = @Column( name = "PARTNER_AGE", nullable = true,
					updatable = false, columnDefinition = TIME_COL_DEF ) ),
			@AttributeOverride( name = MemberDao.STATUS_ATTR,
				column = @Column( name = "PARTNER_STATUS", nullable = true,
					updatable = false ) ),
			@AttributeOverride( name = MemberDao.MALE_ATTR,
				column = @Column( name = "PARTNER_MALE", nullable = true,
					updatable = false ) ),
//			@AttributeOverride( name = MemberDao.BEHAVIOR_ATTR,
//				column = @Column( name = "PARTNER_BEHAVIOR", nullable = true,
//					updatable = false ) )
	} )
	@Embedded
	protected MemberDao partner;

	@AttributeOverrides( {
			@AttributeOverride( name = MemberDao.AGE_ATTR,
				column = @Column( name = "CHILD1_AGE", nullable = true,
					updatable = false, columnDefinition = TIME_COL_DEF ) ),
			@AttributeOverride( name = MemberDao.STATUS_ATTR,
				column = @Column( name = "CHILD1_STATUS", nullable = true,
					updatable = false ) ),
			@AttributeOverride( name = MemberDao.MALE_ATTR,
				column = @Column( name = "CHILD1_MALE", nullable = true,
					updatable = false ) ),
//			@AttributeOverride( name = MemberDao.BEHAVIOR_ATTR,
//				column = @Column( name = "CHILD1_BEHAVIOR", nullable = true,
//					updatable = false ) ) 
	} )
	@Embedded
	protected MemberDao child1;

	@AttributeOverrides( {
			@AttributeOverride( name = MemberDao.AGE_ATTR,
				column = @Column( name = "CHILD2_AGE", nullable = true,
					updatable = false, columnDefinition = TIME_COL_DEF ) ),
			@AttributeOverride( name = MemberDao.STATUS_ATTR,
				column = @Column( name = "CHILD2_STATUS", nullable = true,
					updatable = false ) ),
			@AttributeOverride( name = MemberDao.MALE_ATTR,
				column = @Column( name = "CHILD2_MALE", nullable = true,
					updatable = false ) ),
//			@AttributeOverride( name = MemberDao.BEHAVIOR_ATTR,
//				column = @Column( name = "CHILD2_BEHAVIOR", nullable = true,
//					updatable = false ) ) 
	} )
	@Embedded
	protected MemberDao child2;

	@AttributeOverrides( {
			@AttributeOverride( name = MemberDao.AGE_ATTR,
				column = @Column( name = "CHILD3_AGE", nullable = true,
					updatable = false, columnDefinition = TIME_COL_DEF ) ),
			@AttributeOverride( name = MemberDao.STATUS_ATTR,
				column = @Column( name = "CHILD3_STATUS", nullable = true,
					updatable = false ) ),
			@AttributeOverride( name = MemberDao.MALE_ATTR,
				column = @Column( name = "CHILD3_MALE", nullable = true,
					updatable = false ) ),
//			@AttributeOverride( name = MemberDao.BEHAVIOR_ATTR,
//				column = @Column( name = "CHILD3_BEHAVIOR", nullable = true,
//					updatable = false ) ) 
	} )
	@Embedded
	protected MemberDao child3;

	@Override
	public String toString()
	{
		return stringify();
	}
}
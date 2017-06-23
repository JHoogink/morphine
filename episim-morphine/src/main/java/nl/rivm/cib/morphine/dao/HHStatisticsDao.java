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

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.ujmp.core.Matrix;

import io.coala.bind.LocalId;
import io.coala.persist.JPAUtil;
import io.coala.persist.Persistable;
import io.coala.time.Instant;
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
@SequenceGenerator( name = HHStatisticsDao.HH_SEQ, allocationSize = 25 )
public class HHStatisticsDao implements Persistable.Dao
{
	public static final String HH_SEQ = "HH_SEQ";

	private static final String TIME_COL_DEF = "DECIMAL(10,4)";

	private static final String ATTITUDE_COL_DEF = "DECIMAL(10,8)";

	/**
	 * @param now current virtual time {@link Instant} for calculating age
	 * @param households household data {@link Matrix} per {@link HHAttribute}
	 * @param rowIndex the household's respective row index
	 * @param members member data {@link Matrix} per {@link HHMemberAttribute}
	 * @return a {@link HHMemberDao}
	 */
	public static HHStatisticsDao create( final HHConfigDao run,
		final Instant now, final int seq, final String[] attractorNames,
		final Matrix households, final long rowIndex, final Matrix members )
	{
		final HHStatisticsDao result = new HHStatisticsDao();
//		result.context = context;
		result.config = run;
		result.hh = households.getAsInt( rowIndex,
				HHAttribute.IDENTIFIER.ordinal() );
		result.seq = seq;
		result.attractorRef = attractorNames[households.getAsInt( rowIndex,
				HHAttribute.ATTRACTOR_REF.ordinal() ) % attractorNames.length];
		result.socialNetworkSize = households.getAsInt( rowIndex,
				HHAttribute.SOCIAL_NETWORK_SIZE.ordinal() );
		result.socialAssortativity = households.getAsBigDecimal( rowIndex,
				HHAttribute.SOCIAL_ASSORTATIVITY.ordinal() );
		result.impressionDays = households.getAsBigDecimal( rowIndex,
				HHAttribute.IMPRESSION_DAYS.ordinal() );
		;
		result.impressionInpeers = households.getAsBigDecimal( rowIndex,
				HHAttribute.IMPRESSION_INPEER.ordinal() );
		result.impressionOutpeers = households.getAsBigDecimal( rowIndex,
				HHAttribute.IMPRESSION_OUTPEER.ordinal() );
		result.impressionSelf = households.getAsBigDecimal( rowIndex,
				HHAttribute.IMPRESSION_SELF.ordinal() );
		result.impressionAttractor = households.getAsBigDecimal( rowIndex,
				HHAttribute.IMPRESSION_ATTRACTOR.ordinal() );
//		result.schoolAssortativity = households.getAsBigDecimal( rowIndex,
//				HHAttribute.SCHOOL_ASSORTATIVITY.ordinal() );
//		result.religious = households.getAsBoolean( rowIndex,
//				HHAttribute.RELIGIOUS.ordinal() );
//		result.alternative = households.getAsBoolean( rowIndex,
//				HHAttribute.ALTERNATIVE.ordinal() );
		result.calculation = households.getAsBigDecimal( rowIndex,
				HHAttribute.CALCULATION.ordinal() );
		result.confidence = households.getAsBigDecimal( rowIndex,
				HHAttribute.CONFIDENCE.ordinal() );
		result.complacency = households.getAsBigDecimal( rowIndex,
				HHAttribute.COMPLACENCY.ordinal() );
		result.referent = HHMemberDao.create( now, members, households
				.getAsLong( rowIndex, HHAttribute.REFERENT_REF.ordinal() ) );
//		result.partner = MemberDao.create( now, members, households
//				.getAsLong( rowIndex, HHAttribute.PARTNER_REF.ordinal() ) );
		result.child1 = HHMemberDao.create( now, members, households
				.getAsLong( rowIndex, HHAttribute.CHILD1_REF.ordinal() ) );
//		result.child2 = MemberDao.create( now, members, households
//				.getAsLong( rowIndex, HHAttribute.CHILD2_REF.ordinal() ) );
//		result.child3 = MemberDao.create( now, members, households
//				.getAsLong( rowIndex, HHAttribute.CHILD3_REF.ordinal() ) );
		return result;
	}

	/**
	 * @param em
	 */
	public void persist( final EntityManager em, final LocalId id )
	{
		if( !em.contains( this.config ) )
			this.config = JPAUtil.findOrCreate( em,
					() -> HHConfigDao.find( em, id ),
					() -> em.merge( this.config ) );
		em.persist( this );
	}

	@Id
	@GeneratedValue( generator = HH_SEQ )
	@Column( name = "PK", nullable = false, updatable = false )
	protected Integer pk = null;

	/** time stamp of insert, as per http://stackoverflow.com/a/3107628 */
//	@Temporal( TemporalType.TIMESTAMP )
//	@Column( name = "CREATED_TS", insertable = false, updatable = false,
//		columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP" )
//	@JsonIgnore
//	protected Date created = null;

//	@Column( name = "CONTEXT", nullable = false, updatable = false, length = 16,
//		columnDefinition = "BINARY(16)" )
//	@Convert( converter = UUIDToByteConverter.class )
//	protected UUID context;

	@ManyToOne( optional = false, cascade = CascadeType.PERSIST,
		fetch = FetchType.LAZY )
//	@Column( name = "CONFIG", nullable = false, updatable = false )
	public HHConfigDao config;

	@Column( name = "HH", nullable = false, updatable = false )
	protected int hh;

	@Column( name = "SEQ", nullable = false, updatable = false )
	protected int seq;

	@Column( name = "ATTRACTOR_REF", nullable = false, updatable = false )
	protected String attractorRef;

	@Column( name = "SOCIAL_ASSORTATIVITY", nullable = false, updatable = false,
		columnDefinition = ATTITUDE_COL_DEF )
	protected BigDecimal socialAssortativity;

	@Column( name = "SOCIAL_NETWORK_SIZE", nullable = false, updatable = false )
	protected int socialNetworkSize;

	@Column( name = "IMPRESSION_DAYS", nullable = false, updatable = false,
		columnDefinition = ATTITUDE_COL_DEF )
	protected BigDecimal impressionDays;

	@Column( name = "IMPRESSION_INPEERS", nullable = false, updatable = false,
		columnDefinition = ATTITUDE_COL_DEF )
	protected BigDecimal impressionInpeers;

	@Column( name = "IMPRESSION_OUTPEERS", nullable = false, updatable = false,
		columnDefinition = ATTITUDE_COL_DEF )
	protected BigDecimal impressionOutpeers;

	@Column( name = "IMPRESSION_SELF", nullable = false, updatable = false,
		columnDefinition = ATTITUDE_COL_DEF )
	protected BigDecimal impressionSelf;

	@Column( name = "IMPRESSION_ATTRACTOR", nullable = false, updatable = false,
		columnDefinition = ATTITUDE_COL_DEF )
	protected BigDecimal impressionAttractor;

//	@Column( name = "SCHOOL_ASSORTATIVITY", nullable = false, updatable = false,
//		columnDefinition = ATTITUDE_COL_DEF )
//	protected BigDecimal schoolAssortativity;

//	@Column( name = "RELIGIOUS", nullable = false, updatable = false )
//	protected boolean religious;

//	@Column( name = "ALTERNATIVE", nullable = false, updatable = false )
//	protected boolean alternative;

	@Column( name = "CALCULATION", nullable = false, updatable = false,
		columnDefinition = ATTITUDE_COL_DEF )
	protected BigDecimal calculation;

	@Column( name = "CONFIDENCE", nullable = false, updatable = false,
		columnDefinition = ATTITUDE_COL_DEF )
	protected BigDecimal confidence;

	@Column( name = "COMPLACENCY", nullable = false, updatable = false,
		columnDefinition = ATTITUDE_COL_DEF )
	protected BigDecimal complacency;

	@AttributeOverrides( {
			@AttributeOverride( name = HHMemberDao.AGE_ATTR,
				column = @Column( name = "REFERENT_AGE", nullable = false,
					updatable = false, columnDefinition = TIME_COL_DEF ) ),
//			@AttributeOverride( name = HHMemberDao.STATUS_ATTR,
//				column = @Column( name = "REFERENT_STATUS", nullable = false,
//					updatable = false ) ),
			@AttributeOverride( name = HHMemberDao.MALE_ATTR,
				column = @Column( name = "REFERENT_MALE", nullable = false,
					updatable = false ) ),
//			@AttributeOverride( name = MemberDao.BEHAVIOR_ATTR,
//				column = @Column( name = "REFERENT_BEHAVIOR", nullable = false,
//					updatable = false ) )
	} )
	@Embedded
	protected HHMemberDao referent;

//	@AttributeOverrides( {
//			@AttributeOverride( name = MemberDao.AGE_ATTR,
//				column = @Column( name = "PARTNER_AGE", nullable = true,
//					updatable = false, columnDefinition = TIME_COL_DEF ) ),
//			@AttributeOverride( name = MemberDao.STATUS_ATTR,
//				column = @Column( name = "PARTNER_STATUS", nullable = true,
//					updatable = false ) ),
//			@AttributeOverride( name = MemberDao.MALE_ATTR,
//				column = @Column( name = "PARTNER_MALE", nullable = true,
//					updatable = false ) ),
////			@AttributeOverride( name = MemberDao.BEHAVIOR_ATTR,
////				column = @Column( name = "PARTNER_BEHAVIOR", nullable = true,
////					updatable = false ) )
//	} )
//	@Embedded
//	protected HHMemberDao partner;

	@AttributeOverrides( {
			@AttributeOverride( name = HHMemberDao.AGE_ATTR,
				column = @Column( name = "CHILD1_AGE", nullable = true,
					updatable = false, columnDefinition = TIME_COL_DEF ) ),
			@AttributeOverride( name = HHMemberDao.STATUS_ATTR,
				column = @Column( name = "CHILD1_STATUS", nullable = true,
					updatable = false ) ),
//			@AttributeOverride( name = HHMemberDao.MALE_ATTR,
//				column = @Column( name = "CHILD1_MALE", nullable = true,
//					updatable = false ) ),
//			@AttributeOverride( name = MemberDao.BEHAVIOR_ATTR,
//				column = @Column( name = "CHILD1_BEHAVIOR", nullable = true,
//					updatable = false ) ) 
	} )
	@Embedded
	protected HHMemberDao child1;

//	@AttributeOverrides( {
//			@AttributeOverride( name = MemberDao.AGE_ATTR,
//				column = @Column( name = "CHILD2_AGE", nullable = true,
//					updatable = false, columnDefinition = TIME_COL_DEF ) ),
//			@AttributeOverride( name = MemberDao.STATUS_ATTR,
//				column = @Column( name = "CHILD2_STATUS", nullable = true,
//					updatable = false ) ),
//			@AttributeOverride( name = MemberDao.MALE_ATTR,
//				column = @Column( name = "CHILD2_MALE", nullable = true,
//					updatable = false ) ),
////			@AttributeOverride( name = MemberDao.BEHAVIOR_ATTR,
////				column = @Column( name = "CHILD2_BEHAVIOR", nullable = true,
////					updatable = false ) ) 
//	} )
//	@Embedded
//	protected HHMemberDao child2;

//	@AttributeOverrides( {
//			@AttributeOverride( name = MemberDao.AGE_ATTR,
//				column = @Column( name = "CHILD3_AGE", nullable = true,
//					updatable = false, columnDefinition = TIME_COL_DEF ) ),
//			@AttributeOverride( name = MemberDao.STATUS_ATTR,
//				column = @Column( name = "CHILD3_STATUS", nullable = true,
//					updatable = false ) ),
//			@AttributeOverride( name = MemberDao.MALE_ATTR,
//				column = @Column( name = "CHILD3_MALE", nullable = true,
//					updatable = false ) ),
////			@AttributeOverride( name = MemberDao.BEHAVIOR_ATTR,
////				column = @Column( name = "CHILD3_BEHAVIOR", nullable = true,
////					updatable = false ) ) 
//	} )
//	@Embedded
//	protected HHMemberDao child3;

	@Override
	public String toString()
	{
		return stringify();
	}
}
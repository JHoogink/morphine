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
import java.text.ParseException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import io.coala.bind.LocalBinder;
import io.coala.exception.ExceptionFactory;
import io.coala.json.JsonUtil;
import io.coala.log.LogUtil.Pretty;
import io.coala.time.Duration;
import io.coala.time.Proactive;
import io.coala.time.Scheduler;
import io.coala.time.Timing;
import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.Subject;

/**
 * {@link HHOracle} adds special proactive entities acting as special
 * households, representing the nationally or locally communicated (dynamic)
 * positions of e.g. public health, religious, alternative medicinal
 * authorities, or socially observed disease or adverse events, and determining
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public interface HHOracle
{
	/**
	 * @return an {@link Observable} stream of {@link HHAttribute} values
	 *         {@link Map mapped} as {@link BigDecimal}
	 */
	Observable<Map<HHAttribute, BigDecimal>> position();

	/**
	 * @param config a {@link JsonNode} configuration
	 * @return this {@link HHOracle} for chaining
	 */
	HHOracle reset( JsonNode config ) throws ParseException;

	/**
	 * {@link SignalSchedule} executes simple position updates configured as
	 * {@link SignalSchedule.SignalJson} entries
	 */
	class SignalSchedule implements HHOracle, Proactive
	{
		/** {@link SignalJson} specifies position update rule configurations */
		public static class SignalJson
		{
			public String recurrence;
			public Duration interval;
			public EnumMap<HHAttribute, SortedMap<Integer, BigDecimal>> series;
		}

		public static final String SCHEDULE_KEY = "schedule";

		public static final String SERIES_SEP = Pattern.quote( ";" );

		private transient BehaviorSubject<Map<HHAttribute, BigDecimal>> position;

		@Inject
		private Scheduler scheduler;

		@Override
		public String toString()
		{
			return getClass().getSimpleName() + "@"
					+ Integer.toHexString( hashCode() );
		}

		@Override
		public Scheduler scheduler()
		{
			return this.scheduler;
		}

		@Override
		public Observable<Map<HHAttribute, BigDecimal>> position()
		{
			return this.position;
		}

		@Override
		public HHOracle reset( final JsonNode config )
		{
			if( this.position != null ) this.position.onComplete();

			// set default position (attribute values) from config
			final BehaviorSubject<Map<HHAttribute, BigDecimal>> position = BehaviorSubject
					.createDefault( Arrays.stream( HHAttribute.values() )
							.filter( att -> config.has( att.jsonValue() ) )
							.collect( Collectors.toMap( att -> att,
									att -> config
											.get( att.jsonValue() )
											.decimalValue(),
									( k1, k2 ) -> k1, () -> new EnumMap<>(
											HHAttribute.class ) ) ) );

			// schedule specified attribute value schedules, if any
			if( config.has( SCHEDULE_KEY ) )
			{
				Observable.fromIterable( config.get( SCHEDULE_KEY ) ).map(
						node -> JsonUtil.valueOf( node, SignalJson.class ) )
						.blockingSubscribe( item ->
						{
							atEach( Timing.of( item.recurrence ).iterate(),
									t -> updatePosition( position,
											item.interval, item.series, 0 ) );
						} );
			}
			this.position = position;
			return this;
		}

		// this method repeatedly schedules itself until the series are complete
		protected void updatePosition(
			final Subject<Map<HHAttribute, BigDecimal>> position,
			final Duration interval,
			final Map<HHAttribute, SortedMap<Integer, BigDecimal>> series,
			final int index )
		{
			if( series.isEmpty() || position.hasComplete() ) return;
			final EnumMap<HHAttribute, BigDecimal> newPosition = series
					.entrySet().parallelStream()
					.filter( e -> index < e.getValue().size() )
					.collect( Collectors.toMap( Entry::getKey,
							e -> e.getValue().get( index ), ( k1,
								k2 ) -> k1,
							() -> new EnumMap<>( HHAttribute.class ) ) );
			if( newPosition.isEmpty() ) return;
			position.onNext( newPosition );
			after( interval ).call( t -> updatePosition( position, interval,
					series, index + 1 ) );
		}
	}

	interface Factory
	{
		String TYPE_KEY = "type";

		HHOracle create( JsonNode config ) throws Exception;

		default Observable<HHOracle> createAll( final JsonNode config,
			final LocalBinder binder )
		{
			return Observable.fromIterable( config ).flatMap( node ->
			{
				try
				{
					final Class<? extends HHOracle> type = node.has( TYPE_KEY )
							? Class.forName( node.get( TYPE_KEY ).textValue() )
									.asSubclass( HHOracle.class )
							: SignalSchedule.class;
					return Observable
							.just( binder.inject( type ).reset( node ) );
				} catch( final Exception e )
				{
					return Observable.error(
							ExceptionFactory.createUnchecked( e, Pretty.of(
									() -> "Problem with config: " + node ) ) );
				}
			} );
		}

		@Singleton
		class SimpleBinding implements Factory
		{
			@Inject
			private LocalBinder binder;

			@Override
			public HHOracle create( final JsonNode config )
				throws ClassNotFoundException
			{
				return this.binder.inject(
						Class.forName( config.get( TYPE_KEY ).textValue() )
								.asSubclass( HHOracle.class ) );
			}
		}
	}
}

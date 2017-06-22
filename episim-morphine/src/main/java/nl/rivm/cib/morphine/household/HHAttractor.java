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
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import io.coala.bind.LocalBinder;
import io.coala.exception.Thrower;
import io.coala.time.Duration;
import io.coala.time.Scheduler;
import io.reactivex.Observable;
import nl.rivm.cib.morphine.json.HesitancyProfileJson;
import nl.rivm.cib.morphine.json.HesitancyProfileJson.Category;

/**
 * {@link HHAttractor} adds special proactive entities acting as special
 * households, representing the nationally or locally communicated (dynamic)
 * positions of e.g. public health, religious, alternative medicinal
 * authorities, or socially observed disease or adverse events, and determining
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
public interface HHAttractor extends HHScenarioConfigurable
{
	String TYPE_KEY = "type";

	String RELIGIOUS_KEY = "religious";

	boolean RELIGIOUS_DEFAULT = false;

	String ALTERNATIVE_KEY = "alternative";

	boolean ALTERNATIVE_DEFAULT = false;

	/**
	 * @return an {@link Observable} stream of {@link HHAttribute} values
	 *         {@link Map mapped} as {@link BigDecimal}
	 */
	Observable<Map<HHAttribute, BigDecimal>> position();

	HesitancyProfileJson.Category toHesitancyProfile();

	/**
	 * {@link SignalSchedule} executes simple position updates configured as
	 * {@link SignalSchedule.SignalYaml} entries
	 */
	class SignalSchedule implements HHAttractor
	{

		private JsonNode config;

		@Inject
		private Scheduler scheduler;

		@Override
		public Scheduler scheduler()
		{
			return this.scheduler;
		}

		@Override
		public Observable<Map<HHAttribute, BigDecimal>> position()
		{
			if( this.config == null ) return Observable.empty();

			final Map<HHAttribute, BigDecimal> initial = Arrays
					.stream( HHAttribute.values() )
					.filter( attr -> this.config.has( attr.jsonValue() ) )
					.collect(
							Collectors.toMap( attr -> attr, attr -> this.config
									.get( attr.jsonValue() ).decimalValue() ) );

			if( !this.config.has( SCHEDULE_KEY ) )
				return Observable.just( initial );

			return Observable.create( sub ->
			{
				after( Duration.ZERO ).call( t -> sub.onNext( initial ) );
				iterate( this.config.get( SCHEDULE_KEY ), HHAttribute.class,
						BigDecimal.class ).subscribe( sub::onNext,
								sub::onError );
			} );
		}

		@Override
		public HHAttractor reset( final JsonNode config ) throws ParseException
		{
			this.config = config;
			return this;
		}

		@Override
		public String toString()
		{
			return getClass().getSimpleName() + this.config;
		}

		@Override
		public Category toHesitancyProfile()
		{
			return new HesitancyProfileJson.Category(
					this.config.get( RELIGIOUS_KEY )
							.asBoolean( RELIGIOUS_DEFAULT ),
					this.config.get( ALTERNATIVE_KEY )
							.asBoolean( ALTERNATIVE_DEFAULT ) );
		}
	}

	interface Factory
	{
		HHAttractor create( JsonNode config ) throws Exception;

		static HHAttractor construct( final JsonNode node,
			final LocalBinder binder )
			throws ClassNotFoundException, ParseException
		{
			final Class<? extends HHAttractor> type = node.has( TYPE_KEY )
					? Class.forName( node.get( TYPE_KEY ).textValue() )
							.asSubclass( HHAttractor.class )
					: SignalSchedule.class;
			return binder.inject( type ).reset( node );
		}

		default Map<String, HHAttractor> createAll( final JsonNode config,
			final LocalBinder binder )
		{
			if( config.isArray() ) return IntStream.range( 0, config.size() )
					.mapToObj( i -> i ).collect( Collectors.toMap(
							i -> String.format( "attractor%02d", i ), i ->
							{
								try
								{
									return construct( config.get( i ), binder );
								} catch( final Exception e )
								{
									return Thrower.rethrowUnchecked( e );
								}
							}, ( k1, k2 ) -> k1, TreeMap::new ) );
			if( config.isObject() )
			{
				final Map<String, HHAttractor> result = new TreeMap<>();
				config.fields().forEachRemaining( e ->
				{
					try
					{
						result.put( e.getKey(),
								construct( e.getValue(), binder ) );
					} catch( final Exception e1 )
					{
						Thrower.rethrowUnchecked( e1 );
					}
				} );
				return result;
			}
			return Collections.emptyMap();
		}

		@Singleton
		class SimpleBinding implements Factory
		{
			@Inject
			private LocalBinder binder;

			@Override
			public HHAttractor create( final JsonNode config )
				throws ClassNotFoundException
			{
				return this.binder.inject(
						Class.forName( config.get( TYPE_KEY ).textValue() )
								.asSubclass( HHAttractor.class ) );
			}
		}
	}
}

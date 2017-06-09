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
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.fasterxml.jackson.databind.JsonNode;

import io.coala.bind.LocalBinder;
import io.coala.exception.ExceptionFactory;
import io.coala.log.LogUtil.Pretty;
import io.coala.time.Duration;
import io.coala.time.Scheduler;
import io.reactivex.Observable;

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
	/**
	 * @return an {@link Observable} stream of {@link HHAttribute} values
	 *         {@link Map mapped} as {@link BigDecimal}
	 */
	Observable<Map<HHAttribute, BigDecimal>> position();

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
	}

	interface Factory
	{
		String TYPE_KEY = "type";

		HHAttractor create( JsonNode config ) throws Exception;

		default Observable<HHAttractor> createAll( final JsonNode config,
			final LocalBinder binder )
		{
			return Observable.fromIterable( config ).flatMap( node ->
			{
				try
				{
					final Class<? extends HHAttractor> type = node
							.has( TYPE_KEY )
									? Class
											.forName( node.get( TYPE_KEY )
													.textValue() )
											.asSubclass( HHAttractor.class )
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

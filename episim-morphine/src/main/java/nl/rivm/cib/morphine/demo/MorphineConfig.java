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
package nl.rivm.cib.morphine.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aeonbits.owner.Config.Sources;

import io.coala.config.ConfigUtil;
import io.coala.config.LocalDateConverter;
import io.coala.config.PeriodConverter;
import io.coala.config.YamlConfig;
import io.coala.math.DecimalUtil;
import nl.rivm.cib.epidemes.demo.DemoScenario.Demical.PersonBroker;
import nl.rivm.cib.epidemes.demo.DemoScenario.Medical.HealthBroker;
import nl.rivm.cib.epidemes.demo.DemoScenario.Regional.SiteBroker;
import nl.rivm.cib.epidemes.demo.DemoScenario.Social.PeerBroker;
import nl.rivm.cib.epidemes.demo.DemoScenario.Social.SocietyBroker;
import nl.rivm.cib.epidemes.model.MSEIRS.Compartment;
import nl.rivm.cib.util.RandomSeedConverter;

/**
 * {@link MorphineConfig} used in {@link DemoModel}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
@Sources( {
		"file:" + MorphineConfig.CONFIG_BASE_PARAM + MorphineConfig.CONFIG_YAML_FILE,
		"file:" + MorphineConfig.CONFIG_BASE_DIR + MorphineConfig.CONFIG_YAML_FILE,
		"classpath:" + MorphineConfig.CONFIG_YAML_FILE } )
public interface MorphineConfig extends YamlConfig
{

	String CONF_ARG = "conf";

	String CONFIG_YAML_FILE = "demo.yaml";

	/** configuration and data file base directory */
	String CONFIG_BASE_DIR = "dist/";

	/** configuration file name system property */
	String CONFIG_BASE_KEY = "config.base";

	/** configuration file name system property */
	String CONFIG_BASE_PARAM = "${" + CONFIG_BASE_KEY + "}";

	@DefaultValue( CONFIG_BASE_DIR )
	@Key( CONFIG_BASE_KEY )
	String configBase();

	String DATASOURCE_JNDI = "jdbc/pilotDB";

	/** configuration key separator */
	String KEY_SEP = ConfigUtil.CONFIG_KEY_SEP;

	/** configuration key */
	String SCENARIO_BASE = "scenario";

	String RANDOM_SEED_KEY = "random-seed";

	String sep = ";", eol = "\r\n";

	/** configuration key */
	String REPLICATION_BASE = "replication";

	/** configuration key */
	String REPLICATION_PREFIX = SCENARIO_BASE + KEY_SEP + REPLICATION_BASE
			+ KEY_SEP;

	String MODULE_KEY = "module";

	/** configuration key */
	String DEMOGRAPHY_BASE = "demography";

	/** configuration key */
	String GEOGRAPHY_BASE = "geography";

	/** configuration key */
	String MOTION_BASE = "mobility";

	/** configuration key */
	String EPIDEMIOLOGY_BASE = "epidemiology";

	/** configuration key */
	String HESITANCY_BASE = "hesitancy";

	@Key( REPLICATION_PREFIX + "setup-name" )
	@DefaultValue( "pilot" )
	String setupName();

	@Key( REPLICATION_PREFIX + RANDOM_SEED_KEY )
	@DefaultValue( "NaN" )
	@ConverterClass( RandomSeedConverter.class )
	Long randomSeed();

	@Key( REPLICATION_PREFIX + "duration-period" )
	@DefaultValue( "P1Y" )
	@ConverterClass( PeriodConverter.class )
	Period duration();

	@Key( REPLICATION_PREFIX + "offset-date" )
	@DefaultValue( "2012-01-01" )
	@ConverterClass( LocalDateConverter.class )
	LocalDate offset();

	@Key( SCENARIO_BASE + KEY_SEP + DEMOGRAPHY_BASE + KEY_SEP + MODULE_KEY )
//	@DefaultValue( "nl.rivm.cib.epidemes.demo.impl.PersonBrokerSimple" )
	Class<? extends PersonBroker> demeModule();

	@Key( SCENARIO_BASE + KEY_SEP + EPIDEMIOLOGY_BASE + KEY_SEP + MODULE_KEY )
//	@DefaultValue( "nl.rivm.cib.epidemes.demo.impl.HealthBrokerSimple" )
	Class<? extends HealthBroker> healthModule();

	@Key( SCENARIO_BASE + KEY_SEP + HESITANCY_BASE + KEY_SEP + MODULE_KEY )
//	@DefaultValue( "nl.rivm.cib.epidemes.demo.impl.PeerBrokerSimple" )
	Class<? extends PeerBroker> peerModule();

	@Key( SCENARIO_BASE + KEY_SEP + GEOGRAPHY_BASE + KEY_SEP + MODULE_KEY )
//	@DefaultValue( "nl.rivm.cib.epidemes.demo.impl.SiteBrokerSimple" )
	Class<? extends SiteBroker> siteModule();

	@Key( SCENARIO_BASE + KEY_SEP + MOTION_BASE + KEY_SEP + MODULE_KEY )
//	@DefaultValue( "nl.rivm.cib.epidemes.demo.impl.SocietyBrokerSimple" )
	Class<? extends SocietyBroker> societyModule();

	static String toHeader( final Object config,
		final List<Compartment> sirCols,
		final Map<String, Set<String>> colMapping )
	{
		return "\"ActualTime "
				// escape double-quotes within double-quotes by doubling them
				// see https://stackoverflow.com/a/43274459
				+ config.toString().replaceAll( "\"", "\"\"" ) + "\"" + sep
				+ "VirtualTime" + sep
				+ String.join( sep,
						sirCols.stream().map( c -> c.name() )
								.toArray( String[]::new ) )
				+ sep
				+ String.join( sep, sirCols.stream()
						.flatMap( c -> colMapping.keySet().stream()
								.map( reg -> c.name().substring( 0, 1 ) + '_'
										+ reg ) )
						.toArray( String[]::new ) )
				+ eol;
	}

	static String toLine( final List<Compartment> sirCols, final String t,
		final Map<String, Set<String>> colMapping,
		final Map<String, EnumMap<Compartment, Long>> homeSIR )
	{
		return DateTimeFormatter.ISO_LOCAL_DATE_TIME
				.format( ZonedDateTime.now() ) + sep + t
				+ sep
				+ String.join( sep, sirCols.stream()
						.map( c -> colMapping.entrySet().stream()
								.flatMap( e -> e.getValue().stream() )
								.map( reg -> homeSIR
										.computeIfAbsent( reg,
												k -> new EnumMap<>(
														Compartment.class ) )
										.computeIfAbsent( c, k -> 0L ) )
								.mapToLong( n -> n ).sum() )
						.map( Object::toString ).toArray( String[]::new ) )
				+ sep
				+ String.join( sep, sirCols.stream().flatMap( c -> colMapping
						.entrySet().stream()
						.map( e -> e.getValue().stream()
								.mapToLong( reg -> homeSIR.get( reg ).get( c ) )
								.sum() ) )
						.map( Object::toString ).toArray( String[]::new ) )
				+ eol;
	}

	static BigDecimal evaluateFraction( final String key,
		final Map<String, EnumMap<Compartment, Long>> values,
		final Compartment dividendCol )
	{
		final EnumMap<Compartment, Long> v = values.computeIfAbsent( key,
				k -> new EnumMap<>( Compartment.class ) );
		final Long dividend = v.get( dividendCol );
		if( dividend == null || dividend.longValue() == 0L )
			return BigDecimal.ZERO;
		final long divisor = v.values().stream().mapToLong( Math::abs ).sum();
//		if( sum == 0 )
//			System.err.println( dividend + "/" + sum + "?" + v.values() );
		final BigDecimal result = DecimalUtil.divide( dividend, divisor );
		return result;
	}

	static String toLog( final List<Compartment> sirCols,
		final Map<String, EnumMap<Compartment, Long>> homeSIR, final int n,
		final Compartment descendCol )
	{
		return String.join( ", ", homeSIR.keySet().stream()
				.sorted( ( r, l ) -> evaluateFraction( l, homeSIR, descendCol )
						.compareTo(
								evaluateFraction( r, homeSIR, descendCol ) ) )
				.limit( n )
				.map( reg -> reg + ":["
						+ String.join( ",", sirCols.stream().map( c -> homeSIR
								.computeIfAbsent( reg,
										k -> new EnumMap<>(
												Compartment.class ) )
								.computeIfAbsent( c, k -> 0L ).toString() )
								.toArray( String[]::new ) )
						+ "]" )
				.toArray( String[]::new ) );
	}
}
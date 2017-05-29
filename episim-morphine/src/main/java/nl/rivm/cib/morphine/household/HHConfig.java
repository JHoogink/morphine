package nl.rivm.cib.morphine.household;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.Period;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Converter;
import org.aeonbits.owner.loaders.Loader;

import io.coala.config.ConfigUtil;
import io.coala.config.GlobalConfig;
import io.coala.config.YamlUtil;
import io.coala.math.DecimalUtil;
import io.coala.math.Range;
import io.coala.math.WeightedValue;
import io.coala.random.ProbabilityDistribution;
import io.coala.random.ProbabilityDistribution.Parser;
import io.coala.time.Instant;
import io.coala.time.Scheduler;
import io.coala.time.Timing;
import io.coala.util.FileUtil;
import io.coala.util.InputStreamConverter;
import io.coala.util.Instantiator;
import io.reactivex.internal.functions.Functions;
import nl.rivm.cib.epidemes.cbs.json.CBSRegionType;
import nl.rivm.cib.epidemes.cbs.json.Cbs71486json;
import nl.rivm.cib.episim.model.vaccine.attitude.VaxOccasion;
import nl.rivm.cib.morphine.pienter.HesitancyProfileJson;

/**
 * {@link HHConfig}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
@Sources( { /*
			 * "${user.dir}/conf/" + HHConfig.MORPHINE_CONFIG_YAML_FILE,
			 * "${user.home}/" + HHConfig.MORPHINE_CONFIG_YAML_FILE,
			 */
//		"classpath:" + 
		"file:/C:/Users/Joram%20Hoogink/Documents/Morphine/Source/morphine-episim/episim-morphine/"
				+ HHConfig.MORPHINE_CONFIG_YAML_FILE } )
public interface HHConfig extends GlobalConfig
{

	/** configuration file name */
	String MORPHINE_CONFIG_YAML_FILE = "morphine.yaml";

	/** configuration key */
	String MORPHINE_PREFIX = "morphine" + ConfigUtil.CONFIG_KEY_SEP;
	/** configuration key */
	String REPLICATION_PREFIX = MORPHINE_PREFIX + "replication"
			+ ConfigUtil.CONFIG_KEY_SEP;
	/** configuration key */
	String STATISTICS_PREFIX = REPLICATION_PREFIX + "statistics"
			+ ConfigUtil.CONFIG_KEY_SEP;
	/** configuration key */
	String JDBC_PREFIX = STATISTICS_PREFIX + "jdbc" + ConfigUtil.CONFIG_KEY_SEP;
	/** configuration key */
	String POPULATION_PREFIX = MORPHINE_PREFIX + "population"
			+ ConfigUtil.CONFIG_KEY_SEP;
	/** configuration key */
	String HESITANCY_PREFIX = POPULATION_PREFIX + "hesitancy"
			+ ConfigUtil.CONFIG_KEY_SEP;
	/** configuration key */
	String EPIDEMIC_PREFIX = POPULATION_PREFIX + "epidemic"
			+ ConfigUtil.CONFIG_KEY_SEP;
	/** configuration key */
	String VACCINATION_PREFIX = POPULATION_PREFIX + "vaccination"
			+ ConfigUtil.CONFIG_KEY_SEP;

	String DATASOURCE_JNDI = "jdbc/hhDB";

	@Key( REPLICATION_PREFIX + "duration-period" )
	@DefaultValue( "P1Y" )
	@ConverterClass( PeriodConverter.class )
	Period duration();

	@Key( REPLICATION_PREFIX + "offset-date" )
	@DefaultValue( "2012-01-01" )
	@ConverterClass( LocalDateConverter.class )
	LocalDate offset();

	@Key( STATISTICS_PREFIX + "recurrence" )
	@DefaultValue( "0 0 0 14 * ? *" )
	String statisticsRecurrence();

	default Iterable<Instant> statisticsRecurrence( final Scheduler scheduler )
		throws ParseException
	{
		return Timing.of( statisticsRecurrence() ).offset( scheduler.offset() )
				.iterate();
	}

//	"jdbc:neo4j:bolt://192.168.99.100:7687/db/data" 
//	"jdbc:mysql://localhost/hhdb" 
//	"jdbc:hsqldb:mem:hhdb" 
	@Key( JDBC_PREFIX + "url" )
	@DefaultValue( "jdbc:hsqldb:file:target/hhdb" )
	String hsqlUrl();

	@Key( JDBC_PREFIX + "username" )
	@DefaultValue( "SA" )
	String hsqlUser();

	@Key( JDBC_PREFIX + "password" )
	@DefaultValue( "" )
	String hsqlPassword();

	/////////////////////////////////////////////////////////////////////////

	@Key( POPULATION_PREFIX + "size" )
	@DefaultValue( "" + 100000 )
	int populationSize();

	@Key( POPULATION_PREFIX + "cbs-region-type" )
	@DefaultValue( "COROP" )
	CBSRegionType cbsRegionLevel();

	@Key( POPULATION_PREFIX + "cbs-71486ned-data" )
	@DefaultValue( "conf/71486ned-TS-2010-2016.json" )
	@ConverterClass( InputStreamConverter.class )
	InputStream cbs71486Data();

	default Map<LocalDate, Collection<WeightedValue<Cbs71486json.Category>>>
		cbs71486( final Range<LocalDate> timeFilter )
	{
		final CBSRegionType cbsRegionLevel = this.cbsRegionLevel();
		return Cbs71486json.readAsync( this::cbs71486Data, timeFilter )
				.filter( wv -> wv.getValue().regionType() == cbsRegionLevel )
				.toMultimap( wv -> wv.getValue().offset(), Functions.identity(),
						TreeMap::new )
				.blockingGet();
	}

	@Key( VACCINATION_PREFIX + "recurrence" )
	@DefaultValue( "0 0 0 7 * ? *" )
	String vaccinationRecurrence();

	default Iterable<Instant> vaccinationRecurrence( final Scheduler scheduler )
		throws ParseException
	{
		return Timing.of( vaccinationRecurrence() )
				.offset( scheduler.now().toJava8( scheduler.offset() ) )
				.iterate();
	}

	/** @see VaxOccasion#utility() */
	@Key( VACCINATION_PREFIX + "utility-dist" )
	@DefaultValue( "const(0.5)" )
	String vaccinationUtilityDist();

	default ProbabilityDistribution<Number>
		vaccinationUtilityDist( final Parser distParser ) throws ParseException
	{
		return distParser.parse( vaccinationUtilityDist() );
	}

	/** @see VaxOccasion#proximity() */
	@Key( VACCINATION_PREFIX + "proximity-dist" )
	@DefaultValue( "const(0.5)" )
	String vaccinationProximityDist();

	default ProbabilityDistribution<Number> vaccinationProximityDist(
		final Parser distParser ) throws ParseException
	{
		return distParser.parse( vaccinationProximityDist() );
	}

	/** @see VaxOccasion#clarity() */
	@Key( VACCINATION_PREFIX + "clarity-dist" )
	@DefaultValue( "const(0.5)" )
	String vaccinationClarityDist();

	default ProbabilityDistribution<Number>
		vaccinationClarityDist( final Parser distParser ) throws ParseException
	{
		return distParser.parse( vaccinationClarityDist() );
	}

	/** @see VaxOccasion#affinity() */
	@Key( VACCINATION_PREFIX + "affinity-dist" )
	@DefaultValue( "const(0.5)" )
	String vaccinationAffinityDist();

	default ProbabilityDistribution<Number>
		vaccinationAffinityDist( final Parser distParser ) throws ParseException
	{
		return distParser.parse( vaccinationAffinityDist() );
	}

	@Key(HESITANCY_PREFIX+"oracle-factory")
	@DefaultValue("nl.rivm.cib.morphine.household.HHOracle$Factory$Simple")
	Class<? extends HHOracle.Factory> hesitancyOracleFactory();
	
	/** @see HesitancyProfileJson */
	@Key( HESITANCY_PREFIX + "profile-data" )
	@DefaultValue( "conf/hesitancy-univariate.json" )
	@ConverterClass( InputStreamConverter.class )
	InputStream hesitancyProfiles();

	default ProbabilityDistribution<HesitancyProfileJson>
		hesitancyProfiles( final ProbabilityDistribution.Factory distFactory )
	{
		return distFactory.createCategorical( HesitancyProfileJson
				.parse( this::hesitancyProfiles ).toList().blockingGet() );
	}

	/** @see HesitancyProfileJson */
	@Key( HESITANCY_PREFIX + "calculation-dist" )
	@DefaultValue( "const(0.5)" )
	String hesitancyCalculationDist();

	default ProbabilityDistribution<BigDecimal> hesitancyCalculationDist(
		final Parser distParser ) throws ParseException
	{
		return distParser.<Number>parse( hesitancyCalculationDist() )
				.map( DecimalUtil::valueOf );
	}

	/** @see HHAttitudeEvaluator */
	@Key( HESITANCY_PREFIX + "evaluator" )
	@DefaultValue( "nl.rivm.cib.morphine.household.HHAttitudeEvaluator$Average" )
	Class<? extends HHAttitudeEvaluator> attitudeEvaluatorType();

	default HHAttitudeEvaluator attitudeEvaluator()
	{
		return Instantiator.instantiate( attitudeEvaluatorType() );
	}

	/** @see HHAttitudePropagator */
	@Key( HESITANCY_PREFIX + "propagator" )
	@DefaultValue( "nl.rivm.cib.morphine.household.HHAttitudePropagator$Shifted" )
	Class<? extends HHAttitudePropagator> attitudePropagatorType();

	default HHAttitudePropagator attitudePropagator()
	{
		return Instantiator.instantiate( attitudePropagatorType() );
	}

//	@Key( "morphine.measles.contact-period" )
//	@DefaultValue( "10 h" )
//	Duration contactPeriod();
//
//	@DefaultValue( "uniform-discrete(-5;0)" )
//	String birthDist();
//
//	@DefaultValue( "2 day" )
//	Duration latentPeriodConst();
//
//	@DefaultValue( "5 day" )
//	Duration recoverPeriodConst();
//
//	@DefaultValue( "9999 day" )
//	Duration wanePeriodConst();
//
//	@DefaultValue( "3 day" )
//	Duration onsetPeriodConst();
//
//	@DefaultValue( "7 day" )
//	Duration symptomPeriodConst();

	/** {@link PeriodConverter} parses {@link Period}s */
	class PeriodConverter implements Converter<Period>
	{
		@Override
		public Period convert( final Method method, final String input )
		{
			return Period.parse( input );
		}
	}

	/** {@link LocalDateConverter} parses {@link LocalDate}s */
	class LocalDateConverter implements Converter<LocalDate>
	{
		@Override
		public LocalDate convert( final Method method, final String input )
		{
			return LocalDate.parse( input );
		}
	}

	/** {@link YamlLoader} is a {@link Loader} for YAML format configurations */
	class YamlLoader implements Loader
	{
		private static final long serialVersionUID = 1L;

		private static boolean registered = false;

		public synchronized static void register()
		{
			if( !registered )
			{
				ConfigFactory.registerLoader( new YamlLoader() );
				registered = true;
			}
		}

		@Override
		public boolean accept( final URI uri )
		{
			final String path = uri.toASCIIString();
			if( !path.toLowerCase().contains( "yaml" ) ) return false;
			try
			{
				uri.toURL();
				return true;
			} catch( final MalformedURLException ex )
			{
				return new File( path ).exists() || Thread.currentThread()
						.getContextClassLoader().getResource( path ) != null;
			}
		}

		@Override
		public void load( final Properties result, final URI uri )
			throws IOException
		{
			try( final InputStream is = FileUtil.toInputStream( uri ) )
			{
				result.putAll( YamlUtil.flattenYaml( is ) );
			}
		}

		@Override
		public String defaultSpecFor( final String uriPrefix )
		{
			return uriPrefix + ".yaml";
		}

	}

}
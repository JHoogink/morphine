package nl.rivm.cib.morphine.household;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.LocalDate;
import java.time.Period;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigFactory;
import org.aeonbits.owner.Converter;
import org.aeonbits.owner.loaders.Loader;

import io.coala.config.GlobalConfig;
import io.coala.config.YamlUtil;
import io.coala.math.Range;
import io.coala.math.WeightedValue;
import io.coala.util.FileUtil;
import io.coala.util.InputStreamConverter;
import io.reactivex.internal.functions.Functions;
import nl.rivm.cib.epidemes.cbs.json.CBSRegionType;
import nl.rivm.cib.epidemes.cbs.json.Cbs71486json;
import nl.rivm.cib.morphine.pienter.HesitancyProfile;

/**
 * {@link HHConfig}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
@Sources( { "${user.dir}/conf/" + HHConfig.MORPHINE_CONFIG_YAML_FILE,
		"${user.home}/" + HHConfig.MORPHINE_CONFIG_YAML_FILE,
		"classpath:" + HHConfig.MORPHINE_CONFIG_YAML_FILE } )
public interface HHConfig extends GlobalConfig
{

	String MORPHINE_CONFIG_YAML_FILE = "morphine.yaml";

	char KEY_SEP = '.';

	String MORPHINE_PREFIX = "morphine" + KEY_SEP;

	String REPLICATION_PREFIX = MORPHINE_PREFIX + "replication" + KEY_SEP;

	String POPULATION_PREFIX = MORPHINE_PREFIX + "population" + KEY_SEP;

	@Key( REPLICATION_PREFIX + "duration-period" )
	@DefaultValue( "P1Y" )
	@ConverterClass( PeriodConverter.class )
	Period duration();

	@Key( REPLICATION_PREFIX + "offset-date" )
	@DefaultValue( "2012-01-01" )
	@ConverterClass( LocalDateConverter.class )
	LocalDate offset();

	@Key( REPLICATION_PREFIX + "statistics-recurrence" )
	@DefaultValue( "0 0 0 14 * ? *" )
	String statisticsRecurrence();

	@Key( POPULATION_PREFIX + "size" )
	@DefaultValue( "" + 100000 )
	int populationSize();

	@Key( POPULATION_PREFIX + "hesitancy-profile-data" )
	@DefaultValue( "conf/hesitancy-univariate.json" )
	@ConverterClass( InputStreamConverter.class )
	InputStream hesitancyProfileData();

	default List<WeightedValue<HesitancyProfile>> hesitancyProfiles()
	{
		return HesitancyProfile.parse( this::hesitancyProfileData ).toList()
				.blockingGet();
	}

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
			try
			{
				uri.toURL();
				return true;
			} catch( final MalformedURLException ex )
			{
				final String path = uri.toASCIIString();
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
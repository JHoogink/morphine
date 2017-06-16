package nl.rivm.cib.morphine.household;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.measure.Quantity;
import javax.measure.quantity.Frequency;
import javax.measure.quantity.Time;

import org.aeonbits.owner.Config.Sources;
import org.aeonbits.owner.ConfigCache;
import org.aeonbits.owner.ConfigFactory;

import io.coala.bind.LocalBinder;
import io.coala.config.ConfigUtil;
import io.coala.config.GlobalConfig;
import io.coala.config.YamlUtil;
import io.coala.json.JsonUtil;
import io.coala.math.DecimalUtil;
import io.coala.math.QuantityConfigConverter;
import io.coala.persist.JPAConfig;
import io.coala.random.ConditionalDistribution;
import io.coala.random.ProbabilityDistribution;
import io.coala.random.ProbabilityDistribution.Factory;
import io.coala.random.ProbabilityDistribution.Parser;
import io.coala.random.PseudoRandom;
import io.coala.random.QuantityDistribution;
import io.coala.time.Instant;
import io.coala.time.Scheduler;
import io.coala.time.TimeUnits;
import io.coala.time.Timing;
import io.coala.util.FileUtil;
import io.coala.util.InputStreamConverter;
import io.coala.util.MapBuilder;
import io.reactivex.Observable;
import nl.rivm.cib.epidemes.cbs.json.CBSHousehold;
import nl.rivm.cib.episim.model.vaccine.attitude.VaxOccasion;
import nl.rivm.cib.morphine.json.HesitancyProfileJson;
import nl.rivm.cib.morphine.json.HesitancyProfileJson.HesitancyDimension;
import nl.rivm.cib.morphine.json.RelationFrequencyJson;
import nl.rivm.cib.util.LocalDateConverter;
import nl.rivm.cib.util.PeriodConverter;

/**
 * {@link HHConfig}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
@Sources( { "file:" + HHConfig.CONFIG_BASE_DIR + HHConfig.CONFIG_YAML_FILE,
		"file:${user.home}/" + HHConfig.CONFIG_YAML_FILE, // does this work?
		"classpath:" + HHConfig.CONFIG_YAML_FILE } )
public interface HHConfig extends GlobalConfig
{

	/** configuration file name */
	String CONFIG_BASE_DIR = "conf/";

	String CONFIG_YAML_FILE = "morphine.yaml";

	String DATASOURCE_JNDI = "jdbc/hhDB";

	String CONF_ARG = "conf";

	/** configuration key separator */
	String KEY_SEP = ConfigUtil.CONFIG_KEY_SEP;

	/** configuration key */
	String MORPHINE_PREFIX = "morphine" + KEY_SEP;

	/** configuration key */
	String REPLICATION_PREFIX = MORPHINE_PREFIX + "replication" + KEY_SEP;

	/** configuration key */
	String STATISTICS_PREFIX = REPLICATION_PREFIX + "statistics" + KEY_SEP;

	/** configuration key */
	String POPULATION_PREFIX = MORPHINE_PREFIX + "population" + KEY_SEP;

	/** configuration key */
	String HESITANCY_PREFIX = POPULATION_PREFIX + "hesitancy" + KEY_SEP;

	/** configuration key */
	String EPIDEMIC_PREFIX = POPULATION_PREFIX + "epidemic" + KEY_SEP;

	/** configuration key */
	String VACCINATION_PREFIX = POPULATION_PREFIX + "vaccination" + KEY_SEP;

	/**
	 * provide a universal approach for loading the {@link HHConfig}
	 * 
	 * @param args the command-line arguments, any "..=.." will be imported as
	 *            override to the , if any
	 * @return a (cached) {@link HHConfig} instance
	 * @throws IOException
	 */
	static HHConfig getOrCreate( final String... args ) throws IOException
	{
		// convert command-line arguments to map
		final Map<String, String> argMap = Arrays.stream( args )
				.filter( arg -> arg.contains( "=" ) )
				.map( arg -> arg.split( "=" ) ).filter( arr -> arr.length == 2 )
				.collect( Collectors.toMap( arr -> arr[0], arr -> arr[1] ) );
		argMap.computeIfAbsent( CONF_ARG,
				key -> CONFIG_BASE_DIR + CONFIG_YAML_FILE );

		// merge arguments into configuration imported from YAML file
		return ConfigCache.getOrCreate( HHConfig.class, argMap,
				YamlUtil.flattenYaml(
						FileUtil.toInputStream( argMap.get( CONF_ARG ) ) ) );
	}

	// match unit name from persistence.xml
	@DefaultValue( "" + false )
	@Key( STATISTICS_PREFIX + "db-enabled" )
	boolean dbEnabled();

	// match unit name from persistence.xml
	@DefaultValue( "hh_pu" )
	@Key( JPAConfig.JPA_UNIT_NAMES_KEY )
	@Separator( JPAConfig.NAME_DELIMITER )
	String[] jpaPersistenceUnitNames();

	//	"jdbc:neo4j:bolt://192.168.99.100:7687/db/data" 
	//	"jdbc:mysql://localhost/hhdb" 
	//	"jdbc:hsqldb:mem:hhdb" 
	// jdbc:hsqldb:file:target/hh_testdb
	@DefaultValue( "jdbc:h2:~/morphdat/h2_hhdb" )
	@Key( JPAConfig.JPA_JDBC_URL_KEY )
	URI jdbcUrl();

	@DefaultValue( "sa" )
	@Key( JPAConfig.JPA_JDBC_PASSWORD_KEY )
	String dbcUser();

	@DefaultValue( "sa" )
	@Key( JPAConfig.JPA_JDBC_USER_KEY )
	String jdbcPassword();

	default <T extends JPAConfig> T toJPAConfig( final Class<T> jpaConfigType,
		final Map<?, ?>... imports )
	{

		// bind a local HSQL data source for exporting statistics
//		JndiUtil.bindLocally( HHConfig.DATASOURCE_JNDI, '/', () ->
//		{
//			final JDBCDataSource ds = new JDBCDataSource();
//			ds.setUrl( hhConfig.hsqlUrl() );
//			ds.setUser( hhConfig.hsqlUser() );
//			ds.setPassword( hhConfig.hsqlPassword() );
//			return ds;
//		} );

		return ConfigFactory.create( jpaConfigType,
				ConfigUtil.join( export( Pattern.compile(
						"^(" + Pattern.quote( "javax.persistence" ) + ").*" ) ),
						imports ) );
	}

	/////////////////////////////////////////////////////////////////////////

	@Key( REPLICATION_PREFIX + "duration-period" )
	@DefaultValue( "P1Y" )
	@ConverterClass( PeriodConverter.class )
	Period duration();

	@Key( REPLICATION_PREFIX + "offset-date" )
	@DefaultValue( "2012-01-01" )
	@ConverterClass( LocalDateConverter.class )
	LocalDate offset();

	/**
	 * <a
	 * href=http://www.quartz-scheduler.org/documentation/quartz-2.x/tutorials/crontrigger.html>Cron
	 * trigger pattern</a> for timing population statistics export, e.g.
	 * <ol type=a>
	 * <li>{@code "0 0 0 ? * MON *"} : <em>midnight on every Monday</em></li>
	 * <li>{@code "0 0 0 1,15 * ? *"} : <em>midnight on every 1st and 15th day
	 * of the month</em></li>
	 * </ol>
	 */
	@Key( STATISTICS_PREFIX + "recurrence" )
	@DefaultValue( "0 0 0 1 * ? *" )
	String statisticsRecurrence();

	default Iterable<Instant> statisticsRecurrence( final Scheduler scheduler )
		throws ParseException
	{
		return Timing.of( statisticsRecurrence() ).offset( scheduler.offset() )
				.iterate();
	}

	@Key( POPULATION_PREFIX + "size" )
	@DefaultValue( "" + 100000 )
	long populationSize();

	@Key( POPULATION_PREFIX + "hh-type-dist" )
	@DefaultValue( "const(SOLO_1KID)" )
	String householdTypeDist();

	default ProbabilityDistribution<CBSHousehold>
		householdTypeDist( final Parser distParser ) throws ParseException
	{
		return distParser.parse( householdTypeDist(), CBSHousehold.class );
	}

	@Key( POPULATION_PREFIX + "hh-ref-age-dist" )
	@DefaultValue( "uniform(20;40)" )
	String householdReferentAgeDist();

	default QuantityDistribution<Time> householdReferentAgeDist(
		final Parser distParser ) throws ParseException
	{
		return distParser.<Number>parse( householdReferentAgeDist() )
				.toQuantities( TimeUnits.ANNUM );
	}

	/** @see TimeUnits#ANNUM_LABEL */
	@Key( POPULATION_PREFIX + "hh-leavehome-age" )
	@DefaultValue( "20 yr" )
	@ConverterClass( QuantityConfigConverter.class )
	Quantity<Time> householdLeaveHomeAge();

	/** @see TimeUnits#ANNUAL_LABEL */
	@Key( POPULATION_PREFIX + "hh-migrate-rate" )
	@DefaultValue( ".02 annual" )
	@ConverterClass( QuantityConfigConverter.class )
	Quantity<Frequency> householdMigrationRate();

	default QuantityDistribution<Time> householdReplacementDist(
		final Factory distFactory, final long hhTotal )
	{
		final Quantity<?> rate = householdMigrationRate().multiply( hhTotal )
				.to( TimeUnits.DAILY );
		return distFactory
				.createExponential( // mean <- 1/rate
						DecimalUtil.divide( BigDecimal.ONE,
								DecimalUtil.valueOf( rate.getValue() ) ) )
				.toQuantities( TimeUnits.DAYS );
	}

	static BigDecimal inverse( final Number value )
	{
		return DecimalUtil.divide( BigDecimal.ONE,
				DecimalUtil.valueOf( value ) );
	}

//	@Key( POPULATION_PREFIX + "cbs-region-type" )
//	@DefaultValue( "COROP" )
//	CBSRegionType cbsRegionLevel();
//
//	@Key( POPULATION_PREFIX + "cbs-71486ned-data" )
//	@DefaultValue( CONFIG_BASE_DIR + "71486ned-TS-2010-2016.json" )
//	@ConverterClass( InputStreamConverter.class )
//	InputStream cbs71486Data();
//
//	default ConditionalDistribution<Cbs71486json.Category, LocalDate> cbs71486(
//		final Range<LocalDate> timeFilter,
//		final ProbabilityDistribution.Factory distFactory )
//	{
//		final CBSRegionType cbsRegionLevel = cbsRegionLevel();
//		final Map<LocalDate, Collection<WeightedValue<Cbs71486json.Category>>> map = Cbs71486json
//				.readAsync( this::cbs71486Data, timeFilter )
//				.filter( wv -> wv.getValue().regionType() == cbsRegionLevel )
//				.toMultimap( wv -> wv.getValue().offset(), wv -> wv,
//						TreeMap::new )
//				.blockingGet();
//		return ConditionalDistribution.of( distFactory::createCategorical,
//				map );
//	}

	@Key( VACCINATION_PREFIX + "occasion-recurrence" )
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
	@Key( VACCINATION_PREFIX + "occasion-utility-dist" )
	@DefaultValue( "const(0.5)" )
	String vaccinationUtilityDist();

	default ProbabilityDistribution<Number>
		vaccinationUtilityDist( final Parser distParser ) throws ParseException
	{
		return distParser.parse( vaccinationUtilityDist() );
	}

	/** @see VaxOccasion#proximity() */
	@Key( VACCINATION_PREFIX + "occasion-proximity-dist" )
	@DefaultValue( "const(0.5)" )
	String vaccinationProximityDist();

	default ProbabilityDistribution<Number> vaccinationProximityDist(
		final Parser distParser ) throws ParseException
	{
		return distParser.parse( vaccinationProximityDist() );
	}

	/** @see VaxOccasion#clarity() */
	@Key( VACCINATION_PREFIX + "occasion-clarity-dist" )
	@DefaultValue( "const(0.5)" )
	String vaccinationClarityDist();

	default ProbabilityDistribution<Number>
		vaccinationClarityDist( final Parser distParser ) throws ParseException
	{
		return distParser.parse( vaccinationClarityDist() );
	}

	/** @see VaxOccasion#affinity() */
	@Key( VACCINATION_PREFIX + "occasion-affinity-dist" )
	@DefaultValue( "const(0.5)" )
	String vaccinationAffinityDist();

	default ProbabilityDistribution<Number>
		vaccinationAffinityDist( final Parser distParser ) throws ParseException
	{
		return distParser.parse( vaccinationAffinityDist() );
	}

	@Key( HESITANCY_PREFIX + "attractor-factory" )
	@DefaultValue( "nl.rivm.cib.morphine.household.HHAttractor$Factory$SimpleBinding" )
	Class<? extends HHAttractor.Factory> hesitancyAttractorFactory();

	default Observable<HHAttractor>
		hesitancyAttractors( final LocalBinder binder )
	{
		try
		{
			return hesitancyAttractorFactory().newInstance().createAll(
					toJSON( HESITANCY_PREFIX + "attractors" ), binder );
		} catch( final Exception e )
		{
			return Observable.error( e );
		}
	}

	/** @see RelationFrequencyJson */
	@Key( HESITANCY_PREFIX + "profiles" )
	@DefaultValue( CONFIG_BASE_DIR + "relation-frequency.json" )
	@ConverterClass( InputStreamConverter.class )
	InputStream hesitancyRelationFrequencies();

//	default ConditionalDistribution<RelationFrequencyJson>
//		hesitancyRelationFrequencyDist(
//			final ProbabilityDistribution.Factory distFactory )
//	{
//		return ConditionalDistribution.of( distFactory::createCategorical,
//				JsonUtil.readArrayAsync( this::hesitancyRelationFrequencies,
//						RelationFrequencyJson.class )
//						.toMultimap( json -> json.toCategory(),
//								json -> json.intervalDist( distFactory ),
//								TreeMap::new )
//						.blockingGet() );
//	}

	/** @see HesitancyProfileJson */
	@Key( HESITANCY_PREFIX + "profiles" )
	@DefaultValue( CONFIG_BASE_DIR + "hesitancy-univariate.json" )
	@ConverterClass( InputStreamConverter.class )
	InputStream hesitancyProfiles();

	default ProbabilityDistribution<HesitancyProfileJson>
		hesitancyProfileDist( final Factory distFactory )
	{
		return distFactory.createCategorical( HesitancyProfileJson
				.parse( this::hesitancyProfiles ).toList().blockingGet() );
	}

	default <T> ConditionalDistribution<HesitancyProfileJson, T>
		hesitancyProfilesGrouped( final Factory distFactory,
			final Function<HesitancyProfileJson, T> keyMapper )
	{
		return ConditionalDistribution.of( distFactory::createCategorical,
				HesitancyProfileJson.parse( this::hesitancyProfiles )
						.toMultimap( wv -> keyMapper.apply( wv.getValue() ),
								wv -> wv )
						.blockingGet() );
	}

	@Key( HESITANCY_PREFIX + "profile-sample" )
	@DefaultValue( CONFIG_BASE_DIR + "hesitancy-initial.json" )
	@ConverterClass( InputStreamConverter.class )
	InputStream hesitancyProfileSample();

	default
		ConditionalDistribution<Map<HHAttribute, BigDecimal>, HesitancyProfileJson>
		hesitancyProfileSample( final PseudoRandom rng )
	{
		final BigDecimal[][] sample = JsonUtil
				.valueOf( hesitancyProfileSample(), BigDecimal[][].class );
		final Map<HesitancyProfileJson, ProbabilityDistribution<Map<HHAttribute, BigDecimal>>> distCache = new HashMap<>();
		return ConditionalDistribution
				.of( hes -> distCache.computeIfAbsent( hes, key -> () ->
				{
					final int row = rng.nextInt( sample.length );
					final int confCol = hes.indices
							.get( HesitancyDimension.confidence ) - 1;
					final int compCol = hes.indices
							.get( HesitancyDimension.complacency ) - 1;
					return MapBuilder.<HHAttribute, BigDecimal>unordered()
							.put( HHAttribute.CONFIDENCE,
									DecimalUtil
											.valueOf( sample[row][confCol] ) )
							.put( HHAttribute.COMPLACENCY,
									DecimalUtil
											.valueOf( sample[row][compCol] ) )
							.build();
				} ) );
	}

	/**
	 * TODO from profile-data
	 * 
	 * @see HesitancyProfileJson
	 */
	@Key( HESITANCY_PREFIX + "calculation-dist" )
	@DefaultValue( "const(0.5)" )
	String hesitancyCalculationDist();

	default ProbabilityDistribution<BigDecimal> hesitancyCalculationDist(
		final Parser distParser ) throws ParseException
	{
		return distParser.<Number>parse( hesitancyCalculationDist() )
				.map( DecimalUtil::valueOf );
	}

	@Key( HESITANCY_PREFIX + "social-network-degree" )
	@DefaultValue( "10" )
	int hesitancySocialNetworkDegree();

	@Key( HESITANCY_PREFIX + "social-network-beta" )
	@DefaultValue( "0.5" ) // 0 = lattice, 1 = random network
	double hesitancySocialNetworkBeta();

	@Key( HESITANCY_PREFIX + "social-assortativity" )
	@DefaultValue( "0.75" )
	double hesitancySocialAssortativity();

	@Key( HESITANCY_PREFIX + "school-assortativity-dist" )
	@DefaultValue( "bernoulli(0.75)" )
	String hesitancySchoolAssortativity();

	default ProbabilityDistribution<Boolean> hesitancySchoolAssortativity(
		final Parser distParser ) throws ParseException
	{
		return distParser.parse( hesitancySchoolAssortativity() );
	}

	/** @see HHAttitudeEvaluator */
	@Key( HESITANCY_PREFIX + "evaluator" )
	@DefaultValue( "nl.rivm.cib.morphine.household.HHAttitudeEvaluator$Average" )
	Class<? extends HHAttitudeEvaluator> attitudeEvaluatorType();

	default HHAttitudeEvaluator attitudeEvaluator()
		throws InstantiationException, IllegalAccessException
	{
		return attitudeEvaluatorType().newInstance();
	}

	/** @see HHAttitudePropagator */
	@Key( HESITANCY_PREFIX + "propagator" )
	@DefaultValue( "nl.rivm.cib.morphine.household.HHAttitudePropagator$Shifted" )
	Class<? extends HHAttitudePropagator> attitudePropagatorType();

	default HHAttitudePropagator attitudePropagator()
		throws InstantiationException, IllegalAccessException
	{
		return attitudePropagatorType().newInstance();
	}

//	@Key( "morphine.measles.contact-period" )
//	@DefaultValue( "10 h" )
//	Duration contactPeriod();
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

}
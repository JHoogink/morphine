package nl.rivm.cib.morphine.household;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.Period;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.measure.Quantity;
import javax.measure.quantity.Frequency;
import javax.measure.quantity.Time;

import org.aeonbits.owner.ConfigCache;

import io.coala.bind.LocalBinder;
import io.coala.config.ConfigUtil;
import io.coala.config.GlobalConfig;
import io.coala.config.YamlUtil;
import io.coala.json.JsonUtil;
import io.coala.math.DecimalUtil;
import io.coala.math.QuantityConfigConverter;
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
import nl.rivm.cib.morphine.household.HHModel.GenderAge;
import nl.rivm.cib.morphine.profile.HesitancyProfileJson;
import nl.rivm.cib.morphine.profile.HesitancyProfileJson.HesitancyDimension;
import nl.rivm.cib.util.LocalDateConverter;
import nl.rivm.cib.util.PeriodConverter;

/**
 * {@link HHConfig}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
//@Sources( { "${user.dir}/conf/" + HHConfig.MORPHINE_CONFIG_YAML_FILE,
//		"${user.home}/" + HHConfig.MORPHINE_CONFIG_YAML_FILE,
//		"classpath:" + HHConfig.MORPHINE_CONFIG_YAML_FILE } )
public interface HHConfig extends GlobalConfig
{

	/** configuration file name */
	String MORPHINE_CONFIG_YAML_FILE = "morphine.yaml";

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
	String JDBC_PREFIX = STATISTICS_PREFIX + "jdbc" + KEY_SEP;

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
				key -> "conf/" + MORPHINE_CONFIG_YAML_FILE );

		// merge arguments into configuration imported from YAML file
		return ConfigCache.getOrCreate( HHConfig.class, argMap,
				YamlUtil.flattenYaml(
						FileUtil.toInputStream( argMap.get( CONF_ARG ) ) ) );
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
	@Key( POPULATION_PREFIX + "hh-replace-ref-age" )
	@DefaultValue( "40 yr" )
	@ConverterClass( QuantityConfigConverter.class )
	Quantity<Time> householdReplacementAge();

	/** @see TimeUnits#ANNUAL_LABEL */
	@Key( POPULATION_PREFIX + "hh-replace-rate" )
	@DefaultValue( ".02 annual" )
	@ConverterClass( QuantityConfigConverter.class )
	Quantity<Frequency> householdReplacementRate();

	default QuantityDistribution<Time> householdReplacementDist(
		final Factory distFactory, final long hhTotal )
	{
		return distFactory
				.createExponential( householdReplacementRate()
						.multiply( hhTotal ).to( TimeUnits.DAILY ).getValue() )
				.toQuantities( TimeUnits.DAYS );
	}

//	@Key( POPULATION_PREFIX + "cbs-region-type" )
//	@DefaultValue( "COROP" )
//	CBSRegionType cbsRegionLevel();
//
//	@Key( POPULATION_PREFIX + "cbs-71486ned-data" )
//	@DefaultValue( "conf/71486ned-TS-2010-2016.json" )
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

	@Key( HESITANCY_PREFIX + "oracle-factory" )
	@DefaultValue( "nl.rivm.cib.morphine.household.HHOracle$Factory$SimpleBinding" )
	Class<? extends HHOracle.Factory> hesitancyOracleFactory();

	default Observable<HHOracle> hesitancyOracles( final LocalBinder binder )
	{
		try
		{
			return hesitancyOracleFactory().newInstance().createAll(
					toJSON( HESITANCY_PREFIX + "oracles" ), binder );
		} catch( final Exception e )
		{
			return Observable.error( e );
		}
	}

	/** @see HesitancyProfileJson */
	@Key( HESITANCY_PREFIX + "profiles" )
	@DefaultValue( "conf/hesitancy-univariate.json" )
	@ConverterClass( InputStreamConverter.class )
	InputStream hesitancyProfiles();

	default ProbabilityDistribution<HesitancyProfileJson> hesitancyProfileDist(
		final ProbabilityDistribution.Factory distFactory )
	{
		return distFactory.createCategorical( HesitancyProfileJson
				.parse( this::hesitancyProfiles ).toList().blockingGet() );
	}

	default <T> ConditionalDistribution<HesitancyProfileJson, T>
		hesitancyProfilesGrouped(
			final ProbabilityDistribution.Factory distFactory,
			final Function<HesitancyProfileJson, T> keyMapper )
	{
		return ConditionalDistribution.of( distFactory::createCategorical,
				HesitancyProfileJson.parse( this::hesitancyProfiles )
						.toMultimap( wv -> keyMapper.apply( wv.getValue() ),
								wv -> wv )
						.blockingGet() );
	}

	@Key( HESITANCY_PREFIX + "profile-sample" )
	@DefaultValue( "conf/hesitancy-initial.json" )
	@ConverterClass( InputStreamConverter.class )
	InputStream hesitancyProfileSample();

	default
		ConditionalDistribution<Map<HHAttribute, BigDecimal>, HesitancyProfileJson>
		hesitancyProfileSample( final PseudoRandom rng )
	{
		final float[][] sample = JsonUtil.valueOf( hesitancyProfileSample(),
				float[][].class );
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

//	@Key( HESITANCY_PREFIX + "mean-interval-days" )
//	@DefaultValue( "2" )
//	BigDecimal peerPressureMeanWeeklyRate();
//
//	default ConditionalDistribution<Quantity<Time>, GenderAge>
//		peerPressureInterval( final Factory distFactory )
//	{
//		final QuantityDistribution<Time> intervalDist = distFactory
//				.createExponential( peerPressureMeanWeeklyRate() )
//				.toQuantities( TimeUnits.WEEK );
//		return ConditionalDistribution.of( any -> intervalDist );
//	}

//	@DefaultValue( "conf/pc6_buurt.json" )
//	@ConverterClass( InputStreamConverter.class )
//	InputStream cbsNeighborhoodsData();
//
//	@DefaultValue( "GM0363" )
//	Region.ID fallbackRegionRef();
//
//	default ConditionalDistribution<CbsNeighborhood, Region.ID>
//		neighborhoodDist( final Factory distFactory,
//			//final Map<String, Map<CBSRegionType, String>> regionTypes,
//			final Function<Region.ID, Region.ID> fallback )
//	{
//		final CBSRegionType cbsRegionLevel = cbsRegionLevel();
////		final io.reactivex.functions.Function<? super CbsNeighborhood, Region.ID> regional;
////		if( regionTypes != null && cbsRegionLevel != CBSRegionType.MUNICIPAL
////				&& cbsRegionLevel != CBSRegionType.WARD
////				&& cbsRegionLevel != CBSRegionType.BOROUGH )
////			regional = bu -> Region.ID
////					.of( regionTypes
////							.computeIfAbsent( bu.municipalRef().unwrap(),
////									key -> new EnumMap<>(
////											CBSRegionType.class ) )
////							.computeIfAbsent( cbsRegionLevel,
////									key -> "unknown" ) );
////		else // municipal / ward / borough already in neighborhood data
////			regional = bu -> bu.regionRef( cbsRegionLevel );
//
//		final Map<Region.ID, ProbabilityDistribution<CbsNeighborhood>> async = CbsNeighborhood
//				.readAsync( this::cbsNeighborhoodsData )
//				.groupBy( bu -> bu.regionRef( cbsRegionLevel ) )
//				.toMap( GroupedObservable::getKey,
//						group -> distFactory.createCategorical( group
//								.map( CbsNeighborhood::toWeightedValue )
//								.toList( HashSet::new ).blockingGet() ) )
//				.blockingGet();
//
//		return ConditionalDistribution.of( id -> async.computeIfAbsent( id,
//				key -> async.get( fallback.apply( key ) ) ) );
//	}

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
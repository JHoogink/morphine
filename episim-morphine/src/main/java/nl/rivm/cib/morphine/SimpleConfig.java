package nl.rivm.cib.morphine;

import org.aeonbits.owner.Config.Sources;

import io.coala.config.GlobalConfig;
import io.coala.time.Duration;

/**
 * {@link SimpleConfig}
 * 
 * @version $Id$
 * @author Rick van Krevelen
 */
@Sources({ "classpath:" + SimpleConfig.MORPHINE_CONFIG_FILE })
public interface SimpleConfig extends GlobalConfig {

	String MORPHINE_CONFIG_FILE = "morphine.properties";

	@Key("morphine.measles.contact-period")
	@DefaultValue("10 h")
	Duration contactPeriod();

	@DefaultValue("uniform-discrete(-5;0)")
	String birthDist();

	@DefaultValue("2 day")
	Duration latentPeriodConst();

	@DefaultValue("5 day")
	Duration recoverPeriodConst();

	@DefaultValue("9999 day")
	Duration wanePeriodConst();

	@DefaultValue("3 day")
	Duration onsetPeriodConst();

	@DefaultValue("7 day")
	Duration symptomPeriodConst();

	@DefaultValue("0 0 0 14 * ? *")
	String statisticsRule();

	@DefaultValue("2017-01-01T00:00:00Z")
	String offsetDate();
}
@ECHO OFF
REM $Id$
CLS
REM change to directory of this batch script: (d)rive and (p)ath of 0th arg
CD /d %~dp0

IF "%~1" == "" (
  SET /A COUNT=1
) ELSE (
  SET /A "COUNT=%1"
)
FOR /L %%P IN (1000,100,1000) DO (
  FOR /L %%A IN (1,1,%COUNT%) DO (
    ECHO.
    ECHO Starting MORPHINE setup__n_%%P iteration %%A of %COUNT% in location: %~dp0
    ECHO   - press CTRL+C to interrupt
    ECHO.
    CALL "java" -Dlog4j.configurationFile=./log4j2.yaml ^
      -jar ./morphine-full-1.0.jar ^
      config.base=./ ^
      morphine.replication.setup-name=setup_n_%%P ^
      morphine.population.size=%%P ^
      javax.persistence.jdbc.url=jdbc:h2:./morphine;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false
  )
)
IF NOT ["%ERRORLEVEL%"]==["0"] PAUSE
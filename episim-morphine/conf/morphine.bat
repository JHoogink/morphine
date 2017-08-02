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
FOR /L %%A IN (1,1,%COUNT%) DO (
  ECHO.
  ECHO Starting MORPHINE setup__%%A of %COUNT% in location: %~dp0
  ECHO   - press CTRL+C to interrupt
  ECHO.
  CALL "java" -Dlog4j.configurationFile=./log4j2.yaml ^
    -jar ./morphine-full-1.0.jar ^
    config.base=./ ^
    morphine.replication.setup-name=setup__%%A ^
    javax.persistence.jdbc.url=jdbc:h2:~/morphdat/h2_sinkdb;AUTO_SERVER=TRUE
)
IF NOT ["%ERRORLEVEL%"]==["0"] PAUSE
@ECHO OFF
REM $Id$
CLS
CD /d %~dp0

ECHO.
ECHO Starting H2 Console
ECHO   - webserver properties stored in ~/.h2.server.properties
ECHO   - see also http://www.h2database.com/html/tutorial.html#console_settings
ECHO.
CALL "java" -cp ./morphine-full-1.0.jar org.h2.tools.Console

IF NOT ["%ERRORLEVEL%"]==["0"] PAUSE
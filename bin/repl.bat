@ECHO OFF

IF DEFINED JAVA_HOME GOTO findJavaFromJavaHome

SET JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
IF "%ERRORLEVEL%" == "0" GOTO cp_file_check

ECHO.
ECHO ERROR: JAVA_HOME is not SET and no 'java' command could be found in your PATH.
ECHO.
ECHO Please SET the JAVA_HOME variable in your environment to match the
ECHO location of your Java installation.
GOTO done

:findJavaFromJavaHome
SET JAVA_HOME=%JAVA_HOME:"=%
SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
IF EXIST "%JAVA_EXE%" GOTO cp_file_check

ECHO.
ECHO ERROR: JAVA_HOME is SET to an invalid directory: %JAVA_HOME%
ECHO.
ECHO Please SET the JAVA_HOME variable in your environment to match the
ECHO location of your Java installation.
GOTO done


:cp_file_check
SET CLASSPATH_FILE=build\cp\classpath_bat
IF EXIST %CLASSPATH_FILE% GOTO read_cp_file

ECHO ERROR: file %CLASSPATH_FILE% not found
ECHO Please first do: 'gradle cp' or 'gradlew cp'
GOTO done


:read_cp_file

FOR /F %%P IN (%CLASSPATH_FILE%) DO (
	SET CLASSPATH=%%P
	GOTO end
)
:end

SET CMD_LINE_ARGS=%*

ECHO starting repl ...   (CTRL-D to exit)
ECHO.
"%JAVA_EXE%" -classpath "%CLASSPATH%" jline.ConsoleRunner "clojure.main" %CMD_LINE_ARGS%

:done

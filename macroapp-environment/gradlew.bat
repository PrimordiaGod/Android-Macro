@echo off
rem Set local scope for variables
setlocal

rem Find Java executable
set JAVA_HOME=%JAVA_HOME%
if not defined JAVA_HOME (
    set JAVA_HOME=%ProgramFiles%\Java\jdk1.8.0_202
)
set JAVA_EXE="%JAVA_HOME%\bin\java.exe"
if not exist %JAVA_EXE% (
    set JAVA_EXE=java
)

rem Set Gradle options
set DIR=%~dp0
set GRADLE_WRAPPER_JAR=%DIR%gradle\wrapper\gradle-wrapper.jar
set DEFAULT_JVM_OPTS=
set GRADLE_OPTS=

rem Execute Gradle wrapper
%JAVA_EXE% %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -jar "%GRADLE_WRAPPER_JAR%" %*
endlocal

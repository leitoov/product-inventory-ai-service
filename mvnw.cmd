@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script
@REM ----------------------------------------------------------------------------
@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET DP0=%~dp0
@SET MAVEN_PROJECTBASEDIR=%DP0%
@SET MVNW_REPOURL=https://repo.maven.apache.org/maven2

@SET WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

@SET DOWNLOAD_URL="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"

@SET WRAPPER_JAR="%MAVEN_PROJECTBASEDIR%\.mvn\wrapper\maven-wrapper.jar"

@IF EXIST %WRAPPER_JAR% (
    @GOTO run
)

@echo Downloading Maven Wrapper...
@powershell -Command "Invoke-WebRequest -Uri %DOWNLOAD_URL% -OutFile %WRAPPER_JAR% -UseBasicParsing"

:run
@SET MAVEN_OPTS=%MAVEN_OPTS%
@"%JAVA_HOME%\bin\java.exe" -classpath %WRAPPER_JAR% "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" %WRAPPER_LAUNCHER% %*

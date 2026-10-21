@echo off
set "MAVEN_CMD=C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2024.3.1\plugins\maven\lib\maven3\bin\mvn.cmd"
if exist "%MAVEN_CMD%" (
    "%MAVEN_CMD%" %*
) else (
    mvn %*
)

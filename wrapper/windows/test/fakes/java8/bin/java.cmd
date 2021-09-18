@echo off

if defined JAVA_TOOL_OPTIONS (
    echo Picked up JAVA_TOOL_OPTIONS: %JAVA_TOOL_OPTIONS%
)

echo openjdk version "1.8.0_212" 1>&2
echo OpenJDK Runtime Environment (build 1.8.0_212-8u212-b03-0ubuntu1.18.04.1-b03) 1>&2
echo OpenJDK 64-Bit Server VM (build 25.212-b03, mixed mode) 1>&2
echo Args are: %*
echo The application has started.

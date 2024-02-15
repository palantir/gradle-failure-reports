<p align="right">
<a href="https://autorelease.general.dmz.palantir.tech/palantir/gradle-failure-reports"><img src="https://img.shields.io/badge/Perform%20an-Autorelease-success.svg" alt="Autorelease"></a>
</p>

## gradle-failure-reports

A Gradle plugin that detects failures in CircleCI jobs and converts them into JUnit test reports, which are then displayed in the CircleCI user interface for a more streamlined view of the build status and error details.

Currently, the plugin renders failure reports for `Java` projects, specifically targeting `compilation` tasks, `verifyLocks` tasks, and `checkstyle` tasks. Support for additional task types is under development and will be available in the near future.


## gradle-failure-reports-exceptions

A library that exposes an `ExceptionWithSuggestion` that provides additional context or guidance when errors occur.

## Usage

To apply this plugin, `build.gradle` should look something like:

```
buildscript {
    repositories {
       mavenCentral()
    }

    dependencies {
        classpath 'com.palantir.gradle.failure-reports:gradle-failure-reports:<version>'
    }
}

apply plugin: 'com.palantir.failure-reports'
```

The plugin generates a `build/failure-reports/build-TEST.xml` file which encapsulates the errors during the CircleCI job into a JUnit format.

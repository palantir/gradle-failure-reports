/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.failurereports

import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import org.apache.tools.ant.taskdefs.Exec
import org.assertj.core.util.Throwables

class FailureReportsProjectsPluginIntegrationSpec extends IntegrationSpec {

    public static final List<String> GRADLE_VERSIONS =
            List.of("8.4", "8.6");

    def '#gradleVersionNumber: javaCompile error is reported'() {
        setup:
        gradleVersion = gradleVersionNumber
        // language=gradle
        buildFile << '''
            apply plugin: 'com.palantir.failure-reports'
        '''.stripIndent(true)

        buildFile << setFailureReportOutputFile(gradleVersionNumber)

        def subProjectDir = addSubproject("myProject", '''
            apply plugin: 'java'
        '''.stripIndent(true))
        // language=java
        writeJavaSourceFile('''
            package app;

            public class ClassA {
                public static void main() {
                    return 0
                }
            }
        '''.stripIndent(true), subProjectDir)

        enableTestCiRun()

        when:
        ExecutionResult result = runTasksWithFailure('compileJava')
        def failureMessage = Throwables.getRootCause(result.failure).message

        then:
        failureMessage.contains('Compilation failed; see the compiler error output for details.')
        result.standardError.contains('error: \';\' expected')
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "javaCompile", gradleVersionNumber)

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: multiple javaCompile errors are reported'() {
        setup:
        gradleVersion = gradleVersionNumber
        // language=gradle
        buildFile << '''
            apply plugin: 'com.palantir.failure-reports'
        '''.stripIndent(true)

        buildFile << setFailureReportOutputFile(gradleVersionNumber)

        def subProjectDir = addSubproject('myProject', '''
            apply plugin: 'java'

            sourceSets { foo }
        '''.stripIndent(true))

        writeJavaSourceFile('''
            package app;

            public class ClassFoo {
                public static void main() {
                    return 0
                }
            }
        '''.stripIndent(true), "src/foo/java", subProjectDir)

        writeJavaSourceFile('''
            package app;

            public class ClassA extends ClassThatDoesNotExist{
            }
        '''.stripIndent(true), subProjectDir);

        enableTestCiRun()

        when:
        runTasks('compileFooJava', 'compileJava', '--continue', "--parallel")

        then:
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "multiple-javaCompile", gradleVersionNumber)

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: multiple project errors are reported'() {
        setup:
        gradleVersion = gradleVersionNumber
        // language=gradle
        buildFile << '''
            apply plugin: 'com.palantir.failure-reports'
        '''.stripIndent(true)

        buildFile << setFailureReportOutputFile(gradleVersionNumber)

        def projectDir1 = addSubproject('myProject1', '''
            apply plugin: 'java'

        '''.stripIndent(true))

        writeJavaSourceFile('''
            package app;

            public class ClassFoo {
                public static void main() {
                    / wrong
                    return;
                }
            }
        '''.stripIndent(true), projectDir1)

        def projectDir2 = addSubproject('myProject2', '''
            apply plugin: 'java'

        '''.stripIndent(true))

        writeJavaSourceFile('''
            package app;

            public class ClassA extends AnotherClass {
            }
        '''.stripIndent(true), projectDir2);

        file('gradle.properties') << """
            __TESTING = true
            __TESTING_CI=true
        """.stripIndent(true)

        when:
        ExecutionResult result = runTasksWithFailure('compileJava', '--continue', "--parallel")

        then:
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "multiple-projects-javaCompile", gradleVersionNumber)

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: successful build does not report failures ' () {
        setup:
        gradleVersion = gradleVersionNumber
        // language=gradle
        buildFile << '''
            apply plugin: 'com.palantir.failure-reports'
        '''.stripIndent(true)

        buildFile << setFailureReportOutputFile(gradleVersionNumber)

        //language=groovy
        def subProjectDir = addSubproject("myProject", '''
            apply plugin: 'java'

            tasks.withType(JavaCompile.class).configureEach(javaCompileTask ->{
                javaCompileTask.doFirst {
                project.getLogger().Error("This is a warning") }
            })
        '''.stripIndent(true))

        writeHelloWorld(subProjectDir)

        enableTestCiRun()

        when:
        runTasks('compileJava', 'compileTestJava')

        then:
        def reportXml = new File(projectDir, "build/failure-reports/build-TEST.xml")
        !reportXml.exists()

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }


    def '#gradleVersionNumber: checkstyle reports failures' () {
        setup:
        gradleVersion = gradleVersionNumber
        setupRootCheckstyleBuild()

        // language=gradle
        def subProjectDir = addSubproject('myProject', '''
            repositories {
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }

            apply plugin: 'com.palantir.baseline-checkstyle'
            apply plugin: 'java'
        '''.stripIndent(true))

        writeJavaSourceFile('''
            package app;
            public class ClassA {
                public static void main() {
                    System.out.println("something");
                }
            }
        '''.stripIndent(true), subProjectDir);

        enableTestCiRun()

        when:
        runTasksSuccessfully('baselineUpdateConfig')
        ExecutionResult executionResult = runTasksWithFailure('checkstyleMain')

        def failureMessage = Throwables.getRootCause(executionResult.failure).message

        then:
        failureMessage.contains('Checkstyle rule violations were found.')
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "checkstyle", gradleVersionNumber)

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: successful checkstyle does not report failures' () {
        setup:
        gradleVersion = gradleVersionNumber
        // language=gradle
        setupRootCheckstyleBuild()

        // language=gradle
        def subProjectDir = addSubproject('myProject', '''
            repositories {
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }

            apply plugin: 'com.palantir.baseline-checkstyle'
            apply plugin: 'java'
        '''.stripIndent(true))

        writeJavaSourceFile('''
            package app;
            public final class ClassA {}
        '''.stripIndent(true), subProjectDir);

        enableTestCiRun()

        when:
        runTasksSuccessfully('baselineUpdateConfig')
        ExecutionResult executionResult = runTasksSuccessfully('checkstyleMain')

        then:
        executionResult.success
        def reportXml = new File(projectDir, getOutputFile(gradleVersionNumber))
        !reportXml.exists()

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: verifyLocks reports failures' () {
        setup:
        gradleVersion = gradleVersionNumber
        // language=gradle
        buildFile << '''
            buildscript {
                repositories {
                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                }

                dependencies {
                    classpath 'com.palantir.gradle.consistentversions:gradle-consistent-versions:2.18.0'
                }
            }

            repositories {
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }

            apply plugin: 'com.palantir.failure-reports'
            apply plugin: 'com.palantir.consistent-versions'
            apply plugin: 'java'
            dependencies {
                implementation 'com.squareup.okhttp3:okhttp'
            }
        '''.stripIndent(true)

        buildFile << setFailureReportOutputFile(gradleVersionNumber)

        file('versions.props').text = 'com.squareup.okhttp3:okhttp = 3.12.0'
        file('versions.lock').text = ''

        enableTestCiRun()

        when:
        runTasksWithFailure('verifyLocks')

        then:
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "verifyLocks", gradleVersionNumber)

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: ExceptionWithSuggestion is reported as a failure' () {
        setup:
        gradleVersion = gradleVersionNumber
        // language=gradle
        buildFile << '''
            import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion

            apply plugin: 'com.palantir.failure-reports'
            apply plugin: 'java'

            tasks.register('throwExceptionWithSuggestedFix') {
                doLast {
                    throw new ExceptionWithSuggestion("ExceptionWithSuggestedFixMessage", "./gradlew runFix")
                }
            }
        '''.stripIndent(true)

        buildFile << setFailureReportOutputFile(gradleVersionNumber)

        // language=gradle
        addSubproject("myProject", '''
            import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion

            apply plugin: 'java'

            tasks.register('throwInnerExceptionWithSuggestedFix') {
                doLast {
                    throw new GradleException("OuterGradleException",
                        new ExceptionWithSuggestion("InnerExceptionWithSuggestedFixMessage", "./gradlew fixMe", new RuntimeException("InnerRuntimeException")))
                }
            }

            tasks.register('throwGradleException') {
                doLast {
                    throw new GradleException("This is a gradle exception that is ignored")
                }
            }
        '''.stripIndent(true))

        enableTestCiRun()

        when:
        runTasksWithFailure( 'throwExceptionWithSuggestedFix', 'throwInnerExceptionWithSuggestedFix', 'throwGradleException', '--continue')

        then:
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "throwException", gradleVersionNumber)

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: when running locally, no failure report is created'() {
        setup:
        gradleVersion = gradleVersionNumber
        // language=gradle
        setupCompileErrorsWthGradleProperties("""
            __TESTING = true
        """.stripIndent(true))

        when:
        ExecutionResult result = runTasksWithFailure('compileJava')

        then:
        result.failure.message.contains('Execution failed for task \':compileJava\'.')
        result.standardError.contains('error: \';\' expected')

        def reportXml = new File(projectDir, "build/failure-reports/build-TEST.xml")
        !reportXml.exists()

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: when CIRCLE_NODE_INDEX is not 0, no failure report is created'() {
        setup:
        gradleVersion = gradleVersionNumber
        // language=gradle
        setupCompileErrorsWthGradleProperties("""
            __TESTING = true
            __TESTING_CI = true
            __TESTING_CIRCLE_NODE_INDEX=5
        """.stripIndent(true))

        when:
        ExecutionResult result = runTasksWithFailure('compileJava')

        then:
        result.failure.message.contains('Execution failed for task \':compileJava\'.')
        result.standardError.contains('error: \';\' expected')

        def reportXml = new File(projectDir, "build/failure-reports/build-TEST.xml")
        !reportXml.exists()

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: when CIRCLE_NODE_INDEX is not set, javaCompile errors are reported'() {
        setup:
        gradleVersion = gradleVersionNumber
        // language=gradle
        setupCompileErrorsWthGradleProperties("""
            __TESTING = true
            __TESTING_CI = true
        """.stripIndent(true))

        buildFile << setFailureReportOutputFile(gradleVersionNumber)

        when:
        ExecutionResult result = runTasksWithFailure('compileJava')

        then:
        result.failure.message.contains('Execution failed for task \':compileJava\'.')
        result.standardError.contains('error: \';\' expected')

        def reportXml = new File(projectDir, getOutputFile(gradleVersionNumber))
        reportXml.exists()

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def setupCompileErrorsWthGradleProperties(String gradleProperties) {
        buildFile << '''
            apply plugin: 'com.palantir.failure-reports'
            apply plugin: 'java'
        '''.stripIndent(true)

        // language=java
        writeJavaSourceFile('''
            package app;

            public class ClassA {
                public static void main() {
                    return 0
                }
            }
        '''.stripIndent(true))

        file('gradle.properties') << gradleProperties
    }


    def setupRootCheckstyleBuild() {
        // language=gradle
        buildFile << '''
            buildscript {
                repositories {
                    gradlePluginPortal()
                    mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
                }

                dependencies {
                    classpath 'com.palantir.baseline:gradle-baseline-java:5.38.0'
                }
            }

            repositories {
                gradlePluginPortal()
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }

            apply plugin: 'com.palantir.failure-reports'
            apply plugin: 'com.palantir.baseline'
        '''.stripIndent(true)

        buildFile << setFailureReportOutputFile(gradleVersion)
    }

    def enableTestCiRun() {
        file('gradle.properties') << """
            __TESTING = true
            __TESTING_CI=true
            __TESTING_CIRCLE_NODE_INDEX=0
        """.stripIndent(true)
    }

    def setFailureReportOutputFile(String gradleVersionNumber) {
        // changing the report failure location to prevent the failure reports from this tests from being displayed
        // in the CircleCi Tests tab
        return String.format("""
            failureReports {
                failureReportOutputFile = project.file('build/failure-reports/unit-test-%s.xml')
            }
        """.stripIndent(true), gradleVersionNumber);
    }

    def getOutputFile(String gradleVersionNumber) {
        return String.format('build/failure-reports/unit-test-%s.xml', gradleVersionNumber)
    }
}

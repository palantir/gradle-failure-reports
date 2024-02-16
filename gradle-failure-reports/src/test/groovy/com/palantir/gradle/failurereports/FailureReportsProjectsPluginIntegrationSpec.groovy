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

    def 'javaCompile error is reported'() {
        setup:
        // language=gradle
        buildFile << '''
            apply plugin: 'com.palantir.failure-reports'
        '''.stripIndent(true)

        buildFile << setFailureReportOutputFile()

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
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "javaCompile")
    }

    def 'multiple javaCompile errors are reported'() {
        setup:
        // language=gradle
        buildFile << '''
            apply plugin: 'com.palantir.failure-reports'
        '''.stripIndent(true)

        buildFile << setFailureReportOutputFile()

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
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "multiple-javaCompile")
    }

    def 'multiple project errors are reported'() {
        setup:
        // language=gradle
        buildFile << '''
            apply plugin: 'com.palantir.failure-reports'
        '''.stripIndent(true)

        buildFile << setFailureReportOutputFile()

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
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "multiple-projects-javaCompile")
    }

    def 'successful build does not report failures ' () {
        setup:
        // language=gradle
        buildFile << '''
            apply plugin: 'com.palantir.failure-reports'
        '''.stripIndent(true)

        buildFile << setFailureReportOutputFile()

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
    }


    def 'checkstyle reports failures' () {
        setup:
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
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "checkstyle")
    }

    def 'successful checkstyle does not report failures' () {
        setup:
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
        def reportXml = new File(projectDir, "build/failure-reports/unit-test.xml")
        !reportXml.exists()
    }

    def 'verifyLocks reports failures' () {
        setup:
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

        buildFile << setFailureReportOutputFile()

        file('versions.props').text = 'com.squareup.okhttp3:okhttp = 3.12.0'
        file('versions.lock').text = ''

        enableTestCiRun()

        when:
        runTasksWithFailure('verifyLocks')

        then:
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "verifyLocks")
    }

    def 'ExceptionWithSuggestion is reported as a failure' () {
        setup:
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

        buildFile << setFailureReportOutputFile()

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
                    throw new GradleException("This is a gradle exception that is not ignored")
                }
            }

           tasks.register('throwOOM') {
                doLast {
                    throw new OutOfMemoryError()
                }
            }
        '''.stripIndent(true))

        enableTestCiRun()

        when:
        runTasksWithFailure( 'throwExceptionWithSuggestedFix', 'throwInnerExceptionWithSuggestedFix', 'throwGradleException', 'throwOOM', '--continue')

        then:
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "throwException")
    }

    def 'when running locally, no failure report is created'() {
        setup:
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
    }

    def 'when CIRCLE_NODE_INDEX is not 0, no failure report is created'() {
        setup:
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
    }

    def 'when CIRCLE_NODE_INDEX is not set, javaCompile errors are reported'() {
        setup:
        // language=gradle
        setupCompileErrorsWthGradleProperties("""
            __TESTING = true
            __TESTING_CI = true
        """.stripIndent(true))

        buildFile << setFailureReportOutputFile()

        when:
        ExecutionResult result = runTasksWithFailure('compileJava')

        then:
        result.failure.message.contains('Execution failed for task \':compileJava\'.')
        result.standardError.contains('error: \';\' expected')

        def reportXml = new File(projectDir, "build/failure-reports/unit-test.xml")
        reportXml.exists()
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

        buildFile << setFailureReportOutputFile()
    }

    def enableTestCiRun() {
        file('gradle.properties') << """
            __TESTING = true
            __TESTING_CI=true
            __TESTING_CIRCLE_NODE_INDEX=0
        """.stripIndent(true)
    }

    def setFailureReportOutputFile() {
        // changing the report failure location to prevent the failure reports from this tests from being displayed
        // in the CircleCi Tests tab
        return """
            failureReports {
                failureReportOutputFile = project.file('build/failure-reports/unit-test.xml')
            }
        """.stripIndent(true)
    }
}

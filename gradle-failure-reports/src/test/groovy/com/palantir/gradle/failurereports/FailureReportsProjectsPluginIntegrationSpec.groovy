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
import org.assertj.core.util.Throwables

import java.nio.file.Path

class FailureReportsProjectsPluginIntegrationSpec extends IntegrationSpec {

    public static final List<String> GRADLE_VERSIONS =
            List.of("7.6", "8.6");

    def '#gradleVersionNumber: javaCompile error is reported'() {
        setup:
        gradleVersion = gradleVersionNumber
        // language=gradle
        buildFile << '''
            apply plugin: 'com.palantir.failure-reports'
        '''.stripIndent(true)

        //buildFile << setDefaultReportsOutputFiles(gradleVersionNumber)

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
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "javaCompile", getDefaultOutputFile(gradleVersionNumber))

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

        //buildFile << setDefaultReportsOutputFiles(gradleVersionNumber)

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
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "multiple-javaCompile", getDefaultOutputFile(gradleVersionNumber))

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

        buildFile << setDefaultReportsOutputFiles(gradleVersionNumber)

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
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "multiple-projects-javaCompile", getDefaultOutputFile(gradleVersionNumber))

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

        buildFile << setDefaultReportsOutputFiles(gradleVersionNumber)

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
        buildFile << setDefaultReportsOutputFiles(gradleVersion)

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
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "checkstyle", getDefaultOutputFile(gradleVersionNumber))

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: checkstyle and javaCompile report failures' () {
        setup:
        gradleVersion = gradleVersionNumber
        setupRootCheckstyleBuild()
        buildFile << setReportsOutputFiles(gradleVersion)

        // language=gradle
        def subProjectDir1 = addSubproject('myProject1', '''
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
        '''.stripIndent(true), subProjectDir1);

        def subProjectDir2 = addSubproject('myProject2', '''
            repositories {
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }
            
            apply plugin: 'java'
        '''.stripIndent(true))

        writeJavaSourceFile('''
            package foo;
            public class Foo extends NonExistentClass
        '''.stripIndent(true), subProjectDir2);

        enableTestCiRun()

        when:
        runTasksSuccessfully('baselineUpdateConfig')
        ExecutionResult executionResult = runTasksWithFailure('checkstyleMain', "compileJava", "--continue", "--parallel")

        then:
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "multi-errors-checkstyle", getDefaultOutputFile(gradleVersionNumber))
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "multi-errors-compile", getCompileOutputFile(gradleVersionNumber))

        where:
        gradleVersionNumber << GRADLE_VERSIONS
    }

    def '#gradleVersionNumber: successful checkstyle does not report failures' () {
        setup:
        gradleVersion = gradleVersionNumber
        // language=gradle
        setupRootCheckstyleBuild()
        buildFile << setDefaultReportsOutputFiles(gradleVersion)

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
        def reportXml = getDefaultOutputFile(gradleVersionNumber).toFile()
        !reportXml.exists()

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

        buildFile << setDefaultReportsOutputFiles(gradleVersionNumber)

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
                    throw new GradleException("This is a normal gradle exception")
                }
            }
            
            tasks.register('throwExceptionNoMessage') {
                doLast {
                    throw new OutOfMemoryError()
                }
            }
        '''.stripIndent(true))

        enableTestCiRun()

        when:
        runTasksWithFailure( 'throwExceptionWithSuggestedFix', 'throwInnerExceptionWithSuggestedFix', 'throwGradleException', 'throwExceptionNoMessage', '--continue')

        then:
        CheckedInExpectedReports.checkOrUpdateFor(projectDir, "throwException", getDefaultOutputFile(gradleVersionNumber))

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

        buildFile << setDefaultReportsOutputFiles(gradleVersionNumber)

        when:
        ExecutionResult result = runTasksWithFailure('compileJava')

        then:
        result.failure.message.contains('Execution failed for task \':compileJava\'.')
        result.standardError.contains('error: \';\' expected')

        def reportXml = getDefaultOutputFile(gradleVersionNumber).toFile()
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
    }

    def enableTestCiRun() {
        file('gradle.properties') << """
            __TESTING = true
            __TESTING_CI=true
            __TESTING_CIRCLE_NODE_INDEX=0
        """.stripIndent(true)
    }

    def setDefaultReportsOutputFiles(String gradleVersionNumber) {
        // changing the report failure location to prevent the failure reports from this tests from being displayed
        // in the CircleCi Tests tab
        return String.format("""
            failureReports {
                failureReportOutputFile = project.file('build/failure-reports/unit-test-%s.xml')
                failureReportCompileOutputFile = project.file('build/failure-reports/unit-test-%s.xml')
            }
        """.stripIndent(true), gradleVersionNumber, gradleVersionNumber);
    }

    def setReportsOutputFiles(String gradleVersionNumber) {
        // changing the report failure location to prevent the failure reports from this tests from being displayed
        // in the CircleCi Tests tab
        return String.format("""
            failureReports {
                failureReportOutputFile = project.file('build/failure-reports/unit-test-%s.xml')
                failureReportCompileOutputFile = project.file('build/failure-reports/unit-test-compile--%s.xml')
            }
        """.stripIndent(true), gradleVersionNumber, gradleVersionNumber);
    }

    private Path getCompileOutputFile(String gradleVersionNumber) {
        return Path.of(projectDir.getPath()).resolve(String.format('build/failure-reports/unit-test-compile--%s.xml', gradleVersionNumber));
    }


    private Path getDefaultOutputFile(String gradleVersionNumber) {
        return Path.of(projectDir.getPath()).resolve(String.format('build/failure-reports/unit-test-%s.xml', gradleVersionNumber));
    }
}

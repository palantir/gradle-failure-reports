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

package com.palantir.gradle.failurereports;

import static com.palantir.gradle.testing.assertion.GradlePluginTestAssertions.assertThat;

import com.palantir.gradle.testing.execution.DefaultGradleInvoker;
import com.palantir.gradle.testing.execution.GradleInvoker;
import com.palantir.gradle.testing.execution.InvocationResult;
import com.palantir.gradle.testing.files.gradle.GradleFile;
import com.palantir.gradle.testing.junit.DisabledConfigurationCache;
import com.palantir.gradle.testing.junit.GradlePluginTests;
import com.palantir.gradle.testing.project.RootProject;
import com.palantir.gradle.testing.project.SubProject;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@GradlePluginTests
@DisabledConfigurationCache
class FailureReportsProjectsPluginIntegrationTest {

    @Test
    void generates_a_failure_report_to_make_sure_circleci_renders_it_correctly(
            GradleInvoker gradle, RootProject rootProject, SubProject myProject) {
        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");

        myProject.buildGradle().plugins().add("java");
        myProject.mainSourceSet().java().writeClass("""
            package app;

            public class TestsThatCircleCiCanRenderTheFailureReport {
                public static void main() {
                    return 0
                }
            }
            """);

        enableTestCiRun(rootProject);
        gradle.withArgs("compileJava").buildsWithFailure();
    }

    @Test
    void javacompile_error_is_reported(GradleInvoker gradle, RootProject rootProject, SubProject myProject)
            throws IOException {
        String gradleVersionNumber =
                ((DefaultGradleInvoker) gradle).gradleVersion().version();

        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        setDefaultReportsOutputFiles(rootProject, gradleVersionNumber);

        myProject.buildGradle().plugins().add("java");
        myProject.mainSourceSet().java().writeClass("""
            package app;

            public class ClassA {
                public static void main() {
                    return 0
                }
            }
            """);

        enableTestCiRun(rootProject);

        InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

        assertThat(result).output().contains("Compilation failed; ");
        assertThat(result).output().contains("error: ';' expected");
        CheckedInExpectedReports.checkOrUpdateFor(
                rootProject.path().toFile(), "javaCompile", getDefaultOutputFile(rootProject, gradleVersionNumber));
    }

    @Test
    void multiple_javacompile_errors_are_reported(GradleInvoker gradle, RootProject rootProject, SubProject myProject)
            throws IOException {
        String gradleVersionNumber =
                ((DefaultGradleInvoker) gradle).gradleVersion().version();

        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        setDefaultReportsOutputFiles(rootProject, gradleVersionNumber);

        myProject.buildGradle().plugins().add("java");
        myProject.buildGradle().append("""
            sourceSets { foo }
            """);

        myProject.sourceSet("foo").java().writeClass("""
            package app;

            public class ClassFoo {
                public static void main() {
                    return 0
                }
            }
            """);

        myProject.mainSourceSet().java().writeClass("""
            package app;

            public class ClassA extends ClassThatDoesNotExist{
            }
            """);

        enableTestCiRun(rootProject);

        gradle.withArgs("compileFooJava", "compileJava", "--continue", "--parallel")
                .buildsWithFailure();

        CheckedInExpectedReports.checkOrUpdateFor(
                rootProject.path().toFile(),
                "multiple-javaCompile",
                getDefaultOutputFile(rootProject, gradleVersionNumber));
    }

    @Test
    void multiple_project_errors_are_reported(
            GradleInvoker gradle, RootProject rootProject, SubProject myProject1, SubProject myProject2)
            throws IOException {
        String gradleVersionNumber =
                ((DefaultGradleInvoker) gradle).gradleVersion().version();

        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        setDefaultReportsOutputFiles(rootProject, gradleVersionNumber);

        myProject1.buildGradle().plugins().add("java");

        myProject1.mainSourceSet().java().writeClass("""
            package app;

            public class ClassFoo {
                public static void main() {
                    / wrong
                    return;
                }
            }
            """);

        myProject2.buildGradle().plugins().add("java");

        myProject2.mainSourceSet().java().writeClass("""
            package app;

            public class ClassA extends AnotherClass {
            }
            """);

        rootProject.gradlePropertiesFile().appendProperty("__TESTING", "true").appendProperty("__TESTING_CI", "true");

        gradle.withArgs("compileJava", "--continue", "--parallel").buildsWithFailure();

        CheckedInExpectedReports.checkOrUpdateFor(
                rootProject.path().toFile(),
                "multiple-projects-javaCompile",
                getDefaultOutputFile(rootProject, gradleVersionNumber));
    }

    @Test
    void successful_build_does_not_report_failures(
            GradleInvoker gradle, RootProject rootProject, SubProject myProject) {
        String gradleVersionNumber =
                ((DefaultGradleInvoker) gradle).gradleVersion().version();

        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        setDefaultReportsOutputFiles(rootProject, gradleVersionNumber);

        myProject.buildGradle().plugins().add("java");
        myProject.buildGradle().append("""
            tasks.withType(JavaCompile.class).configureEach(javaCompileTask ->{
                javaCompileTask.doFirst {
                project.getLogger().Error("This is a warning") }
            })
            """);

        myProject.mainSourceSet().java().writeClass("""
            package com.example;
            public final class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
            """);

        enableTestCiRun(rootProject);

        gradle.withArgs("compileJava", "compileTestJava").buildsSuccessfully();

        rootProject
                .buildDir()
                .file("failure-reports/build-TEST.xml")
                .assertThat()
                .doesNotExist();
    }

    @Test
    void checkstyle_reports_failures(GradleInvoker gradle, RootProject rootProject, SubProject myProject)
            throws IOException {
        String gradleVersionNumber =
                ((DefaultGradleInvoker) gradle).gradleVersion().version();

        setupRootCheckstyleBuild(rootProject);
        setDefaultReportsOutputFiles(rootProject, gradleVersionNumber);

        myProject.buildGradle().append("""
            repositories {
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }

            apply plugin: 'com.palantir.baseline-checkstyle'
            apply plugin: 'java'
            """);

        myProject.mainSourceSet().java().writeClass("""
            package app;
            public class ClassA {
                public static void main() {
                    System.out.println("something");
                }
            }
            """);

        enableTestCiRun(rootProject);

        gradle.withArgs("baselineUpdateConfig").buildsSuccessfully();
        InvocationResult executionResult = gradle.withArgs("checkstyleMain").buildsWithFailure();

        assertThat(executionResult).output().contains("Checkstyle rule violations were found.");
        CheckedInExpectedReports.checkOrUpdateFor(
                rootProject.path().toFile(), "checkstyle", getDefaultOutputFile(rootProject, gradleVersionNumber));
    }

    @Test
    void checkstyle_and_javacompile_report_failures(
            GradleInvoker gradle, RootProject rootProject, SubProject myProject1, SubProject myProject2)
            throws IOException {
        String gradleVersionNumber =
                ((DefaultGradleInvoker) gradle).gradleVersion().version();

        setupRootCheckstyleBuild(rootProject);
        setReportsOutputFiles(rootProject, gradleVersionNumber);

        myProject1.buildGradle().append("""
            repositories {
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }

            apply plugin: 'com.palantir.baseline-checkstyle'
            apply plugin: 'java'
            """);

        myProject1.mainSourceSet().java().writeClass("""
            package app;
            public class ClassA {
                public static void main() {
                    System.out.println("something");
                }
            }
            """);

        myProject2.buildGradle().append("""
            repositories {
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }

            apply plugin: 'java'
            """);

        myProject2.mainSourceSet().java().writeClass("""
            package foo;
            public class Foo extends NonExistentClass
            """);

        enableTestCiRun(rootProject);

        gradle.withArgs("baselineUpdateConfig").buildsSuccessfully();
        gradle.withArgs("checkstyleMain", "compileJava", "--continue", "--parallel")
                .buildsWithFailure();

        CheckedInExpectedReports.checkOrUpdateFor(
                rootProject.path().toFile(),
                "multi-errors-checkstyle",
                getDefaultOutputFile(rootProject, gradleVersionNumber));
        CheckedInExpectedReports.checkOrUpdateFor(
                rootProject.path().toFile(),
                "multi-errors-compile",
                getCompileOutputFile(rootProject, gradleVersionNumber));
    }

    @Test
    void successful_checkstyle_does_not_report_failures(
            GradleInvoker gradle, RootProject rootProject, SubProject myProject) {
        String gradleVersionNumber =
                ((DefaultGradleInvoker) gradle).gradleVersion().version();

        setupRootCheckstyleBuild(rootProject);
        setDefaultReportsOutputFiles(rootProject, gradleVersionNumber);

        myProject.buildGradle().append("""
            repositories {
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }

            apply plugin: 'com.palantir.baseline-checkstyle'
            apply plugin: 'java'
            """);

        myProject.mainSourceSet().java().writeClass("""
            package app;
            public final class ClassA {}
            """);

        enableTestCiRun(rootProject);

        gradle.withArgs("baselineUpdateConfig").buildsSuccessfully();
        InvocationResult executionResult = gradle.withArgs("checkstyleMain").buildsSuccessfully();

        assertThat(executionResult).task(":checkstyleMain").succeeded();
        rootProject
                .buildDir()
                .file(String.format("failure-reports/unit-test-%s.xml", gradleVersionNumber))
                .assertThat()
                .as("report XML should not exist for successful build")
                .doesNotExist();
    }

    @Test
    void exceptionwithsuggestion_is_reported_as_a_failure(
            GradleInvoker gradle, RootProject rootProject, SubProject myProject) throws IOException {
        String gradleVersionNumber =
                ((DefaultGradleInvoker) gradle).gradleVersion().version();

        rootProject.buildGradle().append("""
            import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion

            apply plugin: 'com.palantir.failure-reports'
            apply plugin: 'java'

            tasks.register('throwExceptionWithSuggestedFix') {
                doLast {
                    throw new ExceptionWithSuggestion("ExceptionWithSuggestedFixMessage", "./gradlew runFix")
                }
            }
            """);

        setDefaultReportsOutputFiles(rootProject, gradleVersionNumber);

        myProject.buildGradle().append("""
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
            """);

        enableTestCiRun(rootProject);

        gradle.withArgs(
                        "throwExceptionWithSuggestedFix",
                        "throwInnerExceptionWithSuggestedFix",
                        "throwGradleException",
                        "throwExceptionNoMessage",
                        "--continue")
                .buildsWithFailure();

        CheckedInExpectedReports.checkOrUpdateFor(
                rootProject.path().toFile(), "throwException", getDefaultOutputFile(rootProject, gradleVersionNumber));
    }

    @Test
    void exceptionwithlogs_is_reported_as_a_failure(GradleInvoker gradle, RootProject rootProject, SubProject myProject)
            throws IOException {
        String gradleVersionNumber =
                ((DefaultGradleInvoker) gradle).gradleVersion().version();

        rootProject.buildGradle().append("""
            import com.palantir.gradle.failurereports.exceptions.ExceptionWithLogs

            apply plugin: 'com.palantir.failure-reports'
            apply plugin: 'java'

            tasks.register('throwExceptionWithLogs') {
                doLast {
                    throw new ExceptionWithLogs("Failed after 2 attempts with exit code 1", "this is log line1\\nthis is log line 2", false)
                }
            }
            """);

        setDefaultReportsOutputFiles(rootProject, gradleVersionNumber);

        myProject.buildGradle().append("""
            import com.palantir.gradle.failurereports.exceptions.ExceptionWithLogs

            apply plugin: 'java'

            tasks.register('throwInnerExceptionWithLogs') {
                doLast {
                    throw new GradleException("OuterGradleException",
                        new ExceptionWithLogs("myCustomMessage", "I have a log line", new RuntimeException("someRuntimeException")))
                }
            }
            """);

        enableTestCiRun(rootProject);

        gradle.withArgs("throwExceptionWithLogs", "throwInnerExceptionWithLogs", "--continue")
                .buildsWithFailure();

        CheckedInExpectedReports.checkOrUpdateFor(
                rootProject.path().toFile(),
                "throwExceptionWithLogs",
                getDefaultOutputFile(rootProject, gradleVersionNumber));
    }

    @Test
    void ignored_task_failures_are_not_reported(
            GradleInvoker gradle, RootProject rootProject, SubProject mySubproject) {
        rootProject.buildGradle().append("""
            import com.palantir.gradle.failurereports.exceptions.ExceptionWithLogs

            apply plugin: 'com.palantir.failure-reports'
            apply plugin: 'java'

            abstract class ParentCustomTask extends DefaultTask {}
            abstract class MyCustomTask extends ParentCustomTask {}

            tasks.register('throwExceptionWithLogs', MyCustomTask.class) {
                doLast {
                    throw new ExceptionWithLogs("Failed after 2 attempts with exit code 1", "this is log line1\\nthis is log line 2", false)
                }
            }

            failureReports {
                getIgnoredTasks().add(ParentCustomTask.class)
            }
            """);

        mySubproject.buildGradle().append("""
            import com.palantir.gradle.failurereports.exceptions.ExceptionWithLogs
            import com.palantir.gradle.failurereports.FailureReportsExtension
            apply plugin: 'java'

            abstract class SubProjectTask extends DefaultTask {}

            tasks.register('throwExceptionWithLogsFromSubproject', SubProjectTask.class) {
                doLast {
                    throw new ExceptionWithLogs("Failed", "log line", false)
                }
            }

            project.getRootProject().getPluginManager().withPlugin("com.palantir.failure-reports", _plugin -> {
                 project.getRootProject().getExtensions().getByType(FailureReportsExtension.class).getIgnoredTasks().add(SubProjectTask.class)
            })
            """);

        enableTestCiRun(rootProject);

        InvocationResult result = gradle.withArgs(
                        "throwExceptionWithLogs", "throwExceptionWithLogsFromSubproject", "--continue")
                .buildsWithFailure();

        assertThat(result)
                .output()
                .contains("Execution failed for task ':throwExceptionWithLogs'.")
                .contains("Execution failed for task ':mySubproject:throwExceptionWithLogsFromSubproject'.");

        rootProject
                .buildDir()
                .file("failure-reports/build-TEST.xml")
                .assertThat()
                .doesNotExist();
    }

    @Test
    void when_running_locally_no_failure_report_is_created(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        rootProject.buildGradle().plugins().add("java");

        rootProject.mainSourceSet().java().writeClass("""
            package app;

            public class ClassA {
                public static void main() {
                    return 0
                }
            }
            """);

        rootProject.gradlePropertiesFile().appendProperty("__TESTING", "true");

        InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

        assertThat(result).output().contains("Execution failed for task ':compileJava'.");
        assertThat(result).output().contains("error: ';' expected");

        rootProject
                .buildDir()
                .file("failure-reports/build-TEST.xml")
                .assertThat()
                .doesNotExist();
    }

    @Test
    void when_circle_node_index_is_not_0_no_failure_report_is_created(GradleInvoker gradle, RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        rootProject.buildGradle().plugins().add("java");

        rootProject.mainSourceSet().java().writeClass("""
            package app;

            public class ClassA {
                public static void main() {
                    return 0
                }
            }
            """);

        rootProject
                .gradlePropertiesFile()
                .appendProperty("__TESTING", "true")
                .appendProperty("__TESTING_CI", "true")
                .appendProperty("__TESTING_CIRCLE_NODE_INDEX", "5");

        InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

        assertThat(result).output().contains("Execution failed for task ':compileJava'.");
        assertThat(result).output().contains("error: ';' expected");

        rootProject
                .buildDir()
                .file("failure-reports/build-TEST.xml")
                .assertThat()
                .doesNotExist();
    }

    @Test
    void when_circle_node_index_is_not_set_javacompile_errors_are_reported(
            GradleInvoker gradle, RootProject rootProject) {
        String gradleVersionNumber =
                ((DefaultGradleInvoker) gradle).gradleVersion().version();

        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        rootProject.buildGradle().plugins().add("java");

        rootProject.mainSourceSet().java().writeClass("""
            package app;

            public class ClassA {
                public static void main() {
                    return 0
                }
            }
            """);

        rootProject.gradlePropertiesFile().appendProperty("__TESTING", "true").appendProperty("__TESTING_CI", "true");

        setDefaultReportsOutputFiles(rootProject, gradleVersionNumber);

        InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

        assertThat(result).output().contains("Execution failed for task ':compileJava'.");
        assertThat(result).output().contains("error: ';' expected");

        rootProject
                .buildDir()
                .file(String.format("failure-reports/unit-test-%s.xml", gradleVersionNumber))
                .assertThat()
                .exists();
    }

    private GradleFile setupRootCheckstyleBuild(RootProject rootProject) {
        rootProject.buildGradle().append("""
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
            """);

        return rootProject.buildGradle();
    }

    private void enableTestCiRun(RootProject rootProject) {
        rootProject
                .gradlePropertiesFile()
                .appendProperty("__TESTING", "true")
                .appendProperty("__TESTING_CI", "true")
                .appendProperty("__TESTING_CIRCLE_NODE_INDEX", "0");
    }

    private GradleFile setDefaultReportsOutputFiles(RootProject rootProject, String gradleVersionNumber) {
        // changing the report failure location to prevent the failure reports from this tests from being displayed
        // in the CircleCi Tests tab
        return rootProject.buildGradle().append("""
            failureReports {
                failureReportOutputFile = project.file('build/failure-reports/unit-test-%s.xml')
                failureReportCompileOutputFile = project.file('build/failure-reports/unit-test-%s.xml')
            }
            """, gradleVersionNumber, gradleVersionNumber);
    }

    private GradleFile setReportsOutputFiles(RootProject rootProject, String gradleVersionNumber) {
        // changing the report failure location to prevent the failure reports from this tests from being displayed
        // in the CircleCi Tests tab
        return rootProject.buildGradle().append("""
            failureReports {
                failureReportOutputFile = project.file('build/failure-reports/unit-test-%s.xml')
                failureReportCompileOutputFile = project.file('build/failure-reports/unit-test-compile--%s.xml')
            }
            """, gradleVersionNumber, gradleVersionNumber);
    }

    private Path getCompileOutputFile(RootProject rootProject, String gradleVersionNumber) {
        return rootProject
                .buildDir()
                .path()
                .resolve(String.format("failure-reports/unit-test-compile--%s.xml", gradleVersionNumber));
    }

    private Path getDefaultOutputFile(RootProject rootProject, String gradleVersionNumber) {
        return rootProject
                .buildDir()
                .path()
                .resolve(String.format("failure-reports/unit-test-%s.xml", gradleVersionNumber));
    }
}

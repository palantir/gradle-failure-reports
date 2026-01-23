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
        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        setDefaultReportsOutputFiles(rootProject);

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
                rootProject.path().toFile(), "javaCompile", getDefaultOutputFile(rootProject));
    }

    @Test
    void multiple_javacompile_errors_are_reported(GradleInvoker gradle, RootProject rootProject, SubProject myProject)
            throws IOException {
        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        setDefaultReportsOutputFiles(rootProject);

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
                rootProject.path().toFile(), "multiple-javaCompile", getDefaultOutputFile(rootProject));
    }

    @Test
    void multiple_project_errors_are_reported(
            GradleInvoker gradle, RootProject rootProject, SubProject myProject1, SubProject myProject2)
            throws IOException {
        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        setDefaultReportsOutputFiles(rootProject);

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
                rootProject.path().toFile(), "multiple-projects-javaCompile", getDefaultOutputFile(rootProject));
    }

    @Test
    void successful_build_does_not_report_failures(
            GradleInvoker gradle, RootProject rootProject, SubProject myProject) {
        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        setDefaultReportsOutputFiles(rootProject);

        myProject.buildGradle().plugins().add("java");
        myProject.buildGradle().append("""
            tasks.withType(JavaCompile.class).configureEach(javaCompileTask ->{
                javaCompileTask.doFirst {
                project.getLogger().error("This is a warning") }
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
        setupRootCheckstyleBuild(rootProject);
        setDefaultReportsOutputFiles(rootProject);

        myProject.buildGradle().plugins().add("com.palantir.baseline-checkstyle");
        myProject.buildGradle().plugins().add("java");
        myProject.buildGradle().append("""
            repositories {
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }
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
                rootProject.path().toFile(), "checkstyle", getDefaultOutputFile(rootProject));
    }

    @Test
    void checkstyle_and_javacompile_report_failures(
            GradleInvoker gradle, RootProject rootProject, SubProject myProject1, SubProject myProject2)
            throws IOException {
        setupRootCheckstyleBuild(rootProject);
        setReportsOutputFiles(rootProject);

        myProject1.buildGradle().plugins().add("com.palantir.baseline-checkstyle");
        myProject1.buildGradle().plugins().add("java");
        myProject1.buildGradle().append("""
            repositories {
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }
            """);

        myProject1.mainSourceSet().java().writeClass("""
            package app;
            public class ClassA {
                public static void main() {
                    System.out.println("something");
                }
            }
            """);

        myProject2.buildGradle().plugins().add("java");
        myProject2.buildGradle().append("""
            repositories {
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }
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
                rootProject.path().toFile(), "multi-errors-checkstyle", getDefaultOutputFile(rootProject));
        CheckedInExpectedReports.checkOrUpdateFor(
                rootProject.path().toFile(), "multi-errors-compile", getCompileOutputFile(rootProject));
    }

    @Test
    void successful_checkstyle_does_not_report_failures(
            GradleInvoker gradle, RootProject rootProject, SubProject myProject) {
        setupRootCheckstyleBuild(rootProject);
        setDefaultReportsOutputFiles(rootProject);

        myProject.buildGradle().plugins().add("com.palantir.baseline-checkstyle");
        myProject.buildGradle().plugins().add("java");
        myProject.buildGradle().append("""
            repositories {
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }
            """);

        myProject.mainSourceSet().java().writeClass("""
            package app;
            public final class ClassA {}
            """);

        enableTestCiRun(rootProject);

        gradle.withArgs("baselineUpdateConfig").buildsSuccessfully();
        InvocationResult executionResult = gradle.withArgs("checkstyleMain").buildsSuccessfully();

        assertThat(executionResult).task(":myProject:checkstyleMain").succeeded();
        rootProject
                .buildDir()
                .file("failure-reports/unit-test.xml")
                .assertThat()
                .as("report XML should not exist for successful build")
                .doesNotExist();
    }

    @Test
    void exceptionwithsuggestion_is_reported_as_a_failure(
            GradleInvoker gradle, RootProject rootProject, SubProject myProject) throws IOException {
        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        rootProject.buildGradle().plugins().add("java");
        rootProject.buildGradle().append("""
            import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion

            tasks.register('throwExceptionWithSuggestedFix') {
                doLast {
                    throw new ExceptionWithSuggestion("ExceptionWithSuggestedFixMessage", "./gradlew runFix")
                }
            }
            """);

        setDefaultReportsOutputFiles(rootProject);

        myProject.buildGradle().plugins().add("java");
        myProject.buildGradle().append("""
            import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion

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
                rootProject.path().toFile(), "throwException", getDefaultOutputFile(rootProject));
    }

    @Test
    void exceptionwithlogs_is_reported_as_a_failure(GradleInvoker gradle, RootProject rootProject, SubProject myProject)
            throws IOException {
        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        rootProject.buildGradle().plugins().add("java");
        rootProject.buildGradle().append("""
            import com.palantir.gradle.failurereports.exceptions.ExceptionWithLogs

            tasks.register('throwExceptionWithLogs') {
                doLast {
                    throw new ExceptionWithLogs("Failed after 2 attempts with exit code 1", "this is log line1\\nthis is log line 2", false)
                }
            }
            """);

        setDefaultReportsOutputFiles(rootProject);

        myProject.buildGradle().plugins().add("java");
        myProject.buildGradle().append("""
            import com.palantir.gradle.failurereports.exceptions.ExceptionWithLogs

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
                rootProject.path().toFile(), "throwExceptionWithLogs", getDefaultOutputFile(rootProject));
    }

    @Test
    void ignored_task_failures_are_not_reported(
            GradleInvoker gradle, RootProject rootProject, SubProject mySubproject) {
        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        rootProject.buildGradle().plugins().add("java");
        rootProject.buildGradle().append("""
            import com.palantir.gradle.failurereports.exceptions.ExceptionWithLogs

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

        mySubproject.buildGradle().plugins().add("java");
        mySubproject.buildGradle().append("""
            import com.palantir.gradle.failurereports.exceptions.ExceptionWithLogs
            import com.palantir.gradle.failurereports.FailureReportsExtension

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

        setDefaultReportsOutputFiles(rootProject);

        InvocationResult result = gradle.withArgs("compileJava").buildsWithFailure();

        assertThat(result).output().contains("Execution failed for task ':compileJava'.");
        assertThat(result).output().contains("error: ';' expected");

        rootProject
                .buildDir()
                .file("failure-reports/unit-test.xml")
                .assertThat()
                .exists();
    }

    private GradleFile setupRootCheckstyleBuild(RootProject rootProject) {
        rootProject.buildGradle().plugins().add("com.palantir.failure-reports");
        rootProject.buildGradle().plugins().add("com.palantir.baseline");
        rootProject.buildGradle().append("""
            repositories {
                gradlePluginPortal()
                mavenCentral() { metadataSources { mavenPom(); ignoreGradleMetadataRedirection() } }
            }
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

    private GradleFile setDefaultReportsOutputFiles(RootProject rootProject) {
        // changing the report failure location to prevent the failure reports from this tests from being displayed
        // in the CircleCi Tests tab
        return rootProject.buildGradle().append("""
            failureReports {
                failureReportOutputFile = project.file('build/failure-reports/unit-test.xml')
                failureReportCompileOutputFile = project.file('build/failure-reports/unit-test.xml')
            }
            """);
    }

    private GradleFile setReportsOutputFiles(RootProject rootProject) {
        // changing the report failure location to prevent the failure reports from this tests from being displayed
        // in the CircleCi Tests tab
        return rootProject.buildGradle().append("""
            failureReports {
                failureReportOutputFile = project.file('build/failure-reports/unit-test.xml')
                failureReportCompileOutputFile = project.file('build/failure-reports/unit-test-compile.xml')
            }
            """);
    }

    private Path getCompileOutputFile(RootProject rootProject) {
        return rootProject.buildDir().path().resolve("failure-reports/unit-test-compile.xml");
    }

    private Path getDefaultOutputFile(RootProject rootProject) {
        return rootProject.buildDir().path().resolve("failure-reports/unit-test.xml");
    }
}

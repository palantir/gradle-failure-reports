/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.gradle.failurereports.junit.TestSuites;
import com.palantir.gradle.failurereports.util.XmlResources;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * Helper class that checks the validity of the generated XML files.
 */
public final class CheckedInExpectedReports {

    private static final Logger log = Logging.getLogger(CheckedInExpectedReports.class);
    private static final Path TEST_RESOURCES_PATH = Paths.get("src/test/resources/");
    private static final String PROJECT_DIR_PLACEHOLDER = "_PROJECT_DIR";
    private static final String OTHER_STACK_FRAMES_REGEX = "(?m)^\\sat (?!com\\.palantir\\.).*\n";
    private static final String STACKFRAME_MORE_REGEX = "... \\d+ more";
    private static final String IGNORE_FAILURE_MESSAGE_ATTRIBUTE = "<failure message=\".*\" type=\".*\">";

    /**
     * When ran _locally_, it copies the generated reports from the tests to the "src/test/resources/" path.
     * Any encounters of the {@code projectDir.getAbsolutePath()} in the generated xml files will be replaced with a
     * placeholder {@code "_PROJECT_DIR"}.
     * When ran in _CI_ (CI env var is set and true), it checks that the generated report files from the tests are equal
     * to the expected xml files generated locally.
     *
     * @param projectDir the current project's dir.
     * @param testName the name of the current test. It is used to generate and read the expected reportXml file.
     */
    public static void checkOrUpdateFor(File projectDir, String testName) throws IOException {
        Path expectedReportPath = TEST_RESOURCES_PATH.resolve(getExpectedReportFilename(testName));
        Path actualReportPath = projectDir.toPath().resolve("build/failure-reports/unit-test.xml");
        String actualReportXmlContent = getReportWithProjectPlaceholder(
                actualReportPath, projectDir.toPath().toAbsolutePath());
        // making sure the redacted string content is still a valid TestSuites object
        XmlResources.readXml(actualReportXmlContent, TestSuites.class);

        if (runningInCi()) {
            String expectedReportXmlContent = Files.readString(expectedReportPath);
            assertThat(actualReportXmlContent)
                    .describedAs("Rerun this test locally to regenerate the examples")
                    .isEqualTo(expectedReportXmlContent);
            return;
        }
        Files.deleteIfExists(expectedReportPath);
        log.lifecycle("Running locally, updating the reportXml files");
        Files.write(expectedReportPath, actualReportXmlContent.getBytes(StandardCharsets.UTF_8));
    }

    private static String getReportWithProjectPlaceholder(Path reportPath, Path projectDir) throws IOException {
        return maybeRedactStacktrace(Files.readString(reportPath)
                // if local paths are too big, they might get truncated in the errorMessage
                .replaceAll(projectDir.toString(), PROJECT_DIR_PLACEHOLDER));
    }

    private static boolean runningInCi() {
        Optional<String> ciEnv = Optional.ofNullable(System.getenv().get("CI"));
        return ciEnv.isPresent() && Boolean.parseBoolean(ciEnv.get());
    }

    private static String getExpectedReportFilename(String testName) {
        return String.format("expected-%s-error-report.xml", testName);
    }

    private static String maybeRedactStacktrace(String report) {
        return dropFailureMessage(report.replaceAll(OTHER_STACK_FRAMES_REGEX, "")
                // the number of stacktrace frames might differ between local runs and CI runs
                .replaceAll(STACKFRAME_MORE_REGEX, "... PLACEHOLDER_NUMBER more"));
    }

    private static String dropFailureMessage(String report) {
        return report.replaceAll(
                IGNORE_FAILURE_MESSAGE_ATTRIBUTE, "<failure message=\"_IGNORED_IN_TESTS\" type=\"ERROR\">");
    }

    private CheckedInExpectedReports() {}
}

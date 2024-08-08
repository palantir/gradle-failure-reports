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

package com.palantir.gradle.failurereports.junit;

import com.palantir.gradle.failurereports.FailureReport;
import com.palantir.gradle.failurereports.junit.TestSuites.TestSuite;
import com.palantir.gradle.failurereports.junit.TestSuites.TestSuite.TestCase;
import com.palantir.gradle.failurereports.junit.TestSuites.TestSuite.TestCase.Failure;
import com.palantir.gradle.failurereports.util.XmlResources;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import one.util.streamex.EntryStream;

/**
 * Helper class that writes all the failures encountered {@link com.palantir.gradle.failurereports.FailureReport}s into a JUNIT XML format that can be
 * rendered in the CircleCi `Tests` section.
 */
public final class JunitReporter {

    public static void reportFailures(File junitXmlFile, List<FailureReport> failureReports) throws IOException {
        if (failureReports.isEmpty()) {
            return;
        }
        createNewFile(junitXmlFile);
        Map<String, List<FailureReport>> failureReportsByClickableSources =
                failureReports.stream().collect(Collectors.groupingBy(FailureReport::clickableSource));
        List<TestSuite> testSuites = EntryStream.of(failureReportsByClickableSources)
                .map(failureReportBySource -> {
                    List<TestCase> testCases = failureReportBySource.getValue().stream()
                            .map(JunitReporter::from)
                            .collect(Collectors.toList());
                    return TestSuite.builder()
                            .tests(testCases.size())
                            .testcases(testCases)
                            .name(failureReportBySource.getKey())
                            .build();
                })
                .collect(Collectors.toList());
        XmlResources.writeXml(
                junitXmlFile, TestSuites.builder().testSuite(testSuites).build());
    }

    private static TestCase from(FailureReport failureReport) {
        return TestCase.builder()
                .name(failureReport.header())
                .className(failureReport.header())
                .failure(Failure.builder()
                        .value(failureReport.errorMessage())
                        .message(failureReport.errorMessage())
                        .build())
                .time(0L)
                .build();
    }

    private static void createNewFile(File file) throws IOException {
        if (file.exists()) {
            file.delete();
        }
        Files.createDirectories(file.toPath().getParent());
        Files.createFile(file.toPath());
    }

    private JunitReporter() {}
}

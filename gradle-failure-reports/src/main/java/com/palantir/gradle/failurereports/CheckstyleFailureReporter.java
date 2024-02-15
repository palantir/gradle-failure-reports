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

import com.palantir.gradle.failurereports.Finalizer.FailureReport;
import com.palantir.gradle.failurereports.checkstyle.CheckstyleOutput;
import com.palantir.gradle.failurereports.util.FailureReporterResources;
import com.palantir.gradle.failurereports.util.XmlResources;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.quality.Checkstyle;

public final class CheckstyleFailureReporter implements FailureReporter<Checkstyle> {

    private static final Logger log = Logging.getLogger(CheckstyleFailureReporter.class);

    @Override
    public Stream<FailureReport> collect(Project project, Checkstyle checkstyleTask) {
        if (!FailureReporterResources.executedAndFailed(checkstyleTask)) {
            return Stream.empty();
        }
        File checkstyleReportXml = checkstyleTask
                .getReports()
                .getXml()
                .getOutputLocation()
                .getAsFile()
                .get();
        try {
            CheckstyleOutput checkstyleOutputReport = XmlResources.readXml(checkstyleReportXml, CheckstyleOutput.class);
            return from(project, checkstyleOutputReport);
        } catch (IOException e) {
            log.error("Unable to read the checkstyleReport", e);
            return Stream.empty();
        }
    }

    @Override
    public void configureTask(Checkstyle _checkstyleTask) {}

    private static Stream<FailureReport> from(Project project, CheckstyleOutput checkstyleOutputReport) {
        return checkstyleOutputReport.files().stream()
                .flatMap(checkstyleFileFailure -> checkstyleFileFailure.errors().stream()
                        .map(checkstyleError -> {
                            FailureReport report = project.getObjects().newInstance(FailureReport.class);
                            report.getClickableSource()
                                    .set(FailureReporterResources.getRelativePathWithLineNumber(
                                            project.getRootDir().toPath(),
                                            Path.of(checkstyleFileFailure.name()),
                                            checkstyleError.line()));
                            report.getErrorMessage().set(checkstyleError.message());
                            report.getHeader()
                                    .set(FailureReporterResources.sourceFileWithErrorMessage(
                                            FailureReporterResources.getFileName(checkstyleFileFailure.name()),
                                            checkstyleError.line(),
                                            checkstyleError.message(),
                                            checkstyleError.severity()));
                            return report;
                        }));
    }
}

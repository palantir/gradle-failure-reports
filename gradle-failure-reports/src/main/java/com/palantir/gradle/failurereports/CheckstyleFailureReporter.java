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

import com.palantir.gradle.failurereports.actions.GithubActionAnnotation;
import com.palantir.gradle.failurereports.checkstyle.CheckstyleOutput;
import com.palantir.gradle.failurereports.util.FailureReporterResources;
import com.palantir.gradle.failurereports.util.XmlResources;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.Project;
import org.gradle.api.plugins.quality.Checkstyle;

public final class CheckstyleFailureReporter {

    public static List<FailureReport> collectFailureReports(Project project, Checkstyle checkstyleTask) {
        if (!FailureReporterResources.executedAndFailed(checkstyleTask)) {
            return List.of();
        }
        File checkstyleReportXml = checkstyleTask
                .getReports()
                .getXml()
                .getOutputLocation()
                .getAsFile()
                .get();
        try {
            CheckstyleOutput checkstyleOutputReport = XmlResources.readXml(checkstyleReportXml, CheckstyleOutput.class);
            return toFailureReports(project, checkstyleOutputReport);
        } catch (IOException e) {
            project.getLogger().error("Unable to read the checkstyleReport", e);
            return List.of();
        }
    }

    public static List<GithubActionAnnotation> collectGithubActionAnnotations(
            Project project, Checkstyle checkstyleTask) {
        if (!FailureReporterResources.executedAndFailed(checkstyleTask)) {
            return List.of();
        }
        File checkstyleReportXml = checkstyleTask
                .getReports()
                .getXml()
                .getOutputLocation()
                .getAsFile()
                .get();
        try {
            CheckstyleOutput checkstyleOutputReport = XmlResources.readXml(checkstyleReportXml, CheckstyleOutput.class);
            return toGithubActionAnnotations(checkstyleOutputReport);
        } catch (IOException e) {
            project.getLogger().error("Unable to read the checkstyleReport", e);
            return List.of();
        }
    }

    private static List<GithubActionAnnotation> toGithubActionAnnotations(CheckstyleOutput checkstyleOutputReport) {
        return checkstyleOutputReport.files().stream()
                .flatMap(checkstyleFileFailure -> checkstyleFileFailure.errors().stream()
                        .map(checkstyleError -> GithubActionAnnotation.builder()
                                .file(checkstyleFileFailure.name())
                                .line(checkstyleError.line())
                                .message(checkstyleError.message())
                                .title(FailureReporterResources.getFileName(checkstyleFileFailure.name()))
                                .severity("error")
                                .build()))
                .collect(Collectors.toList());
    }

    private static List<FailureReport> toFailureReports(Project project, CheckstyleOutput checkstyleOutputReport) {
        return checkstyleOutputReport.files().stream()
                .flatMap(checkstyleFileFailure -> checkstyleFileFailure.errors().stream()
                        .map(checkstyleError -> FailureReport.builder()
                                .header(FailureReporterResources.sourceFileWithErrorMessage(
                                        FailureReporterResources.getFileName(checkstyleFileFailure.name()),
                                        checkstyleError.line(),
                                        checkstyleError.message(),
                                        checkstyleError.severity()))
                                .clickableSource(FailureReporterResources.getRelativePathWithLineNumber(
                                        project.getRootDir().toPath(),
                                        Path.of(checkstyleFileFailure.name()),
                                        checkstyleError.line()))
                                .errorMessage(checkstyleError.message())
                                .build()))
                .collect(Collectors.toList());
    }

    private CheckstyleFailureReporter() {}
}

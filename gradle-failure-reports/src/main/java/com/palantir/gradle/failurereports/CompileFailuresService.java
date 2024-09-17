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

import com.google.common.base.Splitter;
import com.palantir.gradle.failurereports.CompileFailuresService.Parameters;
import com.palantir.gradle.failurereports.common.FailureReport;
import com.palantir.gradle.failurereports.common.FailureReporterResources;
import com.palantir.gradle.failurereports.junit.JunitReporter;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.tasks.compile.AbstractCompile;

public abstract class CompileFailuresService implements BuildService<Parameters>, AutoCloseable {

    interface Parameters extends BuildServiceParameters {
        RegularFileProperty getOutputFile();

        RegularFileProperty getCompileOutputFile();

        Property<File> getRootDir();
    }

    private static final Pattern COMPILE_ERROR_FIRST_LINE_PATTERN =
            Pattern.compile("^(?<sourcePath>[^:]*):(?<lineNumber>\\d+): (?<errorMessage>error: .*)$");
    private static final Pattern COMPILE_ERROR_LAST_LINE_PATTERN = Pattern.compile("^\\d+ errors?$");
    private static final Pattern COMPILE_ERROR_PATTERN =
            Pattern.compile("^(?<sourcePath>[^:]*):(?<lineNumber>\\d+): (?<errorMessage>error: [\\s\\S]*)$");

    private final ConcurrentMap<String, Boolean> startedCompileErrorsByTaskPath = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StringBuilder> compilerErrorsByTaskPath = new ConcurrentHashMap<>();

    public final <T extends AbstractCompile> void maybeCollectErrorMessage(
            T task, CharSequence charSequence, Set<String> taskCompiledSourcePaths) {
        Matcher firstCompileErrorMatcher = COMPILE_ERROR_FIRST_LINE_PATTERN.matcher(charSequence);
        if (firstCompileErrorMatcher.matches()) {
            String sourcePathFromError = firstCompileErrorMatcher.group("sourcePath");

            // When running in parallel, javaCompileTasks will see the output of other tasks,
            // see: https://github.com/gradle/gradle/issues/6068 for context.
            // If the matched sourcePath is not part of the current javaCompileTask's sourceSet, we need to
            // ignore it.
            if (!taskCompiledSourcePaths.contains(sourcePathFromError)) {
                return;
            }
            markCompileErrorStarted(task.getPath());
            compilerErrorsByTaskPath
                    .computeIfAbsent(task.getPath(), _k -> new StringBuilder())
                    .append(charSequence);
            return;
        }
        if (!isCompileErrorStarted(task.getPath())) {
            return;
        }
        Matcher lastCompileErrorMatcher = COMPILE_ERROR_LAST_LINE_PATTERN.matcher(charSequence);
        if (lastCompileErrorMatcher.matches()) {
            markCompileErrorFinished(task.getPath());
            return;
        }
        compilerErrorsByTaskPath
                .computeIfAbsent(task.getPath(), _k -> new StringBuilder())
                .append(charSequence);
    }

    public final Stream<FailureReport> collectFailureReports(String taskPath) {
        if (!compilerErrorsByTaskPath.containsKey(taskPath)) {
            return Stream.empty();
        }
        return Splitter.on(COMPILE_ERROR_LAST_LINE_PATTERN)
                .splitToStream(compilerErrorsByTaskPath.get(taskPath).toString())
                .map(this::maybeGetFailureReport)
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    public static Provider<CompileFailuresService> getSharedCompileFailuresService(
            Project project, FailureReportsExtension failureReportsExtension) {
        return project.getGradle()
                .getSharedServices()
                .registerIfAbsent("compileFailuresService", CompileFailuresService.class, spec -> {
                    spec.getParameters().getOutputFile().set(failureReportsExtension.getFailureReportOutputFile());
                    spec.getParameters()
                            .getCompileOutputFile()
                            .set(failureReportsExtension.getFailureReportCompileOutputFile());
                    spec.getParameters().getRootDir().set(project.provider(project::getRootDir));
                });
    }

    private void markCompileErrorStarted(String taskPath) {
        startedCompileErrorsByTaskPath.put(taskPath, true);
    }

    private void markCompileErrorFinished(String taskPath) {
        startedCompileErrorsByTaskPath.put(taskPath, false);
    }

    private boolean isCompileErrorStarted(String taskPath) {
        return startedCompileErrorsByTaskPath.getOrDefault(taskPath, false);
    }

    private Optional<FailureReport> maybeGetFailureReport(String multiLineError) {
        Matcher matcher = COMPILE_ERROR_PATTERN.matcher(multiLineError);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String sourcePathFromError = matcher.group("sourcePath");
        String errorMessage = matcher.group("errorMessage");
        int lineNumber = Integer.parseInt(matcher.group("lineNumber"));

        return Optional.of(FailureReport.builder()
                .header(extractCompileErrorHeader(sourcePathFromError, lineNumber, errorMessage))
                .clickableSource(FailureReporterResources.getRelativePathWithLineNumber(
                        getParameters().getRootDir().get().toPath(), Path.of(sourcePathFromError), lineNumber))
                .errorMessage(matcher.group())
                .build());
    }

    private static String extractCompileErrorHeader(String sourcePath, int lineNumber, String error) {
        // the relevant compiler error header is the first line of the error message.
        int errorExplanationIndex = error.indexOf("\n");
        int maxIndex = errorExplanationIndex < 0 ? error.length() : errorExplanationIndex;
        return FailureReporterResources.sourceFileWithErrorMessage(
                FailureReporterResources.getFileName(sourcePath), lineNumber, error.substring(0, maxIndex));
    }

    @Override
    public final void close() throws Exception {
        JunitReporter.reportFailures(
                getParameters().getCompileOutputFile().getAsFile().get(),
                compilerErrorsByTaskPath.keySet().stream()
                        .flatMap(this::collectFailureReports)
                        .collect(Collectors.toList()));
    }
}

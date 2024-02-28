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
import com.palantir.gradle.failurereports.util.FailureReporterResources;
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
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters.None;
import org.gradle.api.tasks.compile.AbstractCompile;

public abstract class CompileFailuresService implements BuildService<None> {

    private static final Pattern COMPILE_ERROR_FIRST_LINE_PATTERN =
            Pattern.compile("^(?<sourcePath>.*):(?<lineNumber>\\d+): (?<errorMessage>error: .*)$");
    private static final Pattern COMPILE_ERROR_LAST_LINE_PATTERN = Pattern.compile("^\\d+ error(s)*$");
    private static final Pattern COMPILE_ERROR_PATTERN =
            Pattern.compile("(?m)^(?<sourcePath>.*):(?<lineNumber>\\d+): (?<errorMessage>error: (.|\\s)*)");

    private final ConcurrentMap<String, Boolean> startedCompileErrorsByTaskPath = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, StringBuilder> compilerErrorsByTaskPath = new ConcurrentHashMap<>();

    public <T extends AbstractCompile> void maybeCollectErrorMessage(T task, CharSequence charSequence) {
        Matcher firstCompileErrorMatcher = COMPILE_ERROR_FIRST_LINE_PATTERN.matcher(charSequence);
        if (firstCompileErrorMatcher.find()) {
            String sourcePathFromError = firstCompileErrorMatcher.group("sourcePath");

            // When running in parallel, javaCompileTasks will see the output of other tasks,
            // see: https://github.com/gradle/gradle/issues/6068 for context.
            // If the matched sourcePath is not part of the current javaCompileTask's sourceSet, we need to
            // ignore it.
            Set<String> taskCompiledSourcePaths = task.getSource().getFiles().stream()
                    .map(File::getAbsolutePath)
                    .collect(Collectors.toSet());
            if (!taskCompiledSourcePaths.contains(sourcePathFromError)) {
                return;
            }
            markCompileErrorStarted(task.getPath());
            compilerErrorsByTaskPath
                    .computeIfAbsent(task.getPath(), _k -> new StringBuilder())
                    .append(charSequence);
            return;
        }
        Matcher lastCompileErrorMessage = COMPILE_ERROR_LAST_LINE_PATTERN.matcher(charSequence);
        if (isCompileErrorStarted(task.getPath()) && lastCompileErrorMessage.find()) {
            markCompileErrorFinished(task.getPath());
            return;
        }
        if (isCompileErrorStarted(task.getPath())) {
            compilerErrorsByTaskPath
                    .computeIfAbsent(task.getPath(), _k -> new StringBuilder())
                    .append(charSequence);
        }
    }

    public Stream<FailureReport> collectFailureReports(Project project, String taskPath) {
        if (!compilerErrorsByTaskPath.containsKey(taskPath)) {
            return Stream.empty();
        }
        return Splitter.on(COMPILE_ERROR_LAST_LINE_PATTERN)
                .splitToStream(compilerErrorsByTaskPath.get(taskPath).toString())
                .map(multiLineError -> maybeGetFailureReport(project, multiLineError))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    public static Provider<CompileFailuresService> getSharedCompileFailuresService(Project project) {
        return project.getGradle()
                .getSharedServices()
                .registerIfAbsent("compileFailuresService", CompileFailuresService.class, _spec -> {});
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

    private static Optional<FailureReport> maybeGetFailureReport(Project project, String multiLineError) {
        Matcher matcher = COMPILE_ERROR_PATTERN.matcher(multiLineError);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String sourcePathFromError = matcher.group("sourcePath");
        String errorMessage = matcher.group("errorMessage");
        int lineNumber = Integer.parseInt(matcher.group("lineNumber"));

        return Optional.of(FailureReport.builder()
                .header(extractCompileErrorHeader(sourcePathFromError, lineNumber, errorMessage))
                .clickableSource(FailureReporterResources.getRelativePathWithLineNumber(
                        project.getRootDir().toPath(), Path.of(sourcePathFromError), lineNumber))
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
}

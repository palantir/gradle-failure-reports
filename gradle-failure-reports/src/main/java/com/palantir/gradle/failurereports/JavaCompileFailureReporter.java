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
import com.palantir.gradle.failurereports.Finalizer.FailureReport;
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
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.tasks.compile.JavaCompile;

public final class JavaCompileFailureReporter implements FailureReporter<JavaCompile> {

    private static final Pattern COMPILE_ERROR_FIRST_LINE_PATTERN =
            Pattern.compile("^(?<sourcePath>.*):(?<lineNumber>\\d+): (?<errorMessage>error: .*)$");
    private static final Pattern COMPILE_ERROR_LAST_LINE_PATTERN = Pattern.compile("^\\d+ error(s)*$");
    private static final Pattern COMPILE_ERROR_PATTERN =
            Pattern.compile("(?m)^(?<sourcePath>.*):(?<lineNumber>\\d+): (?<errorMessage>error: (.|\\s)*)");

    private final ConcurrentMap<String, StringBuilder> compilerErrorsByTaskPath = new ConcurrentHashMap<>();

    @Override
    public Stream<FailureReport> collect(Project project, JavaCompile javaCompileTask) {
        if (!FailureReporterResources.executedAndFailed(javaCompileTask)
                || !compilerErrorsByTaskPath.containsKey(javaCompileTask.getPath())) {
            return Stream.empty();
        }
        return Splitter.on(COMPILE_ERROR_LAST_LINE_PATTERN)
                .splitToStream(
                        compilerErrorsByTaskPath.get(javaCompileTask.getPath()).toString())
                .map(multiLineError -> maybeGetFailureReport(project, multiLineError))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private static Optional<FailureReport> maybeGetFailureReport(Project project, String multiLineError) {
        Matcher matcher = COMPILE_ERROR_PATTERN.matcher(multiLineError);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String sourcePathFromError = matcher.group("sourcePath");
        String errorMessage = matcher.group("errorMessage");
        int lineNumber = Integer.parseInt(matcher.group("lineNumber"));

        FailureReport report = project.getObjects().newInstance(FailureReport.class);
        report.getErrorMessage().set(matcher.group());
        report.getClickableSource()
                .set(FailureReporterResources.getRelativePathWithLineNumber(
                        project.getRootDir().toPath(), Path.of(sourcePathFromError), lineNumber));
        report.getHeader().set(extractCompileErrorHeader(sourcePathFromError, lineNumber, errorMessage));
        return Optional.of(report);
    }

    @Override
    public void configureTask(JavaCompile javaCompileTask) {
        javaCompileTask.getLogging().addStandardErrorListener(new StandardOutputListener() {

            private boolean startedErrorMessage = false;

            @Override
            public void onOutput(CharSequence charSequence) {
                Matcher firstCompileErrorMatcher = COMPILE_ERROR_FIRST_LINE_PATTERN.matcher(charSequence);
                if (firstCompileErrorMatcher.find()) {
                    String sourcePathFromError = firstCompileErrorMatcher.group("sourcePath");

                    // When running in parallel, javaCompileTasks will see the output of other tasks,
                    // see: https://github.com/gradle/gradle/issues/6068 for context.
                    // If the matched sourcePath is not part of the current javaCompileTask's sourceSet, we need to
                    // ignore it.
                    Set<String> taskCompiledSourcePaths = javaCompileTask.getSource().getFiles().stream()
                            .map(File::getAbsolutePath)
                            .collect(Collectors.toSet());
                    if (!taskCompiledSourcePaths.contains(sourcePathFromError)) {
                        return;
                    }
                    startedErrorMessage = true;
                    compilerErrorsByTaskPath
                            .computeIfAbsent(javaCompileTask.getPath(), _k -> new StringBuilder())
                            .append(charSequence);
                    return;
                }
                Matcher lastCompileErrorMessage = COMPILE_ERROR_LAST_LINE_PATTERN.matcher(charSequence);
                if (startedErrorMessage && lastCompileErrorMessage.find()) {
                    startedErrorMessage = false;
                    return;
                }
                if (startedErrorMessage) {
                    compilerErrorsByTaskPath
                            .computeIfAbsent(javaCompileTask.getPath(), _k -> new StringBuilder())
                            .append(charSequence);
                }
            }
        });
    }

    private static String extractCompileErrorHeader(String sourcePath, int lineNumber, String error) {
        // the relevant compiler error header is the first line of the error message.
        int errorExplanationIndex = error.indexOf("\n");
        int maxIndex = errorExplanationIndex < 0 ? error.length() : errorExplanationIndex;
        return FailureReporterResources.sourceFileWithErrorMessage(
                FailureReporterResources.getFileName(sourcePath), lineNumber, error.substring(0, maxIndex));
    }
}

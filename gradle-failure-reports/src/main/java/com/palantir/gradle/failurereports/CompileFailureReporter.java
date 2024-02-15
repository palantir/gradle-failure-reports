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
import org.gradle.api.tasks.compile.AbstractCompile;

/**
 * Interface that sets up the configuration and the collection of failures for all {@link AbstractCompile} tasks.
 */
public abstract class CompileFailureReporter<T extends AbstractCompile> implements FailureReporter<T> {

    /**
     * A mapping between the task path and the compiler errors that were written to standardError.
     * A task can have multiple errors, so we use a StringBuilder to append the errors as they come in. When collecting
     * the errors, we split the errors using {@link #getMultipleErrorsSplitterPattern()}. Each split sequence must be
     * matched against {@link #getCompileErrorPattern()}.
     */
    private final ConcurrentMap<String, StringBuilder> compilerErrorsByTaskPath = new ConcurrentHashMap<>();

    /**
     * Pattern used to match an error instance.
     */
    abstract Pattern getCompileErrorPattern();

    /**
     * The delimiter pattern that is used to split multiple errors for the same taskPath.
     */
    abstract Pattern getMultipleErrorsSplitterPattern();

    /**
     * Sets up a {@link StandardOutputListener} that gathers the failures into {@link #compilerErrorsByTaskPath}.
     * Note: should call {@link #taskHasSourcePath(AbstractCompile, String)} in order to remove the errors that do not
     * correspond to the current task.
     */
    abstract StandardOutputListener getErrorOutputListener(T task);

    @Override
    public final Stream<FailureReport> collect(Project project, T task) {
        if (!FailureReporterResources.executedAndFailed(task)
                || !compilerErrorsByTaskPath.containsKey(task.getPath())) {
            return Stream.empty();
        }
        return Splitter.on(getMultipleErrorsSplitterPattern())
                .splitToStream(compilerErrorsByTaskPath.get(task.getPath()).toString())
                .map(multiLineError -> maybeGetFailureReport(project, multiLineError))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    @Override
    public final void configureTask(T task) {
        task.getLogging().addStandardErrorListener(getErrorOutputListener(task));
    }

    protected final ConcurrentMap<String, StringBuilder> compilerErrorsByTaskPath() {
        return compilerErrorsByTaskPath;
    }

    protected final boolean taskHasSourcePath(T task, String sourcePathFromError) {
        // When running in parallel, compileTasks will see the output of other tasks,
        // see: https://github.com/gradle/gradle/issues/6068 for context.
        // If the matched sourcePath is not part of the current compileTasks' sourceSet, we need to
        // ignore it.
        Set<String> taskCompiledSourcePaths =
                task.getSource().getFiles().stream().map(File::getAbsolutePath).collect(Collectors.toSet());
        return taskCompiledSourcePaths.contains(sourcePathFromError);
    }

    protected final Optional<FailureReport> maybeGetFailureReport(Project project, String multiLineError) {
        Matcher matcher = getCompileErrorPattern().matcher(multiLineError);
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

    protected final String extractCompileErrorHeader(String sourcePath, int lineNumber, String error) {
        // the relevant compiler error header is the first line of the error message.
        int errorExplanationIndex = error.indexOf("\n");
        int maxIndex = errorExplanationIndex < 0 ? error.length() : errorExplanationIndex;
        return FailureReporterResources.sourceFileWithErrorMessage(
                FailureReporterResources.getFileName(sourcePath), lineNumber, error.substring(0, maxIndex));
    }
}

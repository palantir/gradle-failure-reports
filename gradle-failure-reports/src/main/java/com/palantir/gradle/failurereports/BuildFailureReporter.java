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

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.palantir.gradle.failurereports.junit.JunitReporter;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.execution.MultipleBuildFailures;

public final class BuildFailureReporter {

    private static Logger log = Logging.getLogger(BuildFailureReporter.class);

    public static void report(File outputFile, Throwable buildThrowable) {
        Optional.ofNullable(buildThrowable).ifPresent(failure -> {
            try {
                reportFailures(outputFile, failure);
            } catch (IOException e) {
                log.error("Failed to report build failures", e);
            }
        });
    }

    private static void reportFailures(File outputFile, Throwable buildThrowable) throws IOException {
        ImmutableList.Builder<FailureReport> failureReports = ImmutableList.builder();
        for (TaskExecutionException taskExecutionException : getTaskExecutionExceptions(buildThrowable)) {
            Task task = taskExecutionException.getTask();
            if (task instanceof JavaCompile) {
                // TODO(crogoz): use compileFailuresService to report the errors once everything is on gradle >= 8.6
                // for now this is a noop, once the {@link CompileFailuresService} is closed, it will report all the
                // errors that were collected
            } else if (task instanceof Checkstyle) {
                failureReports.addAll(CheckstyleFailureReporter.collect(task.getProject(), (Checkstyle) task)
                        .collect(Collectors.toList()));
            } else if (!(task instanceof Test)) {
                // test failures are already reported
                failureReports.add(ThrowableFailureReporter.getFailureReport(task));
            }
        }
        JunitReporter.reportFailures(outputFile, failureReports.build());
    }

    private static List<TaskExecutionException> getTaskExecutionExceptions(Throwable buildThrowable) {
        ImmutableList.Builder<Throwable> rootExceptions = ImmutableList.builder();
        if (buildThrowable instanceof MultipleBuildFailures) {
            rootExceptions.addAll(((MultipleBuildFailures) buildThrowable).getCauses());
        } else {
            rootExceptions.add(buildThrowable);
        }
        return rootExceptions.build().stream()
                .map(Throwables::getCausalChain)
                .flatMap(Collection::stream)
                .filter(throwable -> throwable instanceof TaskExecutionException)
                .map(throwable -> (TaskExecutionException) throwable)
                .collect(Collectors.toList());
    }

    private BuildFailureReporter() {}
}

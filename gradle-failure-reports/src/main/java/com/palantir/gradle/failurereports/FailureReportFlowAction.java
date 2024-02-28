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
import org.gradle.api.flow.BuildWorkResult;
import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.execution.MultipleBuildFailures;

public final class FailureReportFlowAction implements FlowAction<FailureReportFlowAction.Parameters> {

    interface Parameters extends FlowParameters {
        @Input
        Property<BuildWorkResult> getBuildResult();

        @Input
        Property<File> getOutputFile();
    }

    @Override
    public void execute(Parameters parameters) {
        Optional<Throwable> failure = parameters.getBuildResult().get().getFailure();
        try {
            if (failure.isPresent()) {
                reportBuildFailure(parameters.getOutputFile().get(), failure.get());
            }
        } catch (Exception e) {
            // TODO(crogoz): change this to logger
            throw new RuntimeException(e);
        }
    }


    private static void reportBuildFailure(File outputFile, Throwable buildThrowable) throws IOException {
        ImmutableList.Builder<Throwable> rootExceptions = ImmutableList.builder();
        ImmutableList.Builder<FailureReport> failureReports = ImmutableList.builder();
        if (buildThrowable instanceof MultipleBuildFailures) {
            rootExceptions.addAll(((MultipleBuildFailures) buildThrowable).getCauses());
        } else {
            rootExceptions.add(buildThrowable);
        }
        List<Throwable> throwables = rootExceptions.build().stream()
                .map(Throwables::getCausalChain)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        for (Throwable throwable : throwables) {
            if (throwable instanceof TaskExecutionException) {
                TaskExecutionException taskExecutionException = (TaskExecutionException) throwable;
                Task task = taskExecutionException.getTask();

                if (task.getName().equals("verifyLocks")) {
                    // do something here
                    failureReports.add(VerifyLocksFailureReporter.getFailureReport(task));
                } else if (task instanceof JavaCompile) {
                    // do something here
                } else if (task instanceof Checkstyle) {
                    // do something here
                    failureReports.addAll(CheckstyleFailureReporter.collect(task.getProject(), (Checkstyle) task)
                            .collect(Collectors.toList()));
                }

                ThrowableFailureReporter.maybeGetFailureReport(task).ifPresent(failureReports::add);
            }
        }
        JunitReporter.reportFailures(outputFile, failureReports.build());
    }
}

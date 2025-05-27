/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.palantir.gradle.failurereports.LocalFailureRecommendationFlowAction.Parameters;
import com.palantir.gradle.failurereports.handlers.ExecCommandNotFoundFailure;
import com.palantir.gradle.failurereports.handlers.ExecCommandNotFoundHandler;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.flow.BuildWorkResult;
import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskExecutionException;

public final class LocalFailureRecommendationFlowAction implements FlowAction<Parameters> {

    private static Logger log = Logging.getLogger(LocalFailureRecommendationFlowAction.class);
    private static ExecCommandNotFoundHandler failureHandler = new ExecCommandNotFoundHandler();

    interface Parameters extends FlowParameters {
        @Input
        Property<BuildWorkResult> getBuildResult();
    }

    @Override
    public void execute(Parameters parameters) {
        parameters
                .getBuildResult()
                .get()
                .getFailure()
                .ifPresent(LocalFailureRecommendationFlowAction::checkRecommendations);
    }

    public static void checkRecommendations(Throwable buildThrowable) {
        ImmutableList.Builder<ExecCommandNotFoundFailure> expandedFailuresBuilder = ImmutableList.builder();
        for (TaskExecutionException taskExecutionException : BuildFailures.getTaskExecutionExceptions(buildThrowable)) {
            Task task = taskExecutionException.getTask();
            failureHandler.handle(task, taskExecutionException).ifPresent(expandedFailuresBuilder::add);
        }

        List<ExecCommandNotFoundFailure> expandedFailures = expandedFailuresBuilder.build();
        if (!expandedFailures.isEmpty()) {
            throw new GradleException(ExecCommandNotFoundFailure.renderMessage(expandedFailures));
        }
    }
}

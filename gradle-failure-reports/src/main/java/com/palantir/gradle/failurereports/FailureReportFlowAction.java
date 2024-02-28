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

import java.io.File;
import java.util.Optional;
import org.gradle.api.flow.BuildWorkResult;
import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;
import org.gradle.api.provider.Property;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Input;

public final class FailureReportFlowAction implements FlowAction<FailureReportFlowAction.Parameters> {

    interface Parameters extends FlowParameters {
        @Input
        Property<BuildWorkResult> getBuildResult();

        @Input
        Property<File> getOutputFile();

        @ServiceReference
        Property<CompileFailuresService> getCompileFailuresService();
    }

    @Override
    public void execute(Parameters parameters) {
        parameters
                .getBuildResult()
                .get()
                .getFailure()
                .ifPresent(failure -> BuildFailureReporter.report(
                        parameters.getOutputFile().get(),
                        Optional.of(parameters.getCompileFailuresService().get()),
                        failure));
    }
}

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

import javax.inject.Inject;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;

public abstract class FailureReportsFlowActionsPlugin implements Plugin<Project> {

    @Inject
    protected abstract FlowScope getFlowScope();

    @Inject
    protected abstract FlowProviders getFlowProviders();

    @Override
    public void apply(Project project) {
        FailureReportsExtension failureReportsExtension =
                project.getExtensions().create("failureReports", FailureReportsExtension.class);

        getFlowScope().always(FailureReportFlowAction.class, spec -> {
            spec.getParameters()
                    .getOutputFile()
                    .set(failureReportsExtension.getFailureReportOutputFile().getAsFile());
            spec.getParameters().getBuildResult().set(getFlowProviders().getBuildWorkResult());
        });
    }
}

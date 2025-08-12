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

import com.palantir.gradle.failurereports.util.PluginResources;
import com.palantir.gradle.utils.environmentvariables.EnvironmentVariables;
import javax.inject.Inject;
import org.gradle.api.Project;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

public abstract class FailureReportsExtension {

    public abstract RegularFileProperty getFailureReportOutputFile();

    public abstract RegularFileProperty getFailureReportCompileOutputFile();

    public abstract Property<Boolean> getEnableParallelWorkerReports();

    @Inject
    public abstract ProjectLayout getProjectLayout();

    @Inject
    public abstract ObjectFactory getObjects();

    @Inject
    public abstract Project getProject();

    public FailureReportsExtension() {
        Provider<String> indexSuffix = getProject().provider(() -> {
            if (!getEnableParallelWorkerReports().get()) {
                return "";
            }
            EnvironmentVariables env = getObjects().newInstance(EnvironmentVariables.class);
            return PluginResources.maybeGetCircleNode(env)
                    .map(nodeIndex -> "-node" + nodeIndex)
                    .orElse("");
        });
        getFailureReportOutputFile().convention(indexSuffix.map(s -> getProjectLayout()
                .getBuildDirectory()
                .file("failure-reports/build-TEST" + s + ".xml")
                .get()));
        getFailureReportCompileOutputFile().convention(indexSuffix.map(s -> getProjectLayout()
                .getBuildDirectory()
                .file("failure-reports/build-compile-TEST" + s + ".xml")
                .get()));

        getEnableParallelWorkerReports().convention(false);
    }
}

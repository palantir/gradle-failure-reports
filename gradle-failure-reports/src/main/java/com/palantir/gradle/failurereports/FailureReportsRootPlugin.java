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

import com.palantir.gradle.failurereports.util.ExtensionUtils;
import com.palantir.gradle.failurereports.util.PluginResources;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;

public final class FailureReportsRootPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        if (project.getRootProject() != project) {
            throw new IllegalArgumentException("com.palantir.failure-reports must be applied to the root project only");
        }

        applyLocalRecommendationsPlugin(project);

        applyFailureReportsPlugin(project);
    }

    private static void applyLocalRecommendationsPlugin(Project project) {
        if (!PluginResources.isRunningLocally(project)) {
            return;
        }
        if (!PluginResources.canUseFlowActions(project)) {
            return;
        }
        project.getPluginManager().apply(LocalFailuresRecommendationsPlugin.class);
    }

    private static void applyFailureReportsPlugin(Project project) {
        if (!PluginResources.isRunningOnInitialCircleNode(project)) {
            return;
        }

        FailureReportsExtension failureReportsExtension =
                ExtensionUtils.maybeCreate(project, "failureReports", FailureReportsExtension.class);
        CompileFailuresService.getSharedCompileFailuresService(project, failureReportsExtension);

        project.allprojects(subproject -> subproject.getPluginManager().apply(FailureReportsProjectsPlugin.class));
        if (PluginResources.canUseFlowActions(project)) {
            project.getPluginManager().apply(FailureReportsFlowActionsPlugin.class);
        } else {
            project.getGradle().addBuildListener(new BuildListener() {
                @Override
                public void settingsEvaluated(Settings _settings) {}

                @Override
                public void projectsLoaded(Gradle _gradle) {}

                @Override
                public void projectsEvaluated(Gradle _gradle) {}

                @Override
                public void buildFinished(BuildResult result) {
                    BuildFailureReporter.report(
                            failureReportsExtension
                                    .getFailureReportOutputFile()
                                    .getAsFile()
                                    .get(),
                            result.getFailure(),
                            failureReportsExtension.getIgnoredTasks().get());
                }
            });
        }
    }
}

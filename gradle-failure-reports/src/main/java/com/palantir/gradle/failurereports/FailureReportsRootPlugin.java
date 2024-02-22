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

import com.palantir.gradle.failurereports.Finalizer.FinalizerTask;
import com.palantir.gradle.failurereports.util.FailureReporterResources;
import com.palantir.gradle.failurereports.util.PluginResources;
import java.util.List;
import one.util.streamex.StreamEx;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

public final class FailureReportsRootPlugin implements Plugin<Project> {

    public static final String FINALIZER_TASK = "failureReportFinalizer";
    private static final String VERIFY_LOCKS_TASK = "verifyLocks";

    @Override
    public void apply(Project project) {
        if (!PluginResources.shouldApplyPlugin(project)) {
            return;
        }
        if (project.getRootProject() != project) {
            throw new IllegalArgumentException("com.palantir.failure-reports must be applied to the root project only");
        }
        FailureReportsExtension failureReportsExtension =
                project.getExtensions().create("failureReports", FailureReportsExtension.class);
        TaskProvider<FinalizerTask> finalizerTask = project.getTasks()
                .register(FINALIZER_TASK, FinalizerTask.class, task -> {
                    task.getOutputFile().set(failureReportsExtension.getFailureReportOutputFile());
                });
        project.getRootProject()
                .allprojects(subProject -> subProject.getPlugins().apply(FailureReportsProjectsPlugin.class));
        collectVerifyLocksFailureReports(project, finalizerTask);
        collectOtherTaskFailures(project, finalizerTask);
    }

    private void collectVerifyLocksFailureReports(Project project, TaskProvider<FinalizerTask> finalizerTask) {
        project.getPluginManager().withPlugin("com.palantir.versions-lock", _javaPlugin -> {
            TaskProvider<Task> verifyLocksTask = project.getTasks().named(VERIFY_LOCKS_TASK);
            verifyLocksTask.configure(task -> task.finalizedBy(finalizerTask));

            finalizerTask.configure(finalizer -> finalizer.getFailureReports().addAll(project.provider(() -> {
                if (FailureReporterResources.executedAndFailed(verifyLocksTask.get())) {
                    return List.of(VerifyLocksFailureReporter.getFailureReport(verifyLocksTask.get()));
                }
                return List.of();
            })));
        });
    }

    private void collectOtherTaskFailures(Project project, TaskProvider<FinalizerTask> finalizerTask) {
        project.allprojects(subProject -> {
            TaskContainer projectTasks = subProject.getTasks();
            projectTasks.configureEach(task -> {
                if (isAllowedTask(task)) {
                    task.finalizedBy(finalizerTask);
                }
            });
            finalizerTask.configure(
                    finalizer -> finalizer.getFailureReports().addAll(subProject.provider(() -> StreamEx.of(
                                    projectTasks)
                            .filter(FailureReportsRootPlugin::isAllowedTask)
                            .filter(FailureReporterResources::executedAndFailed)
                            .map(ThrowableFailureReporter::getFailureReport))));
        });
    }

    private static boolean isAllowedTask(Task task) {
        return !(task instanceof JavaCompile
                || task instanceof Checkstyle
                || task instanceof FinalizerTask
                || task.getName().equals(VERIFY_LOCKS_TASK));
    }
}

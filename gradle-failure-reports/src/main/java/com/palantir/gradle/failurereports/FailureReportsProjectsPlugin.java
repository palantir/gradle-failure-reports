/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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
import one.util.streamex.StreamEx;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.quality.Checkstyle;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

/**
 * Collects all the errors from gradle tasks of type {@link Checkstyle}, {@link JavaCompile} and
 * {@code VerifyLocksTask} and renders them into a JUNIT XML file that can be read by CircleCi and shown in the
 * `Tests` section.
 */
public final class FailureReportsProjectsPlugin implements Plugin<Project> {
    private static final Logger log = Logging.getLogger(FailureReportsProjectsPlugin.class);

    @Override
    public void apply(Project project) {
        if (!PluginResources.shouldApplyPlugin(project)) {
            return;
        }
        project.getPluginManager().withPlugin("java", _javaPlugin -> {
            TaskProvider<FinalizerTask> finalizerTask = project.getRootProject()
                    .getTasks()
                    .named(FailureReportsRootPlugin.FINALIZER_TASK, FinalizerTask.class);
            collectFailureReportsByType(project, finalizerTask, JavaCompile.class, new JavaCompileFailureReporter());
            collectFailureReportsByType(project, finalizerTask, Checkstyle.class, new CheckstyleFailureReporter());
        });
    }

    private <T extends Task> void collectFailureReportsByType(
            Project project,
            TaskProvider<FinalizerTask> finalizerTask,
            Class<T> taskClass,
            FailureReporter<T> collector) {
        TaskCollection<T> tasksWithClassType = project.getTasks().withType(taskClass);
        finalizerTask.configure(
                finalizer -> finalizer.getFailureReports().addAll(project.provider(() -> StreamEx.of(tasksWithClassType)
                        .filter(FailureReporterResources::executedAndFailed)
                        .flatMap(task -> collector.collect(project, task)))));
        tasksWithClassType.configureEach(task -> {
            task.finalizedBy(finalizerTask);
            collector.configureTask(task);
        });
    }
}

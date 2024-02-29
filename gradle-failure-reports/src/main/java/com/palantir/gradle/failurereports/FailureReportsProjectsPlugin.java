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
import java.io.File;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.compile.JavaCompile;

public final class FailureReportsProjectsPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        if (!PluginResources.shouldApplyPlugin(project)) {
            return;
        }
        FailureReportsExtension failureReportsExtension =
                ExtensionUtils.maybeCreate(project, "failureReports", FailureReportsExtension.class);
        Provider<CompileFailuresService> compileService =
                CompileFailuresService.getSharedCompileFailuresService(project, failureReportsExtension);
        project.getPluginManager().withPlugin("java", _javaPlugin -> {
            configureCompileTasks(project, compileService);
        });
    }

    private void configureCompileTasks(Project project, Provider<CompileFailuresService> compileService) {
        TaskCollection<JavaCompile> tasksWithClassType = project.getTasks().withType(JavaCompile.class);
        tasksWithClassType.configureEach(javaCompileTask -> {
            javaCompileTask.usesService(compileService);
            javaCompileTask.getLogging().addStandardErrorListener(new StandardOutputListener() {
                private Optional<Set<String>> sourcePathsCache = Optional.empty();

                @Override
                public void onOutput(CharSequence charSequence) {
                    compileService
                            .get()
                            .maybeCollectErrorMessage(
                                    javaCompileTask, charSequence, sourcePathsCache.orElseGet(this::getSourcePaths));
                }

                private Set<String> getSourcePaths() {
                    this.sourcePathsCache = Optional.of(javaCompileTask.getSource().getFiles().stream()
                            .map(File::getAbsolutePath)
                            .collect(Collectors.toSet()));
                    return sourcePathsCache.get();
                }
            });
        });
    }
}

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

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.execution.MultipleBuildFailures;

public final class BuildFailures {

    public static List<TaskExecutionException> getTaskExecutionExceptions(Throwable buildThrowable) {
        ImmutableList.Builder<Throwable> rootExceptions = ImmutableList.builder();
        if (buildThrowable instanceof MultipleBuildFailures multipleBuildFailures) {
            rootExceptions.addAll(multipleBuildFailures.getCauses());
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

    private BuildFailures() {}
}

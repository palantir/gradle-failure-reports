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

import com.palantir.gradle.failurereports.Finalizer.FailureReport;
import java.util.stream.Stream;
import org.gradle.api.Project;
import org.gradle.api.Task;

/**
 * Interface that configures tasks of type {@param T} and collects any failures for them into a stream of
 * {@link FailureReport}s.
 * @param <T> the task type e.g. JavaCompile/Checkstyle etc
 */
public interface FailureReporter<T extends Task> {

    /**
     * Collects the failures from a task of type {@param T}  from the current {@link  Project}.
     */
    Stream<FailureReport> collect(Project project, T task);

    /**
     * Configures a task of type {@param T} to prepare the failure report collection.
     */
    void configureTask(T task);
}

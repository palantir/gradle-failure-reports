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

import com.google.common.base.Throwables;
import com.palantir.gradle.failurereports.util.FailureReporterResources;
import com.palantir.gradle.failurereports.util.ThrowableResources;
import org.gradle.api.Task;

public final class VerifyLocksFailureReporter {

    public static FailureReport getFailureReport(Task task) {
        Throwable throwable = task.getState().getFailure();
        return FailureReport.builder()
                .header(FailureReporterResources.getTaskErrorHeader(
                        task.getPath(), Throwables.getRootCause(throwable).toString()))
                .clickableSource("./gradlew --write-locks")
                .errorMessage(ThrowableResources.formatThrowable(throwable))
                .build();
    }

    private VerifyLocksFailureReporter() {}
}

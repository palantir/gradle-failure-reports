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
import com.palantir.gradle.failurereports.common.FailureReport;
import com.palantir.gradle.failurereports.common.FailureReporterResources;
import com.palantir.gradle.failurereports.common.ThrowableResources;
import com.palantir.gradle.failurereports.exceptions.FailureReporterException;
import java.util.Optional;
import org.gradle.api.Task;

public final class ThrowableFailureReporter {

    public static <T extends Task> FailureReport getFailureReport(T task) {
        Throwable throwable = task.getState().getFailure();
        return getFailureReport(throwable, task.getPath());
    }

    static FailureReport getFailureReport(Throwable throwable, String taskPath) {
        // try to get the last FailureReporterException in the causal chain
        Optional<FailureReporterException> maybeFailureReporterException = Throwables.getCausalChain(throwable).stream()
                .filter(FailureReporterException.class::isInstance)
                .map(FailureReporterException.class::cast)
                .findFirst();
        return maybeFailureReporterException
                .map(exception -> exception.getTaskFailureReport(taskPath, throwable))
                .orElseGet(() -> getGenericExceptionReport(taskPath, throwable));
    }

    private static FailureReport getGenericExceptionReport(String taskPath, Throwable throwable) {
        return FailureReport.builder()
                .header(FailureReporterResources.getTaskErrorHeader(taskPath, throwable))
                .clickableSource(taskPath)
                .errorMessage(ThrowableResources.formatThrowableWithMessage(throwable))
                .build();
    }

    private ThrowableFailureReporter() {}
}

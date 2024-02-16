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
import com.palantir.gradle.failurereports.Finalizer.FailureReport;
import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion;
import com.palantir.gradle.failurereports.util.FailureReporterResources;
import com.palantir.gradle.failurereports.util.ThrowableResources;
import java.util.Optional;
import org.gradle.api.Task;

public final class ThrowableFailureReporter {

    public static <T extends Task> FailureReport getFailureReport(T task) {
        Throwable throwable = task.getState().getFailure();
        // retrieving the last ExceptionWithSuggestion in the causal chain
        Optional<ExceptionWithSuggestion> maybeExtraInfoException = Throwables.getCausalChain(throwable).stream()
                .filter(ExceptionWithSuggestion.class::isInstance)
                .map(ExceptionWithSuggestion.class::cast)
                .findFirst();
        return maybeExtraInfoException
                .map(exception -> getExceptionWithSuggestionReport(task, throwable, exception))
                .orElseGet(() -> getGenericExceptionReport(task, throwable));
    }

    @SuppressWarnings("NullAway")
    private static <T extends Task> FailureReport getExceptionWithSuggestionReport(
            T task, Throwable initialThrowable, ExceptionWithSuggestion extraInfoException) {
        FailureReport report = task.getProject().getObjects().newInstance(FailureReport.class);
        report.getClickableSource().set(extraInfoException.getSuggestion());
        report.getErrorMessage()
                .set(ThrowableResources.formatThrowableWithMessage(initialThrowable, extraInfoException.getMessage()));
        report.getHeader()
                .set(FailureReporterResources.getTaskErrorHeader(task.getPath(), extraInfoException.getMessage()));
        return report;
    }

    private static <T extends Task> FailureReport getGenericExceptionReport(T task, Throwable throwable) {
        FailureReport report = task.getProject().getObjects().newInstance(FailureReport.class);
        report.getClickableSource().set(task.getPath());
        report.getErrorMessage().set(ThrowableResources.formatThrowable(throwable));
        report.getHeader().set(FailureReporterResources.getTaskErrorHeader(task.getPath(), throwable.getMessage()));
        return report;
    }

    private ThrowableFailureReporter() {}
}

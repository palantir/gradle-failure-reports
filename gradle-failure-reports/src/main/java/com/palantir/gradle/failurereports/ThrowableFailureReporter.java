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
import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion;
import com.palantir.gradle.failurereports.exceptions.MinimalException;
import com.palantir.gradle.failurereports.util.FailureReporterResources;
import com.palantir.gradle.failurereports.util.ThrowableResources;
import java.util.Optional;
import org.gradle.api.Task;

public final class ThrowableFailureReporter {

    public static <T extends Task> FailureReport getFailureReport(T task) {
        Throwable throwable = task.getState().getFailure();
        // try to get the last ExceptionWithSuggestion in the causal chain
        Optional<ExceptionWithSuggestion> maybeExceptionWithSuggestion = Throwables.getCausalChain(throwable).stream()
                .filter(ExceptionWithSuggestion.class::isInstance)
                .map(ExceptionWithSuggestion.class::cast)
                .findFirst();
        Optional<MinimalException> maybeMinimalException = Throwables.getCausalChain(throwable).stream()
                .filter(MinimalException.class::isInstance)
                .map(MinimalException.class::cast)
                .findFirst();
        return maybeExceptionWithSuggestion
                .map(exception -> getEnhancedExceptionReport(task.getPath(), throwable, exception))
                .orElseGet(() -> maybeMinimalException
                        .map(exception -> getExceptionReport(task.getPath(), exception))
                        .orElseGet(() -> getGenericExceptionReport(task, throwable)));
    }

    @SuppressWarnings("NullAway")
    public static FailureReport getEnhancedExceptionReport(
            String taskPath, Throwable initialThrowable, ExceptionWithSuggestion extraInfoException) {
        return FailureReport.builder()
                .header(FailureReporterResources.getTaskErrorHeader(taskPath, extraInfoException.getMessage()))
                .clickableSource(extraInfoException.getSuggestion())
                .errorMessage(ThrowableResources.formatThrowableWithMessage(
                        initialThrowable, extraInfoException.getMessage()))
                .build();
    }

    public static FailureReport getExceptionReport(String taskPath, MinimalException exception) {
        return FailureReport.builder()
                .header(FailureReporterResources.getTaskErrorHeader(taskPath, exception.getMessage()))
                .clickableSource(taskPath)
                .errorMessage(exception.getMessage())
                .build();
    }

    private static <T extends Task> FailureReport getGenericExceptionReport(T task, Throwable throwable) {
        return FailureReport.builder()
                .header(FailureReporterResources.getTaskErrorHeader(task.getPath(), throwable))
                .clickableSource(task.getPath())
                .errorMessage(ThrowableResources.formatThrowable(throwable))
                .build();
    }

    private ThrowableFailureReporter() {}
}

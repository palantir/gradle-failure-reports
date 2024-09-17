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

package com.palantir.gradle.failurereports.exceptions;

import com.palantir.gradle.failurereports.common.FailureReport;

/**
 * Subclass of RuntimeException that can display the exception as a Failure Report in Circle CI.
 * For examples of usages see {@link ExceptionWithLogs} and {@link ExceptionWithSuggestion}.
 */
public abstract class FailureReporterException extends RuntimeException {

    public FailureReporterException(String message) {
        super(message);
    }

    public FailureReporterException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Rendering a FailureReporterException exception that is part of the causalChain of {@code initialThrowable} as
     * a FailureReport that can be shown in the CircleCI `Tests` tab.
     * @param taskPath The Gradle task that caused the exception
     * @param initialThrowable the throwable that contains the FailureReporterException exception in the casualChain
     * @return the FailureReport object
     */
    public abstract FailureReport getTaskFailureReport(String taskPath, Throwable initialThrowable);
}

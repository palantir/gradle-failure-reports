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
import com.palantir.gradle.failurereports.common.FailureReporterResources;
import com.palantir.gradle.failurereports.common.ThrowableResources;

/**
 * An exception type that allows passing an extra logs field which is rendered in the CircleCi failure report.
 * It is useful for surfacing errors derived from a subprocess.
 */
public final class ExceptionWithLogs extends FailureReporterException {

    private final String logs;
    private final boolean includeStackTrace;

    public ExceptionWithLogs(String message, String logs, boolean includeStackTrace) {
        this(message, logs, null, includeStackTrace);
    }

    public ExceptionWithLogs(String message, String logs) {
        this(message, logs, null, true);
    }

    public ExceptionWithLogs(String message, String logs, Throwable throwable) {
        this(message, logs, throwable, true);
    }

    public ExceptionWithLogs(String message, String logs, Throwable throwable, boolean includeStackTrace) {
        super(message, throwable);
        // keeping only the last 100kb of logs to avoid any potential OOM issues if the logs are really large.
        this.logs = FailureReporterResources.keepLastBytesSizeOutput(logs, 100 * 1024);
        this.includeStackTrace = includeStackTrace;
    }

    @Override
    public FailureReport getTaskFailureReport(String taskPath, Throwable initialThrowable) {
        String maybeIncludeStacktrace =
                includeStackTrace ? "\n" + ThrowableResources.formatThrowable(initialThrowable) : "";
        return FailureReport.builder()
                .header(FailureReporterResources.getTaskErrorHeader(taskPath, getMessage()))
                .clickableSource(taskPath)
                .errorMessage(String.join("\n", getMessage(), logs, maybeIncludeStacktrace))
                .build();
    }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion;
import com.palantir.gradle.failurereports.exceptions.MinimalException;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ThrowableFailureReporterTest {
    private static final String EXCEPTION_MESSAGE = "Exception on line 21 of script.sh";

    @Mock
    Task task;

    @Mock
    TaskState taskState;

    @BeforeEach
    public void before() {
        when(task.getPath()).thenReturn("taskPath");
        when(task.getState()).thenReturn(taskState);
    }

    @Test
    public void getFailureReportSuggestion() {
        when(taskState.getFailure()).thenReturn(new ExceptionWithSuggestion(EXCEPTION_MESSAGE, "Remove semicolon"));

        FailureReport report = ThrowableFailureReporter.getFailureReport(task);
        assertThat(report.header()).isEqualTo("[taskPath] error: " + EXCEPTION_MESSAGE);
        assertThat(report.clickableSource()).isEqualTo("Remove semicolon");
        assertThat(report.errorMessage())
                .startsWith(EXCEPTION_MESSAGE
                        + "\n\n* Causal chain is:\n\t"
                        + "com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion: "
                        + EXCEPTION_MESSAGE + "\n\n* Full exception is:\n"
                        + "com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion: "
                        + EXCEPTION_MESSAGE);
    }

    @Test
    public void getFailureReportMinimal() {
        when(taskState.getFailure()).thenReturn(new MinimalException(EXCEPTION_MESSAGE));

        FailureReport report = ThrowableFailureReporter.getFailureReport(task);
        assertThat(report.header()).isEqualTo("[taskPath] error: " + EXCEPTION_MESSAGE);
        assertThat(report.clickableSource()).isEqualTo("taskPath");
        assertThat(report.errorMessage()).isEqualTo(EXCEPTION_MESSAGE);
    }
}

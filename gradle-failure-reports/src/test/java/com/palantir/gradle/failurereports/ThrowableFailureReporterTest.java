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

import com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion;
import com.palantir.gradle.failurereports.exceptions.MinimalException;
import org.junit.jupiter.api.Test;

public class ThrowableFailureReporterTest {
    private static final String EXCEPTION_MESSAGE = "Exception on line 21 of script.sh";

    @Test
    public void exception_with_suggestion_provides_error_with_suggestion() {
        FailureReport report = ThrowableFailureReporter.getFailureReport(
                new ExceptionWithSuggestion(EXCEPTION_MESSAGE, "Remove semicolon"), "taskPath");
        assertThat(report.header()).isEqualTo("[taskPath] error: " + EXCEPTION_MESSAGE);
        assertThat(report.clickableSource()).isEqualTo("Remove semicolon");
        assertThat(report.errorMessage())
                .startsWith(EXCEPTION_MESSAGE
                        + "\n\n* Causal chain is:\n\t"
                        + "com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion: "
                        + "\n\n* Full exception is:\n"
                        + "com.palantir.gradle.failurereports.exceptions.ExceptionWithSuggestion: ");
    }

    @Test
    public void minimal_exception_provides_minimal_error_message() {
        FailureReport report =
                ThrowableFailureReporter.getFailureReport(new MinimalException(EXCEPTION_MESSAGE), "taskPath");
        assertThat(report.header()).isEqualTo("[taskPath] error: " + EXCEPTION_MESSAGE);
        assertThat(report.clickableSource()).isEqualTo("taskPath");
        assertThat(report.errorMessage()).isEqualTo(EXCEPTION_MESSAGE);
    }
}

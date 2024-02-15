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

/**
 * An exception type that provides additional context or guidance when errors occur.
 * A suggestion can be either a command that fixes the error e.g. "./gradlew --write-locks" or a reference to
 * a source file where changes need to be applied e.g. "versions.props:3"  (line 3 from versions.props is invalid)
 * The suggestion is intended to be quickly actionable by the user to resolve the error.
 */
public class ExceptionWithSuggestion extends RuntimeException {

    private final String suggestion;

    public ExceptionWithSuggestion(String message, String suggestion) {
        super(message);
        this.suggestion = suggestion;
    }

    public ExceptionWithSuggestion(String message, String suggestion, Throwable throwable) {
        super(message, throwable);
        this.suggestion = suggestion;
    }

    public final String getSuggestion() {
        return suggestion;
    }
}

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
 * An exception type that simply surfaces the given exception message and does not assume the exception
 * has a useful stacktrace, and thus is useful for surfacing errors derived from a subprocess.
 */
public class MinimalException extends RuntimeException {

    public MinimalException(String message) {
        super(message);
    }

    public MinimalException(String message, Throwable throwable) {
        super(message, throwable);
    }
}

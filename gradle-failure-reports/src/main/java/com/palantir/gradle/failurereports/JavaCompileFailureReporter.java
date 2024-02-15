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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.tasks.compile.JavaCompile;

public final class JavaCompileFailureReporter extends CompileFailureReporter<JavaCompile> {

    private static final Pattern COMPILE_ERROR_FIRST_LINE_PATTERN =
            Pattern.compile("^(?<sourcePath>.*):(?<lineNumber>\\d+): (?<errorMessage>error: .*)$");
    private static final Pattern COMPILE_ERROR_LAST_LINE_PATTERN = Pattern.compile("^\\d+ error(s)*$");

    private static final Pattern MULTILINE_COMPILE_ERROR_LAST_LINE_PATTERN = Pattern.compile("(?m)^\\d+ error(s)*$");
    private static final Pattern MULTILINE_COMPILE_ERROR_PATTERN =
            Pattern.compile("(?m)^(?<sourcePath>.*):(?<lineNumber>\\d+): (?<errorMessage>error: (.|\\s)*)");

    @Override
    public StandardOutputListener getErrorOutputListener(JavaCompile javaCompileTask) {
        return new StandardOutputListener() {

            private boolean startedErrorMessage = false;

            @Override
            public void onOutput(CharSequence charSequence) {
                Matcher firstCompileErrorMatcher = COMPILE_ERROR_FIRST_LINE_PATTERN.matcher(charSequence);
                if (firstCompileErrorMatcher.matches()
                        && taskHasSourcePath(javaCompileTask, firstCompileErrorMatcher.group("sourcePath"))) {
                    startedErrorMessage = true;
                    compilerErrorsByTaskPath()
                            .computeIfAbsent(javaCompileTask.getPath(), _k -> new StringBuilder())
                            .append(charSequence);
                } else if (startedErrorMessage) {
                    compilerErrorsByTaskPath()
                            .computeIfAbsent(javaCompileTask.getPath(), _k -> new StringBuilder())
                            .append(charSequence);
                    Matcher lastCompileErrorMessage = COMPILE_ERROR_LAST_LINE_PATTERN.matcher(charSequence);
                    if (lastCompileErrorMessage.matches()) {
                        startedErrorMessage = false;
                        compilerErrorsByTaskPath()
                                .computeIfAbsent(javaCompileTask.getPath(), _k -> new StringBuilder())
                                .append("\n");
                    }
                }
            }
        };
    }

    @Override
    public Pattern getCompileErrorPattern() {
        return MULTILINE_COMPILE_ERROR_PATTERN;
    }

    @Override
    public Pattern getMultipleErrorsSplitterPattern() {
        return MULTILINE_COMPILE_ERROR_LAST_LINE_PATTERN;
    }
}

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
import org.gradle.api.tasks.scala.ScalaCompile;

public final class ScalaCompileFailureReporter extends CompileFailureReporter<ScalaCompile> {

    private static final Pattern COMPILE_ERROR_PATTERN = Pattern.compile(
            "\\[Error\\] (?<sourcePath>.*):(?<lineNumber>\\d+):(?<columnNumber>\\d+): (?<errorMessage>.*)$");

    @Override
    public StandardOutputListener getErrorOutputListener(ScalaCompile scalaCompileTask) {
        return charSequence -> {
            Matcher matcher = getCompileErrorPattern().matcher(charSequence);
            if (!matcher.matches() || !taskHasSourcePath(scalaCompileTask, matcher.group("sourcePath"))) {
                return;
            }
            compilerErrorsByTaskPath()
                    .computeIfAbsent(scalaCompileTask.getPath(), _k -> new StringBuilder())
                    .append(charSequence + "\n");
        };
    }

    @Override
    public Pattern getCompileErrorPattern() {
        return COMPILE_ERROR_PATTERN;
    }

    @Override
    public Pattern getMultipleErrorsSplitterPattern() {
        return Pattern.compile("\n");
    }
}

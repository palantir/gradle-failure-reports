/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.gradle.failurereports.handlers;

import com.google.common.base.Throwables;
import java.io.IOException;
import java.util.Arrays;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.Exec;

public final class ExecCommandNotFoundJava21 implements FailureHandler {

    private static Logger log = Logging.getLogger(ExecCommandNotFoundJava21.class);

    @Override
    public void handle(Task task, Throwable throwable) {
        if (task instanceof Exec) {
            Throwable rootCause = Throwables.getRootCause(throwable);
            if (rootCause instanceof IOException && rootCause.getMessage().contains("No such file or directory")) {
                String firstCommand = ((Exec) task).getCommandLine().get(0);
                String message = String.format(
                        """
                                Failed to run `%s`.
                                A Gradle Bug (https://github.com/gradle/gradle/issues/10483) when running\
                                 an Exec task on the Daemon Java version == 21 on macos prevents the executable `%s` to\
                                 be found in the PATH.
                                In order to fix this error please migrate all Exec tasks to BetterExec:\
                                 https://github.com/palantir/better-exec/tree/develop?tab=readme-ov-file#usage.""",
                        task.getPath(), firstCommand);
                int maxLineSize = Arrays.stream(message.split("\n"))
                        .mapToInt(String::length)
                        .max()
                        .orElseThrow();
                String headerFooter = "*".repeat(maxLineSize);
                log.error(String.join("\n", headerFooter, message, headerFooter));
                throw new GradleException(message);
            }
        }
    }
}

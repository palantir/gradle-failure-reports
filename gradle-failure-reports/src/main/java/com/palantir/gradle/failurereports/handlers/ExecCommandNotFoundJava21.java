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
                String command = ((Exec) task).getCommandLine().get(0);
                boolean commandFound = commandExists(command);
                String messageIfCommandExists = commandFound
                        ? "The command `%s` is present in the PATH, but executing the task via Gradle does not"
                                + "recognize it."
                        : "";
                String message = String.format(
                        """
                            Execution of `%s` failed.

                            %s

                            This issue might occur due to a Gradle bug (https://github.com/gradle/gradle/issues/10483)
                            when running an Exec task on the Daemon Java version 21 on macOS, which prevents the
                            executable `%s` from being located in the PATH.

                            To determine if the issue is related to Gradle, execute the following command in bash:
                                `%s`
                            If the command completes successfully, it indicates that the Gradle bug is responsible.

                            To resolve this issue, please migrate the Exec task `%s` to BetterExec:
                            https://github.com/palantir/better-exec/tree/develop?tab=readme-ov-file#usage.""",
                        task.getPath(), messageIfCommandExists, command, command, task.getName());
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

    public static boolean commandExists(String commandName) {
        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", "command -v " + commandName);
            Process process = builder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}

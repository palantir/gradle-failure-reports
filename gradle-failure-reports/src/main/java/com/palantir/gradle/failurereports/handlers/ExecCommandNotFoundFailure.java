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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record ExecCommandNotFoundFailure(String taskPath, String missingCommand, boolean commandFoundOnPath) {

    public static String renderMessage(List<ExecCommandNotFoundFailure> failures) {
        String taskPaths = failures.stream()
                .map(ExecCommandNotFoundFailure::taskPath)
                .distinct()
                .collect(Collectors.joining(", "));
        String executables = failures.stream()
                .map(ExecCommandNotFoundFailure::missingCommand)
                .distinct()
                .collect(Collectors.joining(", "));
        String commandsToRun = failures.stream()
                .map(ExecCommandNotFoundFailure::missingCommand)
                .distinct()
                .map(command -> String.format("\t- %s", command))
                .collect(Collectors.joining("\n"));
        String tasksToRun = failures.stream()
                .map(ExecCommandNotFoundFailure::taskPath)
                .distinct()
                .collect(Collectors.joining(" "));
        Set<String> foundCommandsOnPath = failures.stream()
                .filter(ExecCommandNotFoundFailure::commandFoundOnPath)
                .map(ExecCommandNotFoundFailure::missingCommand)
                .collect(Collectors.toSet());
        String foundCommandsMessage = foundCommandsOnPath.isEmpty()
                ? ""
                : String.format(
                        "The command(s) `%s` are present in the PATH, but executing the task via Gradle does not"
                                + "recognize it.",
                        String.join(", ", foundCommandsOnPath));
        return String.format("""
            Execution of `%s` failed.
            %s
            This issue might occur due to a Gradle bug (https://github.com/gradle/gradle/issues/10483)
            when running an Exec task on the Daemon Java version 21 on macOS, which prevents the
            executable(s) `%s` from being located in the PATH.

            To determine if the issue is related to Gradle, execute the following commands in bash:
            %s
            If the command completes successfully, it indicates that the Gradle bug is responsible.

            A quick workaround is running:
                - `./gradlew --stop` to stop all the Gradle Daemons
                - `./gradlew %s` from the *terminal* (NOT through Intellij)

            To resolve this issue, please migrate the Exec tasks and ExecSpecs usages to BetterExec:
            https://github.com/palantir/better-exec/tree/develop?tab=readme-ov-file#usage.\
            """, taskPaths, foundCommandsMessage, executables, commandsToRun, tasksToRun);
    }
}

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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public final class ExecCommandNotFoundHandler {

    private static Logger log = Logging.getLogger(ExecCommandNotFoundHandler.class);

    private static final Pattern NO_SUCH_COMMAND = Pattern.compile("Cannot run program \"(.+)\" .*");

    public Optional<ExecCommandNotFoundFailure> handle(Task task, Throwable throwable) {
        Optional<String> command = maybeGetMissingCommand(throwable);
        if (command.isEmpty()) {
            return Optional.empty();
        }
        boolean commandFound = commandExists(command.get());
        return Optional.of(new ExecCommandNotFoundFailure(task.getPath(), command.get(), commandFound));
    }

    private static Optional<String> maybeGetMissingCommand(Throwable throwable) {
        Throwable rootCause = Throwables.getRootCause(throwable);
        if (!(rootCause instanceof IOException && rootCause.getMessage().contains("No such file or directory"))) {
            return Optional.empty();
        }
        return Throwables.getCausalChain(throwable).stream()
                .map(error -> {
                    Matcher matcher = NO_SUCH_COMMAND.matcher(error.getMessage());
                    if (matcher.find()) {
                        return Optional.of(matcher.group(1));
                    }
                    return Optional.<String>empty();
                })
                .<String>mapMulti(Optional::ifPresent)
                .findFirst();
    }

    private static boolean commandExists(String commandName) {
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

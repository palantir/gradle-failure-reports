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

package com.palantir.gradle.failurereports.util;

import com.google.common.base.Throwables;
import java.util.stream.Collectors;

@SuppressWarnings("SafeLoggingPropagation")
public final class ThrowableResources {

    public static final String CAUSAL_CHAIN = "* Causal chain is:";
    public static final String EXCEPTION_MESSAGE = "* Full exception is:";

    public static final String FOOTER =
            "This failure report is produced by the `com.palantir.failure-reports plugin`, which retrieves information"
                + " from the exceptions generated by failed tasks. For further details about the failure, refer to the"
                + " stdout logs. Due to a known issue (https://github.com/gradle/gradle/issues/6068), error logs for a"
                + " generic task are not included in the report.\n"
                + "Please report any issues and share your feedback at"
                + " https://github.com/palantir/gradle-failure-reports/issues.";

    public static String formatThrowable(Throwable throwable) {
        String errorMessage = getFormattedErrorMessage(Throwables.getRootCause(throwable));
        return formatThrowableWithMessage(throwable, errorMessage);
    }

    public static String formatThrowableWithMessage(Throwable throwable, String errorMessage) {
        String causalChain = Throwables.getCausalChain(throwable).stream()
                .map(ThrowableResources::printThrowableCause)
                .collect(Collectors.joining("\n"));
        return String.format(
                "%s\n\n%s\n%s\n\n%s\n%s\n\n%s",
                errorMessage,
                CAUSAL_CHAIN,
                causalChain,
                EXCEPTION_MESSAGE,
                Throwables.getStackTraceAsString(throwable),
                FOOTER);
    }

    public static String printThrowableCause(Throwable throwableCause) {
        if (throwableCause.getMessage() == null || throwableCause.getMessage().isEmpty()) {
            return String.format("\t%s", throwableCause.getClass().getCanonicalName());
        }
        return String.format("\t%s: %s", throwableCause.getClass().getCanonicalName(), throwableCause.getMessage());
    }

    private static String getFormattedErrorMessage(Throwable rootCauseThrowable) {
        if (rootCauseThrowable.getMessage() == null
                || rootCauseThrowable.getMessage().isEmpty()) {
            return String.format(
                    "An error occurred, %s exception thrown",
                    rootCauseThrowable.getClass().getCanonicalName());
        }
        return rootCauseThrowable.getMessage();
    }

    private ThrowableResources() {}
}

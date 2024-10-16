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

package com.palantir.gradle.failurereports.common;

import com.google.common.base.Throwables;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public final class FailureReporterResources {

    private static final Integer MAX_TITLE_ERROR_LENGTH = 150;
    private static final String ERROR_SEVERITY = "Error";
    private static final String EMPTY_SPACE = " ";

    public static String getFileName(String fullPath) {
        return Path.of(fullPath).getFileName().toString();
    }

    public static String getPathWithLineNumber(String path, Integer lineNumber) {
        return String.format("%s:%d", path, lineNumber);
    }

    public static String getRelativePathWithLineNumber(Path sourceDir, Path fullFilePath, Integer lineNumber) {
        return getPathWithLineNumber(getRelativePathToProject(sourceDir, fullFilePath), lineNumber);
    }

    private static String getRelativePathToProject(Path projectDir, Path fullFilePath) {
        try {
            return projectDir.relativize(fullFilePath).toString();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                    String.format("Unable to relativize path %s from %s", fullFilePath, projectDir), e);
        }
    }

    public static String getTaskErrorHeader(String path, Throwable throwable) {
        Throwable rootThrowable = Throwables.getRootCause(throwable);
        return getTaskErrorHeader(
                path,
                Optional.ofNullable(rootThrowable.getMessage())
                        .orElseGet(() -> String.format(
                                "%s exception thrown", rootThrowable.getClass().getCanonicalName())));
    }

    public static String getTaskErrorHeader(String taskPath, String errorDescription) {
        return getTaskErrorHeader(taskPath, errorDescription, ERROR_SEVERITY);
    }

    public static String getTaskErrorHeader(String taskPath, String errorDescription, String severity) {
        return String.format(
                "[%s] %s: %s", taskPath, severity.toLowerCase(Locale.ROOT), getTruncatedErrorMessage(errorDescription));
    }

    public static String sourceFileWithErrorMessage(String sourceFile, Integer lineNumber, String errorMessage) {
        return String.format("%s:%d: %s", sourceFile, lineNumber, errorMessage);
    }

    public static String sourceFileWithErrorMessage(
            String sourceFile, Integer lineNumber, String errorMessage, String severity) {
        return String.format("%s:%d: %s: %s", sourceFile, lineNumber, severity, errorMessage);
    }

    private static String getTruncatedErrorMessage(String errorMessage) {
        if (errorMessage.length() > MAX_TITLE_ERROR_LENGTH) {
            // trying not to truncate in the middle of a word
            int truncatedAtIndex = errorMessage.indexOf(EMPTY_SPACE, MAX_TITLE_ERROR_LENGTH);
            int endIndex = truncatedAtIndex < 0 ? MAX_TITLE_ERROR_LENGTH : truncatedAtIndex;
            return errorMessage.substring(0, endIndex) + "...";
        }
        return errorMessage;
    }

    /**
     * Keeps the last @param bytesSize of the fullString.
     * @param fullString the string that should be truncated if it exceeds the bytesSize.
     * @param bytesSize the size in bytes of the fullString that needs to be preserved.
     * @return the truncated string prefixed by `...[truncated]` if it exceeds bytesSize, otherwise the fullString
     */
    public static String keepLastBytesSizeOutput(String fullString, int bytesSize) {
        byte[] bytes = fullString.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > bytesSize) {
            int startIndex = bytes.length - bytesSize;
            return "...[truncated]" + new String(bytes, startIndex, bytesSize, StandardCharsets.UTF_8);
        }
        return fullString;
    }

    private FailureReporterResources() {}
}

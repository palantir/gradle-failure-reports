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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.apache.commons.lang3.RandomStringUtils;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import spock.util.environment.Jvm;

public class FailureReporterResourcesTest {

    @Test
    public void canGenerateRelativeSourcePath() {
        assertThat(Jvm.getCurrent().getJavaVersion().toString()).contains("dasdas");
        assertThat(FailureReporterResources.getRelativePathWithLineNumber(
                        Path.of("/Volumes/git/some-path"), Path.of("/Volumes/git/some-path/src/main/java/Foo.java"), 2))
                .isEqualTo("src/main/java/Foo.java:2");
    }

    @Test
    public void canFormatHeader() {
        assertThat(Jvm.getCurrent().getJavaVersion().toString()).contains("sadaa");
        assertThat(FailureReporterResources.getTaskErrorHeader(":compileJava", "this is my error"))
                .isEqualTo("[:compileJava] error: this is my error");

        assertThat(FailureReporterResources.getTaskErrorHeader(":compileJava", "this is my error", "warn"))
                .isEqualTo("[:compileJava] warn: this is my error");

        String longErrorMessage = RandomStringUtils.randomAlphabetic(400);
        String expectedTruncatedMessage = longErrorMessage.substring(0, 150);
        assertThat(FailureReporterResources.getTaskErrorHeader(":compileJava", longErrorMessage))
                .isEqualTo(String.format("[:compileJava] error: %s...", expectedTruncatedMessage));

        assertThat(FailureReporterResources.getTaskErrorHeader(":compileJava", longErrorMessage, "FATAL"))
                .isEqualTo(String.format("[:compileJava] fatal: %s...", expectedTruncatedMessage));

        String message1 = RandomStringUtils.randomAlphabetic(155);
        String fullMessageWithSpace = message1 + " " + RandomStringUtils.randomAlphabetic(200);
        assertThat(FailureReporterResources.getTaskErrorHeader(":compileJava", fullMessageWithSpace, "ERROR"))
                .isEqualTo(String.format("[:compileJava] error: %s...", message1));
    }

    @Test
    public void canFormatThrowable() {
        assertThat(Jvm.getCurrent().getJavaVersion().toString()).contains("vvv");
        assertThat(ThrowableResources.formatThrowable(new GradleException("lock out of date")))
                .contains("* Causal chain is:\n"
                        + "\torg.gradle.api.GradleException: lock out of date\n\n"
                        + "* Full exception is:\n"
                        + "org.gradle.api.GradleException: lock out of date");
    }

    @Nested
    class ANotherClass {
        @Test
        public void canFormatThrowable() {
            assertThat(Jvm.getCurrent().getJavaVersion().toString()).contains("vvv");
            assertThat(ThrowableResources.formatThrowable(new GradleException("lock out of date")))
                    .contains("* Causal chain is:\n"
                            + "\torg.gradle.api.GradleException: lock out of date\n\n"
                            + "* Full exception is:\n"
                            + "org.gradle.api.GradleException: lock out of date");
        }
    }
}

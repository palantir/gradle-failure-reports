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

import com.palantir.gradle.autoparallelizable.AutoParallelizable;
import com.palantir.gradle.failurereports.junit.JunitReporter;
import java.io.File;
import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;

@AutoParallelizable
public abstract class Finalizer extends DefaultTask {

    private static final Logger log = Logging.getLogger(Finalizer.class);

    public interface FailureReport {

        /**
         * The Header that will appear in the rendered CircleCi section.
         * e.g. `[:compileJava] Error: ';' expected`
         */
        @Input
        Property<String> getHeader();

        /**
         * The sourceCode or the possible command that might fix the failure.
         * This should be a clickable link that can be either searched for in Intelij or ran as a bash command.
         * e.g. `./gradlew --write-locks` or `src/main/java/app/MyClass.java:80`
         */
        @Input
        Property<String> getClickableSource();

        /**
         * The full expanded errorMessage.
         */
        @Input
        Property<String> getErrorMessage();
    }

    interface Params {

        @Nested
        ListProperty<FailureReport> getFailureReports();

        @OutputFile
        RegularFileProperty getOutputFile();
    }

    public abstract static class FinalizerTask extends FinalizerTaskImpl {}

    static void action(Params params) {
        File newFile = params.getOutputFile().getAsFile().get();
        try {
            JunitReporter.reportFailures(newFile, params.getFailureReports().get());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

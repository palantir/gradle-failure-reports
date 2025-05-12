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

import com.palantir.gradle.failurereports.FailureReportsRootPlugin;
import com.palantir.gradle.utils.environmentvariables.EnvironmentVariables;
import java.util.Optional;
import org.gradle.api.Project;
import org.gradle.util.GradleVersion;

public final class PluginResources {

    private static final String CIRCLE_NODE_INDEX = "CIRCLE_NODE_INDEX";
    private static final Integer INITIAL_CIRCLE_NODE = 0;

    public static boolean isRunningOnInitialCircleNode(Project project) {
        EnvironmentVariables environmentVariables = project.getObjects().newInstance(EnvironmentVariables.class);
        if (!environmentVariables.isCi().get()) {
            return false;
        }
        return maybeGetCircleNode(environmentVariables)
                // when parallelism is configured, we only run on the first node.
                .map(circleNode -> circleNode.equals(INITIAL_CIRCLE_NODE))
                // even without parallelism this node always exists.
                .orElse(true);
    }

    public static boolean canUseFlowActions(Project project) {
        return GradleVersion.version(project.getGradle().getGradleVersion())
                        .compareTo(FailureReportsRootPlugin.GRADLE_FLOW_ACTIONS_ENABLED)
                >= 0;
    }

    public static boolean isRunningLocally(Project project) {
        EnvironmentVariables environmentVariables = project.getObjects().newInstance(EnvironmentVariables.class);
        return !environmentVariables.isCi().get();
    }

    private static Optional<Integer> maybeGetCircleNode(EnvironmentVariables environmentVariables) {
        return Optional.ofNullable(environmentVariables
                        .envVarOrFromTestingProperty(CIRCLE_NODE_INDEX)
                        .getOrNull())
                .map(Integer::parseInt);
    }

    private PluginResources() {}
}

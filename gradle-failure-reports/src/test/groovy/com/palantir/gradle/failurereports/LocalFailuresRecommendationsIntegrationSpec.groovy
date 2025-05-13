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

package com.palantir.gradle.failurereports

import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import org.assertj.core.util.Throwables

class LocalFailuresRecommendationsIntegrationSpec extends IntegrationSpec {
    
    def '#gradleVersionNumber: reproduce gradle/issues/10483'() {
        setup:
        gradleVersion = gradleVersionNumber
        // language=gradle
        buildFile << '''
            apply plugin: 'com.palantir.failure-reports'
        '''.stripIndent(true)

        file('gradle.properties') << """
            __TESTING = true
            __TESTING_CI = false
        """.stripIndent(true)

        //language=Groovy
        addSubproject("myProject", '''import org.gradle.api.DefaultTask
            apply plugin: 'java'
            
            tasks.register("myExecTask", Exec.class, task -> {
                task.setCommandLine("non-existing")
            })
            
            tasks.register("execSpecTask", DefaultTask.class, task -> {
                doFirst {
                    project.exec {
                      executable = "non-existing-execSpec"
                    }
                }
            })
        '''.stripIndent(true))
        when:
        ExecutionResult result = runTasksWithFailure('myExecTask', 'execSpecTask', '--continue')

        then:
        result.failure.causes.size() == 3
        result.standardError.contains("Execution of `:myProject:myExecTask, :myProject:execSpecTask` failed.")
        result.standardError.contains("gradle/issues/10483")
        result.standardError.contains("`./gradlew :myProject:myExecTask :myProject:execSpecTask` from the *terminal*")
        result.standardError.contains("- non-existing")
        result.standardError.contains("- non-existing-execSpec")

        where:
        gradleVersionNumber << List.of("8.8")
    }
}

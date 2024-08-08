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

package com.palantir.gradle.failurereports.junit;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import com.palantir.gradle.failurereports.util.ImmutablesStyle;
import java.util.List;
import org.immutables.value.Value;
import org.immutables.value.Value.Default;

/**
 * The minimal JUNIT XML format for failures that can be rendered in CircleCi in the `Test` section.
 * see: https://www.ibm.com/docs/en/developer-for-zos/14.1?topic=formats-junit-xml-format.
 */
@ImmutablesStyle
@Value.Immutable
@JsonSerialize(as = ImmutableTestSuites.class)
@JsonDeserialize(as = ImmutableTestSuites.class)
@JacksonXmlRootElement(localName = "testsuites")
public interface TestSuites {

    @JsonProperty("testsuite")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<TestSuite> testSuite();

    static Builder builder() {
        return new Builder();
    }

    final class Builder extends ImmutableTestSuites.Builder {}

    @ImmutablesStyle
    @Value.Immutable
    @JsonSerialize(as = ImmutableTestSuite.class)
    @JsonDeserialize(as = ImmutableTestSuite.class)
    interface TestSuite {

        @JacksonXmlProperty(isAttribute = true)
        String name();

        @JacksonXmlProperty(isAttribute = true)
        Integer tests();

        @JsonProperty("testcase")
        @JacksonXmlElementWrapper(useWrapping = false)
        List<TestCase> testcases();

        static Builder builder() {
            return new Builder();
        }

        final class Builder extends ImmutableTestSuite.Builder {}

        @ImmutablesStyle
        @Value.Immutable
        @JsonSerialize(as = ImmutableTestCase.class)
        @JsonDeserialize(as = ImmutableTestCase.class)
        interface TestCase {

            @JacksonXmlProperty(isAttribute = true)
            String name();

            @JacksonXmlProperty(isAttribute = true)
            @JsonProperty("classname")
            String className();

            @JacksonXmlProperty(isAttribute = true)
            Long time();

            Failure failure();

            @ImmutablesStyle
            @Value.Immutable
            @JsonSerialize(as = ImmutableFailure.class)
            @JsonDeserialize(as = ImmutableFailure.class)
            interface Failure {

                @JacksonXmlProperty(isAttribute = true)
                String message();

                @Default
                @JacksonXmlProperty(isAttribute = true)
                default String type() {
                    return "ERROR";
                }

                @JacksonXmlText
                String value();

                static Builder builder() {
                    return new Builder();
                }

                final class Builder extends ImmutableFailure.Builder {}
            }

            static Builder builder() {
                return new Builder();
            }

            final class Builder extends ImmutableTestCase.Builder {}
        }
    }
}

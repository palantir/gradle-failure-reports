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

package com.palantir.gradle.failurereports.checkstyle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.palantir.gradle.failurereports.util.ImmutablesStyle;
import java.util.List;
import org.immutables.value.Value;

/**
 * A minimal format for the output of the {@link CheckstyleOutput} gradle tasks (eg. build/checkstyle/main.xml)
 * see: https://github.com/checkstyle/checkstyle/blob/253430250fe784998d1b9d8a1c1b54c452751154/
 * src/main/java/com/puppycrawl/tools/checkstyle/XMLLogger.java#L157.
 */
@ImmutablesStyle
@Value.Immutable
@JsonSerialize(as = ImmutableCheckstyleOutput.class)
@JsonDeserialize(as = ImmutableCheckstyleOutput.class)
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "checkstyle")
public interface CheckstyleOutput {

    @JsonProperty("file")
    @JacksonXmlElementWrapper(useWrapping = false)
    List<File> files();

    static CheckstyleOutput.Builder builder() {
        return new CheckstyleOutput.Builder();
    }

    final class Builder extends ImmutableCheckstyleOutput.Builder {}

    @ImmutablesStyle
    @Value.Immutable
    @JsonSerialize(as = ImmutableFile.class)
    @JsonDeserialize(as = ImmutableFile.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    interface File {

        @JacksonXmlProperty(isAttribute = true)
        String name();

        @JsonProperty("error")
        @JacksonXmlElementWrapper(useWrapping = false)
        List<FileError> errors();

        static Builder builder() {
            return new Builder();
        }

        final class Builder extends ImmutableFile.Builder {}

        @ImmutablesStyle
        @Value.Immutable
        @JsonSerialize(as = ImmutableFileError.class)
        @JsonDeserialize(as = ImmutableFileError.class)
        @JsonIgnoreProperties(ignoreUnknown = true)
        interface FileError {

            @JacksonXmlProperty(isAttribute = true)
            Integer line();

            @JacksonXmlProperty(isAttribute = true)
            String severity();

            @JacksonXmlProperty(isAttribute = true)
            String message();

            static Builder builder() {
                return new Builder();
            }

            final class Builder extends ImmutableFileError.Builder {}
        }
    }
}

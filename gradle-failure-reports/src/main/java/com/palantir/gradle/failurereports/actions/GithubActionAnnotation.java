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

package com.palantir.gradle.failurereports.actions;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.palantir.gradle.failurereports.util.ImmutablesStyle;
import org.immutables.value.Value;

@ImmutablesStyle
@Value.Immutable
@JsonSerialize(as = ImmutableGithubActionAnnotation.class)
@JsonDeserialize(as = ImmutableGithubActionAnnotation.class)
public interface GithubActionAnnotation {

    String file();

    int line();

    String title();

    String message();

    String severity();

    default String logAsMessage() {
        return String.format("::%s file=%s,line=%d,title=%s::%s", severity(), file(), line(), title(), message());
    }

    static GithubActionAnnotation.Builder builder() {
        return new GithubActionAnnotation.Builder();
    }

    final class Builder extends ImmutableGithubActionAnnotation.Builder {}
}

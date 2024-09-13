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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@ImmutablesStyle
@Value.Immutable
@JsonSerialize(as = ImmutableFailureReport.class)
@JsonDeserialize(as = ImmutableFailureReport.class)
public interface FailureReport {

    String header();

    String clickableSource();

    String errorMessage();

    static FailureReport.Builder builder() {
        return new FailureReport.Builder();
    }

    final class Builder extends ImmutableFailureReport.Builder {}
}

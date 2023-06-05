/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

<<<<<<<< HEAD:subprojects/tooling-api/src/main/java/org/gradle/tooling/events/problems/internal/package-info.java
/**
 * Internal problems specisficx.
 **/

@NonNullApi
package org.gradle.tooling.events.problems.internal;
|||||||| parent of 878fd58eb59 (Use BuildOperationProgressEventEmitter add all the wiring.):subprojects/tooling-api/src/main/java/org/gradle/tooling/internal/consumer/DefaultProblem.java
package org.gradle.tooling.internal.consumer;
========
package org.gradle.tooling.internal.protocol;
>>>>>>>> 878fd58eb59 (Use BuildOperationProgressEventEmitter add all the wiring.):subprojects/tooling-api/src/main/java/org/gradle/tooling/internal/protocol/InternalProblemEvent.java

import org.gradle.api.NonNullApi;
<<<<<<<< HEAD:subprojects/tooling-api/src/main/java/org/gradle/tooling/events/problems/internal/package-info.java
|||||||| parent of 878fd58eb59 (Use BuildOperationProgressEventEmitter add all the wiring.):subprojects/tooling-api/src/main/java/org/gradle/tooling/internal/consumer/DefaultProblem.java
import org.gradle.tooling.Problem;

import java.util.Map;
@NonNullApi
public class DefaultProblem implements Problem {

    private final Map<String, String> rawAttributes;

    public DefaultProblem(Map<String, String> rawAttributes) {
        this.rawAttributes = rawAttributes;
    }

    @Override
    public Map<String, String> getRawAttributes() {
        return rawAttributes;
    }
}
========
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;

import java.util.Map;


/**
 * implements org.gradle.tooling.Problem
 */
@NonNullApi
public interface InternalProblemEvent extends InternalProgressEvent {

    Map<String, String> getRawAttributes();
}
>>>>>>>> 878fd58eb59 (Use BuildOperationProgressEventEmitter add all the wiring.):subprojects/tooling-api/src/main/java/org/gradle/tooling/internal/protocol/InternalProblemEvent.java

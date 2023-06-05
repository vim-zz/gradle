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
<<<<<<<< HEAD:subprojects/tooling-api/src/main/java/org/gradle/tooling/events/problems/ProblemEvent.java
package org.gradle.tooling.events.problems;
|||||||| parent of 878fd58eb59 (Use BuildOperationProgressEventEmitter add all the wiring.):subprojects/tooling-api/src/main/java/org/gradle/tooling/Problem.java
package org.gradle.tooling;
========

package org.gradle.tooling.events.problems;
>>>>>>>> 878fd58eb59 (Use BuildOperationProgressEventEmitter add all the wiring.):subprojects/tooling-api/src/main/java/org/gradle/tooling/events/problems/ProblemDescriptor.java

import org.gradle.api.Incubating;
<<<<<<<< HEAD:subprojects/tooling-api/src/main/java/org/gradle/tooling/events/problems/ProblemEvent.java
import org.gradle.api.NonNullApi;
import org.gradle.tooling.events.ProgressEvent;
|||||||| parent of 878fd58eb59 (Use BuildOperationProgressEventEmitter add all the wiring.):subprojects/tooling-api/src/main/java/org/gradle/tooling/Problem.java
import org.gradle.api.NonNullApi;

import java.util.Map;
========
import org.gradle.tooling.events.OperationDescriptor;

import java.util.Map;
>>>>>>>> 878fd58eb59 (Use BuildOperationProgressEventEmitter add all the wiring.):subprojects/tooling-api/src/main/java/org/gradle/tooling/events/problems/ProblemDescriptor.java

/**
 * Describes a problem operation.
 *
 * @since 8.4
 */
@Incubating
<<<<<<<< HEAD:subprojects/tooling-api/src/main/java/org/gradle/tooling/events/problems/ProblemEvent.java
public interface ProblemEvent extends ProgressEvent {
    @Override
    ProblemDescriptor getDescriptor();
|||||||| parent of 878fd58eb59 (Use BuildOperationProgressEventEmitter add all the wiring.):subprojects/tooling-api/src/main/java/org/gradle/tooling/Problem.java
public interface Problem {

    Map<String, String> getRawAttributes();
========
public interface ProblemDescriptor extends OperationDescriptor {
    Map<String, String> getRawAttributes();
>>>>>>>> 878fd58eb59 (Use BuildOperationProgressEventEmitter add all the wiring.):subprojects/tooling-api/src/main/java/org/gradle/tooling/events/problems/ProblemDescriptor.java
}

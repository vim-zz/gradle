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

package org.gradle.api.internal.tasks.compile

import groovy.transform.CompileStatic
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.problems.interfaces.ProblemGroup
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.problems.ProblemDescriptor
import org.gradle.tooling.events.problems.internal.DefaultProblemsOperationDescriptor

class JdkJavaCompilerCrossVersionTest extends ToolingApiSpecification {

    def "single java compilation failure problem arrives"() {
        buildFile << """
        plugins {
            id 'java'
        }
        """

        file("src/main/java/App.java") << """
            public class App {
               public static void main(String[] args) {
                 nonExistingMethod();
                 System.out.println("App running with " + System.getProperty("java.home"));
               }
            }
        """

        List<ProblemDescriptor> problems = []
        def progressListener = new ProgressListener() {
            @Override
            void statusChanged(ProgressEvent event) {
                def problemDescriptor = event.descriptor as DefaultProblemsOperationDescriptor
                problems += problemDescriptor
            }
        }

        when:
        withConnection {
            it.newBuild()
                .forTasks("compileJava")
                .addProgressListener(progressListener, OperationType.PROBLEMS)
                .run()
        }

        then:
        def compilationProblems = problems.findAll {
            ProblemGroup.JAVA_COMPILATION.id.equals(it.problemGroup)
        }
        compilationProblems.size() == 1
        compilationProblems[0].description == "Compilation failed"
        thrown BuildException
    }

}

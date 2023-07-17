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

package org.gradle.integtests.tooling.r84

import org.gradle.api.problems.interfaces.ProblemGroup
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.ProblemDescriptor
import org.gradle.tooling.events.problems.internal.DefaultProblemsOperationDescriptor

@TargetGradleVersion(">=8.4")
class CompileProblemsCrossVersionTests extends ToolingApiSpecification {

    List<ProblemDescriptor> problems = []

    def progressListener = new ProgressListener() {
        @Override
        void statusChanged(ProgressEvent event) {
            def problemDescriptor = event.descriptor as DefaultProblemsOperationDescriptor
            System.out.println("Problem observed: " + problemDescriptor.dump())
            System.out.flush()
            problems += problemDescriptor
        }
    }

    def javaBuildFile = """
        plugins {
            id 'java'
        }
    """

    def groovyBuildFile = """
        plugins {
            id 'groovy'
        }

        repositories {
            mavenCentral()
        }

        dependencies {
            implementation("org.codehaus.groovy:groovy-all:3.0.18")
        }
    """

    def "single java class with single method-level compilation failure problem arrives"() {
        given:
        buildFile << javaBuildFile
        file("src/main/java/App.java") << """
            public class App {
               public static void main(String[] args) {
                 nonExistingMethod();
               }
            }
        """

        when:
        withConnection {
            it.newBuild()
                .forTasks("compileJava")
                .addProgressListener(progressListener, OperationType.PROBLEMS)
                .run()
        }

        then:
        thrown BuildException
        def compilationProblems = problems.findAll {
            ProblemGroup.COMPILATION.name() == it.problemGroup
        }
        compilationProblems.size() == 1
        verifyAll(compilationProblems[0]) {
            it.message == "Compilation failed"
        }
    }

    def "single java class with double method-level compilation failure problem arrives"() {
        given:
        buildFile << javaBuildFile
        file("src/main/java/App.java") << """
            public class App {
               public static void main(String[] args) {
                 nonExistingMethod1();
                 nonExistingMethod2();
               }
            }
        """

        when:
        withConnection {
            it.newBuild()
                .forTasks("compileJava")
                .addProgressListener(progressListener, OperationType.PROBLEMS)
                .run()
        }

        then:
        thrown BuildException
        def compilationProblems = problems.findAll {
            ProblemGroup.COMPILATION.name() == it.problemGroup
        }
        compilationProblems.size() == 2
        verifyAll(compilationProblems[0]) {
            it.message == "Compilation failed"
        }
        verifyAll(compilationProblems[1]) {
            it.message == "Compilation failed"
        }
    }

}

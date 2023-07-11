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

import org.gradle.api.problems.interfaces.ProblemGroup
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.tooling.BuildException
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.gradle.tooling.events.problems.ProblemDescriptor
import org.gradle.tooling.events.problems.internal.DefaultProblemsOperationDescriptor

@TargetGradleVersion(">=8.3")
class JdkJavaCompilerCrossVersionTest extends ToolingApiSpecification {

    List<ProblemDescriptor> problems = []
    def progressListener = new ProgressListener() {
        @Override
        void statusChanged(ProgressEvent event) {
            def problemDescriptor = event.descriptor as DefaultProblemsOperationDescriptor
            problems += problemDescriptor
        }
    }

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

        when:
        withConnection {
            it.newBuild()
                .forTasks("compileJava")
                .addProgressListener(progressListener, OperationType.PROBLEMS)
                .run()
        }

        then:
        def compilationProblems = problems.findAll {
            ProblemGroup.JAVA_COMPILATION.name() == it.problemGroup
        }
        compilationProblems.size() == 1
        prettyPrintProblem(compilationProblems[0])
        compilationProblems[0].message == "Compilation failed"
        thrown BuildException
    }

    void prettyPrintProblem(ProblemDescriptor problem) {
        def treeFormatter = new TreeFormatter()
        treeFormatter.node(problem.message)
        treeFormatter.startChildren()
        treeFormatter.node("at " + problem.path + ":" + problem.line + " " + problem.column)
        treeFormatter.endChildren()
        println(treeFormatter.toString())
    }

}

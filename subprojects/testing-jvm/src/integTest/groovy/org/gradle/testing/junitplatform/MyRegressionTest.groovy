/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.testing.junitplatform

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class MyRegressionTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/22333")
    @Issue("https://github.com/JetBrains/intellij-community/commit/d41841670c8a98c0464ef25ef490c79b5bafe8a9")
    def "original reproducer"() {
        buildFile << """
            plugins {
                id 'java'
                id 'org.jetbrains.intellij' version '1.9.0'
            }

            intellij {
                pluginName = 'Awesome Plugin'
//                version = '2021.2.3' // Before introduction of LauncherSessionListener service
                version = '2021.3' // After introduction

                // There is a separate issue here with the instrumentation.
                // Ignore this for now and disable instrumentation.
                instrumentCode = false
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation(platform("org.junit:junit-bom:5.9.1"))
                testImplementation 'org.junit.jupiter:junit-jupiter'
                testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine'
                testRuntimeOnly 'org.junit.vintage:junit-vintage-engine'
            }

            test {
                useJUnitPlatform()
                debugOptions.enabled = true
                debugOptions.port = 5005
                debugOptions.suspend = true
            }
        """
        file("src/test/java/com/example/MyTest.java") << "package com.example; class MyTest {}"

        expect:
        succeeds "test"
    }

    def "reproducer without intellij plugin"() {
        settingsFile << "include 'other'"
        file("other/build.gradle") << """
            plugins {
                id 'java'
            }

            // The root of the problem is here. We have a compile-only dependency on junit platform launcher,
            // so when junit attempts to auto-load our LauncherSessionListener, when it loads the implementation
            // we define, it cannot load the base interface. This is what the intelliJ plugin is doing. It does not
            // carry along the launcher dependency with it (it defines it as a PROVIDED dependency), so its not on
            // the classpath of the `JUnitPlatformTestClassProcessor` during the execution of `processAllTestClasses`
            // (and the subsequent call to LauncherFactory.create).

            // To resolve this, we may need to ensure SOME launcher is on the same ClassLoader that the
            // LauncherSessionListener is loaded from. Perhaps we should require that if users want to provide
            // a LauncherSessionListener they need to provide their own launcher. Or, perhaps we should make sure
            // the launcher we add ourselves ends up on the classpath which the user declares their LauncherSessionListener.

            // The real question now is why this worked on Gradle 7.4.2. Did we have a different classpath setup where we loaded
            // the listeners using the classpath which had the launcher?

            // Gradle 7.4.2 used junit platform 1.7.x, while Gradle 7.5.x uses junit platform 1.8.x.
            // 1.8.x introduced teh LauncherSessionListener, which is why we see newer Gradle versions with this issue,
            // since older versions weren't even looking for the listener.

            // This problem is reproducible if we have new enough intelliJ vesion which defines the
            // Listener and a new enough Gradle version which has junit platform 1.8
            dependencies {
                compileOnly 'org.junit.platform:junit-platform-launcher:1.9.1'
            }
        """
        file("src/main/java/com/example/MyLauncherSessionListener.java") << """
            package com.example;
            import org.junit.platform.launcher.LauncherSession;
            import org.junit.platform.launcher.LauncherSessionListener;
            public class MyLauncherSessionListener implements LauncherSessionListener {
                public void launcherSessionOpened(LauncherSession session) {
                    System.out.println("Session opened");
                }
                public void launcherSessionClosed(LauncherSession session) {
                    System.out.println("Session closed");
                }
            }
        """
        file("src/main/resources/META-INF/services/org.junit.platform.launcher.LauncherSessionListener") << """
            com.example.MyLauncherSessionListener
        """

        buildFile << """
            plugins {
                id 'java'
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation project(':other')
            }

//            dependencies {
//                testImplementation(platform("org.junit:junit-bom:5.9.1"))
//                testImplementation 'org.junit.jupiter:junit-jupiter'
//                testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine'
//            }
//            test {
//                useJUnitPlatform()
//                debugOptions.enabled = true
//                debugOptions.port = 5005
//                debugOptions.suspend = true
//            }

            // Turns out it succeeds with test suites since the launcher is on the testRuntimeClasspath, and thus is accessible
            // by the context classloader in LauncherFactory.create.
            // This is cause the testRuntimeClasspath dependencies live in the AppClassLoader, and
            // we let `org.junit` packages leak from the the AppClassLoader to the implementationClassLoader.
            testing.suites.test {
                useJUnitJupiter()
                targets.all {
                    testTask.configure {
                        debugOptions {
                            enabled = true
                            port = 5005
                            suspend = true
                        }
                    }
                }
            }
        """
        file("src/test/java/com/example/MyTest.java") << """
            package com.example;
            public class MyTest {}
        """

        expect:
        succeeds "test"
    }
}

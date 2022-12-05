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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions

class KotlinMultiplatformDisambiguationIntegrationTest extends AbstractIntegrationSpec {
    def "kotlin project can consume kotlin multiplatform java project"() {
        buildKotlinFile << """
            plugins {
                kotlin("jvm") version "${KotlinGradlePluginVersions.latest}"
            }

            dependencies {
                implementation(project(":other"))
            }

            tasks.register("resolve") {
                dependsOn(configurations.getByName("compileClasspath"))
                doLast {
                    println(configurations.getByName("compileClasspath").files())
                }
            }
        """

        settingsFile << "include 'other'"
        file("other/build.gradle.kts") << """
            plugins {
                kotlin("multiplatform")
            }

            kotlin {
                jvm {
                    withJava()
                }
            }
        """

        expect:
        succeeds "resolve"
    }
}

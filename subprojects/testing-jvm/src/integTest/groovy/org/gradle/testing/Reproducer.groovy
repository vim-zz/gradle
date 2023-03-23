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

package org.gradle.testing

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class Reproducer extends AbstractIntegrationSpec {
    def test() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                testImplementation "org.junit.jupiter:junit-jupiter-api:$version"
                testImplementation "org.junit.jupiter:junit-jupiter-params:$version"
                testRuntimeOnly "org.junit.jupiter:junit-jupiter-engine:$version"
            }

            test {
                useJUnitPlatform()
            }
        """

        file("src/test/java/SomeTest.java") << """
            import java.util.stream.Stream;
            import org.junit.jupiter.params.ParameterizedTest;
            import org.junit.jupiter.params.provider.Arguments;
            import org.junit.jupiter.params.provider.MethodSource;

            class SomeTest {
                public static Stream<Arguments> args() {
                    return Stream.of(Arguments.of("blah"));
                }

                @ParameterizedTest
                @MethodSource("args")
                void someTest(String value) { }
            }
        """

        expect:
        succeeds "test"

        where:
        version << ["5.9.2", "5.6.3"]
    }
}

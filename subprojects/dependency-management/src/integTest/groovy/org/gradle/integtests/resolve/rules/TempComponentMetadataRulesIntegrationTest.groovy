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

package org.gradle.integtests.resolve.rules

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TempComponentMetadataRulesIntegrationTest extends AbstractIntegrationSpec {
    def "attributes added to new variants are only added to the specified variant"() {
        buildFile("""
            configurations {
                register("someConfiguration")
            }

            ${mavenCentralRepository()}

            Attribute custom1 = Attribute.of("custom1", String)
            Attribute custom2 = Attribute.of("custom2", String)
            dependencies { DependencyHandler deps ->
                deps.components {
                    all {
                        addVariant("customVariant") { metadata ->
                            // The bug is here. Calling getAttribtues() returns an attributes which affects ALL
                            // variants. Not just the variant we are adding here. `customVariant2` below
                            // will get the custom1 attribute even though it shouldn't.
                            metadata.getAttributes().attribute(custom1, "custom1-value")
                        }
                        addVariant("customVariant2") { metadata ->
                            metadata.attributes {
                               it.attribute(custom2, "custom2-value")
                            }
                        }
                    }
                }

                someConfiguration 'com.google.guava:guava:30.1-jre'
            }
        """)

        when:
        succeeds "dependencyInsight", "--configuration", "someConfiguration", "--dependency", "guava"

        then:
        !result.output.contains("""              - Variant 'customVariant2' capability com.google.guava:guava:30.1-jre:
                  - Unmatched attributes:
                      - Provides custom1 'custom1-value' but the consumer didn't ask for it
                      - Provides custom2 'custom2-value' but the consumer didn't ask for it
                      - Provides org.gradle.status 'release' but the consumer didn't ask for it""")
    }
}

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

    def "reproducer"() {
        given:
        def kotlinVersion = "1.7.10"
        buildFile << """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "$kotlinVersion"
                id("jvm-test-suite")
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                // basic Kotlin dependencies
                implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

                // Kafka dependencies
                implementation("org.apache.kafka:kafka-clients:3.3.1")

                // IBM MQ dependencies
                implementation("com.ibm.mq:com.ibm.mq.allclient:9.1.0.11")
                implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.19.0")
                implementation("org.apache.logging.log4j:log4j-core:2.19.0")
            }

            test {
                testLogging.showStandardStreams = true
//                debug = true
            }

            testing {
                suites {
                    test {
                        def testContainersVersion = "1.17.3"
                        useKotlinTest("$kotlinVersion")
                        dependencies {
                            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
                            implementation("io.kotest:kotest-assertions-core-jvm:5.5.4")
                            implementation("io.kotest:kotest-assertions-json-jvm:5.5.4")
                            implementation("io.kotest.extensions:kotest-assertions-arrow-jvm:1.3.0")
                            implementation("org.testcontainers:testcontainers:\$testContainersVersion")
                            implementation("org.testcontainers:junit-jupiter:\$testContainersVersion")
                            implementation("org.testcontainers:kafka:\$testContainersVersion")
                            implementation("org.testcontainers:toxiproxy:\$testContainersVersion")
                        }
                    }
                }
            }
            """

            file("src/test/kotlin/mq/MQIT.kt") << """
                package mq

                import com.ibm.msg.client.jms.JmsConnectionFactory
                import com.ibm.msg.client.jms.JmsFactoryFactory
                import com.ibm.msg.client.wmq.WMQConstants
                import io.kotest.matchers.ints.shouldBeExactly
                import kotlinx.coroutines.Dispatchers
                import kotlinx.coroutines.runInterruptible
                import kotlinx.coroutines.*
                import org.junit.jupiter.api.*
                import org.testcontainers.containers.GenericContainer
                import org.testcontainers.containers.Network
                import org.testcontainers.containers.ToxiproxyContainer
                import org.testcontainers.junit.jupiter.Container
                import org.testcontainers.junit.jupiter.Testcontainers
                import javax.jms.BytesMessage
                import javax.jms.Destination
                import javax.jms.JMSContext
                import javax.jms.JMSProducer
                import javax.jms.Queue

                class KGenericContainer(imageName: String) : GenericContainer<KGenericContainer>(imageName)

                @Testcontainers
                class MQIT {

                    @Test
                    fun `reproducer`() = runBlocking {
                        if (1 == 1) {
                            throw com.ibm.msg.client.jms.internal.JmsErrorUtils.createException("JMSCC0046", java.util.HashMap<String?, Object?>())
                        }


//                        val connectionFactory = JmsFactoryFactory.getInstance(WMQConstants.WMQ_PROVIDER)
//                            .createConnectionFactory()
//                            .apply { fillFromConfig() }
//
//                        connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE).use { context ->
//                            val body = context.createBytesMessage()
//
////                            if (1==1) {
////                                throw RuntimeException()
////                            }
//
////
////                            // This throws an exception, but also might initialize some resources?
//                            val result = body.bodyLength.toInt()
//                        }
                    }

                    companion object {
                        private const val MQ_PORT = 1414
                        private const val MQ_PASSWORD = "passwd"

                        private val network = Network.newNetwork()

                        @Container
                        private val mqContainer = KGenericContainer("ibmcom/mq:9.1.5.0-r2")
                            .withEnv(
                                mapOf(
                                    "LICENSE" to "accept",
                                    "MQ_APP_PASSWORD" to MQ_PASSWORD
                                )
                            )
                            .withExposedPorts(MQ_PORT)
                            .withNetwork(network)

                        @Container
                        private val proxyContainer = ToxiproxyContainer("shopify/toxiproxy:2.1.0")
                            .withNetwork(network)
                            .withNetworkAliases("toxiProxyAlias")

                        fun JmsConnectionFactory.fillFromConfig() {
                            val proxy = proxyContainer.getProxy(mqContainer, MQ_PORT)
                            setStringProperty(WMQConstants.WMQ_CONNECTION_NAME_LIST, "\${proxy.containerIpAddress}(\${proxy.proxyPort})")
                            setStringProperty(WMQConstants.WMQ_CHANNEL, "DEV.APP.SVRCONN")
                            setStringProperty(WMQConstants.USERID, "app")
                            setStringProperty(WMQConstants.PASSWORD, MQ_PASSWORD)
                            setIntProperty(WMQConstants.WMQ_CONNECTION_MODE, WMQConstants.WMQ_CM_CLIENT)
                        }
                    }
                }
            """

        expect:
        succeeds "test"
    }

    def "reproducer2"() {
        given:
        buildFile << """
            plugins {
                id("java-library")
            }

            repositories {
                mavenCentral()
            }

            test {
//                debug = true
            }

            testing {
                suites {
                    test {
                        useJUnitJupiter()
//                        dependencies {
//                            implementation("com.ibm.mq:com.ibm.mq.allclient:9.1.0.11")
//                        }
                    }
                }
            }
        """

        file("src/test/java/com/example/ExampleTest.java") << """
            package com.example;
            import org.junit.jupiter.api.Test;
            public class ExampleTest {
                @Test
                public void test() throws Exception {
                    throw new CustomException();
//                    throw new com.ibm.msg.client.jms.DetailedMessageNotReadableException("", "", "", "", new java.util.HashMap<>());
                }

                public static class CustomException extends Exception {
                    private Object writeReplace() {
                        return new WriteReplacer();
                    }
                }

                public static final class WriteReplacer implements java.io.Serializable {
                    private Object readResolve() {
                        return new RuntimeException();
                    }
                }
            }
        """

        expect:
        succeeds("test")
    }
}

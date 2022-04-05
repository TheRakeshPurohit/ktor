/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import org.jetbrains.kotlin.gradle.plugin.*
import java.io.*
import java.net.*

description = "Common tests for client"

plugins {
    id("kotlinx-serialization")
}

val osName: String = System.getProperty("os.name")

kotlin.sourceSets {
    commonMain {
        dependencies {
            api(project(":ktor-client:ktor-client-mock"))
            api(project(":ktor-test-dispatcher"))
        }
    }
    commonTest {
        dependencies {
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json:ktor-client-serialization"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-auth"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-encoding"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-content-negotiation"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json"))
            api(project(":ktor-client:ktor-client-plugins:ktor-client-json:ktor-client-serialization"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx:ktor-serialization-kotlinx-json"))
        }
    }
    jvmMain {
        dependencies {
            api(libs.kotlinx.serialization.json)
            api(project(":ktor-network:ktor-network-tls:ktor-network-tls-certificates"))
            api(project(":ktor-server"))
            api(project(":ktor-server:ktor-server-cio"))
            api(project(":ktor-server:ktor-server-netty"))
            api(project(":ktor-server:ktor-server-jetty"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-auth"))
            api(project(":ktor-server:ktor-server-plugins:ktor-server-websockets"))
            api(project(":ktor-shared:ktor-serialization:ktor-serialization-kotlinx"))
            api(libs.logback.classic)
            api(libs.junit)
            api(libs.kotlin.test.junit)
            implementation(libs.kotlinx.coroutines.debug)
        }
    }

    jvmTest {
        dependencies {
            api(project(":ktor-client:ktor-client-apache"))
            runtimeOnly(project(":ktor-client:ktor-client-cio"))
            runtimeOnly(project(":ktor-client:ktor-client-android"))
            runtimeOnly(project(":ktor-client:ktor-client-okhttp"))
            if (currentJdk >= 11) {
                runtimeOnly(project(":ktor-client:ktor-client-java"))
            }
            implementation(project(":ktor-client:ktor-client-plugins:ktor-client-logging"))
            implementation(libs.kotlinx.coroutines.slf4j)
        }
    }

    jsTest {
        dependencies {
            api(project(":ktor-client:ktor-client-js"))
        }
    }

    if (!(rootProject.ext.get("native_targets_enabled") as Boolean)) return@sourceSets

    listOf("linuxX64Test", "mingwX64Test", "macosX64Test").map { getByName(it) }.forEach {
        it.dependencies {
            api(project(":ktor-client:ktor-client-curl"))
        }
    }

    if (!osName.startsWith("Windows")) {
        listOf("linuxX64Test", "macosX64Test", "iosX64Test").map { getByName(it) }.forEach {
            it.dependencies {
                api(project(":ktor-client:ktor-client-cio"))
            }
        }
    }
    listOf("iosX64Test", "macosX64Test", "macosArm64Test").map { getByName(it) }.forEach {
        it.dependencies {
            api(project(":ktor-client:ktor-client-darwin"))
        }
    }
}

val testTasks = mutableListOf(
    "jvmTest",

    // 1.4.x JS tasks
    "jsLegacyNodeTest",
    "jsIrNodeTest",
    "jsLegacyBrowserTest",
    "jsIrBrowserTest",

    "posixTest",
    "darwinTest",
    "macosX64Test",
    "macosArm64Test",
    "linuxX64Test",
    "iosX64Test",
    "mingwX64Test"
)

afterEvaluate {
    val kotlinCompilation = kotlin.targets.getByName("jvm").compilations["test"]
    val classpath = (kotlinCompilation as KotlinCompilationToRunnableFiles<*>).runtimeDependencyFiles
        .map { file -> file.toURI().toURL() }.toTypedArray()

    val testServer = gradle.sharedServices.registerIfAbsent("ktor-test-server", KtorTestServer::class.java) {
        parameters {
            serverClasspath.set(classpath)
            main.set("io.ktor.client.tests.utils.TestServerKt")
        }
    }

    rootProject.allprojects {
        if (!path.contains("ktor-client") || path.contains("ktor-shared")) return@allprojects

        val tasks = tasks.matching { it.name in testTasks }
        configure(tasks) {
            usesService(testServer)

            doFirst {
                testServer.get()
            }
        }
    }
}

useJdkVersionForJvmTests(11)

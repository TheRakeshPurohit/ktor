/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

description = "I18n support to Ktor"

plugins {
    id("ktorbuild.project.server-plugin")
}

kotlin {
    sourceSets {
        jvmTest {
            dependencies {
                implementation(project(":ktor-server:ktor-server-plugins:ktor-server-status-pages"))
            }
        }
    }
}

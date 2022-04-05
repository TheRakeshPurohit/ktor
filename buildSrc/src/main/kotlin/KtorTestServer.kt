import org.gradle.api.provider.*
import org.gradle.api.services.*
import java.io.*
import java.net.*
import javax.inject.*

/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

abstract class KtorTestServer : BuildService<KtorTestServer.Params>, AutoCloseable {

    interface Params : BuildServiceParameters {
        val serverClasspath: Property<Array<URL>>
        val main: Property<String>
    }

    @Volatile
    private var counter = 0

    private var server: Closeable? = null

    init {
        start()
    }

    override fun close() {
        stop()
    }

    private fun start() {
        val current = ++counter
        if (current == 1) {
            startServer()
        }
        println("Starting server $current")
    }

    private fun stop() {
        val current = --counter
        println("Stopping server $current")
        if (current == 0) {
            stopServer()
        }
    }

    private fun startServer() {
        try {
            println("[TestServer] start")
            val loader = URLClassLoader(parameters.serverClasspath.get(), ClassLoader.getSystemClassLoader())

            val mainClass = loader.loadClass(parameters.main.get())
            val main = mainClass.getMethod("startServer")
            server = main.invoke(null) as Closeable
            println("[TestServer] started")
        } catch (cause: Throwable) {
            println("[TestServer] failed: ${cause.message}")
            cause.printStackTrace()
        }
    }

    private fun stopServer() {
        server?.close()
        server = null
        println("[TestServer] stopped")
    }
}

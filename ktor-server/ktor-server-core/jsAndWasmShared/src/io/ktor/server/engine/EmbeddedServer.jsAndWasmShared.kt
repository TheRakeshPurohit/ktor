/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.events.*
import io.ktor.events.EventDefinition
import io.ktor.server.application.*
import io.ktor.server.engine.internal.*
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.*

public actual class EmbeddedServer<
    TEngine : ApplicationEngine,
    TConfiguration : ApplicationEngine.Configuration
    >
actual constructor(
    rootConfig: ServerConfig,
    engineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
    engineConfigBlock: TConfiguration.() -> Unit
) {
    public actual val monitor: Events = Events()

    public actual val environment: ApplicationEnvironment = rootConfig.environment

    public actual val engineConfig: TConfiguration = engineFactory.configuration(engineConfigBlock)

    public actual val application: Application = Application(
        environment,
        rootConfig.developmentMode,
        rootConfig.rootPath,
        monitor,
        rootConfig.parentCoroutineContext,
        ::engine
    )

    public actual val engine: TEngine = engineFactory.create(
        environment,
        monitor,
        rootConfig.developmentMode,
        engineConfig,
        ::application
    )

    private val modules = rootConfig.modules
    private val serverScope = CoroutineScope(rootConfig.parentCoroutineContext + Dispatchers.Default)

    @OptIn(DelicateCoroutinesApi::class)
    private fun prepareToStart() {
        safeRaiseEvent(ApplicationStarting, application)
        try {
            modules.forEach { application.it() }
            monitor.raise(ApplicationStarted, application)
        } catch (cause: Throwable) {
            environment.log.error("Failed to start application.", cause)
            serverScope.launch { destroy(application) }
            throw cause
        }

        CoroutineScope(application.coroutineContext).launch {
            engine.resolvedConnectors().forEach {
                val host = escapeHostname(it.host)
                environment.log.info(
                    "Responding at ${it.type.name.lowercase()}://$host:${it.port}"
                )
            }
        }
    }

    public actual fun start(wait: Boolean): EmbeddedServer<TEngine, TConfiguration> {
        addShutdownHook { stop() }
        prepareToStart()
        engine.start(wait)
        return this
    }

    @OptIn(DelicateCoroutinesApi::class)
    public actual suspend fun startSuspend(wait: Boolean): EmbeddedServer<TEngine, TConfiguration> {
        addShutdownHook { serverScope.launch { stopSuspend() } }
        prepareToStart()
        engine.startSuspend(wait)
        return this
    }

    @OptIn(DelicateCoroutinesApi::class)
    public actual fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        engine.stop(gracePeriodMillis, timeoutMillis)
        serverScope.launch { destroy(application) }
    }

    public actual suspend fun stopSuspend(gracePeriodMillis: Long, timeoutMillis: Long) {
        engine.stopSuspend(gracePeriodMillis, timeoutMillis)
        destroy(application)
    }

    @OptIn(InternalAPI::class)
    private suspend fun destroy(application: Application) {
        safeRaiseEvent(ApplicationStopping, application)
        try {
            application.disposeAndJoin()
        } catch (e: Throwable) {
            environment.log.error("Failed to destroy application instance.", e)
        }
        safeRaiseEvent(ApplicationStopped, application)
    }

    private fun safeRaiseEvent(event: EventDefinition<Application>, application: Application) {
        try {
            monitor.raise(event, application)
        } catch (cause: Throwable) {
            environment.log.error("One or more of the handlers thrown an exception", cause)
        }
    }
}

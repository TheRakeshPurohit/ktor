/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.server.plugins.di.annotations.*
import io.ktor.util.reflect.*
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.jvm.jvmErasure

/**
 * Provides the default reflection behavior for the JVM platform, relying on the standard library
 * calls for inferring which constructors to use, and how to evaluate the parameters as dependency keys.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyReflectionJvm)
 */
public open class DependencyReflectionJvm : DependencyReflection {

    override suspend fun <T : Any> create(kClass: KClass<T>, init: suspend (DependencyKey) -> Any): T {
        if (kClass.isAbstract || kClass.java.isInterface) {
            throw DependencyAbstractTypeConstructionException(kClass.qualifiedName ?: "<unknown>")
        }

        val constructors = findConstructors(kClass)
        var lastFailure: Throwable? = null

        // Try to create using available constructors
        val instanceFromConstructors = constructors.firstNotNullOfOrNull { constructor ->
            runCatching {
                constructor.callSuspendBy(
                    mapParameters(constructor.parameters) { param ->
                        init(toDependencyKey(param))
                    }
                )
            }.onFailure {
                lastFailure = it
            }.getOrNull()
        }

        // Throw if we were unable to create a new instance
        return instanceFromConstructors
            ?: throw DependencyInjectionException(
                "No suitable constructor for type: ${kClass.qualifiedName}",
                lastFailure
            )
    }

    /**
     * List constructors of a class in order of preference.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyReflectionJvm.findConstructors)
     */
    public open fun <T : Any> findConstructors(kClass: KClass<T>): Collection<KFunction<T>> =
        kClass.constructors

    /**
     * Maps a parameter to a [DependencyKey].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyReflectionJvm.toDependencyKey)
     */
    public open fun toDependencyKey(parameter: KParameter): DependencyKey {
        val name = parameter.findAnnotations(Named::class).singleOrNull()?.value?.takeIf { it.isNotBlank() }
        val property = parameter.findAnnotations(Property::class).singleOrNull()?.value?.takeIf { it.isNotBlank() }
        if (name != null && property != null) {
            throw DependencyInjectionException("Parameter cannot be annotated with both @Named and @Property")
        }

        return DependencyKey(
            type = TypeInfo(parameter.type.jvmErasure, parameter.type),
            name = name ?: property,
            qualifier = PropertyQualifier.takeIf { property != null }
        )
    }

    /**
     * Resolves the list of parameters from the provided resolve function.
     *
     * When optional or nullable, failures to retrieve the values are ignored.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.DependencyReflectionJvm.mapParameters)
     */
    public suspend inline fun mapParameters(
        parameters: List<KParameter>,
        resolve: suspend (KParameter) -> Any?
    ): Map<KParameter, Any?> =
        parameters.mapNotNull { parameter ->
            parameter to try {
                resolve(parameter)
            } catch (cause: Exception) {
                when {
                    parameter.isOptional -> return@mapNotNull null // ignore
                    parameter.type.isMarkedNullable -> null // let value = null
                    else -> throw cause
                }
            }
        }.toMap()
}

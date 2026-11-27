package com.custom.breenocallvoice.hook.core

/**
 * Modular contract for an independent hook feature.
 * Each feature owns its own target resolution, interceptors, and error isolation.
 */
interface FeatureHook {
    val name: String
    fun install(ctx: HookContext)
}

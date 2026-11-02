package com.localai.cutout.core.model

import android.content.Context

/** Separate on-demand model downloads for Photo Restoration. */
class PhotoRestorationModelStore(context: Context) {
    private val scratch = ModelManager(context.applicationContext, ModelSpecs.restorationScratch)
    private val lama = ModelManager(context.applicationContext, ModelSpecs.restorationLama)
    private val colorizer = ModelManager(context.applicationContext, ModelSpecs.restorationColorizer)
    private val enhancer = ModelManager(context.applicationContext, ModelSpecs.restorationEnhancer)

    fun scratchInstalled(): Boolean = scratch.installed()
    fun lamaInstalled(): Boolean = lama.installed()
    fun colorizerInstalled(): Boolean = colorizer.installed()
    fun enhancerInstalled(): Boolean = enhancer.installed()
    fun scratchPath(): String = scratch.path()
    fun lamaPath(): String = lama.path()
    fun colorizerPath(): String = colorizer.path()
    fun enhancerPath(): String = enhancer.path()
    fun scratchBytes(): Long = scratch.sizeBytes()
    fun lamaBytes(): Long = lama.sizeBytes()
    fun colorizerBytes(): Long = colorizer.sizeBytes()
    fun enhancerBytes(): Long = enhancer.sizeBytes()

    suspend fun downloadScratch(onProgress: (Long, Long) -> Unit) = scratch.download(onProgress)
    suspend fun downloadLama(onProgress: (Long, Long) -> Unit) = lama.download(onProgress)
    suspend fun downloadColorizer(onProgress: (Long, Long) -> Unit) = colorizer.download(onProgress)
    suspend fun downloadEnhancer(onProgress: (Long, Long) -> Unit) = enhancer.download(onProgress)

    fun deleteScratch() = scratch.delete()
    fun deleteLama() = lama.delete()
    fun deleteColorizer() = colorizer.delete()
    fun deleteEnhancer() = enhancer.delete()
}

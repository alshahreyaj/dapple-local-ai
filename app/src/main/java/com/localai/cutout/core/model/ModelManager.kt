package com.localai.cutout.core.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class ModelManager(context: Context, private val spec: ModelSpec = ModelSpecs.backgroundRemover) {
    private val model = File(context.filesDir, spec.fileName)
    private val partial = File(context.filesDir, "${spec.fileName}.download.part")
    private val stagedModel = File(context.filesDir, "${spec.fileName}.installing")
    fun installed(): Boolean = if (spec.artifacts.isNotEmpty()) {
        model.isDirectory && spec.artifacts.all { artifact ->
            val file = File(model, artifact.fileName)
            file.isFile && (artifact.sizeBytes <= 0L || file.length() == artifact.sizeBytes) &&
                ModelVerifier.sha256(file).equals(artifact.sha256, ignoreCase = true)
        }
    } else model.isFile &&
        (!spec.verifySize || model.length() == spec.sizeBytes) &&
        (spec.sha256.isBlank() || ModelVerifier.sha256(model) == spec.sha256)
    fun sizeBytes(): Long = if (spec.artifacts.isNotEmpty()) {
        if (model.isDirectory) model.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
    } else if (model.exists()) model.length() else 0L
    fun delete() { if (model.isDirectory) model.deleteRecursively() else model.delete(); partial.delete(); stagedModel.delete() }

    suspend fun download(onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        if (spec.artifacts.isNotEmpty()) {
            downloadArtifacts(onProgress)
            return@withContext
        }
        model.parentFile?.mkdirs()
        val usable = model.parentFile?.usableSpace ?: 0
        val estimate = maxOf(spec.storageEstimateBytes, spec.downloadSizeBytes, spec.sizeBytes)
        if (usable < estimate * 2 + 20L * 1024 * 1024) throw IllegalStateException("Not enough storage. Free some space and retry.")
        partial.delete()
        val connection = (URL(spec.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000; readTimeout = 30_000; instanceFollowRedirects = true
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) throw IllegalStateException("Couldn't download this tool. Check your connection and try again.")
            val declared = connection.contentLengthLong.takeIf { it > 0 } ?: spec.downloadSizeBytes
            connection.inputStream.use { input -> FileOutputStream(partial).use { output ->
                val buffer = ByteArray(128 * 1024); var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    output.write(buffer, 0, n); total += n; onProgress(total, declared)
                }
                output.fd.sync()
            } }
            if (spec.verifySize && partial.length() != spec.downloadSizeBytes) {
                Log.e(TAG, "Model size mismatch for ${spec.id}: expected=${spec.downloadSizeBytes}, actual=${partial.length()}")
                throw IllegalStateException("The download was incomplete. Check your connection and try again.")
            }
            if (spec.downloadSha256.isNotBlank()) {
                val actualHash = ModelVerifier.sha256(partial)
                if (!actualHash.equals(spec.downloadSha256, ignoreCase = true)) {
                    Log.e(TAG, "Model SHA-256 mismatch for ${spec.id}: expected=${spec.downloadSha256}, actual=$actualHash, bytes=${partial.length()}")
                    throw IllegalStateException("The downloaded model didn't match the expected file. Please retry.")
                }
            }
            val verifiedModel = if (spec.archiveEntry == null) partial else extractModel(partial)
            if (spec.verifySize && verifiedModel.length() != spec.sizeBytes) {
                Log.e(TAG, "Installed model size mismatch for ${spec.id}: expected=${spec.sizeBytes}, actual=${verifiedModel.length()}")
                throw IllegalStateException("The downloaded model didn't match the expected file. Please retry.")
            }
            if (spec.sha256.isNotBlank()) {
                val actualHash = ModelVerifier.sha256(verifiedModel)
                if (!actualHash.equals(spec.sha256, ignoreCase = true)) {
                    Log.e(TAG, "Installed model SHA-256 mismatch for ${spec.id}: expected=${spec.sha256}, actual=$actualHash, bytes=${verifiedModel.length()}")
                    throw IllegalStateException("The downloaded model didn't match the expected file. Please retry.")
                }
            }
            model.delete()
            if (!verifiedModel.renameTo(model)) throw IllegalStateException("Couldn't prepare this tool. Please try again.")
        } catch (e: Throwable) { partial.delete(); stagedModel.delete(); throw e }
        finally {
            if (spec.archiveEntry != null) partial.delete()
            connection.disconnect()
        }
    }

    private suspend fun downloadArtifacts(onProgress: (Long, Long) -> Unit) {
        val artifacts = spec.artifacts
        val parent = model.parentFile ?: throw IllegalStateException("Couldn't access model storage")
        parent.mkdirs()
        model.mkdirs()
        val knownStoredBytes = artifacts.sumOf { artifact ->
            File(model, artifact.fileName).takeIf { installedArtifactIsValid(it, artifact) }?.length() ?: 0L
        }
        val usable = parent.usableSpace
        if (usable < maxOf(spec.storageEstimateBytes, spec.downloadSizeBytes) + 20L * 1024 * 1024) {
            throw IllegalStateException("Not enough storage. Free some space and retry.")
        }
        var completed = knownStoredBytes
        val progressTotal = spec.downloadSizeBytes.coerceAtLeast(1L)
        for (artifact in artifacts) {
            currentCoroutineContext().ensureActive()
            val target = File(model, artifact.fileName)
            if (installedArtifactIsValid(target, artifact)) continue
            target.delete()
            val temp = File(model, "${artifact.fileName}.download.part")
            temp.delete()
            val connection = (URL(artifact.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                connection.connect()
                if (connection.responseCode !in 200..299) throw IllegalStateException("Couldn't download the speech model. Check your connection and try again.")
                val declaredSize = connection.contentLengthLong
                connection.inputStream.use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var current = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            current += count
                            onProgress(completed + current, progressTotal)
                        }
                        output.fd.sync()
                    }
                }
                if (artifact.sizeBytes > 0L && temp.length() != artifact.sizeBytes) throw IllegalStateException("The speech model download was incomplete. Please retry.")
                if (declaredSize > 0L && temp.length() != declaredSize) throw IllegalStateException("The speech model download was incomplete. Please retry.")
                if (!ModelVerifier.sha256(temp).equals(artifact.sha256, ignoreCase = true)) throw IllegalStateException("The speech model download couldn't be verified. Please retry.")
                if (!temp.renameTo(target)) throw IllegalStateException("Couldn't prepare the speech model. Please retry.")
                completed += target.length()
                onProgress(completed, progressTotal)
            } catch (e: Throwable) {
                temp.delete()
                throw e
            } finally {
                connection.disconnect()
            }
        }
        if (!installed()) throw IllegalStateException("The speech model files couldn't be verified. Please download them again.")
    }

    private fun installedArtifactIsValid(file: File, artifact: ModelArtifact): Boolean =
        file.isFile && (artifact.sizeBytes <= 0L || file.length() == artifact.sizeBytes) &&
            ModelVerifier.sha256(file).equals(artifact.sha256, ignoreCase = true)

    private suspend fun extractModel(archive: File): File {
        stagedModel.delete()
        var found = false
        ZipInputStream(FileInputStream(archive)).use { zip ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zip.nextEntry ?: break
                if (entry.name == spec.archiveEntry) {
                    if (found || entry.isDirectory) throw IllegalStateException("Invalid model archive contents")
                    found = true
                    FileOutputStream(stagedModel).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = zip.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                        }
                        output.fd.sync()
                    }
                }
                zip.closeEntry()
            }
        }
        if (!found) throw IllegalStateException("Model archive did not contain the expected TFLite file")
        return stagedModel
    }

    fun path() = model.absolutePath
    fun artifactPath(fileName: String) = File(model, fileName).absolutePath
    fun spec() = spec
    companion object {
        private const val TAG = "DappleModelManager"
        const val MODEL_FILE = "ormbg.tflite"
        const val EXPECTED_BYTES = 176_240_148L
        const val SHA256 = "f6e359cf35c55e7bf216eb3141f6d76828667a5e8e67a929f53e9f7f38fae030"
        const val MODEL_URL = "https://huggingface.co/buckets/xeneva-studio/localAI/resolve/bg_remover_1.tflite?download=true"
    }
}

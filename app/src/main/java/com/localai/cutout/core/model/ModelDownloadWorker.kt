package com.localai.cutout.core.model

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class ModelDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val id = inputData.getString(KEY_MODEL_ID) ?: ModelSpecs.backgroundRemover.id
        val spec = ModelSpecs.byId(id) ?: return Result.failure(workDataOf(KEY_ERROR to "Unknown model"))
        ModelManager(applicationContext, spec).download { done, total ->
            setProgressAsync(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
        }
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (e: Throwable) { Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download failed"))) }
    companion object { const val KEY_MODEL_ID = "model_id"; const val KEY_DONE = "done"; const val KEY_TOTAL = "total"; const val KEY_ERROR = "error" }
}

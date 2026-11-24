package com.havenheights.app.data

import android.content.Context
import androidx.work.*
import com.havenheights.app.HavenApplication
import com.havenheights.app.data.local.PendingItem
import java.io.File
import java.util.concurrent.TimeUnit

/** Uploads queued readings and repair requests, oldest first, once there is a connection. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        val app = applicationContext as HavenApplication
        val owner = app.session.user()?.let { ownerKey(it) } ?: return ListenableWorker.Result.success()
        var retry = false

        for (item in app.dao.pendingList()) {
            if (item.error != null || item.owner != owner) continue
            val outcome = upload(app, item)
            if (outcome.isSuccess) {
                app.dao.deletePending(item)
                item.photoPath?.let { File(it).delete() }
                continue
            }
            val ex = outcome.exceptionOrNull() as? ApiException
            when {
                ex == null || ex.code == 0 -> { retry = true; break }   // still offline: try again later
                ex.code == 401 -> break                                   // signed out
                else -> app.dao.markError(item.id, ex.message, ex.code)   // server said no: the user decides
            }
        }
        return if (retry) ListenableWorker.Result.retry() else ListenableWorker.Result.success()
    }

    private suspend fun upload(app: HavenApplication, item: PendingItem): kotlin.Result<Any?> = when (item.kind) {
        KIND_READING -> {
            val p = HavenJson.gson.fromJson(item.payload, PendingReading::class.java)
            app.repository.saveReading(p.tenancyId, p.value, item.photoPath?.let { File(it) }, p.confirmJump, p.recordedAt, p.source ?: "MANUAL")
        }
        else -> {
            val r = HavenJson.gson.fromJson(item.payload, NewRequest::class.java)
            app.repository.createRequest(r)
        }
    }
}

object SyncScheduler {
    fun enqueue(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("haven_sync", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

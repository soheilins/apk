package com.soheil.hailofblades
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // TODO: Implement download logic
        return Result.success()
    }
}

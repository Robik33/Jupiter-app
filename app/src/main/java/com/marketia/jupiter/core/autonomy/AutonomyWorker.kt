package com.marketia.jupiter.core.autonomy

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.marketia.jupiter.data.repository.JupiterRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AutonomyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: AutonomyEngine,
    private val repository: JupiterRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = repository.countPendingTasks()
        if (pending == 0) return Result.success()
        if (engine.isRunning()) return Result.success()

        return runCatching {
            engine.processAllPending()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "jupiter_autonomy_periodic"
    }
}

package com.pqsolutions.hdd_monitor.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pqsolutions.hdd_monitor.domain.GetPanelsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

@HiltWorker
class RefreshPanelsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val getPanelsUseCase: GetPanelsUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            getPanelsUseCase(GetPanelsUseCase.Params(null))
                .catch { error ->
                    // Manejar el error aquí
                    Result.failure()
                }
                .collect { panels ->
                    // Aquí puedes procesar los paneles actualizados si es necesario
                }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
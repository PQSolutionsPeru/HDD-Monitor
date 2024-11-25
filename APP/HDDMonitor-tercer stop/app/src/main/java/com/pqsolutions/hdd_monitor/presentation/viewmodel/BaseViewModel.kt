package com.pqsolutions.hdd_monitor.presentation.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch


abstract class BaseViewModel : ViewModel() {
    private val job = SupervisorJob()
    protected val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        handleError(throwable)
    }

    protected val viewModelCoroutineScope = CoroutineScope(
        job + Dispatchers.Main.immediate + exceptionHandler
    )

    protected abstract fun handleError(throwable: Throwable)

    override fun onCleared() {
        super.onCleared()
        viewModelCoroutineScope.coroutineContext.cancelChildren()
        job.cancel()
    }

    protected fun launchWithScope(block: suspend CoroutineScope.() -> Unit): Job {
        return viewModelCoroutineScope.launch { block() }
    }
}

/**
 * Mixin trait for handling data loading
 */
interface DataLoader {
    var loadDataJob: Job?

    fun cancelLoadDataJob() {
        loadDataJob?.cancel()
        loadDataJob = null
    }
}

package com.mrousavy.camera.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Serializes AE updates on the main thread and invalidates confirmations across camera lifecycles. */
internal class ExposureLockController(private val apply: (Boolean) -> Deferred<Unit>) {
  var manualControl = false
    private set
  var isLocked = false
    private set
  private var generation = 0L
  private var pendingOperation: Job? = null
  private val mutex = Mutex()

  fun reset(): Deferred<Unit> {
    invalidate()
    isLocked = false
    // Clear the running request immediately, even while an earlier confirmation is pending.
    return apply(false)
  }

  private fun invalidate() {
    generation++
    pendingOperation?.cancel(CancellationException("Exposure lock was reset."))
  }

  suspend fun setLocked(locked: Boolean, manual: Boolean = true) {
    if (manual) {
      if (!manualControl || !locked) invalidate()
      manualControl = true
    } else if (manualControl) {
      throw ExposureLockCanceledError()
    }
    val requestGeneration = generation
    mutex.withLock {
      if (requestGeneration != generation) throw ExposureLockCanceledError()
      pendingOperation = currentCoroutineContext().job
      try {
        withTimeout(5000L) { apply(locked).await() }
        if (requestGeneration != generation) throw ExposureLockCanceledError()
        isLocked = locked
      } catch (error: Throwable) {
        isLocked = false
        // Cancellation must not skip rollback. Keep the mutex until rollback is confirmed or times out.
        try {
          withContext(NonCancellable) {
            withTimeout(5000L) { apply(false).await() }
          }
        } catch (rollbackError: Throwable) {
          throw ExposureLockUpdateError(error, rollbackError)
        }
        when (error) {
          is TimeoutCancellationException -> throw ExposureLockUpdateError(error)
          is CancellationException -> throw ExposureLockCanceledError()
          is CameraError -> throw error
          else -> throw ExposureLockUpdateError(error)
        }
      } finally {
        pendingOperation = null
      }
    }
  }
}

internal class ExposureLockCanceledError :
  CameraError("session", "exposure-lock-canceled", "The exposure lock request was canceled by an unlock or camera lifecycle change.")

internal class ExposureLockNotSupportedError :
  CameraError("device", "exposure-lock-not-supported", "The current camera does not support auto-exposure locking.")

internal class ExposureLockUpdateError(cause: Throwable, rollbackError: Throwable? = null) :
  CameraError(
    "session",
    "exposure-lock-failed",
    if (rollbackError == null) {
      "CameraX did not confirm the exposure lock update."
    } else {
      "CameraX did not confirm the exposure lock update or its unlock rollback: ${rollbackError.message}"
    },
    cause
  ) {
  init {
    if (rollbackError != null) addSuppressed(rollbackError)
  }
}

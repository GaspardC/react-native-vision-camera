package com.mrousavy.camera.core
import kotlinx.coroutines.*
import java.util.concurrent.Executors
object Log { fun w(tag: String, msg: String, error: Throwable) {} }
object CameraControl { class OperationCanceledException(message: String) : Exception(message) }
class SessionState { var isDestroyed = false }
private class ViewHarness(val mainCoroutineScope: CoroutineScope) {
  val TAG = "CameraView"
  var hasLockedExposure = false
  var exposureLockGeneration = 0L
  var isActive = true
  val cameraSession = SessionState()
  var physicallyLocked = false
  var callbacks = 0
  var schedules = 0
  val errors = mutableListOf<Throwable>()
  val requests = mutableListOf<Pair<Boolean,CompletableDeferred<Unit>>>()
  val exposureLockController = ExposureLockController { locked ->
    // Match Camera2CameraControl.updateConfig: newer requests fail older pending confirmations.
    requests.lastOrNull()?.second?.completeExceptionally(CameraControl.OperationCanceledException("Camera2CameraControl was updated with new options."))
    physicallyLocked = locked
    CompletableDeferred<Unit>().also {
      requests.add(locked to it)
      if (!isActive && !locked) it.complete(Unit)
    }
  }
  fun scheduleExposureLock() { schedules++ }
  fun invokeOnExposureLocked() { callbacks++ }
  fun onError(error: Throwable) { errors.add(error) }
PRODUCTION_METHODS

}
fun main() = runBlocking {
  Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { main ->
    val scope = CoroutineScope(SupervisorJob() + main)
    withContext(main) {
      val view = ViewHarness(scope)
      view.triggerExposureLock()
      yield()
      check(view.requests.size == 1)
      view.releaseExposureLock()
      yield()
      yield()
      // Fire the next one-second timer while the previous rollback still awaits confirmation.
      view.triggerExposureLock()
      yield()
      check(view.hasLockedExposure)
      view.requests.last().second.complete(Unit)
      repeat(8) { yield() }
      check(view.requests.last().first)
      view.requests.last().second.complete(Unit)
      repeat(8) { yield() }
      check(view.physicallyLocked && view.exposureLockController.isLocked && view.callbacks == 1 && view.hasLockedExposure && view.errors.isEmpty())
      println("PASS stale automatic catch cannot clobber confirmed relock: callbacks=${view.callbacks}, hasLockedExposure=${view.hasLockedExposure}, errors=${view.errors.size}")
      val drop = ViewHarness(scope)
      drop.releaseExposureLock()
      drop.isActive = false
      drop.cameraSession.isDestroyed = true
      drop.requests.last().second.completeExceptionally(CameraControl.OperationCanceledException("The camera control has became inactive."))
      repeat(4) { yield() }
      check(drop.errors.isEmpty())
      println("PASS normal destruction does not emit a reset error")
      for (expected in listOf(CancellationException("canceled"),CameraControl.OperationCanceledException("superseded"),ExposureLockCanceledError())) {
        val cancel = ViewHarness(scope)
        cancel.releaseExposureLock()
        cancel.requests.last().second.completeExceptionally(expected)
        repeat(4) { yield() }
        check(cancel.errors.isEmpty())
      }
      println("PASS expected current-generation cancellations are suppressed")
      val failed = ViewHarness(scope)
      failed.releaseExposureLock()
      failed.requests.last().second.completeExceptionally(IllegalStateException("hardware error"))
      repeat(4) { yield() }
      check(failed.errors.size == 1)
      println("PASS genuine active reset failure is reported")
      val stale = ViewHarness(scope)
      stale.releaseExposureLock()
      stale.releaseExposureLock()
      stale.requests.last().second.complete(Unit)
      repeat(4) { yield() }
      check(stale.errors.isEmpty())
      println("PASS stale reset errors are suppressed")
      val timeout = ViewHarness(scope)
      timeout.releaseExposureLock()
      delay(5150)
      println("ACTIVE_RESET_TIMEOUT reportedErrors=${timeout.errors.size}")
      check(timeout.errors.size == 1) { "Active, current-generation reset timeout was silently discarded" }
      check(timeout.errors.single() is ExposureLockUpdateError)
      check((timeout.errors.single() as ExposureLockUpdateError).cause is TimeoutCancellationException)
      println("PASS active reset timeout is reported as session/exposure-lock-failed")
    }
    scope.cancel()
  }
}

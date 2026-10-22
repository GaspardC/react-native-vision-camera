package com.mrousavy.camera.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

// Standalone JVM checks; run with package/scripts/test-exposure-lock.py (no app build required).
private class CameraFixture {
  var active = true
  var supported = true
  var confirmUnlockImmediately = false
  var physicallyLocked = false
  val requests = mutableListOf<Pair<Boolean, CompletableDeferred<Unit>>>()
  val controller = ExposureLockController { locked ->
    if (locked && !active) throw CameraNotReadyError()
    if (locked && !supported) throw ExposureLockNotSupportedError()
    physicallyLocked = locked
    val confirmation = CompletableDeferred<Unit>()
    requests.add(locked to confirmation)
    if (!locked && (!active || !supported || confirmUnlockImmediately)) confirmation.complete(Unit)
    confirmation
  }

  fun confirmLatest() { requests.last().second.complete(Unit) }
}

private fun CoroutineScope.request(camera: CameraFixture, locked: Boolean, manual: Boolean = true) =
  async(start = CoroutineStart.UNDISPATCHED) {
    // Match CameraView: cancel only the main-thread command context, not the bridge caller.
    runCatching { withContext(CoroutineName("command")) { camera.controller.setLocked(locked, manual) } }
  }

fun main() = runBlocking {
  var passed = 0
  suspend fun checkCase(name: String, check: suspend () -> Unit) {
    withTimeout(12000L) { check() }
    passed++
    println("PASS: $name")
  }

  checkCase("waits for a fresh confirmation, including repeated commands") {
    val camera = CameraFixture()
    val first = request(camera, true)
    check(!first.isCompleted && !camera.controller.isLocked)
    camera.confirmLatest()
    check(first.await().isSuccess && camera.controller.isLocked)
    val second = request(camera, true)
    check(!second.isCompleted && camera.requests.size == 2)
    camera.confirmLatest()
    check(second.await().isSuccess)
  }
  checkCase("serializes concurrent lock commands") {
    val camera = CameraFixture()
    val first = request(camera, true)
    val second = request(camera, true)
    check(camera.requests.size == 1)
    camera.confirmLatest()
    check(first.await().isSuccess)
    yield()
    check(camera.requests.size == 2 && !second.isCompleted)
    camera.confirmLatest()
    check(second.await().isSuccess)
  }
  checkCase("failure waits for unlock rollback before rejection") {
    val camera = CameraFixture()
    val locking = request(camera, true)
    camera.requests.first().second.completeExceptionally(IllegalStateException("CameraX failed"))
    yield()
    check(!camera.physicallyLocked && !locking.isCompleted && !camera.requests.last().first)
    camera.confirmLatest()
    check(locking.await().exceptionOrNull() is ExposureLockUpdateError)
  }
  checkCase("inactive unlock is safe and inactive lock rejects") {
    val camera = CameraFixture().apply { active = false }
    check(request(camera, false).await().isSuccess)
    check(request(camera, true).await().exceptionOrNull() is CameraNotReadyError)
    check(!camera.physicallyLocked)
  }
  checkCase("unsupported lock rejects and unlock remains safe") {
    val camera = CameraFixture().apply { supported = false }
    check(request(camera, true).await().exceptionOrNull() is ExposureLockNotSupportedError)
    check(request(camera, false).await().isSuccess)
  }
  checkCase("inactivation cancels pending lock and stale confirmation cannot restore it") {
    val camera = CameraFixture()
    val locking = request(camera, true)
    val old = camera.requests.first().second
    camera.active = false
    camera.controller.reset().await()
    old.complete(Unit)
    check(locking.await().exceptionOrNull() is ExposureLockCanceledError)
    check(!camera.controller.isLocked && !camera.physicallyLocked)
  }
  checkCase("explicit unlock wins a pending lock") {
    val camera = CameraFixture().apply { confirmUnlockImmediately = true }
    val locking = request(camera, true)
    val old = camera.requests.first().second
    val unlocking = request(camera, false)
    check(locking.await().exceptionOrNull() is ExposureLockCanceledError)
    check(unlocking.await().isSuccess)
    old.complete(Unit)
    check(!camera.controller.isLocked && !camera.physicallyLocked)
  }
  checkCase("reset invalidates queued locks as well as the pending lock") {
    val camera = CameraFixture().apply { confirmUnlockImmediately = true }
    val first = request(camera, true)
    val queued = request(camera, true)
    camera.controller.reset().await()
    check(first.await().exceptionOrNull() is ExposureLockCanceledError)
    check(queued.await().exceptionOrNull() is ExposureLockCanceledError)
    check(camera.requests.count { it.first } == 1 && !camera.physicallyLocked)
  }
  checkCase("manual command suppresses pending automatic lock and later automatic relock") {
    val camera = CameraFixture().apply { confirmUnlockImmediately = true }
    val auto = request(camera, true, manual = false)
    val manual = request(camera, false)
    check(auto.await().exceptionOrNull() is ExposureLockCanceledError)
    check(manual.await().isSuccess)
    camera.controller.reset().await()
    check(request(camera, true, manual = false).await().exceptionOrNull() is ExposureLockCanceledError)
    check(!camera.physicallyLocked)
    val nextSeries = request(camera, true)
    camera.confirmLatest()
    check(nextSeries.await().isSuccess)
  }
  checkCase("rollback failure is surfaced") {
    val camera = CameraFixture()
    val locking = request(camera, true)
    camera.requests.first().second.completeExceptionally(IllegalStateException("lock failed"))
    yield()
    camera.requests.last().second.completeExceptionally(IllegalStateException("unlock failed"))
    val error = locking.await().exceptionOrNull()
    check(error is ExposureLockUpdateError && error.message.contains("rollback"))
    check(error.suppressed.size == 1)
  }
  checkCase("missing CameraX confirmation times out and rolls back") {
    val camera = CameraFixture().apply { confirmUnlockImmediately = true }
    val locking = request(camera, true)
    check(locking.await().exceptionOrNull() is ExposureLockUpdateError)
    check(!camera.controller.isLocked && !camera.physicallyLocked)
  }
  checkCase("missing rollback confirmation is bounded too") {
    val camera = CameraFixture()
    val locking = request(camera, true)
    camera.requests.first().second.completeExceptionally(IllegalStateException("lock failed"))
    val error = locking.await().exceptionOrNull()
    check(error is ExposureLockUpdateError && error.message.contains("rollback"))
  }
  println("$passed exposure lock checks passed")
}

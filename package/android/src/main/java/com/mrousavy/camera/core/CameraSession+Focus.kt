package com.mrousavy.camera.core

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import com.mrousavy.camera.core.extensions.await

@SuppressLint("RestrictedApi")
suspend fun CameraSession.focus(meteringPoint: MeteringPoint) {
  val camera = camera ?: throw CameraNotReadyError()

  // CameraX cancels tap-to-focus after 5 seconds by default and returns to continuous AF,
  // which hunts at close range. Keep the tapped focus until the next tap or session restart.
  val action = FocusMeteringAction.Builder(meteringPoint).disableAutoCancel().build()
  if (!camera.cameraInfo.isFocusMeteringSupported(action)) {
    throw FocusNotSupportedError()
  }

  try {
    Log.i(CameraSession.TAG, "┌─────────────────────────────────────────────────────")
    Log.i(CameraSession.TAG, "│ 🎯 FOCUS ATTEMPT")
    Log.i(CameraSession.TAG, "│ Point: ${action.meteringPointsAf.joinToString { "(${it.x}, ${it.y})" }}")
    Log.i(CameraSession.TAG, "│ isFocusMeteringSupported: ${camera.cameraInfo.isFocusMeteringSupported(action)}")
    Log.i(CameraSession.TAG, "└─────────────────────────────────────────────────────")

    val future = camera.cameraControl.startFocusAndMetering(action)
    val result = future.await(CameraQueues.cameraExecutor)

    Log.i(CameraSession.TAG, "┌─────────────────────────────────────────────────────")
    Log.i(CameraSession.TAG, "│ 📊 FOCUS RESULT")
    Log.i(CameraSession.TAG, "│ isFocusSuccessful: ${result.isFocusSuccessful}")
    Log.i(CameraSession.TAG, "└─────────────────────────────────────────────────────")

    Log.i(CameraSession.TAG, "🔒 Focus held until the next tap (auto-cancel disabled)")
  } catch (e: CameraControl.OperationCanceledException) {
    Log.e(CameraSession.TAG, "❌ Focus was canceled: ${e.message}")
    throw FocusCanceledError()
  }
}

package com.mrousavy.camera.core

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import com.mrousavy.camera.core.extensions.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SuppressLint("RestrictedApi")
suspend fun CameraSession.focus(meteringPoint: MeteringPoint) {
  val camera = camera ?: throw CameraNotReadyError()

  val action = FocusMeteringAction.Builder(meteringPoint).build()
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

    if (result.isFocusSuccessful) {
      Log.i(CameraSession.TAG, "🎯 Focused successfully! Locking AF mode...")
      // Lock focus after successful tap-to-focus (must be on UI thread)
      withContext(Dispatchers.Main) {
        configure { config ->
          config.focusLocked = true
        }
      }
      Log.i(CameraSession.TAG, "✅ Focus locked - AF will hold position until preview stops")
    } else {
      Log.i(CameraSession.TAG, "❌ Focus failed - AF lock not applied")
    }
  } catch (e: CameraControl.OperationCanceledException) {
    Log.e(CameraSession.TAG, "❌ Focus was canceled: ${e.message}")
    throw FocusCanceledError()
  }
}

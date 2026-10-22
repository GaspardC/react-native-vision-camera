package com.mrousavy.camera.react

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.view.Gravity
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraState
import androidx.camera.view.PreviewView
import androidx.camera.core.TorchState
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import com.mrousavy.camera.core.CameraConfiguration
import com.mrousavy.camera.core.CameraSession
import com.mrousavy.camera.core.CameraNotReadyError
import com.mrousavy.camera.core.ExposureLockCanceledError
import com.mrousavy.camera.core.ExposureLockController
import com.mrousavy.camera.core.ExposureLockNotSupportedError
import com.mrousavy.camera.core.ExposureLockUpdateError
import com.mrousavy.camera.core.CodeScannerFrame
import com.mrousavy.camera.core.types.CameraDeviceFormat
import com.mrousavy.camera.core.types.CodeScannerOptions
import com.mrousavy.camera.core.types.Orientation
import com.mrousavy.camera.core.types.OutputOrientation
import com.mrousavy.camera.core.types.PixelFormat
import com.mrousavy.camera.core.types.PreviewViewType
import com.mrousavy.camera.core.types.QualityBalance
import com.mrousavy.camera.core.types.ResizeMode
import com.mrousavy.camera.core.types.ShutterType
import com.mrousavy.camera.core.types.Torch
import com.mrousavy.camera.core.types.VideoStabilizationMode
import com.mrousavy.camera.frameprocessors.Frame
import com.mrousavy.camera.frameprocessors.FrameProcessor
import com.mrousavy.camera.react.extensions.installHierarchyFitter
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

//
// TODOs for the CameraView which are currently too hard to implement either because of CameraX' limitations, or my brain capacity.
//
// TODO: High-speed video recordings (export in CameraViewModule::getAvailableVideoDevices(), and set in CameraView::configurePreview()) (120FPS+)
// TODO: Better startRecording()/stopRecording() (promise + callback, wait for TurboModules/JSI)
// TODO: takePhoto() depth data
// TODO: takePhoto() raw capture
// TODO: takePhoto() return with jsi::Value Image reference for faster capture
// TODO: Support videoCodec on Android

@SuppressLint("ClickableViewAccessibility", "ViewConstructor", "MissingPermission")
class CameraView(context: Context) :
  FrameLayout(context),
  CameraSession.Callback,
  FpsSampleCollector.Callback {
  companion object {
    const val TAG = "CameraView"

    // Time for AE to settle after the preview starts or the torch changes, before freezing exposure.
    private const val EXPOSURE_LOCK_DELAY_MS = 1000L
  }

  // react properties
  // props that require reconfiguring
  var cameraId: String? = null
  var enableDepthData = false
  var enablePortraitEffectsMatteDelivery = false
  var isMirrored = false

  // use-cases
  var photo = false
  var video = false
  var audio = false
  var enableFrameProcessor = false
  var pixelFormat: PixelFormat = PixelFormat.YUV
  var enableLocation = false
  var preview = true
    set(value) {
      field = value
      updatePreview()
    }

  // props that require format reconfiguring
  var format: CameraDeviceFormat? = null
  var minFps: Int? = null
  var maxFps: Int? = null
  var videoStabilizationMode: VideoStabilizationMode? = null
  var videoHdr = false
  var photoHdr = false
  var videoBitRateOverride: Double? = null
  var videoBitRateMultiplier: Double? = null

  // TODO: Use .BALANCED once CameraX fixes it https://issuetracker.google.com/issues/337214687
  var photoQualityBalance = QualityBalance.SPEED
  var lowLightBoost = false

  // other props
  var isActive = false
    set(value) {
      val wasActive = field
      field = value
      // Reset locks when camera becomes inactive
      if (wasActive && !value) {
        Log.i(TAG, "isActive changed from true to false - resetting locks")
        resetAllLocks()
      }
    }
  var torch: Torch = Torch.OFF
    set(value) {
      val changed = field != value
      field = value
      // The torch changes scene brightness: release an existing lock and let AE settle again before locking.
      if (changed && isPreviewStreaming && autoLockOnPreviewStart && !exposureLockController.manualControl) {
        if (hasLockedExposure) releaseExposureLock()
        scheduleExposureLock()
      }
    }
  var zoom: Float = 1f // in "factor"
  var exposure: Double = 0.0
  var outputOrientation: OutputOrientation = OutputOrientation.DEVICE
  var autoLockOnPreviewStart = false
  var androidPreviewViewType: PreviewViewType = PreviewViewType.SURFACE_VIEW
    set(value) {
      field = value
      updatePreview()
    }
  var enableZoomGesture = false
    set(value) {
      field = value
      updateZoomGesture()
    }
  var resizeMode: ResizeMode = ResizeMode.COVER
    set(value) {
      field = value
      updatePreview()
    }

  // code scanner
  var codeScannerOptions: CodeScannerOptions? = null

  // private properties
  private var isMounted = false
  private val exposureLockController = ExposureLockController(::applyExposureLock)
  private var hasLockedExposure = false
  private var exposureLockGeneration = 0L
  private var isPreviewStreaming = false
  private val mainCoroutineScope = CoroutineScope(Dispatchers.Main)
  private val handler = android.os.Handler(android.os.Looper.getMainLooper())
  private val exposureLockRunnable = Runnable {
    if (exposureLockController.manualControl || hasLockedExposure || !isActive || !isPreviewStreaming) return@Runnable
    if (torch == Torch.ON && cameraSession.camera?.cameraInfo?.hasFlashUnit() == false) {
      // configureSideProps() rejects the torch on this device (FlashUnavailableError), so locking would fail on every retry.
      Log.w(TAG, "torch=on requested on a device without flash unit, exposure lock skipped")
      return@Runnable
    }
    if (!isTorchApplied()) {
      // CameraX can reject the torch (e.g. before the session is active, or after a resume).
      // Request it again and never lock while it changes.
      Log.i(TAG, "Torch not applied yet, re-requesting it and postponing exposure lock...")
      cameraSession.camera?.cameraControl?.enableTorch(torch == Torch.ON)
      scheduleExposureLock()
      return@Runnable
    }
    triggerExposureLock()
  }

  // session
  internal val cameraSession: CameraSession
  internal var frameProcessor: FrameProcessor? = null
  internal var previewView: PreviewView? = null
  private var currentConfigureCall: Long = System.currentTimeMillis()
  private val fpsSampleCollector = FpsSampleCollector(this)

  init {
    clipToOutline = true
    cameraSession = CameraSession(context, this)
    this.installHierarchyFitter()
    updatePreview()
  }

  override fun onAttachedToWindow() {
    Log.i(TAG, "CameraView attached to window!")
    super.onAttachedToWindow()
    if (!isMounted) {
      // Notifies JS view that the native view is now available
      isMounted = true
      invokeOnViewReady()
    }
    // start collecting FPS samples
    fpsSampleCollector.start()
  }

  override fun onDetachedFromWindow() {
    Log.i(TAG, "CameraView detached from window!")
    super.onDetachedFromWindow()
    // stop collecting FPS samples
    fpsSampleCollector.stop()
  }

  fun destroy() {
    resetAllLocks()
    cameraSession.close()
  }

  /**
   * Locks exposure once the preview and torch have been stable for EXPOSURE_LOCK_DELAY_MS.
   * Each new trigger restarts the delay.
   */
  private fun scheduleExposureLock() {
    if (!autoLockOnPreviewStart || exposureLockController.manualControl || hasLockedExposure || cameraSession.isDestroyed) return
    handler.removeCallbacks(exposureLockRunnable)
    handler.postDelayed(exposureLockRunnable, EXPOSURE_LOCK_DELAY_MS)
  }

  private fun isTorchApplied(): Boolean {
    val cameraInfo = cameraSession.camera?.cameraInfo ?: return false
    val torchOn = cameraInfo.torchState.value == TorchState.ON
    return torchOn == (torch == Torch.ON)
  }

  /** Resolves only after CameraX confirms the update; manual control disables legacy automatic locking. */
  suspend fun setExposureLocked(locked: Boolean) = withContext(Dispatchers.Main.immediate) {
    exposureLockGeneration++
    Log.i(TAG, "Manual exposure lock requested: locked=$locked")
    handler.removeCallbacks(exposureLockRunnable)
    hasLockedExposure = false
    exposureLockController.setLocked(locked)
    Log.i(TAG, "Manual exposure lock confirmed: locked=$locked")
  }

  @OptIn(ExperimentalCamera2Interop::class)
  private fun applyExposureLock(locked: Boolean): Deferred<Unit> {
    val result = CompletableDeferred<Unit>()
    val config = cameraSession.configuration
    val camera = cameraSession.camera
    // Persist unlocks before touching CameraX, including inactive/no-session resets.
    if (!locked) config?.exposureLocked = false
    val active = isActive && !cameraSession.isDestroyed && config?.isActive == true &&
      camera?.cameraInfo?.cameraState?.value?.type == CameraState.Type.OPEN
    if (locked && !active) throw CameraNotReadyError()
    if (camera == null || cameraSession.isDestroyed) {
      result.complete(Unit)
      return result
    }
    val supported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
      Camera2CameraInfo.from(camera.cameraInfo)
      .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE) == true
    if (!supported) {
      if (locked) throw ExposureLockNotSupportedError()
      result.complete(Unit)
      return result
    }
    // Apply directly: configure() swallows errors and can skip unchanged configuration values.
    val update = Camera2CameraControl.from(camera.cameraControl).addCaptureRequestOptions(
      CaptureRequestOptions.Builder()
        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, locked)
        .build()
    )
    config?.exposureLocked = locked
    cameraSession.exposureLockUpdate = update
    update.addListener(
      {
        try {
          update.get()
          if (locked && (!isActive || cameraSession.isDestroyed || cameraSession.camera !== camera)) {
            throw ExposureLockCanceledError()
          }
          result.complete(Unit)
        } catch (error: Throwable) {
          result.completeExceptionally(error.cause ?: error)
        }
      },
      ContextCompat.getMainExecutor(context)
    )
    result.invokeOnCompletion { if (result.isCancelled) update.cancel(false) }
    // There is no running AE to confirm while inactive. The false request is retained for the next start.
    if (!active && !locked) result.complete(Unit)
    return result
  }

  private fun triggerExposureLock() {
    if (hasLockedExposure || exposureLockController.manualControl) return
    hasLockedExposure = true
    val generation = ++exposureLockGeneration
    mainCoroutineScope.launch {
      try {
        exposureLockController.setLocked(true, manual = false)
        if (generation != exposureLockGeneration || !hasLockedExposure || exposureLockController.manualControl) return@launch
        invokeOnExposureLocked()
      } catch (error: Throwable) {
        if (generation != exposureLockGeneration) return@launch
        hasLockedExposure = false
        if (error !is ExposureLockCanceledError) {
          Log.w(TAG, "Automatic exposure lock was not confirmed by CameraX", error)
          scheduleExposureLock()
        }
      }
    }
  }

  private fun releaseExposureLock() {
    val generation = ++exposureLockGeneration
    hasLockedExposure = false
    val reportFailure: (Throwable) -> Unit = { error ->
      if (generation == exposureLockGeneration && isActive && !cameraSession.isDestroyed &&
        (error !is CancellationException || error is TimeoutCancellationException) && error !is ExposureLockCanceledError &&
        error !is CameraControl.OperationCanceledException
      ) {
        onError(if (error is TimeoutCancellationException) ExposureLockUpdateError(error) else error)
      }
    }
    try {
      val update = exposureLockController.reset()
      mainCoroutineScope.launch {
        try {
          withTimeout(5000L) { update.await() }
        } catch (error: Throwable) {
          reportFailure(error)
        }
      }
    } catch (error: Throwable) {
      reportFailure(error)
    }
  }

  private fun resetAllLocks() {
    handler.removeCallbacks(exposureLockRunnable)
    releaseExposureLock()
  }

  fun update() {
    Log.i(TAG, "Updating CameraSession...")
    val now = System.currentTimeMillis()
    currentConfigureCall = now

    mainCoroutineScope.launch {
      cameraSession.configure { config ->
        if (currentConfigureCall != now) {
          // configure waits for a lock, and if a new call to update() happens in the meantime we can drop this one.
          // this works similar to how React implemented concurrent rendering, the newer call to update() has higher priority.
          Log.i(TAG, "A new configure { ... } call arrived, aborting this one...")
          throw CameraConfiguration.AbortThrow()
        }

        // Input Camera Device
        config.cameraId = cameraId

        // Preview
        val previewView = previewView
        if (previewView != null) {
          config.preview = CameraConfiguration.Output.Enabled.create(CameraConfiguration.Preview(previewView.surfaceProvider))
        } else {
          config.preview = CameraConfiguration.Output.Disabled.create()
        }

        // Photo
        if (photo) {
          config.photo = CameraConfiguration.Output.Enabled.create(CameraConfiguration.Photo(isMirrored, photoHdr, photoQualityBalance))
        } else {
          config.photo = CameraConfiguration.Output.Disabled.create()
        }

        // Video
        if (video || enableFrameProcessor) {
          config.video =
            CameraConfiguration.Output.Enabled.create(
              CameraConfiguration.Video(isMirrored, videoHdr, videoBitRateOverride, videoBitRateMultiplier)
            )
        } else {
          config.video = CameraConfiguration.Output.Disabled.create()
        }

        // Frame Processor
        if (enableFrameProcessor) {
          config.frameProcessor = CameraConfiguration.Output.Enabled.create(CameraConfiguration.FrameProcessor(isMirrored, pixelFormat))
        } else {
          config.frameProcessor = CameraConfiguration.Output.Disabled.create()
        }

        // Audio
        if (audio) {
          config.audio = CameraConfiguration.Output.Enabled.create(CameraConfiguration.Audio(Unit))
        } else {
          config.audio = CameraConfiguration.Output.Disabled.create()
        }

        // Location
        config.enableLocation = enableLocation && this@CameraView.isActive

        // Code Scanner
        val codeScanner = codeScannerOptions
        if (codeScanner != null) {
          config.codeScanner = CameraConfiguration.Output.Enabled.create(
            CameraConfiguration.CodeScanner(codeScanner.codeTypes)
          )
        } else {
          config.codeScanner = CameraConfiguration.Output.Disabled.create()
        }

        // Orientation
        config.outputOrientation = outputOrientation

        // Format
        config.format = format

        // Side-Props
        config.minFps = minFps
        config.maxFps = maxFps
        config.enableLowLightBoost = lowLightBoost
        config.torch = torch
        config.exposure = exposure
        config.autoLockOnPreviewStart = autoLockOnPreviewStart

        // Zoom
        config.zoom = zoom

        // isActive
        config.isActive = this@CameraView.isActive
      }
    }
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun updateZoomGesture() {
    if (enableZoomGesture) {
      val scaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
          override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom *= detector.scaleFactor
            update()
            return true
          }
        }
      )
      setOnTouchListener { _, event ->
        scaleGestureDetector.onTouchEvent(event)
      }
    } else {
      setOnTouchListener(null)
    }
  }

  private fun updatePreview() {
    mainCoroutineScope.launch {
      if (preview && previewView == null) {
        // User enabled Preview, add the PreviewView
        previewView = createPreviewView()
        addView(previewView)
      } else if (!preview && previewView != null) {
        // User disabled Preview, remove the PreviewView
        removeView(previewView)
        previewView = null
      }
      previewView?.let {
        // Update implementation type from React
        it.implementationMode = androidPreviewViewType.toPreviewImplementationMode()
        // Update scale type from React
        it.scaleType = resizeMode.toScaleType()
      }
      update()
    }
  }

  private fun createPreviewView(): PreviewView =
    PreviewView(context).also {
      it.installHierarchyFitter()
      it.implementationMode = androidPreviewViewType.toPreviewImplementationMode()
      it.layoutParams = LayoutParams(
        LayoutParams.MATCH_PARENT,
        LayoutParams.MATCH_PARENT,
        Gravity.CENTER
      )
      var lastIsPreviewing = false
      it.previewStreamState.observe(cameraSession) { state ->
        Log.i(TAG, "PreviewView Stream State changed to $state")

        val isPreviewing = state == PreviewView.StreamState.STREAMING
        if (isPreviewing != lastIsPreviewing) {
          isPreviewStreaming = isPreviewing
          // Notify callback
          if (isPreviewing) {
            invokeOnPreviewStarted()
            scheduleExposureLock()
          } else {
            invokeOnPreviewStopped()
            // Reset the exposure lock when preview stops (allows AE to re-adapt on restart)
            resetAllLocks()
          }
          lastIsPreviewing = isPreviewing
        }
      }
    }

  override fun onFrame(frame: Frame) {
    // Update average FPS samples
    fpsSampleCollector.onTick()

    // Call JS Frame Processor
    frameProcessor?.call(frame)
  }

  override fun onError(error: Throwable) {
    invokeOnError(error)
  }

  private fun showUpdateAlert() {
    Toast.makeText(context, "Camera module initialized", Toast.LENGTH_SHORT).show()
  }

  override fun onInitialized() {
    invokeOnInitialized()
  }

  override fun onStarted() {
    invokeOnStarted()
  }

  override fun onStopped() {
    resetAllLocks()
    invokeOnStopped()
  }

  override fun onShutter(type: ShutterType) {
    invokeOnShutter(type)
  }

  override fun onOutputOrientationChanged(outputOrientation: Orientation) {
    invokeOnOutputOrientationChanged(outputOrientation)
  }

  override fun onPreviewOrientationChanged(previewOrientation: Orientation) {
    invokeOnPreviewOrientationChanged(previewOrientation)
  }

  override fun onCodeScanned(codes: List<Barcode>, scannerFrame: CodeScannerFrame) {
    invokeOnCodeScanned(codes, scannerFrame)
  }

  override fun onAverageFpsChanged(averageFps: Double) {
    invokeOnAverageFpsChanged(averageFps)
  }
}

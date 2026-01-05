# Auto-Lock Exposure Specification

## Overview

This document specifies the auto-lock exposure feature for VisionCamera, designed to ensure stable and consistent photo captures across sequences of images.

## Use Case

Medical/scientific imaging applications require capturing sequences of photos (e.g., 124 photos per examination) where all images must have identical exposure and white balance settings to ensure reproducibility and comparability.

## Problem

Currently, VisionCamera uses continuous auto-exposure (AE) and auto white balance (AWB), which means:
- Each photo may have different ISO and exposure time
- White balance may vary between captures
- This results in inconsistent images within a sequence

## Solution

Implement an automatic lock mechanism that:
1. Allows AE and AWB to converge after camera starts
2. Locks exposure settings after stabilization
3. Maintains locked settings for all subsequent captures

## API

### Props

```typescript
interface CameraProps {
  /**
   * If true, automatically locks exposure and white balance
   * after preview stabilization. Useful for stable capture sequences.
   *
   * @platform Android
   * @default false
   */
  autoLockOnPreviewStart?: boolean
}
```

### Callbacks

```typescript
interface CameraProps {
  /**
   * Called when exposure and white balance are locked.
   * Use this to know when it's safe to start capturing a photo sequence.
   *
   * @platform Android
   */
  onExposureLocked?: () => void
}
```

### Usage Example

```typescript
<Camera
  device={device}
  isActive={true}
  photo={true}
  autoLockOnPreviewStart={true}
  onExposureLocked={() => {
    console.log('Exposure locked - ready for capture sequence')
    startPhotoSequence() // Capture 124 photos
  }}
/>
```

## Technical Implementation

### Android

#### Parameters Controlled

| Parameter | Lock Mechanism |
|-----------|---------------|
| `CONTROL_AE_LOCK` | Set to `true` via Camera2Interop |
| `CONTROL_AWB_MODE` | Fixed to `FLUORESCENT` (~4000K) |

#### Lock Timing

Two options for determining when to lock:

1. **Timer-based (Simple):** Lock ~500ms after preview starts
2. **State-based (Precise):** Lock when `CONTROL_AE_STATE == CONVERGED`

#### Code Flow

```
Camera.configure() called
    ↓
Preview starts (onPreviewStarted)
    ↓
Wait for AE/AWB convergence (~500ms)
    ↓
Set exposureLocked = true
    ↓
Reconfigure with CONTROL_AE_LOCK = true
    ↓
Emit onExposureLocked callback
    ↓
All subsequent captures use locked settings
```

### iOS

**Not implemented.** iOS devices will continue to use continuous auto-exposure.

## Limitations

1. **HDR Incompatibility:** `autoLockOnPreviewStart` cannot be used with HDR or Night Mode extensions on Android
2. **No Automatic Reset:** Once locked, settings remain locked until the camera is deactivated (`isActive={false}`) and reactivated
3. **Platform Support:** Android only - iOS not supported
4. **Convergence Delay:** ~500ms delay required for AE/AWB to stabilize before locking

## Files Modified

| File | Changes |
|------|---------|
| `src/types/CameraProps.ts` | Add `autoLockOnPreviewStart`, `onExposureLocked` |
| `src/Camera.tsx` | Pass props to native |
| `android/.../CameraConfiguration.kt` | Add `exposureLocked`, `autoLockOnPreviewStart` |
| `android/.../CameraSession+Configuration.kt` | Apply `CONTROL_AE_LOCK` |
| `android/.../CameraSession.kt` | Auto-lock mechanism |
| `android/.../react/CameraView.kt` | Bridge props |
| `android/.../react/CameraViewManager.kt` | Expose prop + event |

## Testing

1. Enable `autoLockOnPreviewStart={true}`
2. Wait for `onExposureLocked` callback
3. Capture sequence of photos
4. Verify EXIF data shows identical:
   - ISO (ISOSpeedRatings)
   - Exposure time (ExposureTime)
   - White balance (WhiteBalance)

## Related Camera2 Parameters

| Parameter | Description | Status |
|-----------|-------------|--------|
| `CONTROL_AE_LOCK` | Locks auto-exposure | Implemented |
| `CONTROL_AWB_MODE` | White balance mode | Fixed to FLUORESCENT |
| `SENSOR_SENSITIVITY` | ISO value | Read-only (locked via AE_LOCK) |
| `SENSOR_EXPOSURE_TIME` | Shutter speed | Read-only (locked via AE_LOCK) |

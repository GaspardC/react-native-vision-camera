# Auto-Lock Exposure Feature

## Overview

Feature to automatically lock exposure (AE) and white balance (AWB) after camera preview stabilizes. Designed for medical/scientific imaging where photo sequences must have identical exposure settings.

## Use Case

- Capture sequences of 124 photos per examination
- All photos must have consistent exposure and color
- Lock happens automatically before first capture

## API

### Props

```tsx
interface CameraProps {
  /**
   * Auto-lock exposure and AWB after preview stabilization (~500ms).
   * @platform Android
   * @default false
   */
  autoLockOnPreviewStart?: boolean

  /**
   * Callback when exposure is locked and ready for stable captures.
   * @platform Android
   */
  onExposureLocked?: () => void
}
```

### Usage

```tsx
<Camera
  device={device}
  isActive={true}
  photo={true}
  autoLockOnPreviewStart={true}
  onExposureLocked={() => {
    console.log('Ready for stable photo captures')
    startPhotoSequence()
  }}
/>
```

## Behavior

```
[Camera starts] → AE/AWB in AUTO mode
       ↓
[Preview starts] → AE/AWB converge (~500ms)
       ↓
[CONTROL_AE_LOCK = true] → Exposure locked
       ↓
[onExposureLocked callback] → App notified
       ↓
[Capture photos] → All with identical settings
       ↓
[Preview stops] → CONTROL_AE_LOCK = false (reset)
       ↓
[New session] → AE/AWB re-adapt to new conditions
```

## Implementation Details

### Android (Camera2 via CameraX)

| Parameter | Value | Effect |
|-----------|-------|--------|
| `CONTROL_AE_LOCK` | `true` | Locks auto-exposure |
| `CONTROL_AWB_MODE` | `FLUORESCENT` | Fixed white balance (~4000K) |

### Files Modified

**VisionCamera library:**
- `package/src/types/CameraProps.ts` - Added props
- `package/src/Camera.tsx` - Bridge to native
- `package/src/NativeCameraView.ts` - Native props
- `package/android/.../CameraConfiguration.kt` - Config fields
- `package/android/.../CameraSession+Configuration.kt` - AE_LOCK logic
- `package/android/.../CameraView.kt` - Auto-lock trigger & reset
- `package/android/.../CameraViewManager.kt` - React prop
- `package/android/.../Events.kt` - ExposureLocked event

## Limitations

- **Android only** - iOS not supported
- **Photo mode only** - Not for video
- **HDR incompatible** - AWB FLUORESCENT conflicts with HDR/Night Mode
- **Manual reset** - Toggle `isActive` to re-adapt exposure

## Commits

```
93e0b730 docs: add auto-lock exposure specification
e5d260e3 feat(js): add autoLockOnPreviewStart prop and onExposureLocked callback
55f49254 feat(android): implement CONTROL_AE_LOCK via Camera2Interop
a9064277 feat(android): auto-lock exposure on preview stabilization
4656b9f5 feat(android): expose autoLockOnPreviewStart prop via React Native bridge
f145ffb6 fix(android): reset exposure lock when preview stops
```

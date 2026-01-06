# Auto-Lock Exposure & Focus Feature

## Overview

Feature to automatically lock exposure (AE), white balance (AWB), and focus (AF) for stable photo sequences. Designed for medical/scientific imaging where photo sequences must have identical exposure and focus settings.

## Use Case

- Capture sequences of 124 photos per examination
- All photos must have consistent exposure, color, and focus
- Exposure/AWB lock happens automatically after preview stabilization
- Focus lock happens automatically after manual tap-to-focus

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
[Camera starts] → AE/AWB/AF in AUTO mode
       ↓
[Preview starts] → AE/AWB converge (~500ms)
       ↓
[CONTROL_AE_LOCK = true] → Exposure locked
       ↓
[onExposureLocked callback] → App notified
       ↓
[User taps to focus] → Focus at point → AF locked
       ↓
[Capture photos] → All with identical exposure/focus
       ↓
[Preview stops] → All locks reset (AE/AF)
       ↓
[New session] → AE/AWB/AF re-adapt to new conditions
```

## Implementation Details

### Android (Camera2 via CameraX)

| Parameter | Value | Effect |
|-----------|-------|--------|
| `CONTROL_AE_LOCK` | `true` | Locks auto-exposure |
| `CONTROL_AWB_MODE` | `FLUORESCENT` | Fixed white balance (~4000K) |
| `CONTROL_AF_MODE` | `AUTO` | Focus locked after tap (one-shot) |

### Files Modified

**VisionCamera library:**
- `package/src/types/CameraProps.ts` - Added props
- `package/src/Camera.tsx` - Bridge to native
- `package/src/NativeCameraView.ts` - Native props
- `package/android/.../CameraConfiguration.kt` - Config fields (exposureLocked, focusLocked)
- `package/android/.../CameraSession+Configuration.kt` - AE_LOCK + AF_MODE logic
- `package/android/.../CameraSession+Focus.kt` - Auto-lock focus after tap
- `package/android/.../CameraView.kt` - Auto-lock trigger & reset
- `package/android/.../CameraViewManager.kt` - React prop
- `package/android/.../Events.kt` - ExposureLocked event

## Focus Lock Behavior

The focus is automatically locked after any successful tap-to-focus:

1. User taps on the camera preview to focus
2. Camera performs autofocus at that point
3. If focus is successful, `CONTROL_AF_MODE` is set to `AUTO` (one-shot mode)
4. Focus remains locked at that point until preview stops
5. When preview stops (e.g., new exam), focus lock is reset along with exposure lock

**Note:** Focus lock is independent of `autoLockOnPreviewStart` - it happens automatically after any tap-to-focus.

## Limitations

- **Android only** - iOS not supported
- **Photo mode only** - Not for video
- **HDR incompatible** - AWB FLUORESCENT conflicts with HDR/Night Mode
- **Manual reset** - Toggle `isActive` to re-adapt exposure and focus

## Commits

```
93e0b730 docs: add auto-lock exposure specification
e5d260e3 feat(js): add autoLockOnPreviewStart prop and onExposureLocked callback
55f49254 feat(android): implement CONTROL_AE_LOCK via Camera2Interop
a9064277 feat(android): auto-lock exposure on preview stabilization
4656b9f5 feat(android): expose autoLockOnPreviewStart prop via React Native bridge
f145ffb6 fix(android): reset exposure lock when preview stops
```

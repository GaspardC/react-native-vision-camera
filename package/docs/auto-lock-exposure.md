# Auto-Lock Exposure & Focus Feature

## Overview

Automatically lock camera parameters for stable photo sequences in medical/scientific imaging.

## Locked Parameters

| Parameter | Camera2 API | Value | When | Effect |
|-----------|-------------|-------|------|--------|
| **Exposure** | `CONTROL_AE_LOCK` | `true` | 1s after preview start or the last torch change | Freezes ISO + shutter speed + gain |
| **White Balance** | `CONTROL_AWB_MODE` | `FLUORESCENT` | Always | Fixed ~4000K (LED optimized) |
| **Focus** | CameraX `FocusMeteringAction` | auto-cancel disabled | After tap-to-focus | Holds focus distance until the next tap |

## Why These Parameters?

`AE_LOCK` covers the entire exposure chain:
- `SENSOR_SENSITIVITY` (ISO) - included in AE lock
- `SENSOR_EXPOSURE_TIME` - included in AE lock
- Gain/amplification - included in AE lock

Other parameters not locked:
- `NOISE_REDUCTION_MODE` - minimal impact on consistency
- `EDGE_MODE` (sharpening) - post-processing, not critical
- `TONEMAP_MODE` - device compatibility risk

## API

```tsx
<Camera
  autoLockOnPreviewStart={true}
  onExposureLocked={() => console.log('Ready!')}
/>
```

## Behavior Flow

```
[Camera starts] → AE/AWB/AF in AUTO
       ↓
[1s without preview/torch change] → AE locked + onExposureLocked()
       ↓
[User tap] → AF held at point until the next tap
       ↓
[124 photos] → Consistent exposure/focus
       ↓
[isActive=false] → AE lock reset, CameraX releases focus
```

## Limitations

- Android only (Camera2 via CameraX)
- Photo mode only
- HDR incompatible (AWB FLUORESCENT conflict)
- Reset requires `isActive` toggle

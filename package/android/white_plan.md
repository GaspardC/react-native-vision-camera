# White Balance Implementation Guide

## Overview
This document describes the implementation of white balance control in the React Native Vision Camera, with specific focus on medical imaging applications using the device's torch.

## Working Solution ✅

### Implementation
```kotlin
// Preview Configuration
val previewExtender = Camera2Interop.Extender(preview)
if (configuration.whiteBalanceLocked) {
  previewExtender.setCaptureRequestOption(
    CaptureRequest.CONTROL_AWB_MODE,
    CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
  )
} else {
  previewExtender.setCaptureRequestOption(
    CaptureRequest.CONTROL_AWB_MODE,
    CaptureRequest.CONTROL_AWB_MODE_AUTO
  )
}

// Photo Configuration
val photoExtender = Camera2Interop.Extender(photo)
if (configuration.whiteBalanceLocked) {
  photoExtender.setCaptureRequestOption(
    CaptureRequest.CONTROL_AWB_MODE,
    CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
  )
} else {
  photoExtender.setCaptureRequestOption(
    CaptureRequest.CONTROL_AWB_MODE,
    CaptureRequest.CONTROL_AWB_MODE_AUTO
  )
}
```

### Key Points
1. Uses fixed `DAYLIGHT` mode when white balance lock is requested
2. Maintains consistency between preview and photo capture
3. Works optimally with torch enabled
4. No timing dependencies or complex state management

## Why It Works

### Color Temperature Alignment
- Phone LED torch: 5500-6500K
- Daylight: ~5600K
- Perfect match means no color compensation needed

### Medical Imaging Benefits
- Accurate tissue color reproduction
- Consistent results with torch enabled
- No color tinting artifacts
- Works across different devices

## Technical Considerations

### Best Practices
1. **Configuration**:
   - Apply same settings to both preview and photo
   - Keep implementation simple
   - Avoid manual color correction

2. **Extension Compatibility**:
   - Cannot use with HDR
   - Cannot use with Night mode
   - Clear error messages for conflicts

### Color Temperature Reference
- LED Flash/Torch: 5500-6500K
- Daylight: ~5600K
- Warm Fluorescent: 2700-3000K (not recommended)

## Success Criteria ✓
- No green or purple tint
- Accurate color reproduction
- Consistent between preview and capture
- Reliable with torch enabled
- No extension conflicts

## References
- [Camera2 AWB Modes](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AWB_MODE)
- [Color Temperature Guide](https://en.wikipedia.org/wiki/Color_temperature)
- [LED Lighting Color Temperature](https://www.energy.gov/energysaver/led-lighting) 
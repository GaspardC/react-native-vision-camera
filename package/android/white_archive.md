# White Balance Implementation Archive

This file contains the development history and implementation details of the white balance feature.

## Original Implementation Plan

### Root Cause Analysis
1. **Inconsistent AWB Settings**: Different settings between preview and photo outputs
2. **Color Correction Interference**: Manual color correction causing issues
3. **Extension Conflicts**: HDR/Night mode extensions interfering with white balance

### Attempted Solutions
1. **AUTO + Lock Approach**:
   - Produced green tint
   - Had timing/delay issues
   - Created extension conflicts

2. **WARM_FLUORESCENT Mode**:
   - Produced purple tint
   - Over-compensated for perceived warm light
   - Not suitable with LED torch

3. **Dual Lock Mechanism**:
   - Added unnecessary complexity
   - Didn't resolve green tint

## Technical Details

### Camera2 API Integration
- **CaptureRequest Control** [Source](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest)
  - `CONTROL_AWB_MODE`: Controls auto-white-balance (AWB) algorithm
  - `CONTROL_AWB_LOCK`: Locks the white balance when set to true
  - `COLOR_CORRECTION_MODE`: Controls color correction processing

### Extension Conflicts
- Cannot use HDR with white balance lock
- Cannot use Night mode with white balance lock
- Must check for conflicts during configuration

### Device Limitations
- Some devices may not support AWB lock
- Different devices may need different AWB modes
- Need to handle device-specific quirks

## Original Testing Strategy

1. **Basic Testing**
   - Test white balance lock in different lighting
   - Verify color accuracy with color chart
   - Check lock persistence

2. **Device Testing**
   - Test on multiple device manufacturers
   - Document device-specific behaviors
   - Create device-specific workarounds if needed

3. **Performance Testing**
   - Measure AWB convergence time
   - Check impact on capture latency
   - Monitor memory and CPU usage

## References
- [Camera2 AWB Modes](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AWB_MODE)
- [CameraX Extensions](https://developer.android.com/media/camera/camerax/extensions-api)
- [White Balance Issues SO](https://stackoverflow.com/questions/68452665/camera2-api-set-white-balance-but-get-green-picture) 
# White Balance Lock Implementation Plan

## Latest Findings - Green Tint Fix

### Root Cause Analysis
1. **Inconsistent AWB Settings**: Different settings between preview and photo outputs
2. **Color Correction Interference**: Manual color correction causing issues
3. **Extension Conflicts**: HDR/Night mode extensions interfering with white balance

### Solution Implementation
1. **Proper AWB Lock Sequence**:
   - Set `CONTROL_AWB_MODE` to AUTO first
   - Apply `CONTROL_AWB_LOCK` consistently
   - Use `COLOR_CORRECTION_MODE_HIGH_QUALITY`

2. **Extension Handling**:
   - Prevent HDR/Night mode when using white balance lock
   - Throw clear error messages for conflicts

3. **Dual Lock Mechanism**:
   - Primary: Camera2Interop AWB lock
   - Backup: FocusMeteringAction

## Knowledge Base

### Camera2 API Understanding
- **CaptureRequest Control** [Source](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest)
  - `CONTROL_AWB_MODE`: Controls auto-white-balance (AWB) algorithm
  - `CONTROL_AWB_LOCK`: Locks the white balance when set to true
  - `COLOR_CORRECTION_MODE`: Controls color correction processing

### CameraX Integration
- **Camera2Interop** [Source](https://developer.android.com/media/camera/camerax/extensions-api)
  - Provides bridge between CameraX and Camera2 APIs
  - Use `Camera2Interop.Extender` to access Camera2 features
  - Must be applied to both preview and photo outputs

### Known Issues & Solutions
1. **Green Tint Issue** [Source](https://stackoverflow.com/questions/68452665/camera2-api-set-white-balance-but-get-green-picture)
   - Problem: Images turn green when using custom white balance settings
   - Root Cause: Incorrect color correction and AWB sequence
   - Solution: Use proper AWB lock sequence and avoid manual color correction

### Best Practices
- **Extensions API** [Source](https://developer.android.com/media/camera/camera-extensions)
  - Avoid using extensions with white balance lock
  - HDR and Night mode conflict with AWB lock
  - Use high-quality color correction mode

## Implementation Status

### Phase 1: Core Implementation ✓
- [x] Basic white balance configuration
- [x] Camera2Interop integration
- [x] Preview and photo configuration

### Phase 2: Green Tint Fix [In Progress]
- [x] Proper AWB sequence
- [x] Consistent settings across outputs
- [x] Extension conflict prevention
- [ ] Test different AWB modes

### Phase 3: Testing
- [ ] Test on various devices
- [ ] Verify color accuracy
- [ ] Check extension conflicts
- [ ] Monitor performance impact

## Technical Constraints
1. **Configuration Timing**:
   - AWB settings must be set during initial configuration
   - Cannot modify AWB after camera is bound
   - Must be consistent across preview and photo outputs

2. **Extension Conflicts**:
   - Cannot use HDR with white balance lock
   - Cannot use Night mode with white balance lock
   - Must check for conflicts during configuration

3. **Device Limitations**:
   - Some devices may not support AWB lock
   - Different devices may need different AWB modes
   - Need to handle device-specific quirks

## Next Steps
1. Test current implementation on different devices
2. If green tint persists:
   - Try fixed white balance modes (DAYLIGHT, CLOUDY)
   - Implement device-specific handling
   - Consider disabling lock on problematic devices

## Success Criteria
1. No green tint in photos
2. Consistent colors across captures
3. Works on major Android devices
4. No conflicts with other camera features

## References
- [Camera2 AWB Modes](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AWB_MODE)
- [CameraX Extensions](https://developer.android.com/media/camera/camerax/extensions-api)
- [White Balance Issues SO](https://stackoverflow.com/questions/68452665/camera2-api-set-white-balance-but-get-green-picture)

## Current Issue: Green Tint
Photos are showing a green tint when white balance lock is enabled. This is a known issue with Camera2 API on some devices.

## Valid Solution Approaches

### Approach 1: Initial Configuration Lock [Current Implementation]
**Theory**: Set AWB lock during initial camera configuration
```kotlin
// In Preview configuration
val previewExtender = Camera2Interop.Extender(preview)
previewExtender.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
if (configuration.whiteBalanceLocked) {
  previewExtender.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
}

// In Photo configuration
val photoExtender = Camera2Interop.Extender(photo)
photoExtender.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
photoExtender.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, if (configuration.whiteBalanceLocked) true else false)
```

### Approach 2: Fixed White Balance Mode
**Theory**: Use a fixed white balance mode instead of AUTO + lock
```kotlin
// Use a fixed mode like DAYLIGHT or CLOUDY
previewExtender.setCaptureRequestOption(
  CaptureRequest.CONTROL_AWB_MODE,
  CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
)
```

### Approach 3: Device-Specific Handling
**Theory**: Different devices need different AWB strategies
```kotlin
when (Build.MANUFACTURER.lowercase()) {
  "samsung" -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
  "huawei" -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
  else -> CaptureRequest.CONTROL_AWB_MODE_AUTO
}
```

## Implementation Priority

1. **Try Approach 3 First**
   - Most balanced solution
   - Follows Camera2 API best practices
   - Least likely to cause side effects

2. **Fall Back to Approach 1**
   - If Approach 3 still shows green tint
   - Increase delay time if needed

3. **Consider Approach 4**
   - If issues persist on specific devices
   - Maintain device-specific configurations

4. **Last Resort: Approach 2**
   - Most complex to implement
   - Requires careful calibration
   - May need device-specific tuning

## Testing Strategy

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

## Success Criteria
1. No green tint in photos
2. Consistent colors across captures
3. White balance remains locked
4. Works across different devices
5. Minimal impact on capture performance

## References
- [Camera2 AWB Modes](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#CONTROL_AWB_MODE)
- [White Balance Issues SO](https://stackoverflow.com/questions/68452665/camera2-api-set-white-balance-but-get-green-picture)
- [CameraX Extensions](https://developer.android.com/media/camera/camerax/extensions-api)

## Next Steps
1. Implement Approach 3
2. Test on development device
3. If green tint persists:
   - Try increasing convergence delay
   - Test on different devices
   - Consider device-specific approach 
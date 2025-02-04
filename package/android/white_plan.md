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

### Phase 1 & 2: Core Implementation & Integration ✓
- [x] Basic white balance lock
- [x] Camera2Interop integration
- [x] Preview and photo configuration

### Phase 3: Green Tint Fix ✓
- [x] Proper AWB sequence
- [x] Consistent settings across outputs
- [x] Extension conflict prevention
- [x] Color correction mode setting

### Phase 4: Testing
- [ ] Test on various devices
- [ ] Verify color accuracy
- [ ] Check extension conflicts
- [ ] Monitor performance impact

## Next Steps
1. Test on different Android devices
2. Monitor for device-specific issues
3. Consider adding device-specific fallbacks
4. Document any remaining limitations

## References
- [Camera2 API Reference](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest)
- [CameraX Extensions Guide](https://developer.android.com/media/camera/camerax/extensions-api)
- [White Balance Issues SO](https://stackoverflow.com/questions/68452665/camera2-api-set-white-balance-but-get-green-picture) 
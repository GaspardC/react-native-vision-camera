# White Balance Lock Implementation Plan

## Overview
Implement a hardcoded white balance lock for React Native Vision Camera (Android only).
This will help stabilize image quality for cervical cancer detection imaging.

## Knowledge Base

### Camera2 API Understanding
- **CaptureRequest Control** [Source](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest)
  - `CONTROL_AWB_MODE`: Controls auto-white-balance (AWB) algorithm
  - `CONTROL_AWB_LOCK`: Locks the white balance when set to true
  - Proper sequence: Set AWB mode first, then lock

### CameraX Integration
- **Camera2Interop** [Source](https://developer.android.com/media/camera/camerax/extensions-api)
  - Provides bridge between CameraX and Camera2 APIs
  - Use `Camera2Interop.Extender` to access Camera2 features
  - Avoid internal implementation classes (like Camera2CameraInfoImpl)

### Best Practices
- **Extensions API** [Source](https://developer.android.com/media/camera/camera-extensions)
  - Use vendor extensions for enhanced capabilities
  - HDR and Night mode extensions available
  - Extensions may conflict with custom Camera2 controls

### Known Issues
- Camera2Interop limitations with certain devices
- White balance lock might reset on some configurations
- Need to handle extension conflicts

## Implementation Steps

### Phase 1: Core Implementation ✓
- [x] Add white balance lock configuration
  - Added `whiteBalanceLocked` boolean to `CameraConfiguration` (default: true)

### Phase 2: Camera2 Integration ✓
- [x] Preview Configuration
  - Using Camera2Interop.Extender with Preview.Builder
  - Set `CONTROL_AWB_MODE` to `AUTO` first
  - Enable `CONTROL_AWB_LOCK` to lock the white balance

### Phase 3: Camera Control Implementation
- [ ] Fix Camera Control Implementation
  - Remove dependency on internal Camera2CameraInfoImpl
  - Options to explore:
    1. Use public CameraX APIs only
    2. Use Camera2Interop properly with public APIs
    3. Implement fallback mechanism for unsupported devices

### Phase 4: Testing
- [ ] Basic Testing
  - Build command: `cd /Users/gaspardc/Development/CervixMono/apps/expo-eject && bun expo prebuild && bun android`
  - Clear logcat: `~/Library/Android/sdk/platform-tools/adb logcat -c`
  - Debug command: `~/Library/Android/sdk/platform-tools/adb logcat -v time | grep -E "ch.epfl.examvia|VisionCamera|CameraSession|Camera2"`
  - Test cases:
    1. White balance lock persistence
    2. Stability under different lighting
    3. Compatibility with photo/video modes
    4. Extension conflicts handling

### Phase 5: Documentation
- [ ] Update Documentation
  - Document the permanent white balance lock behavior
  - Add notes about Android-only implementation
  - Include known limitations and device-specific issues

## Current Issues
1. Compilation error with Camera2CameraInfoImpl import
   - Solution: Use public CameraX APIs instead of internal implementation
2. Need to verify if our approach with FocusMeteringAction is correct
   - Solution: Use Camera2Interop for direct white balance control

## Next Steps
1. Implement proper Camera2Interop usage following official documentation
2. Add fallback mechanism for devices with limited support
3. Test on various Android devices and API levels
4. Document device-specific behaviors and limitations

## Technical Notes
- Camera2 API sequence is critical:
  1. Set AWB mode to AUTO
  2. Wait for convergence (if needed)
  3. Enable AWB lock
- Potential conflicts:
  1. HDR extension vs. white balance lock
  2. Night mode vs. white balance lock
  3. Video stabilization vs. camera controls
- Error handling needed for:
  1. Unsupported devices
  2. Extension conflicts
  3. Lock failures

## References
- [Camera2 API Reference](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest)
- [CameraX Extensions Guide](https://developer.android.com/media/camera/camerax/extensions-api)
- [Camera Extensions API](https://developer.android.com/media/camera/camera-extensions)
- [Camera2 Extensions API](https://developer.android.com/media/camera/camera2/extensions-api) 
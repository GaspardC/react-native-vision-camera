# White Balance Fix Implementation Plan

## Current State
- Using fixed `DAYLIGHT` mode (5600K) for white balance
- Torch is always enabled during capture

- Images show a persistent yellowish tint across all exposure values
- White balance lock is required for photo homogeneity
- Native camera app shows better results with torch enabled

## Objective
Maintain consistent white balance while eliminating the yellowish tint in images, keeping the torch always on.

## Implementation Plan

### Phase 1: White Balance Mode Adjustment ✅
1. **Current Implementation**
   - Fixed `CONTROL_AWB_MODE_DAYLIGHT` (5600K)
   - Yellowish tint persists regardless of exposure settings
   - Different from native camera app behavior

2. **Proposed Changes**
   - Test alternative fixed white balance modes:
     1. `CONTROL_AWB_MODE_CLOUDY_DAYLIGHT` (~6500K)
     2. `CONTROL_AWB_MODE_FLUORESCENT` (~4000K)
     3. `CONTROL_AWB_MODE_SHADE` (~7500K)
   - Implement mode selection based on testing results
   - Consider making mode configurable via props

3. **Success Criteria**
   - Eliminated yellow tint
   - Matches native camera app quality
   - Consistent results across captures
   - Natural-looking colors
   - Works consistently across different devices

4. **Testing Protocol**
   - Compare each mode with native camera app results
   - Test in various lighting conditions with torch on
   - Document color accuracy for each mode
   - Verify consistency across different devices
   - Compare with professional medical imaging requirements

### Phase 2: Delayed White Balance Lock (Future Implementation) ⏳
1. **Current Issue**
   - White balance might lock before torch reaches stable temperature
   - LED color temperature needs time to stabilize

2. **Proposed Solution**
   - Add delay between torch activation and white balance lock
   - Implement state management for timing coordination
   - Consider device-specific variations

3. **Technical Considerations**
   - Requires modification of camera session configuration
   - Need to handle state management carefully
   - Consider edge cases (e.g., rapid capture sequences)
   - Maintain compatibility with existing implementation

4. **Implementation Risks**
   - More complex state management
   - Potential timing issues
   - Device-specific variations
   - Integration with existing camera workflow

## References
- LED Torch color temperature range: 5500-6500K
- Daylight color temperature: ~5600K
- Cloudy Daylight: ~6500K
- Fluorescent: ~4000K
- Shade: ~7500K
- Current implementation in `CameraSession+Configuration.kt`

## Success Metrics
1. No visible yellow tint in images
2. Color accuracy matches native camera app
3. Consistent white balance across captures
4. Maintained image quality
5. Reliable operation across different devices

## Timeline
1. Phase 1: Immediate implementation and testing of different white balance modes
2. Phase 2: Consider implementation based on Phase 1 results 
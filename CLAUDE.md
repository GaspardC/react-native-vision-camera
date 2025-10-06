# VisionCamera Development Guide

> This file provides guidance for Claude Code (Anthropic's AI coding assistant) when working on this codebase. It contains architecture details, build commands, and development patterns specific to react-native-vision-camera.

## Project Overview

VisionCamera is a powerful, high-performance React Native Camera library featuring photo/video capture, QR/barcode scanning, Frame Processors (real-time processing via JS worklets), and GPU-accelerated rendering with Skia integration.

**Repository Structure:**
- `/package/` - Main library code (published to npm)
- `/example/` - Example React Native app for testing
- `/docs/` - Documentation website (Docusaurus)

This is a **monorepo** using workspaces (see root `package.json`).

## Common Commands

All commands should be run from `/package/` directory unless specified otherwise.

### JavaScript/TypeScript
```bash
bun typecheck              # Type check without emitting files
bun typescript             # Type check (same as typecheck)
bun lint                   # Lint and auto-fix JS/TS code
bun lint-ci                # Lint for CI (GitHub Actions format)
bun build                  # Build library with react-native-builder-bob
bun start                  # Start Metro bundler for example app
```

### iOS Development
```bash
bun check-ios              # Format Swift + lint Swift/C++
bun clean-ios              # Clean iOS build artifacts
bun pods                   # Install CocoaPods (from example dir)
```

**Prerequisites (iOS):**
```bash
xcode-select --install
brew install swiftformat swiftlint
```

### Android Development
```bash
bun check-android          # Format Kotlin + lint C++
bun clean-android          # Clean Android build artifacts
```

**Prerequisites (Android):**
```bash
brew install ktlint
```

### Cross-Platform
```bash
bun check-cpp              # Format C++ code with clang-format
bun check-js               # Lint JS/TS and run TypeScript
bun check-all              # Run all linters and formatters (Swift, Kotlin, C++, JS/TS)
bun clean-js               # Clean JS build artifacts
bun bootstrap              # Install all dependencies and pods
```

### Build & Release
```bash
bun build                  # Build library
bun release                # Build and release with release-it
```

## Architecture Overview

### React Native Bridge Architecture

VisionCamera uses a **multi-layer architecture** with native modules, JSI (JavaScript Interface), and Worklets:

#### 1. **JavaScript Layer** (`/package/src/`)
- **Main Component**: `Camera.tsx` - React component that renders native camera view
- **Hooks**: `useCameraDevice`, `useCameraFormat`, `useFrameProcessor`, `useCameraPermission`
- **Types**: Comprehensive TypeScript definitions for all camera features
- **Frame Processors**: Worklet-based real-time frame processing

#### 2. **Native Module Layer** (Platform Bridge)

**iOS** (`/package/ios/React/`):
- `CameraViewManager.m/.swift` - React Native view manager (bridge)
- `CameraView.swift` - UIView subclass that manages the camera UI
- Exports view properties and methods via `RCT_EXPORT_VIEW_PROPERTY` and `RCT_EXTERN_METHOD`

**Android** (`/package/android/src/main/java/com/mrousavy/camera/react/`):
- `CameraViewManager.kt` - React Native view manager
- `CameraView.kt` - Android View that manages camera UI
- `CameraViewModule.kt` - TurboModule for camera functions
- `CameraPackage.kt` - Registers native modules

#### 3. **Core Camera Layer** (Platform-Specific)

**iOS** (`/package/ios/Core/`):
- `CameraSession.swift` - Main camera session manager using AVFoundation
- `CameraConfiguration.swift` - Configuration diffing and updates
- `PreviewView.swift` - Camera preview rendering
- `RecordingSession.swift` - Video recording management
- Uses **AVCaptureSession** with separate audio/video capture sessions
- Handles orientation via `OrientationManager`

**Android** (`/package/android/src/main/java/com/mrousavy/camera/core/`):
- `CameraSession.kt` - Main camera session using **CameraX**
- `CameraConfiguration.kt` - Configuration management
- Uses CameraX lifecycle with `Preview`, `ImageCapture`, `VideoCapture`, `ImageAnalysis` use cases
- Threading: Separate queues for camera, audio, and location

#### 4. **Frame Processors Layer** (Optional, C++/JSI)

When `react-native-worklets-core` is installed, Frame Processors enable real-time frame processing:

**iOS** (`/package/ios/FrameProcessors/`):
- `VisionCameraProxy.mm` - JSI bridge to native
- `FrameHostObject.mm` - JSI host object exposing Frame to JS
- `FrameProcessorPlugin.h` - Base class for native plugins
- Frame = `CMSampleBuffer` wrapper

**Android** (`/package/android/src/main/cpp/frameprocessors/`):
- `VisionCameraProxy.cpp` - JSI bridge implementation
- `FrameHostObject.cpp` - JSI host object for Frame
- `JFrame.cpp` - Java/JNI bindings
- Frame = `android.media.Image` wrapper

**Shared C++ Headers** expose Frame methods like `toArrayBuffer()`, dimensions, pixel format, etc.

### Key Architecture Patterns

#### Configuration Diffing
Both iOS and Android use **configuration diffing** to minimize camera reconfigurations:
1. User updates props → new configuration object created
2. Diff calculated between old and new config
3. Only changed properties trigger camera updates
4. Updates batched under lock (iOS: `CameraQueues.cameraQueue`, Android: `Mutex`)

#### Threading Model
- **iOS**: Dedicated dispatch queues (`CameraQueues.cameraQueue`, `.audioQueue`, `.locationQueue`)
- **Android**: Coroutines with `Mutex` locking, UI thread for lifecycle
- Frame Processors run on **dedicated JS runtime** (Worklets) on separate thread

#### Session Lifecycle
1. `isActive={true}` → Start capture session
2. Camera configured → `onInitialized` event
3. Session running → `onStarted` event
4. Preview ready → `onPreviewStarted` event
5. `isActive={false}` → Stop session → `onStopped` event

#### Frame Flow (with Frame Processors enabled)
1. Camera captures frame → native callback
2. Frame wrapped in `FrameHostObject` (JSI)
3. Passed to JS Worklet function (via `react-native-worklets-core`)
4. User's frame processor runs synchronously
5. Native plugins called via JSI (zero-copy)
6. Frame reference counted and released

### Build Configuration

#### Conditional Compilation

**Frame Processors** (optional):
- iOS: `$VCEnableFrameProcessors` in Podfile → `VISION_CAMERA_ENABLE_FRAME_PROCESSORS` Swift flag
- Android: `VisionCamera_enableFrameProcessors` in gradle.properties → CMake `ENABLE_FRAME_PROCESSORS`
- Auto-disabled if `react-native-worklets-core` not found

**Location Tags** (optional):
- iOS: `$VCEnableLocation` in Podfile → `VISION_CAMERA_ENABLE_LOCATION` Swift flag

**Code Scanner** (optional):
- Android: `VisionCamera_enableCodeScanner` → includes MLKit barcode scanning (2.4MB)

#### Build Systems

**iOS** (CocoaPods):
- `VisionCamera.podspec` defines 3 subspecs:
  - `Core` - Swift camera core
  - `React` - React Native bridge
  - `FrameProcessors` - C++ JSI layer (conditional)
- C++ standard: `c++17`

**Android** (Gradle + CMake):
- `build.gradle` configures CameraX, Kotlin coroutines, MLKit
- `CMakeLists.txt` builds C++ Frame Processor layer
- Links: `ReactAndroid::jsi`, `fbjni`, `react-native-worklets-core` (conditional)
- Supports New Architecture (TurboModules/Fabric)

**JavaScript** (react-native-builder-bob):
- Outputs: CommonJS, ES Modules, TypeScript definitions
- Source: `/package/src/` → Output: `/package/lib/`

### Module Registration

**iOS**: Automatic via `RCT_EXTERN_REMAP_MODULE` in `CameraViewManager.m`

**Android**: Registered in `react-native.config.js`:
```javascript
android: {
  packageImportPath: 'import com.mrousavy.camera.react.CameraPackage;'
}
```

### Important Files

**Entry Points:**
- `/package/src/index.ts` - Main JS exports
- `/package/src/Camera.tsx` - Main Camera component
- `/package/ios/React/CameraViewManager.m` - iOS bridge entry
- `/package/android/src/main/java/com/mrousavy/camera/react/CameraViewManager.kt` - Android bridge entry

**Configuration:**
- `/package/tsconfig.json` - TypeScript config (strict mode enabled)
- `/package/.eslintrc.js` - ESLint config with custom hooks rules
- `/package/VisionCamera.podspec` - iOS CocoaPods spec
- `/package/android/build.gradle` - Android Gradle config
- `/package/android/CMakeLists.txt` - Android C++ config

## Development Workflow

### Working on Native Code

**iOS:**
1. Open `/example/ios/VisionCameraExample.xcworkspace` in Xcode
2. Edit library code in `/package/ios/`
3. Build with ⌘+B to run swiftlint/swiftformat
4. Test in example app
5. Before committing: `bun check-ios` (from package dir)

**Android:**
1. Open `/example/android/` in Android Studio
2. Edit library code in `/package/android/`
3. Test in example app
4. Before committing: `bun check-android` (from package dir)

### Working on JavaScript/TypeScript

1. Edit files in `/package/src/`
2. Changes hot-reload in example app (Metro)
3. Run `bun typecheck` to verify types
4. Run `bun lint` to fix style issues
5. Build with `bun build` before publishing

### Linting Rules

- **No enums** - Use TypeScript union types instead
- **Strict TypeScript** - All strict checks enabled
- **No unsafe assignments** - TypeScript strict mode
- **Worklet hooks** - Special exhaustive-deps rules for `useFrameProcessor`, `useSkiaFrameProcessor`
- **Explicit return types** required for functions

### Pre-commit Checklist

Run `bun check-all` from `/package/` to:
1. Format Swift code (swiftformat)
2. Lint Swift code (swiftlint)
3. Lint Kotlin code (ktlint)
4. Format C++ code (clang-format)
5. Lint and fix JS/TS (eslint)
6. Type-check TypeScript

## Frame Processor Development

Frame Processors are **JavaScript Worklets** that run synchronously on each camera frame.

### Creating Frame Processor Plugins

**iOS** (`/package/ios/FrameProcessors/`):
```swift
@objc(YourPluginName)
public class YourPlugin: FrameProcessorPlugin {
  public override init(proxy: VisionCameraProxyHolder, options: [AnyHashable: Any]) {
    super.init(proxy: proxy, options: options)
  }

  public override func callback(_ frame: Frame, withArguments args: [AnyHashable: Any]) -> Any {
    let buffer = CMSampleBufferGetImageBuffer(frame.buffer)
    // Process buffer...
    return result
  }
}
```

**Android** (`/package/android/src/main/java/.../frameprocessors/`):
```kotlin
class YourPlugin(proxy: VisionCameraProxyHolder, options: Map<String, Any>) : FrameProcessorPlugin() {
  override fun callback(frame: Frame, arguments: Map<String, Any>): Any {
    val image = frame.image
    // Process image...
    return result
  }
}
```

**JavaScript**:
```typescript
const frameProcessor = useFrameProcessor((frame) => {
  'worklet'
  const result = yourPlugin(frame, options)
}, [options])
```

### Frame Processor Architecture

1. **Worklets Runtime** - Separate JS runtime from React Native
2. **JSI Bridge** - Zero-copy communication between JS and native
3. **Plugin Registry** - Plugins registered via `FrameProcessorPluginRegistry`
4. **Ref Counting** - Frames auto-released via `withFrameRefCounting`

## Testing

- Example app: `/example/` - Full featured demo app
- Run example: `bun start` (from package dir) or `yarn ios`/`yarn android` (from example dir)
- No unit tests currently in this repo

## Important Notes

- **Monorepo**: Changes to `/package/` auto-reflect in `/example/` due to workspace linking
- **Bun**: This project uses `bun` as package manager (not npm/yarn)
- **Frame Processors**: Require `react-native-worklets-core` peer dependency
- **Skia Integration**: Optional `@shopify/react-native-skia` for drawing on frames
- **CameraX**: Android uses CameraX (not Camera2 API directly)
- **AVFoundation**: iOS uses AVFoundation (not deprecated APIs)
- **Permissions**: Camera/Microphone/Location permissions must be requested before use

## Common Issues

### Build Errors
- Clean builds: `bun clean-ios`, `bun clean-android`, `bun clean-js`
- Delete `.cxx` folder if CMake cache corrupted
- Re-run `bun bootstrap` after pulling changes

### Frame Processors Not Working
- Ensure `react-native-worklets-core` installed
- Add worklets Babel plugin to `babel.config.js`
- Add `'worklet'` directive to frame processor function
- Check Frame Processors enabled in build config

### Type Errors
- Run `bun typecheck` to see all TypeScript errors
- Strict mode enabled - no implicit `any`, `null` checks required
- Use type guards for narrowing union types
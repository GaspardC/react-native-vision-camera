from pathlib import Path
import os,subprocess,tempfile
repo=Path(__file__).resolve().parents[2]
runner=repo/'package/scripts/test-exposure-lock.py'
ns={'__file__':str(runner)}
exec(runner.read_text().split('with tempfile.TemporaryDirectory')[0],ns)
view=(repo/'package/android/src/main/java/com/mrousavy/camera/react/CameraView.kt').read_text()
methods=view[view.index('  private fun triggerExposureLock() {'):view.index('  private fun resetAllLocks() {')]
methods=methods.replace('private fun triggerExposureLock()', 'fun triggerExposureLock()').replace('private fun releaseExposureLock()', 'fun releaseExposureLock()')
source=Path(__file__).with_name('exposure-lock-view.template.kt').read_text().replace('PRODUCTION_METHODS',methods)
print('Regenerated production triggerExposureLock/releaseExposureLock methods, including reportFailure lambda.',flush=True)
with tempfile.TemporaryDirectory(prefix='vision-camera-review-final-build-') as output:
    harness=Path(output)/'FinalRecheck.kt'
    harness.write_text(source)
    stub=Path(output)/'VideoStabilizationMode.kt'
    stub.write_text('package com.mrousavy.camera.core.types\nenum class VideoStabilizationMode(val unionValue: String) { OFF("off") }\n')
    core=repo/'package/android/src/main/java/com/mrousavy/camera/core'
    subprocess.run([ns['java'],'-cp',ns['compiler_path'],'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',ns['classpath'],'-d',output,str(core/'ExposureLockController.kt'),str(core/'CameraError.kt'),str(stub),str(harness)],check=True)
    subprocess.run([ns['java'],'-cp',os.pathsep.join([output,ns['classpath']]),'com.mrousavy.camera.core.FinalRecheckKt'],check=True,timeout=30)

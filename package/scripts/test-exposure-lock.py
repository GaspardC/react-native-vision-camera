#!/usr/bin/env python3
"""Run controller JVM checks with already cached Kotlin tooling, without building an app."""
from pathlib import Path
import os
import subprocess
import tempfile

package = Path(__file__).resolve().parents[1]
cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1'


def jar(artifact, version):
    matches = list((cache / artifact / version).glob('**/*.jar'))
    if not matches:
        raise SystemExit(f'Missing cached {artifact}:{version}; run with a Gradle cache containing Kotlin 2.1.20 and coroutines 1.9.0.')
    return str(matches[0])


stdlib = jar('org.jetbrains.kotlin/kotlin-stdlib', '2.1.20')
coroutines = jar('org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm', '1.9.0')
annotations = jar('org.jetbrains/annotations', '23.0.0')
compiler = jar('org.jetbrains.kotlin/kotlin-compiler-embeddable', '2.1.20')
reflect = jar('org.jetbrains.kotlin/kotlin-reflect', '2.0.21')
trove = jar('org.jetbrains.intellij.deps/trove4j', '1.0.20200330')
java = str(Path(os.environ['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in os.environ else 'java'
classpath = os.pathsep.join([stdlib, coroutines, annotations])
compiler_path = os.pathsep.join([compiler, stdlib, coroutines, annotations, reflect, trove])
with tempfile.TemporaryDirectory(prefix='vision-camera-exposure-') as output:
    # CameraError.kt only needs the enum's name; the controller does not use stabilization.
    stub = Path(output) / 'VideoStabilizationMode.kt'
    stub.write_text('package com.mrousavy.camera.core.types\nenum class VideoStabilizationMode(val unionValue: String) { OFF(\"off\") }\n')
    core = package / 'android/src/main/java/com/mrousavy/camera/core'
    test = package / 'android/src/test/java/com/mrousavy/camera/core/ExposureLockControllerTest.kt'
    subprocess.run([java, '-cp', compiler_path, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                    '-no-stdlib', '-no-reflect', '-classpath', classpath, '-d', output,
                    str(core / 'ExposureLockController.kt'), str(core / 'CameraError.kt'), str(stub), str(test)], check=True)
    subprocess.run([java, '-cp', os.pathsep.join([output, classpath]),
                    'com.mrousavy.camera.core.ExposureLockControllerTestKt'], check=True)

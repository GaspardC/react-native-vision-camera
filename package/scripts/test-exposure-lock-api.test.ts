import { describe, expect, test } from 'bun:test'
import { CameraRuntimeError, tryParseNativeCameraError } from '../src/CameraError'

// Evaluate the actual Camera class with platform/bridge stubs; no React Native runtime is needed.
const source = await Bun.file(new URL('../src/Camera.tsx', import.meta.url)).text()
const code = new Bun.Transpiler({ loader: 'tsx' }).transformSync(
  source.replace(/^import[\s\S]*?from ['"][^'"]+['"]\r?\n/gm, '').replace('export class Camera', 'class Camera'),
)

function cameraFor(os: string, bridge: Record<string, unknown>) {
  const Camera = new Function('React', 'Platform', 'StyleSheet', 'CameraModule', 'CameraRuntimeError', 'tryParseNativeCameraError',
    `${code}\nreturn Camera`)(
    { PureComponent: class {} }, { OS: os }, { create: (styles: unknown) => styles }, bridge,
    CameraRuntimeError, tryParseNativeCameraError,
  )
  const camera = Object.create(Camera.prototype) as { setExposureLocked(locked: boolean): Promise<void> }
  Object.defineProperty(camera, 'handle', { value: 42 })
  return camera
}

describe('Camera.setExposureLocked', () => {
  test('iOS and macOS reject before calling the native bridge', async () => {
    for (const os of ['ios', 'macos']) {
      const camera = cameraFor(os, new Proxy({}, { get: () => { throw new Error('Bridge must not be accessed') } }))
      await expect(camera.setExposureLocked(true)).rejects.toMatchObject({ code: 'device/exposure-lock-not-supported' })
      await expect(camera.setExposureLocked(false)).rejects.toMatchObject({ code: 'device/exposure-lock-not-supported' })
    }
  })

  test('forwards the view handle and boolean and waits for native confirmation', async () => {
    let confirm!: () => void
    let completed = false
    const calls: unknown[][] = []
    const camera = cameraFor('android', {
      setExposureLocked: (...args: unknown[]) => { calls.push(args); return new Promise<void>((resolve) => { confirm = resolve }) },
    })
    const pending = camera.setExposureLocked(true).then(() => { completed = true })
    await Promise.resolve()
    expect(calls).toEqual([[42, true]])
    expect(completed).toBe(false)
    confirm()
    await pending
    expect(completed).toBe(true)
    const unlocking = camera.setExposureLocked(false)
    expect(calls[1]).toEqual([42, false])
    confirm()
    expect(await unlocking).toBeUndefined()
  })

  test('parses native rejection without reporting success', async () => {
    const camera = cameraFor('android', {
      setExposureLocked: () => Promise.reject({ code: 'session/exposure-lock-failed', message: 'CameraX failed' }),
    })
    await expect(camera.setExposureLocked(true)).rejects.toMatchObject({ code: 'session/exposure-lock-failed' })
  })
})

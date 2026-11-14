import { FaceLandmarker, ImageSegmenter, type NormalizedLandmark } from '@mediapipe/tasks-vision'
import wasmLoaderPath from '@mediapipe/tasks-vision/vision_wasm_internal.js?url'
import wasmBinaryPath from '@mediapipe/tasks-vision/vision_wasm_internal.wasm?url'
import { BACKGROUNDS, type BackgroundPreset, type EffectSettings, LM, NO_EFFECTS, STICKERS } from './presets'

const FACE_MODEL = 'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task'
const SEGMENT_MODEL = 'https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/1/selfie_segmenter.tflite'

const fileset: Parameters<typeof ImageSegmenter.createFromOptions>[0] = { wasmLoaderPath, wasmBinaryPath }

async function withDelegate<T>(create: (delegate: 'GPU' | 'CPU') => Promise<T>): Promise<T> {
  try {
    return await create('GPU')
  } catch {
    return create('CPU')
  }
}

let segmenterPromise: Promise<ImageSegmenter> | null = null
let facePromise: Promise<FaceLandmarker> | null = null

const loadSegmenter = () =>
  (segmenterPromise ??= withDelegate((delegate) =>
    ImageSegmenter.createFromOptions(fileset, {
      baseOptions: { modelAssetPath: SEGMENT_MODEL, delegate },
      runningMode: 'VIDEO',
      outputConfidenceMasks: true,
      outputCategoryMask: false,
    }),
  ).catch((e) => {
    segmenterPromise = null
    throw e
  }))

const loadFace = () =>
  (facePromise ??= withDelegate((delegate) =>
    FaceLandmarker.createFromOptions(fileset, {
      baseOptions: { modelAssetPath: FACE_MODEL, delegate },
      runningMode: 'VIDEO',
      numFaces: 3,
    }),
  ).catch((e) => {
    facePromise = null
    throw e
  }))

const imageCache = new Map<string, HTMLImageElement>()
function image(src: string) {
  let img = imageCache.get(src)
  if (!img) {
    img = new Image()
    img.decoding = 'async'
    img.src = src
    imageCache.set(src, img)
  }
  return img
}

function createTicker(fps: number, onTick: () => void) {
  const interval = Math.round(1000 / fps)
  const code = `
    let last = 0, busy = false
    onmessage = () => { busy = false }
    setInterval(() => {
      const now = performance.now()
      if (busy || now - last < ${interval}) return
      busy = true
      last = now
      postMessage(0)
    }, 4)`
  const url = URL.createObjectURL(new Blob([code], { type: 'text/javascript' }))
  const worker = new Worker(url)
  worker.onmessage = () => {
    try {
      onTick()
    } finally {
      worker.postMessage('done')
    }
  }
  return () => {
    worker.terminate()
    URL.revokeObjectURL(url)
  }
}

export type EffectsStatus = 'idle' | 'loading' | 'ready' | 'error'

type Point = { x: number; y: number }

export class VideoEffectsProcessor {
  readonly output: MediaStreamTrack
  private readonly video = document.createElement('video')
  private readonly out = document.createElement('canvas')
  private readonly ctx: CanvasRenderingContext2D
  private readonly small = document.createElement('canvas')
  private readonly smallCtx: CanvasRenderingContext2D
  private readonly mask = document.createElement('canvas')
  private readonly maskCtx: CanvasRenderingContext2D
  private readonly person = document.createElement('canvas')
  private readonly personCtx: CanvasRenderingContext2D
  private maskPixels: ImageData | null = null
  private settings: EffectSettings = NO_EFFECTS
  private segmenter: ImageSegmenter | null = null
  private face: FaceLandmarker | null = null
  private lastSegmentTs = -1
  private lastFaceTs = -1
  private gradient: CanvasGradient | null = null
  private smooth: { anchors: Map<number, Point>; width: number; roll: number }[] = []
  private readonly stopTicker: () => void

  onStatus: (status: EffectsStatus) => void = () => {}

  constructor(source: MediaStreamTrack) {
    const { width = 1280, height = 720 } = source.getSettings()
    const scale = Math.min(1, 960 / width)
    this.out.width = Math.round(width * scale)
    this.out.height = Math.round(height * scale)
    this.person.width = this.out.width
    this.person.height = this.out.height
    this.small.width = 320
    this.small.height = Math.round((320 * height) / width)

    this.ctx = this.out.getContext('2d')!
    this.smallCtx = this.small.getContext('2d', { willReadFrequently: false })!
    this.maskCtx = this.mask.getContext('2d')!
    this.personCtx = this.person.getContext('2d')!

    this.video.muted = true
    this.video.playsInline = true
    this.video.srcObject = new MediaStream([source])
    void this.video.play().catch(() => undefined)

    this.output = this.out.captureStream(30).getVideoTracks()[0]
    this.stopTicker = createTicker(30, () => this.frame())
  }

  async update(settings: EffectSettings) {
    this.settings = settings
    this.gradient = null
    const background = this.background()
    if (background.kind === 'image') image(background.src)
    if (settings.background === 'custom' && settings.customImage) image(settings.customImage)
    STICKERS.find((s) => s.id === settings.sticker)?.parts.forEach((p) => image(p.src))

    const needSegmenter = background.kind !== 'none' && !this.segmenter
    const needFace = settings.sticker !== null && !this.face
    if (!needSegmenter && !needFace) return this.onStatus('ready')

    this.onStatus('loading')
    try {
      const [segmenter, face] = await Promise.all([needSegmenter ? loadSegmenter() : null, needFace ? loadFace() : null])
      if (segmenter) this.segmenter = segmenter
      if (face) this.face = face
      this.onStatus('ready')
    } catch (e) {
      console.warn('[video-effects] 모델을 불러오지 못했어요', e)
      this.onStatus('error')
    }
  }

  stop() {
    this.stopTicker()
    this.output.stop()
    this.video.srcObject = null
  }

  private background(): BackgroundPreset {
    if (this.settings.background === 'custom') {
      return this.settings.customImage ? { id: 'custom', kind: 'image', src: this.settings.customImage } : BACKGROUNDS[0]
    }
    return BACKGROUNDS.find((b) => b.id === this.settings.background) ?? BACKGROUNDS[0]
  }

  private frame() {
    const { video, ctx, out } = this
    if (video.readyState < 2) return
    const W = out.width
    const H = out.height
    const background = this.background()

    if (background.kind !== 'none' && this.segmenter) {
      this.smallCtx.drawImage(video, 0, 0, this.small.width, this.small.height)
      if (this.updateMask()) {
        const p = this.personCtx
        p.globalCompositeOperation = 'copy'
        p.drawImage(video, 0, 0, W, H)
        p.globalCompositeOperation = 'destination-in'
        p.filter = 'blur(2px)'
        p.drawImage(this.mask, 0, 0, W, H)
        p.filter = 'none'
        p.globalCompositeOperation = 'source-over'

        this.drawBackground(background, W, H)
        ctx.drawImage(this.person, 0, 0)
      } else {
        ctx.drawImage(video, 0, 0, W, H)
      }
    } else {
      ctx.drawImage(video, 0, 0, W, H)
    }

    if (this.settings.sticker && this.face) this.drawStickers(W, H)
  }

  private updateMask(): boolean {
    const ts = Math.max(performance.now(), this.lastSegmentTs + 1)
    this.lastSegmentTs = ts
    const result = this.segmenter!.segmentForVideo(this.small, ts)
    try {
      const mask = result.confidenceMasks?.[0]
      if (!mask) return false
      if (this.mask.width !== mask.width || this.mask.height !== mask.height || !this.maskPixels) {
        this.mask.width = mask.width
        this.mask.height = mask.height
        this.maskPixels = new ImageData(mask.width, mask.height)
      }
      const confidence = mask.getAsFloat32Array()
      const px = this.maskPixels.data
      for (let i = 0; i < confidence.length; i++) px[i * 4 + 3] = confidence[i] * 255
      this.maskCtx.putImageData(this.maskPixels, 0, 0)
      return true
    } finally {
      result.close()
    }
  }

  private drawBackground(background: BackgroundPreset, W: number, H: number) {
    const ctx = this.ctx
    if (background.kind === 'blur') {
      const r = background.radius
      ctx.filter = `blur(${r}px)`
      ctx.drawImage(this.small, -r * 2, -r * 2, W + r * 4, H + r * 4)
      ctx.filter = 'none'
    } else if (background.kind === 'gradient') {
      if (!this.gradient) {
        const rad = ((background.angle - 90) * Math.PI) / 180
        const dx = (Math.cos(rad) * W) / 2
        const dy = (Math.sin(rad) * H) / 2
        const g = ctx.createLinearGradient(W / 2 - dx, H / 2 - dy, W / 2 + dx, H / 2 + dy)
        background.stops.forEach((c, i) => g.addColorStop(i / (background.stops.length - 1), c))
        this.gradient = g
      }
      ctx.fillStyle = this.gradient
      ctx.fillRect(0, 0, W, H)
    } else if (background.kind === 'image') {
      const img = image(background.src)
      if (!img.complete || !img.naturalWidth) {
        ctx.fillStyle = '#1f2937'
        ctx.fillRect(0, 0, W, H)
        return
      }
      ctx.fillStyle = '#ffffff'
      ctx.fillRect(0, 0, W, H)
      const s = Math.max(W / img.naturalWidth, H / img.naturalHeight)
      const w = img.naturalWidth * s
      const h = img.naturalHeight * s
      ctx.drawImage(img, (W - w) / 2, (H - h) / 2, w, h)
    }
  }

  private drawStickers(W: number, H: number) {
    const sticker = STICKERS.find((s) => s.id === this.settings.sticker)
    if (!sticker) return
    const ts = Math.max(performance.now(), this.lastFaceTs + 1)
    this.lastFaceTs = ts
    const faces = this.face!.detectForVideo(this.video, ts).faceLandmarks
    this.smooth.length = faces.length

    faces.forEach((landmarks, i) => {
      const at = (n: number): Point => ({ x: landmarks[n].x * W, y: landmarks[n].y * H })
      const left = at(LM.leftEyeOuter)
      const right = at(LM.rightEyeOuter)
      const width = distance(at(LM.leftFace), at(LM.rightFace))
      const roll = Math.atan2(right.y - left.y, right.x - left.x)

      const prev = this.smooth[i]
      const k = 0.55
      const state = prev ?? { anchors: new Map<number, Point>(), width, roll }
      state.width = lerp(state.width, width, k)
      state.roll = lerp(state.roll, roll, k)
      this.smooth[i] = state

      for (const part of sticker.parts) {
        const img = image(part.src)
        if (!img.complete || !img.naturalWidth) continue
        const target = anchorPoint(landmarks, part.anchor, W, H)
        const last = state.anchors.get(part.anchor)
        const anchor = last ? { x: lerp(last.x, target.x, k), y: lerp(last.y, target.y, k) } : target
        state.anchors.set(part.anchor, anchor)

        const w = state.width * part.width
        const h = (w * img.naturalHeight) / img.naturalWidth
        this.ctx.save()
        this.ctx.translate(anchor.x, anchor.y)
        this.ctx.rotate(state.roll)
        this.ctx.drawImage(img, -part.ax * w, -part.ay * h, w, h)
        this.ctx.restore()
      }
    })
  }
}

const distance = (a: Point, b: Point) => Math.hypot(a.x - b.x, a.y - b.y)
const lerp = (a: number, b: number, k: number) => a + (b - a) * k
const anchorPoint = (landmarks: NormalizedLandmark[], n: number, W: number, H: number): Point => ({
  x: landmarks[n].x * W,
  y: landmarks[n].y * H,
})

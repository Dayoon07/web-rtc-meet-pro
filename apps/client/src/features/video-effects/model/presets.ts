export type BackgroundId = 'none' | 'blur' | 'blur-strong' | 'violet' | 'sunset' | 'night' | 'christmas' | 'custom'

export type BackgroundPreset =
  | { id: BackgroundId; kind: 'none' }
  | { id: BackgroundId; kind: 'blur'; radius: number }
  | { id: BackgroundId; kind: 'gradient'; stops: string[]; angle: number }
  | { id: BackgroundId; kind: 'image'; src: string }

export const BACKGROUNDS: BackgroundPreset[] = [
  { id: 'none', kind: 'none' },
  { id: 'blur', kind: 'blur', radius: 8 },
  { id: 'blur-strong', kind: 'blur', radius: 20 },
  { id: 'violet', kind: 'gradient', stops: ['#ede9fe', '#c4b5fd', '#7c3aed'], angle: 135 },
  { id: 'sunset', kind: 'gradient', stops: ['#fde68a', '#fb923c', '#db2777'], angle: 160 },
  { id: 'night', kind: 'gradient', stops: ['#0f172a', '#312e81', '#6d28d9'], angle: 180 },
  { id: 'christmas', kind: 'image', src: '/effects/christmas_bg.png' },
]

export const LM = {
  noseTip: 1,
  foreheadTop: 10,
  betweenEyes: 168,
  leftFace: 234,
  rightFace: 454,
  leftEyeOuter: 33,
  rightEyeOuter: 263,
  leftIris: 468,
  rightIris: 473,
} as const

export type StickerPart = { src: string; anchor: number; ax: number; ay: number; width: number }

export type StickerId = 'cat' | 'dog' | 'bear' | 'sunglasses' | 'cowboy' | 'graduation' | 'heart-eyes'

export const STICKERS: { id: StickerId; thumb: string; parts: StickerPart[] }[] = [
  { id: 'cat', thumb: '/effects/cat_face.png', parts: [{ src: '/effects/cat_face.png', anchor: LM.noseTip, ax: 0.5, ay: 0.8, width: 1.55 }] },
  { id: 'dog', thumb: '/effects/dog_face.png', parts: [{ src: '/effects/dog_face.png', anchor: LM.noseTip, ax: 0.5, ay: 0.81, width: 1.6 }] },
  { id: 'bear', thumb: '/effects/bear_face.png', parts: [{ src: '/effects/bear_face.png', anchor: LM.noseTip, ax: 0.5, ay: 0.8, width: 1.5 }] },
  { id: 'sunglasses', thumb: '/effects/sunglasses.png', parts: [{ src: '/effects/sunglasses.png', anchor: LM.betweenEyes, ax: 0.5, ay: 0.4, width: 1.0 }] },
  {
    id: 'cowboy',
    thumb: '/effects/cowboy_hat.png',
    parts: [
      { src: '/effects/cowboy_hat.png', anchor: LM.foreheadTop, ax: 0.5, ay: 0.8, width: 1.9 },
      { src: '/effects/sunglasses.png', anchor: LM.betweenEyes, ax: 0.5, ay: 0.4, width: 1.0 },
    ],
  },
  { id: 'graduation', thumb: '/effects/graduation_cap.png', parts: [{ src: '/effects/graduation_cap.png', anchor: LM.foreheadTop, ax: 0.49, ay: 0.62, width: 1.5 }] },
  {
    id: 'heart-eyes',
    thumb: '/effects/heart.png',
    parts: [
      { src: '/effects/heart.png', anchor: LM.leftIris, ax: 0.5, ay: 0.5, width: 0.42 },
      { src: '/effects/heart.png', anchor: LM.rightIris, ax: 0.5, ay: 0.5, width: 0.42 },
    ],
  },
]

export type EffectSettings = {
  background: BackgroundId
  customImage: string | null
  sticker: StickerId | null
}

export const NO_EFFECTS: EffectSettings = { background: 'none', customImage: null, sticker: null }

export const hasEffects = (s: EffectSettings) => s.background !== 'none' || s.sticker !== null

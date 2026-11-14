import { BACKGROUNDS, type EffectSettings, NO_EFFECTS, STICKERS } from './presets'

const KEY = 'limeet.videoEffects'

export function loadEffectSettings(): EffectSettings {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return NO_EFFECTS
    const parsed = JSON.parse(raw) as Partial<EffectSettings>
    const background = parsed.background === 'custom' || BACKGROUNDS.some((b) => b.id === parsed.background) ? parsed.background! : 'none'
    const sticker = STICKERS.some((s) => s.id === parsed.sticker) ? parsed.sticker! : null
    const customImage = typeof parsed.customImage === 'string' && parsed.customImage.startsWith('data:image/') ? parsed.customImage : null
    return { background: background === 'custom' && !customImage ? 'none' : background, sticker, customImage }
  } catch {
    return NO_EFFECTS
  }
}

export function saveEffectSettings(settings: EffectSettings) {
  try {
    localStorage.setItem(KEY, JSON.stringify(settings))
  } catch {
    try {
      localStorage.setItem(KEY, JSON.stringify({ ...settings, customImage: null, background: settings.background === 'custom' ? 'none' : settings.background }))
    } catch {
    }
  }
}

export function imageFileToDataUrl(file: File, max = 1280): Promise<string> {
  return new Promise((resolve, reject) => {
    if (!file.type.startsWith('image/')) return reject(new Error('NOT_IMAGE'))
    const url = URL.createObjectURL(file)
    const img = new Image()
    img.onload = () => {
      const s = Math.min(1, max / Math.max(img.naturalWidth, img.naturalHeight))
      const canvas = document.createElement('canvas')
      canvas.width = Math.round(img.naturalWidth * s)
      canvas.height = Math.round(img.naturalHeight * s)
      canvas.getContext('2d')!.drawImage(img, 0, 0, canvas.width, canvas.height)
      URL.revokeObjectURL(url)
      resolve(canvas.toDataURL('image/jpeg', 0.85))
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('NOT_IMAGE'))
    }
    img.src = url
  })
}

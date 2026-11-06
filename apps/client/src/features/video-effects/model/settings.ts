import { BACKGROUNDS, type EffectSettings, NO_EFFECTS, STICKERS } from './presets'

const KEY = 'limeet.videoEffects'

/**
 * 마지막으로 쓴 효과는 이 브라우저에 저장해 다음 회의에도 그대로 켠다.
 * 내 사진 배경은 서버로 보내지 않고 이 기기(localStorage)에만 둔다.
 * 시크릿 창 등에서 저장소를 못 쓰면 그냥 효과 없음으로 시작한다.
 */
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
    // 용량 초과(큰 사진) 등: 사진 없이라도 저장
    try {
      localStorage.setItem(KEY, JSON.stringify({ ...settings, customImage: null, background: settings.background === 'custom' ? 'none' : settings.background }))
    } catch {
      /* 저장소를 못 쓰는 환경 */
    }
  }
}

/** 고른 사진을 최대 1280px JPEG 로 줄여 data URL 로 (저장 공간·처리 속도) */
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

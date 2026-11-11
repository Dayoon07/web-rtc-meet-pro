import { Ban, Droplets, ImagePlus, Loader2 } from 'lucide-react'
import type { ReactNode } from 'react'
import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { cn } from '@/shared/lib/cn'
import type { EffectsStatus } from '../model/processor'
import { BACKGROUNDS, type BackgroundId, type EffectSettings, STICKERS, type StickerId } from '../model/presets'
import { imageFileToDataUrl } from '../model/settings'

/** 지금 효과 설정 · 불러오는 상태 · 카메라 켜짐 */
type EffectsView = { settings: EffectSettings; status: EffectsStatus; cameraOn: boolean }

/** choice = 이름 · 골라져 있는지 · 누를 때, children = 미리보기 */
function Choice({ choice, children }: { choice: { label: string; selected: boolean; onClick: () => void }; children: ReactNode }) {
  const { selected, label, onClick } = choice
  return (
    <button
      type="button"
      onClick={onClick}
      title={label}
      aria-label={label}
      aria-pressed={selected}
      className={cn(
        'relative flex aspect-video items-center justify-center overflow-hidden rounded-lg bg-gray-800 text-gray-300 ring-offset-2 ring-offset-gray-900 transition hover:bg-gray-700',
        selected && 'ring-2 ring-brand-400',
      )}
    >
      {children}
    </button>
  )
}

/** 회의 중 배경(흐림·그라데이션·사진)과 얼굴 스티커 고르기 */
export function EffectsPanel({ effects, onChange }: { effects: EffectsView; onChange: (settings: EffectSettings) => void }) {
  const { settings, status, cameraOn } = effects
  const { t } = useTranslation()
  const fileRef = useRef<HTMLInputElement>(null)

  const setBackground = (background: BackgroundId) => onChange({ ...settings, background })
  const setSticker = (sticker: StickerId | null) => onChange({ ...settings, sticker })

  const onFile = async (file: File | undefined) => {
    if (!file) return
    try {
      const customImage = await imageFileToDataUrl(file)
      onChange({ ...settings, background: 'custom', customImage })
    } catch {
      toast.error(t('room.effects.badImage'))
    }
  }

  return (
    <div className="flex-1 space-y-5 overflow-y-auto p-4">
      {!cameraOn && <p className="rounded-lg bg-amber-500/15 px-3 py-2 text-xs text-amber-200">{t('room.effects.cameraOff')}</p>}
      {status === 'loading' && (
        <p className="flex items-center gap-2 text-xs text-gray-400">
          <Loader2 className="size-3.5 animate-spin" />
          {t('room.effects.loading')}
        </p>
      )}
      {status === 'error' && <p className="text-xs text-red-400">{t('room.effects.error')}</p>}

      <section className="space-y-2">
        <h3 className="text-sm font-semibold">{t('room.effects.background')}</h3>
        <div className="grid grid-cols-3 gap-2">
          {BACKGROUNDS.map((b) => {
            const label = t(`room.effects.bg.${b.id}`)
            return (
              <Choice choice={{ selected: settings.background === b.id, label, onClick: () => setBackground(b.id) }} key={b.id}>
                {b.kind === 'none' && <Ban className="size-5" />}
                {b.kind === 'blur' && <Droplets className={cn('size-5', b.radius > 10 && 'size-6')} />}
                {b.kind === 'gradient' && (
                  <span className="size-full" style={{ backgroundImage: `linear-gradient(${b.angle}deg, ${b.stops.join(', ')})` }} />
                )}
                {b.kind === 'image' && <img src={b.src} alt="" className="size-full object-cover" />}
                <span className="absolute bottom-0 left-0 right-0 truncate bg-black/55 px-1 py-0.5 text-[10px] text-white">{label}</span>
              </Choice>
            )
          })}
          <Choice choice={{ selected: settings.background === 'custom', label: t('room.effects.bg.custom'), onClick: () => (settings.customImage ? setBackground('custom') : fileRef.current?.click()) }}>
            {settings.customImage ? <img src={settings.customImage} alt="" className="size-full object-cover" /> : <ImagePlus className="size-5" />}
            <span className="absolute bottom-0 left-0 right-0 truncate bg-black/55 px-1 py-0.5 text-[10px] text-white">{t('room.effects.bg.custom')}</span>
          </Choice>
        </div>
        <button type="button" onClick={() => fileRef.current?.click()} className="text-xs text-brand-300 hover:underline">
          {t('room.effects.upload')}
        </button>
        <input ref={fileRef} type="file" accept="image/*" hidden onChange={(e) => void onFile(e.target.files?.[0]).finally(() => (e.target.value = ''))} />
        <p className="text-[11px] text-gray-500">{t('room.effects.uploadHint')}</p>
      </section>

      <section className="space-y-2">
        <h3 className="text-sm font-semibold">{t('room.effects.sticker')}</h3>
        <div className="grid grid-cols-4 gap-2">
          <Choice choice={{ selected: settings.sticker === null, label: t('room.effects.none'), onClick: () => setSticker(null) }}>
            <Ban className="size-5" />
          </Choice>
          {STICKERS.map((s) => (
            <Choice choice={{ selected: settings.sticker === s.id, label: t(`room.effects.stickers.${s.id}`), onClick: () => setSticker(s.id) }} key={s.id}>
              <img src={s.thumb} alt="" className="max-h-[80%] max-w-[80%] object-contain" />
            </Choice>
          ))}
        </div>
        <p className="text-[11px] text-gray-500">{t('room.effects.stickerHint')}</p>
      </section>
    </div>
  )
}

import { Languages } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/shared/lib/cn'
import type { ShownCaption } from '../model/use-live-captions'

/** 영상 아래쪽에 겹쳐 보이는 자막 (최근 몇 줄). interim = 내가 말하는 중인 문장 */
/** captions = 보여 줄 줄들 · 내가 지금 말하는 중인 문장 · 내 이름 */
export function CaptionOverlay({ captions }: { captions: { lines: ShownCaption[]; interim?: string; selfName: string } }) {
  const { lines, interim, selfName } = captions
  const { t } = useTranslation()
  if (!lines.length && !interim) return null
  return (
    <div className="pointer-events-none absolute inset-x-0 bottom-4 z-10 flex justify-center px-4" aria-live="polite">
      <div className="flex max-w-3xl flex-col gap-1 rounded-xl bg-black/75 px-4 py-2.5 text-center text-sm leading-relaxed text-white shadow-lg backdrop-blur sm:text-base">
        {lines.map((l) => (
          <p key={l.id} className={cn(l.pending && 'opacity-60')}>
            <span className="mr-1.5 font-semibold text-brand-200">{l.speaker}</span>
            {l.text}
            {l.translated && <Languages className="ml-1.5 inline size-3.5 align-[-2px] text-gray-400" aria-label={t('captions.translated')} />}
          </p>
        ))}
        {interim && (
          <p className="text-gray-300 italic">
            <span className="mr-1.5 font-semibold not-italic text-brand-200">{selfName}</span>
            {interim}
          </p>
        )}
      </div>
    </div>
  )
}

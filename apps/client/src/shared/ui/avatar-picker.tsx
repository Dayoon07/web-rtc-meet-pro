import { Camera } from 'lucide-react'
import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Avatar } from './avatar'
import { Spinner } from './spinner'

const ACCEPT = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
const MAX_BYTES = 5 * 1024 * 1024

/** 아바타 + 카메라 버튼. 파일 형식·크기는 여기서 먼저 거르고, 서버가 한 번 더 검증한다 */
export function AvatarPicker({
  src,
  name,
  onPick,
  busy,
}: {
  src: string | null
  name: string
  onPick: (file: File) => void
  busy?: boolean
}) {
  const { t } = useTranslation()
  const input = useRef<HTMLInputElement>(null)

  return (
    <div className="relative inline-block">
      <Avatar src={src} name={name || '?'} size="xl" />
      <button
        type="button"
        onClick={() => input.current?.click()}
        disabled={busy}
        aria-label={t('profile.photo.select')}
        className="absolute right-0 bottom-0 flex size-9 items-center justify-center rounded-full border-2 border-white bg-brand-600 text-white shadow-md transition hover:bg-brand-700 disabled:opacity-60 dark:border-gray-900"
      >
        {busy ? <Spinner className="size-4" /> : <Camera className="size-4" />}
      </button>
      <input
        ref={input}
        type="file"
        accept={ACCEPT.join(',')}
        className="hidden"
        onChange={(e) => {
          const file = e.target.files?.[0]
          e.target.value = ''
          if (!file) return
          if (!ACCEPT.includes(file.type)) return void toast.error(t('profile.photo.invalidType'))
          if (file.size > MAX_BYTES) return void toast.error(t('profile.photo.tooLarge'))
          onPick(file)
        }}
      />
    </div>
  )
}

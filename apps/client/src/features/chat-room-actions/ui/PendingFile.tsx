import { FileText, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useFormat } from '@/shared/lib/format'

/** 보내기 전 첨부파일 한 개: 이미지는 작은 미리보기, 나머지는 아이콘 */
export function PendingFile({ file, onRemove }: { file: File; /** null = 보내는 중이라 뺄 수 없음 */ onRemove: (() => void) | null }) {
  const { t } = useTranslation()
  const fmt = useFormat()
  const [preview, setPreview] = useState<string | null>(null)
  useEffect(() => {
    if (!file.type.startsWith('image/')) return
    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  return (
    <li className="flex max-w-56 items-center gap-2 rounded-xl border border-gray-200 bg-gray-50 py-1.5 pr-1.5 pl-2 dark:border-gray-700 dark:bg-gray-800">
      {preview ? (
        <img src={preview} alt="" className="size-8 shrink-0 rounded-md object-cover" />
      ) : (
        <span className="flex size-8 shrink-0 items-center justify-center rounded-md bg-white text-gray-500 dark:bg-gray-900 dark:text-gray-400">
          <FileText className="size-4" />
        </span>
      )}
      <span className="min-w-0 flex-1">
        <span className="block truncate text-xs font-medium text-gray-800 dark:text-gray-100">{file.name}</span>
        <span className="block text-[11px] text-gray-500 dark:text-gray-400">{fmt.bytes(file.size)}</span>
      </span>
      <button
        type="button"
        disabled={!onRemove}
        onClick={onRemove ?? undefined}
        aria-label={t('chat.removeFile', { name: file.name })}
        title={t('chat.removeFile', { name: file.name })}
        className="rounded-md p-1 text-gray-400 hover:bg-gray-200 hover:text-gray-700 disabled:opacity-40 dark:hover:bg-gray-700 dark:hover:text-gray-200"
      >
        <X className="size-3.5" />
      </button>
    </li>
  )
}

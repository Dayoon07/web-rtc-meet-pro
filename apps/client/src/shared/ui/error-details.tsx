import { Check, Copy, Mail } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { SUPPORT_EMAIL } from '@/shared/config/env'
import { type ErrorReason, useUnexpectedErrorStore } from '@/shared/lib/unexpected-error'
import { Button } from './button'
import { Modal } from './modal'

/**
 * 예상하지 못한 에러의 원인 + 문의 이메일 + 에러 코드.
 * 원인은 쉬운 말로만 보여 준다 (예외 내용·테이블 이름 같은 내부 정보는 서버 admin_error_logs 에만 있다).
 */
export function ErrorDetails({ errorCode, reason }: { errorCode: string; reason: ErrorReason }) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!copied) return
    const id = setTimeout(() => setCopied(false), 1500)
    return () => clearTimeout(id)
  }, [copied])

  const copy = () => {
    void navigator.clipboard
      ?.writeText(errorCode)
      .then(() => setCopied(true))
      .catch(() => {})
  }
  const mailto = `mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent(t('errorDialog.mailSubject', { code: errorCode }))}`

  return (
    <div className="flex flex-col gap-4 text-left text-sm">
      <div className="rounded-xl bg-gray-50 px-4 py-3 dark:bg-gray-800/60">
        <p className="text-xs font-semibold text-gray-500 dark:text-gray-400">{t('errorDialog.cause')}</p>
        <p className="mt-1 text-gray-900 dark:text-gray-100">{t(`errorDialog.reasons.${reason}`)}</p>
      </div>
      <dl className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <dt className="text-gray-500 dark:text-gray-400">{t('errorDialog.email')}:</dt>
          <dd>
            <a
              href={mailto}
              className="inline-flex items-center gap-1 font-medium text-brand-700 underline-offset-2 hover:underline dark:text-brand-300"
            >
              <Mail className="size-3.5" />
              {SUPPORT_EMAIL}
            </a>
          </dd>
        </div>
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <dt className="text-gray-500 dark:text-gray-400">{t('errorDialog.code')}:</dt>
          <dd className="flex items-center gap-1.5">
            <code className="rounded-md bg-gray-100 px-2 py-0.5 font-mono text-[13px] text-gray-900 select-all dark:bg-gray-800 dark:text-gray-100">
              {errorCode}
            </code>
            <button
              type="button"
              onClick={copy}
              aria-label={copied ? t('errorDialog.copied') : t('errorDialog.copy')}
              title={copied ? t('errorDialog.copied') : t('errorDialog.copy')}
              className="rounded-md p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-700 dark:hover:bg-gray-800 dark:hover:text-gray-200"
            >
              {copied ? <Check className="size-4 text-emerald-600" /> : <Copy className="size-4" />}
            </button>
          </dd>
        </div>
      </dl>
      <p className="text-xs text-gray-500 dark:text-gray-400">{t('errorDialog.hint')}</p>
    </div>
  )
}

/** 화면 전체에 하나: 서버 500 이나 서버 연결 실패 때 request() 가 띄운다 */
export function UnexpectedErrorDialog() {
  const { t } = useTranslation()
  const current = useUnexpectedErrorStore((s) => s.current)
  const close = useUnexpectedErrorStore((s) => s.close)
  if (!current) return null
  return (
    <Modal open onClose={close} title={t('errorDialog.title')} size="sm" footer={<Button onClick={close}>{t('errorDialog.ok')}</Button>}>
      <p className="mb-4 text-[15px] text-gray-700 dark:text-gray-300">{t('errorDialog.lead')}</p>
      <ErrorDetails errorCode={current.errorCode} reason={current.reason} />
    </Modal>
  )
}

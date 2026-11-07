import { useMutation, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { useSessionStore } from '@/entities/session'
import { type Me, userApi, userKeys } from '@/entities/user'
import { errorMessage } from '@/shared/api'
import { Button, Field, FieldError, Input, Modal } from '@/shared/ui'

/**
 * 설정 > 로그인 방법의 "비밀번호 바꾸기"(구글 전용 계정은 "비밀번호 만들기").
 * 바꾸면 다른 기기는 로그아웃되고, 이 기기는 서버가 준 새 토큰으로 계속 로그인 상태다.
 */
export function ChangePasswordButton({ me }: { me: Me }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const setTokens = useSessionStore((s) => s.setTokens)
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ current: '', next: '', confirm: '' })
  const [error, setError] = useState<string | null>(null)
  const creating = !me.hasPassword

  const change = useMutation({
    mutationFn: () => userApi.changePassword(creating ? null : form.current, form.next),
    onSuccess: (tokens) => {
      setTokens(tokens)
      void queryClient.invalidateQueries({ queryKey: userKeys.me })
      toast.success(t(creating ? 'settings.changePassword.created' : 'settings.changePassword.changed'))
      close()
    },
  })
  const close = () => {
    setOpen(false)
    setForm({ current: '', next: '', confirm: '' })
    setError(null)
    change.reset()
  }
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (form.next.length < 8) return setError(t('auth.validation.passwordLength'))
    // 가입 화면과 같은 규칙 (영문·숫자 함께)
    if (!/[A-Za-z]/.test(form.next) || !/\d/.test(form.next)) return setError(t('auth.validation.passwordMix'))
    if (form.next !== form.confirm) return setError(t('auth.validation.passwordMismatch'))
    setError(null)
    change.mutate()
  }
  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm((f) => ({ ...f, [key]: e.target.value }))

  return (
    <>
      <Button size="sm" variant="outline" onClick={() => setOpen(true)}>
        {t(creating ? 'settings.changePassword.create' : 'settings.changePassword.change')}
      </Button>
      <Modal
        open={open}
        onClose={close}
        title={t(creating ? 'settings.changePassword.create' : 'settings.changePassword.change')}
        size="sm"
        footer={
          <>
            <Button variant="outline" onClick={close}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" form="change-password" loading={change.isPending} disabled={(!creating && !form.current) || !form.next || !form.confirm}>
              {t('common.save')}
            </Button>
          </>
        }
      >
        <form id="change-password" onSubmit={submit} className="flex flex-col gap-4">
          <p className="text-sm text-gray-500 dark:text-gray-400">{t('settings.changePassword.hint')}</p>
          {!creating && (
            <Field label={t('settings.changePassword.current')} htmlFor="cp-current">
              <Input id="cp-current" type="password" autoComplete="current-password" value={form.current} onChange={set('current')} />
            </Field>
          )}
          <Field label={t('settings.changePassword.new')} htmlFor="cp-new" hint={t('auth.fields.passwordHint')}>
            <Input id="cp-new" type="password" autoComplete="new-password" value={form.next} onChange={set('next')} />
          </Field>
          <Field label={t('auth.fields.passwordConfirm')} htmlFor="cp-confirm">
            <Input id="cp-confirm" type="password" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} />
          </Field>
          <FieldError>{error ?? errorMessage(change.error)}</FieldError>
        </form>
      </Modal>
    </>
  )
}

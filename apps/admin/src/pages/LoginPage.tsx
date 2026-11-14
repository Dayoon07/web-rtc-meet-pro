import { LogIn } from 'lucide-react'
import { type FormEvent, useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { adminApi, ApiError, useSession } from '@/lib/api'
import { Button, Card, inputClass, Logo } from '@/ui'

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const loggedIn = useSession((s) => s.accessToken !== null)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(
    (location.state as { notAdmin?: boolean } | null)?.notAdmin ? '관리자 권한이 없는 계정이에요.' : null,
  )

  if (loggedIn && !pending) return <Navigate to="/" replace />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setPending(true)
    setError(null)
    try {
      await adminApi.login(email.trim(), password)
    } catch (err) {
      setError(err instanceof Error ? err.message : '로그인하지 못했어요.')
      setPending(false)
      return
    }
    try {
      await adminApi.me()
      navigate('/', { replace: true })
    } catch (err) {
      await adminApi.logout()
      setError(err instanceof ApiError && err.status === 403 ? '관리자 권한이 없는 계정이에요.' : err instanceof Error ? err.message : '확인하지 못했어요.')
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="flex min-h-dvh items-center justify-center px-4 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex justify-center">
          <Logo />
        </div>
        <Card className="p-6">
          <h1 className="text-lg font-bold">관리자 로그인</h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">서비스와 같은 이메일·비밀번호로 로그인해요.</p>
          <form onSubmit={(e) => void submit(e)} className="mt-5 flex flex-col gap-3">
            <label className="flex flex-col gap-1.5 text-sm font-medium">
              이메일
              <input type="email" required autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} className={inputClass} />
            </label>
            <label className="flex flex-col gap-1.5 text-sm font-medium">
              비밀번호
              <input
                type="password"
                required
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={inputClass}
              />
            </label>
            {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-300">{error}</p>}
            <Button type="submit" loading={pending} icon={<LogIn className="size-4" />} className="mt-1 h-10">
              로그인
            </Button>
          </form>
        </Card>
        <p className="mt-4 text-center text-xs leading-relaxed text-gray-500 dark:text-gray-400">
          관리자 지정은 DB 에서 합니다: <code className="font-mono">users.system_role = 'ADMIN'</code>
        </p>
      </div>
    </main>
  )
}

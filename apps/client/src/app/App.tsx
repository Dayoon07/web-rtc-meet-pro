import '@/shared/i18n'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router'
import { Toaster } from 'sonner'
import { useApplyTheme } from '@/shared/lib/theme'
import { UnexpectedErrorDialog } from '@/shared/ui'
import { initErrorReporting } from './init-error-reporting'
import { initRequest } from './init-request'
import { router } from './router'
import './styles/index.css'

initRequest()
initErrorReporting()

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 30_000 },
  },
})

export function App() {
  const theme = useApplyTheme()
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <Toaster position="top-center" theme={theme} richColors closeButton />
      {/* 서버 500·서버 연결 실패 안내 (에러 코드 + 문의 이메일) */}
      <UnexpectedErrorDialog />
    </QueryClientProvider>
  )
}

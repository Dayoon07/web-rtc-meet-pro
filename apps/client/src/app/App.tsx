import '@/shared/i18n'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router'
import { Toaster } from 'sonner'
import { useApplyTheme } from '@/shared/lib/theme'
import { initHttp } from './init-http'
import { router } from './router'
import './styles/index.css'

initHttp()

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
    </QueryClientProvider>
  )
}

import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router'
import { Toaster } from 'sonner'
import { AdminLayout } from './app/Layout'
import { SCREENS } from './app/screens'
import { LoginPage } from './pages/LoginPage'
import './styles.css'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 10_000 } },
})

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <AdminLayout />,
    children: SCREENS.map(({ path, element }) => ({ path, element })),
  },
  { path: '*', element: <Navigate to="/" replace /> },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <Toaster position="top-center" richColors closeButton />
    </QueryClientProvider>
  </StrictMode>,
)

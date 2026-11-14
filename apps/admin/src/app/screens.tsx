import { CreditCard, Database, LayoutDashboard, type LucideIcon, MailCheck, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'
import { DashboardPage } from '@/pages/dashboard/DashboardPage'
import { TableDataPage } from '@/pages/data/TableDataPage'
import { TablesPage } from '@/pages/data/TablesPage'
import { ErrorLogDetailPage } from '@/pages/error-logs/ErrorLogDetailPage'
import { ErrorLogsPage } from '@/pages/error-logs/ErrorLogsPage'
import { PaymentsPage } from '@/pages/payments/PaymentsPage'
import { PendingSignupsPage } from '@/pages/signups/PendingSignupsPage'

export type Screen = {
  path: string
  element: ReactNode
  menu?: { section: string; label: string; icon: LucideIcon; activeFor?: string[] }
}

export const SCREENS: Screen[] = [
  { path: '/', element: <DashboardPage />, menu: { section: '한눈에', label: '대시보드', icon: LayoutDashboard } },
  { path: '/errors', element: <ErrorLogsPage />, menu: { section: '운영', label: '에러 기록', icon: TriangleAlert, activeFor: ['/errors/'] } },
  { path: '/errors/:code', element: <ErrorLogDetailPage /> },
  { path: '/signups', element: <PendingSignupsPage />, menu: { section: '회원', label: '이메일 인증', icon: MailCheck } },
  { path: '/payments', element: <PaymentsPage />, menu: { section: '회원', label: '결제·환불', icon: CreditCard } },
  { path: '/data', element: <TablesPage />, menu: { section: '데이터', label: '데이터 관리', icon: Database, activeFor: ['/data/'] } },
  { path: '/data/:table', element: <TableDataPage /> },
]

export const MENU_SECTIONS = SCREENS.filter((s) => s.menu).reduce<{ title: string; items: Screen[] }[]>((acc, s) => {
  const section = acc.find((x) => x.title === s.menu!.section)
  if (section) section.items.push(s)
  else acc.push({ title: s.menu!.section, items: [s] })
  return acc
}, [])

export const isMenuActive = (screen: Screen, pathname: string) =>
  pathname === screen.path || (screen.menu?.activeFor ?? []).some((p) => pathname.startsWith(p))

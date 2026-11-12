import { Database, LayoutDashboard, type LucideIcon, MailCheck, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'
import { DashboardPage } from '@/pages/dashboard/DashboardPage'
import { TableDataPage } from '@/pages/data/TableDataPage'
import { TablesPage } from '@/pages/data/TablesPage'
import { ErrorLogDetailPage } from '@/pages/error-logs/ErrorLogDetailPage'
import { ErrorLogsPage } from '@/pages/error-logs/ErrorLogsPage'
import { PendingSignupsPage } from '@/pages/signups/PendingSignupsPage'

/**
 * 관리자 사이트 화면 목록. 화면을 새로 만들면 여기에 한 줄만 추가한다 → 주소(라우터)와 왼쪽 메뉴가 같이 생긴다.
 *   menu 가 없으면 메뉴에는 안 보이는 화면 (상세 화면 등)
 *   activeFor: 이 주소들로 시작하면 메뉴를 선택된 것으로 보여 준다 (상세 화면에서도 메뉴가 켜져 있게)
 */
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
  { path: '/data', element: <TablesPage />, menu: { section: '데이터', label: '데이터 관리', icon: Database, activeFor: ['/data/'] } },
  { path: '/data/:table', element: <TableDataPage /> },
]

/** 메뉴를 묶음(section) 순서대로 */
export const MENU_SECTIONS = SCREENS.filter((s) => s.menu).reduce<{ title: string; items: Screen[] }[]>((acc, s) => {
  const section = acc.find((x) => x.title === s.menu!.section)
  if (section) section.items.push(s)
  else acc.push({ title: s.menu!.section, items: [s] })
  return acc
}, [])

export const isMenuActive = (screen: Screen, pathname: string) =>
  pathname === screen.path || (screen.menu?.activeFor ?? []).some((p) => pathname.startsWith(p))

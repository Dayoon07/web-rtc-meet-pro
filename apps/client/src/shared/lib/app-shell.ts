import { createContext, useContext } from 'react'

/** 앱 화면 틀의 상태 (AppLayout 이 넣어 준다): 데스크톱 왼쪽 사이드바가 접혔는지 */
export const AppShellContext = createContext<{ sidebarCollapsed: boolean }>({ sidebarCollapsed: false })

export const useAppShell = () => useContext(AppShellContext)

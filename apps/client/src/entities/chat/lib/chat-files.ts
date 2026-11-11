import type { TFunction } from 'i18next'

/** 서버 ChatService 와 같은 제한. 보내기 전에 화면에서 먼저 확인한다 */
export const CHAT_FILE_LIMITS = {
  maxFiles: 10,
  maxFileMb: 20,
  maxTotalMb: 50,
  blockedExtensions: ['exe', 'msi', 'bat', 'cmd', 'com', 'scr', 'pif', 'cpl', 'vbs', 'vbe', 'wsf', 'wsh', 'ps1', 'hta', 'lnk', 'reg'],
} as const

/** 첨부할 파일 목록 확인. 문제가 있으면 안내 문장, 없으면 null */
export function checkChatFiles(files: File[], t: TFunction): string | null {
  const { maxFiles, maxFileMb, maxTotalMb, blockedExtensions } = CHAT_FILE_LIMITS
  if (files.length > maxFiles) return t('chat.fileErrors.tooMany', { max: maxFiles })
  for (const f of files) {
    const ext = f.name.includes('.') ? f.name.split('.').pop()!.toLowerCase() : ''
    if ((blockedExtensions as readonly string[]).includes(ext)) return t('chat.fileErrors.blocked', { name: f.name, ext })
    if (f.size === 0) return t('chat.fileErrors.empty', { name: f.name })
    if (f.size > maxFileMb * 1024 * 1024) return t('chat.fileErrors.tooLarge', { name: f.name, max: maxFileMb })
  }
  if (files.reduce((sum, f) => sum + f.size, 0) > maxTotalMb * 1024 * 1024) return t('chat.fileErrors.totalTooLarge', { max: maxTotalMb })
  return null
}

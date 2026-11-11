// 공지 숨기기: 이 브라우저에만 저장 (새 공지가 올라오면 다시 보인다)
const hiddenKey = (roomId: number) => `mq.noticeHidden.${roomId}`

export const readHidden = (roomId: number) => {
  try {
    return localStorage.getItem(hiddenKey(roomId))
  } catch {
    return null
  }
}

export const saveHidden = (roomId: number, stamp: string | null) => {
  try {
    if (stamp) localStorage.setItem(hiddenKey(roomId), stamp)
    else localStorage.removeItem(hiddenKey(roomId))
  } catch {
    // 저장이 막힌 브라우저면 이번 화면에서만
  }
}

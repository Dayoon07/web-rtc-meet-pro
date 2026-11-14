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
  }
}

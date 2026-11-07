import { toast } from 'sonner'
import { ApiError, errorMessage } from './http'

/**
 * 오류 토스트. 예상하지 못한 에러(서버 500·연결 실패)는 이미 에러 코드와 함께 안내 창으로 보여 줬으므로
 * 토스트를 또 띄우지 않는다. 화면을 떠나며 취소된 요청도 조용히 넘어간다.
 */
export function toastError(error: unknown, fallback?: string) {
  if (error instanceof ApiError && (error.unexpected || error.code === 'ABORTED')) return
  toast.error(errorMessage(error) ?? fallback)
}

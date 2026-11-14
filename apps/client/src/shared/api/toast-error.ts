import { toast } from 'sonner'
import { ApiError, errorMessage } from './request'

export function toastError(error: unknown, fallback?: string) {
  if (error instanceof ApiError && (error.unexpected || error.code === 'ABORTED')) return
  toast.error(errorMessage(error) ?? fallback)
}

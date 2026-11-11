import { type Language } from '@/shared/i18n'

/** 각 언어 이름은 그 언어로 표시 (사용자가 못 읽는 언어로 바뀌어도 되돌릴 수 있게) */
export const NATIVE_NAMES: Record<Language, string> = { ko: '한국어', en: 'English', ja: '日本語', zh: '简体中文' }

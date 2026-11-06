import 'i18next'
import type ko from './locales/ko'

/** t('auth.login.title') 처럼 키를 타입으로 검사 */
declare module 'i18next' {
  interface CustomTypeOptions {
    defaultNS: 'translation'
    resources: { translation: typeof ko }
  }
}

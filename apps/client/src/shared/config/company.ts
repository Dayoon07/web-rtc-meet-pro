import { SUPPORT_EMAIL } from './env'

const env = import.meta.env
const need = (v: string | undefined) => v?.trim() || '(입력 필요)'

export const COMPANY = {
  name: need(env.VITE_COMPANY_NAME),
  ceo: need(env.VITE_COMPANY_CEO),
  bizNo: need(env.VITE_COMPANY_BIZ_NO),
  mailOrderNo: need(env.VITE_COMPANY_MAIL_ORDER_NO),
  address: need(env.VITE_COMPANY_ADDRESS),
  phone: need(env.VITE_COMPANY_PHONE),
  email: SUPPORT_EMAIL,
  hosting: env.VITE_COMPANY_HOSTING?.trim() || '스마일서브(iwinv)',
  privacyOfficer: need(env.VITE_PRIVACY_OFFICER),
  privacyOfficerContact: env.VITE_PRIVACY_OFFICER_CONTACT?.trim() || SUPPORT_EMAIL,
  policyDate: env.VITE_POLICY_DATE?.trim() || '2026-11-12',
}

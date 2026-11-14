import { loadTossPayments } from '@tosspayments/tosspayments-sdk'
import { type Card, planApi } from '@/entities/plan'
import { ROUTES } from '@/shared/config/routes'
import type { PurchaseIntent } from '../model/types'

export const CARD_RETURN_PATH = ROUTES.billingCard

export async function openCardRegistration(intent?: PurchaseIntent) {
  const prepared = await planApi.prepareCard()
  const params = new URLSearchParams()
  if (intent) {
    params.set('team', String(intent.teamId))
    params.set('plan', intent.plan)
    params.set('cycle', intent.cycle)
    params.set('autoRenew', intent.autoRenew ? '1' : '0')
  }
  const base = window.location.origin + CARD_RETURN_PATH
  const query = params.toString()
  const toss = await loadTossPayments(prepared.clientKey)
  await toss.payment({ customerKey: prepared.customerKey }).requestBillingAuth({
    method: 'CARD',
    successUrl: query ? `${base}?${query}` : base,
    failUrl: `${base}?failed=1${query ? `&${query}` : ''}`,
    customerEmail: prepared.customerEmail,
    customerName: prepared.customerName,
  })
}

export const isUserCancel = (e: unknown) => typeof e === 'object' && e !== null && 'code' in e && (e as { code: string }).code === 'USER_CANCEL'

export const cardLabel = (card: Card) => [card.company, card.numberMasked].filter(Boolean).join(' ') || '-'

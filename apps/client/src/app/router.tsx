import { createBrowserRouter } from 'react-router'
import { AppHomePage } from '@/pages/app-home'
import { BillingPage, CardReturnPage } from '@/pages/billing'
import { ChatPage } from '@/pages/chat'
import { HomePage } from '@/pages/home'
import { LicensesPage, PrivacyPage, TermsPage } from '@/pages/legal'
import { InvitationsPage } from '@/pages/invitations'
import { LoginPage } from '@/pages/login'
import { MeetingDetailPage } from '@/pages/meeting-detail'
import { MeetingsPage } from '@/pages/meetings'
import { NotFoundPage } from '@/pages/not-found'
import { OAuthCallbackPage } from '@/pages/oauth-callback'
import { JoinTeamPage } from '@/pages/join-team'
import { OnboardingPage } from '@/pages/onboarding'
import { TeamStartPage } from '@/pages/team-start'
import { ForgotPasswordPage, ResetPasswordPage } from '@/pages/password-reset'
import { PricingPage } from '@/pages/pricing'
import { RouteErrorPage } from '@/pages/route-error'
import { SettingsPage } from '@/pages/settings'
import { SignupPage } from '@/pages/signup'
import { TeamDetailPage } from '@/pages/team-detail'
import { TeamsPage } from '@/pages/teams'
import { ConfirmEmailPage, VerifyEmailPage } from '@/pages/verify-email'
import { PlanExpiryGate } from '@/widgets/plan-expiry-notice'
import { GuestOnly, OnboardingOnly, RequireAuth, RequireCompletedProfile, RequireTeam, VerifyEmailOnly } from './guards'
import { AppLayout, AuthLayout, MarketingLayout } from './layouts'
import { APP_CHILD_ROUTES, ROUTES } from '@/shared/config/routes'

export const router = createBrowserRouter([
  {
    errorElement: <RouteErrorPage />,
    children: [
      {
        element: <MarketingLayout />,
        children: [
          { path: ROUTES.home, element: <HomePage /> },
          { path: ROUTES.pricing, element: <PricingPage /> },
          { path: ROUTES.terms, element: <TermsPage /> },
          { path: ROUTES.privacy, element: <PrivacyPage /> },
          { path: ROUTES.licenses, element: <LicensesPage /> },
        ],
      },
      {
        element: <AuthLayout />,
        children: [
          {
            element: <GuestOnly />,
            children: [
              { path: ROUTES.login, element: <LoginPage /> },
              { path: ROUTES.signup, element: <SignupPage /> },
              { path: ROUTES.forgotPassword, element: <ForgotPasswordPage /> },
            ],
          },
          { path: ROUTES.resetPassword, element: <ResetPasswordPage /> },
          { path: ROUTES.verifyEmailConfirm, element: <ConfirmEmailPage /> },
        ],
      },
      { path: ROUTES.oauthCallback, element: <OAuthCallbackPage /> },
      {
        element: <RequireAuth />,
        children: [
          {
            element: <AuthLayout />,
            children: [
              { element: <OnboardingOnly />, children: [{ path: ROUTES.onboarding, element: <OnboardingPage /> }] },
              { element: <VerifyEmailOnly />, children: [{ path: ROUTES.verifyEmail, element: <VerifyEmailPage /> }] },
            ],
          },
          {
            element: <RequireCompletedProfile />,
            children: [
              { element: <AuthLayout />, children: [{ path: ROUTES.joinPattern, element: <JoinTeamPage />, handle: { title: 'join.title' } }] },
              { path: ROUTES.meetPattern, lazy: async () => ({ Component: (await import('@/pages/meet')).MeetPage }) },
              {
                path: ROUTES.app,
                element: <AppLayout />,
                children: [
                  {
                    element: <PlanExpiryGate />,
                    children: [
                  { path: APP_CHILD_ROUTES.start, element: <TeamStartPage />, handle: { title: 'start.title' } },
                  { path: APP_CHILD_ROUTES.invitations, element: <InvitationsPage />, handle: { title: 'nav.invitations' } },
                  { path: APP_CHILD_ROUTES.settings, element: <SettingsPage />, handle: { title: 'nav.settings' } },
                  {
                    element: <RequireTeam />,
                    children: [
                      { index: true, element: <AppHomePage />, handle: { title: 'nav.home' } },
                      { path: APP_CHILD_ROUTES.chat, element: <ChatPage />, handle: { title: 'nav.chat' } },
                      { path: APP_CHILD_ROUTES.meetings, element: <MeetingsPage />, handle: { title: 'nav.meetings' } },
                      { path: APP_CHILD_ROUTES.meeting, element: <MeetingDetailPage />, handle: { title: 'nav.meetings' } },
                      { path: APP_CHILD_ROUTES.teams, element: <TeamsPage />, handle: { title: 'nav.teams' } },
                      { path: APP_CHILD_ROUTES.team, element: <TeamDetailPage />, handle: { title: 'nav.teams' } },
                      { path: APP_CHILD_ROUTES.billing, element: <BillingPage />, handle: { title: 'nav.billing' } },
                      { path: APP_CHILD_ROUTES.billingCard, element: <CardReturnPage />, handle: { title: 'nav.billing' } },
                    ],
                  },
                    ],
                  },
                ],
              },
            ],
          },
        ],
      },
      { element: <MarketingLayout />, children: [{ path: '*', element: <NotFoundPage /> }] },
    ],
  },
])

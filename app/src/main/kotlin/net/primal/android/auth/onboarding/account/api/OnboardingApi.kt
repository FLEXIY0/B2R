package net.primal.android.auth.onboarding.account.api

import retrofit2.http.GET

interface OnboardingApi {

    // b2r fork (Sprint 1.2): follow-suggestions endpoint moved off Primal's
    // media server. b2r has no central suggestions service; this resolves to a
    // non-routable host so the call fails closed (empty suggestions) instead of
    // reaching primal.net. Peer-sourced discovery replaces it in a later step.
    @GET("https://disabled.b2r.invalid/api/suggestions_2")
    suspend fun getFollowSuggestions(): FollowSuggestionsResponse
}

package com.an0obIs.pref.monetization

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.an0obIs.pref.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The interstitial between a finished deal and its score table. The game stays
 * fully usable without it: no network, no consent, no loaded ad or "Remove ads"
 * all simply skip the ad and carry straight on.
 */
object AdsManager {

    private var initialized = false
    private var loading = false
    private var interstitial: InterstitialAd? = null
    private var lastShownAt = Long.MIN_VALUE / 2

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /** True where the law asks for a way to reopen the consent choice (EEA, UK). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired

    val enabled: Boolean get() = BuildConfig.ADS_ENABLED

    /**
     * Gathers consent where the law asks for it (EEA, UK), then starts the ads
     * SDK. Safe to call on every launch: Google remembers earlier consent.
     */
    fun init(activity: Activity) {
        if (!enabled || Engagement.adFree) return
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        refreshPrivacyOptions(consent)
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    refreshPrivacyOptions(consent)
                    startSdk(activity, consent)
                }
            },
            { startSdk(activity, consent) } // offline or failed: still honour an earlier choice
        )
        if (consent.canRequestAds()) startSdk(activity, consent)
    }

    /** Lets the player change their ad consent choice, from the menu. */
    fun showPrivacyOptions(activity: Activity) {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            refreshPrivacyOptions(consent)
            startSdk(activity, consent)
        }
    }

    private fun refreshPrivacyOptions(consent: ConsentInformation) {
        _privacyOptionsRequired.value = consent.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun startSdk(context: Context, consent: ConsentInformation) {
        if (initialized || !consent.canRequestAds()) return
        initialized = true
        MobileAds.initialize(context.applicationContext) { preload(context) }
    }

    fun preload(context: Context) {
        if (!enabled || !initialized || loading || interstitial != null || Engagement.adFree) return
        loading = true
        InterstitialAd.load(
            context.applicationContext,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    loading = false
                }
            }
        )
    }

    /**
     * Shows the ad when the rules say so, then calls [then] — exactly once,
     * whether the ad ran, failed or was skipped.
     */
    fun showIfDue(activity: Activity, dealsFinished: Int, then: () -> Unit) {
        var done = false
        val finish = { if (!done) { done = true; then() } }

        val ad = interstitial
        val now = SystemClock.elapsedRealtime()
        val show = AdPolicy.shouldShow(
            adsEnabled = enabled,
            adFree = Engagement.adFree,
            dealsFinished = dealsFinished,
            adReady = ad != null,
            msSinceLastAd = now - lastShownAt
        )
        if (!show || ad == null) {
            preload(activity)
            finish()
            return
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                lastShownAt = SystemClock.elapsedRealtime()
                preload(activity)
                finish()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                preload(activity)
                finish()
            }
        }
        ad.show(activity)
    }
}

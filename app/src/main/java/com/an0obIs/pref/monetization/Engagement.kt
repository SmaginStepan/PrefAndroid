package com.an0obIs.pref.monetization

import com.an0obIs.pref.model.PrefStorage
import kotlinx.serialization.Serializable

@Serializable
class EngagementState {
    /** single-player deals finished on this device, ever */
    var dealsFinished: Int = 0

    /** the player tapped Rate (the store cannot tell us whether a review followed) */
    var rated: Boolean = false

    /** "Remove ads" is owned; confirmed by the store, remembered for offline starts */
    var adFree: Boolean = false
}

/**
 * What the app remembers about the player for ads and review prompts. Kept in
 * one small file; every call reads it fresh, so there is no stale copy.
 */
object Engagement {

    /** A new player's first deals are free of ads. */
    const val FREE_DEALS = 10

    /** The rate banner returns after this many finished deals until the player rates. */
    const val RATE_EVERY = 10

    private const val FILE = "engagement.json"

    fun state(): EngagementState = try {
        PrefStorage.readText(FILE)
            ?.let { PrefStorage.json.decodeFromString(EngagementState.serializer(), it) }
            ?: EngagementState()
    } catch (e: Exception) {
        EngagementState()
    }

    private fun save(s: EngagementState) = try {
        PrefStorage.writeText(FILE, PrefStorage.json.encodeToString(EngagementState.serializer(), s))
    } catch (e: Exception) {
        android.util.Log.w("Pref", "engagement save failed", e)
    }

    val adFree: Boolean get() = state().adFree

    fun setAdFree(owned: Boolean) {
        val s = state()
        if (s.adFree != owned) {
            s.adFree = owned
            save(s)
        }
    }

    fun markRated() {
        val s = state()
        if (!s.rated) {
            s.rated = true
            save(s)
        }
    }

    /** A single-player deal just ended; returns how many have ended in total. */
    fun dealFinished(): Int {
        val s = state()
        s.dealsFinished++
        save(s)
        return s.dealsFinished
    }

    /** The rate banner is due at every [RATE_EVERY]th finished deal, until the player rates. */
    fun rateBannerDue(s: EngagementState = state()): Boolean =
        !s.rated && s.dealsFinished > 0 && s.dealsFinished % RATE_EVERY == 0
}

/** The rules deciding whether the end of a deal shows an interstitial. */
object AdPolicy {

    /** Never show two ads closer together than this, whatever the deal count. */
    const val MIN_GAP_MS = 45_000L

    /**
     * @param dealsFinished the deal that has just ended, counted from one
     * @param msSinceLastAd time since the previous interstitial closed
     */
    fun shouldShow(
        adsEnabled: Boolean,
        adFree: Boolean,
        dealsFinished: Int,
        adReady: Boolean,
        msSinceLastAd: Long
    ): Boolean =
        adsEnabled && !adFree && adReady &&
                dealsFinished > Engagement.FREE_DEALS &&
                msSinceLastAd >= MIN_GAP_MS
}

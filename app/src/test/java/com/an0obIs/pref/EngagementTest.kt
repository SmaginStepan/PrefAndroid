package com.an0obIs.pref

import com.an0obIs.pref.model.PrefStorage
import com.an0obIs.pref.monetization.AdPolicy
import com.an0obIs.pref.monetization.Engagement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

class EngagementTest {

    @Before
    fun setUp() {
        PrefStorage.init(Files.createTempDirectory("pref-engagement-test").toFile())
    }

    private val longAgo = 10 * AdPolicy.MIN_GAP_MS

    private fun show(deals: Int, adFree: Boolean = false, ready: Boolean = true, since: Long = longAgo) =
        AdPolicy.shouldShow(
            adsEnabled = true, adFree = adFree, dealsFinished = deals,
            adReady = ready, msSinceLastAd = since
        )

    @Test
    fun aNewPlayersFirstTenDealsHaveNoAds() {
        for (deal in 1..10) assertFalse("deal $deal must be ad-free", show(deal))
        assertTrue("the eleventh deal is the first with an ad", show(11))
        assertTrue(show(12))
        assertTrue(show(500))
    }

    @Test
    fun adsStayOffWhenPurchasedDisabledOrNotLoaded() {
        assertFalse(show(50, adFree = true))
        assertFalse(show(50, ready = false))
        assertFalse(
            AdPolicy.shouldShow(
                adsEnabled = false, adFree = false, dealsFinished = 50,
                adReady = true, msSinceLastAd = longAgo
            )
        )
    }

    @Test
    fun twoAdsAreNeverShownBackToBack() {
        assertFalse(show(50, since = 0))
        assertFalse(show(50, since = AdPolicy.MIN_GAP_MS - 1))
        assertTrue(show(50, since = AdPolicy.MIN_GAP_MS))
    }

    @Test
    fun everyFinishedDealIsCountedAndRemembered() {
        assertEquals(0, Engagement.state().dealsFinished)
        assertEquals(1, Engagement.dealFinished())
        assertEquals(2, Engagement.dealFinished())
        assertEquals(2, Engagement.state().dealsFinished) // survives a reload from disk
    }

    @Test
    fun theRateBannerComesBackEveryTenDealsUntilTheyRate() {
        val due = mutableListOf<Int>()
        repeat(35) {
            val n = Engagement.dealFinished()
            if (Engagement.rateBannerDue()) due += n
        }
        assertEquals(listOf(10, 20, 30), due)

        // once the player taps Rate the banner is gone for good
        Engagement.markRated()
        repeat(15) {
            Engagement.dealFinished()
            assertFalse(Engagement.rateBannerDue())
        }
    }

    @Test
    fun theAdFreePurchaseIsRememberedForOfflineStarts() {
        assertFalse(Engagement.adFree)
        Engagement.setAdFree(true)
        assertTrue(Engagement.adFree)
        Engagement.setAdFree(false) // a refund seen at the next store check
        assertFalse(Engagement.adFree)
    }
}

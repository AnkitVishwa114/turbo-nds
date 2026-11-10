package com.appkraft.turbonds

import com.appkraft.turbonds.ads.AdPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPolicyTest {
    private val now = 400L * AdPolicy.DAY

    @Test fun newUserCanSeeAnAd() = assertTrue(AdPolicy.interstitialDue(now, 0, 0, 0))

    @Test fun fullScreenAdsAreSpacedApart() {
        assertFalse(AdPolicy.interstitialDue(now, now - 2 * AdPolicy.MINUTE, 1, 0))
        assertTrue(AdPolicy.interstitialDue(now, now - 5 * AdPolicy.MINUTE, 1, 0))
    }

    @Test fun dailyCapHolds() = assertFalse(AdPolicy.interstitialDue(now, 0, AdPolicy.DAILY_CAP, 0))

    @Test fun rewardKeepsTheHourAdFree() {
        assertFalse(AdPolicy.interstitialDue(now, 0, 0, now + 10 * AdPolicy.MINUTE))
        assertTrue(AdPolicy.interstitialDue(now, 0, 0, now - 1))
    }

    @Test fun adFreeOfferComesBackEveryHour() {
        // Not while the hour is running.
        assertFalse(AdPolicy.adFreeOfferDue(now, now + 5 * AdPolicy.MINUTE, now - AdPolicy.HOUR, 1))
        // Once it ran out, offered again.
        assertTrue(AdPolicy.adFreeOfferDue(now, now - 1, now - AdPolicy.HOUR, 1))
        // But not twice within half an hour.
        assertFalse(AdPolicy.adFreeOfferDue(now, 0, now - 10 * AdPolicy.MINUTE, 1))
        assertFalse(AdPolicy.adFreeOfferDue(now, 0, 0, AdPolicy.OFFER_DAILY_CAP))
    }

    @Test fun playOfferOnlyAfterAnAdHourlyAndCapped() {
        assertFalse(AdPolicy.playOfferDue(adsSeenToday = 0, now = now, lastPlayOffer = 0, playOffersToday = 0))
        assertTrue(AdPolicy.playOfferDue(adsSeenToday = 1, now = now, lastPlayOffer = 0, playOffersToday = 0))
        assertFalse(AdPolicy.playOfferDue(1, now, lastPlayOffer = now - 40 * AdPolicy.MINUTE, playOffersToday = 1))
        assertTrue(AdPolicy.playOfferDue(1, now, lastPlayOffer = now - 61 * AdPolicy.MINUTE, playOffersToday = 1))
        assertFalse(AdPolicy.playOfferDue(1, now, lastPlayOffer = 0, playOffersToday = AdPolicy.PLAY_OFFER_DAILY_CAP))
    }

    @Test fun fullScreenAdsShareOneDailyCap() {
        assertTrue(AdPolicy.underFullScreenCap(interstitialsToday = 5, appOpensToday = 2))
        assertFalse(AdPolicy.underFullScreenCap(interstitialsToday = 6, appOpensToday = 2))
        assertFalse(AdPolicy.underFullScreenCap(interstitialsToday = 7, appOpensToday = 1))
    }

    @Test fun appOpenNeverOnFirstLaunch() =
        assertFalse(AdPolicy.appOpenDue(now, launches = 1, coldStart = true, awayMs = 0, lastAppOpen = 0, lastFullScreen = 0, shownToday = 0, adFreeUntil = 0))

    @Test fun appOpenOnColdStartOrLongBreak() {
        assertTrue(AdPolicy.appOpenDue(now, 3, coldStart = true, awayMs = 0, lastAppOpen = 0, lastFullScreen = 0, shownToday = 0, adFreeUntil = 0))
        assertTrue(AdPolicy.appOpenDue(now, 3, coldStart = false, awayMs = 40 * AdPolicy.MINUTE, lastAppOpen = 0, lastFullScreen = 0, shownToday = 0, adFreeUntil = 0))
        assertFalse(AdPolicy.appOpenDue(now, 3, coldStart = false, awayMs = 5 * AdPolicy.MINUTE, lastAppOpen = 0, lastFullScreen = 0, shownToday = 0, adFreeUntil = 0))
    }

    @Test fun appOpenRespectsGapCapAndAdFree() {
        assertFalse(AdPolicy.appOpenDue(now, 3, true, 0, lastAppOpen = now - 20 * AdPolicy.MINUTE, lastFullScreen = 0, shownToday = 0, adFreeUntil = 0))
        assertFalse(AdPolicy.appOpenDue(now, 3, true, 0, lastAppOpen = 0, lastFullScreen = now - AdPolicy.MINUTE, shownToday = 0, adFreeUntil = 0))
        assertFalse(AdPolicy.appOpenDue(now, 3, true, 0, lastAppOpen = 0, lastFullScreen = 0, shownToday = AdPolicy.APP_OPEN_DAILY_CAP, adFreeUntil = 0))
        assertFalse(AdPolicy.appOpenDue(now, 3, true, 0, lastAppOpen = 0, lastFullScreen = 0, shownToday = 0, adFreeUntil = now + AdPolicy.MINUTE))
    }
}

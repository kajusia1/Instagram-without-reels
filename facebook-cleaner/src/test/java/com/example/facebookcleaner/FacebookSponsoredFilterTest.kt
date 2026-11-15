package com.example.facebookcleaner

import org.junit.Assert.assertTrue
import org.junit.Test

class FacebookSponsoredFilterTest {
    @Test
    fun scriptContainsSupportedSponsoredLabels() {
        assertTrue(FacebookSponsoredFilter.script.contains("sponsored"))
        assertTrue(FacebookSponsoredFilter.script.contains("sponsorowane"))
        assertTrue(FacebookSponsoredFilter.script.contains("promowane"))
    }
}

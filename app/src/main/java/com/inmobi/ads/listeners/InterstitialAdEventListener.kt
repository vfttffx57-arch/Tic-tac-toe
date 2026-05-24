package com.inmobi.ads.listeners

import com.inmobi.ads.InMobiInterstitial
import com.inmobi.ads.AdMetaInfo
import com.inmobi.ads.InMobiAdRequestStatus

abstract class InterstitialAdEventListener {
    open fun onAdFetchSuccessful(ad: InMobiInterstitial, info: AdMetaInfo) {}
    open fun onAdLoadSucceeded(ad: InMobiInterstitial, info: AdMetaInfo) {}
    open fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {}
    open fun onAdDismissed(ad: InMobiInterstitial) {}
    open fun onAdRewardActionCompleted(ad: InMobiInterstitial, rewards: Map<Any, Any>?) {}
    open fun onAdDisplayFailed(ad: InMobiInterstitial) {}
}

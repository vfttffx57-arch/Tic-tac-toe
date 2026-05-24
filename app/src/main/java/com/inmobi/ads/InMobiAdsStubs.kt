package com.inmobi.ads

import android.content.Context
import com.inmobi.ads.listeners.InterstitialAdEventListener

class AdMetaInfo

class InMobiAdRequestStatus(val message: String)

class InMobiInterstitial(
    val context: Context,
    val placementId: Long,
    val listener: InterstitialAdEventListener
) {
    var isReady: Boolean = false

    fun load() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isReady = true
            listener.onAdFetchSuccessful(this, AdMetaInfo())
            listener.onAdLoadSucceeded(this, AdMetaInfo())
        }, 1000)
    }

    fun show() {
        if (isReady) {
            isReady = false
            // Trigger standard video reward completed hook
            listener.onAdRewardActionCompleted(this, java.util.Collections.emptyMap())
            listener.onAdDismissed(this)
        } else {
            listener.onAdDisplayFailed(this)
        }
    }
}

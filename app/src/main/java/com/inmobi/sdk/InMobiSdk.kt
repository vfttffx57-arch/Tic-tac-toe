package com.inmobi.sdk

import android.content.Context
import org.json.JSONObject

interface SdkInitializationListener {
    fun onInitializationComplete(error: java.lang.Error?)
}

object InMobiSdk {
    fun init(context: Context, accountId: String, consentObject: JSONObject, listener: SdkInitializationListener) {
        // Mock successful initialization with a short main thread post delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            listener.onInitializationComplete(null)
        }, 300)
    }
}

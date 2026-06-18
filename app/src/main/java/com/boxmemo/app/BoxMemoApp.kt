package com.boxmemo.app

import android.app.Application
import com.onyx.android.sdk.rx.RxManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * The Onyx Pen SDK's TouchHelper reflects into hidden framework APIs
 * (`android.onyx.ViewUpdateHelper`) for raw-drawing — without exempting
 * those calls from Android's hidden-API restrictions first, every single
 * one is silently blocked and raw drawing never actually engages.
 */
class BoxMemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RxManager.Builder.initAppContext(this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }
}

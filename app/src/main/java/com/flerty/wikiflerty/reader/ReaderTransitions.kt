package com.flerty.wikiflerty.reader

import android.app.Activity
import android.os.Build
import com.flerty.wikiflerty.R

object ReaderTransitions {
    fun opened(host: Activity) {
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            host.overridePendingTransition(R.anim.reader_in, R.anim.reader_hold)
        }
    }

    fun install(reader: Activity) {
        if (Build.VERSION.SDK_INT >= 34) {
            reader.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, R.anim.reader_in, R.anim.reader_hold)
            reader.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, R.anim.reader_hold, R.anim.reader_out)
        }
    }

    fun closing(reader: Activity) {
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            reader.overridePendingTransition(R.anim.reader_hold, R.anim.reader_out)
        }
    }
}

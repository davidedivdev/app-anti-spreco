package com.davide.salvacibo.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Risale dal Context di Compose all'Activity (serve per acquisti, consenso e recensioni). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

package com.example.iptvpreview.ui.utils

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun toggleImmersiveMode(isEnabled: Boolean, activity: ComponentActivity) {
    val window = activity.window
    WindowCompat.setDecorFitsSystemWindows(window, !isEnabled)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        if (isEnabled) {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        } else show(WindowInsetsCompat.Type.systemBars())
    }
}

fun Context.componentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.takeIf { it !== this }?.componentActivity()
    else -> null
}

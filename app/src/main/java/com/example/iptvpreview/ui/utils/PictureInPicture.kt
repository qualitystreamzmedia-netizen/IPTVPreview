package com.example.iptvpreview.ui.utils

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.O)
fun ComponentActivity.enterPipMode(aspectRatio: Rational = Rational(16, 9)): Boolean {
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return false
    return try {
        enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(aspectRatio).build())
    } catch (_: IllegalArgumentException) { false }
    catch (_: IllegalStateException) { false }
}

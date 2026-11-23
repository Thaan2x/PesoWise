package com.example.pesowise

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnAttach

internal fun Activity.applyTopSystemInsets() {
    val root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: return
    updateStatusBarAppearance(root)

    val left = root.paddingLeft
    val top = root.paddingTop
    val right = root.paddingRight
    val bottom = root.paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val safeInsets = insets.getInsets(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.setPadding(left, top + safeInsets.top, right, bottom)
        insets
    }
    root.doOnAttach { ViewCompat.requestApplyInsets(it) }
}

internal fun Activity.updateStatusBarAppearance(backgroundView: View) {
    val attributes = obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
    val backgroundColor = (backgroundView.background as? ColorDrawable)?.color
        ?: attributes.getColor(0, Color.BLACK)
    attributes.recycle()
    val root = findViewById<ViewGroup>(android.R.id.content).getChildAt(0) ?: return
    root.setBackgroundColor(backgroundColor)
    WindowCompat.getInsetsController(window, root).apply {
        isAppearanceLightStatusBars = ColorUtils.calculateLuminance(backgroundColor) > 0.5
        isAppearanceLightNavigationBars = true
    }
}

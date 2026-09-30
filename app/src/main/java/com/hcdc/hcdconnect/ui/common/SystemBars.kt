package com.hcdc.hcdconnect.ui.common

import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

/**
 * Draws edge to edge with the maroon toolbar extending up behind the status bar, like the
 * header on hcdc.edu.ph. The status bar icons are white to stay readable on maroon.
 *
 * The toolbar grows by the status bar height and pads its content below it; the root is
 * padded for the other system bars (and the keyboard when [includeIme] is true).
 */
fun ComponentActivity.setUpBrandedSystemBars(root: View, toolbar: View, includeIme: Boolean = false) {
    enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))

    // The toolbar's height from the layout (?attr/actionBarSize), before any status bar space.
    val toolbarHeight = toolbar.layoutParams.height
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val types = WindowInsetsCompat.Type.systemBars() or
            (if (includeIme) WindowInsetsCompat.Type.ime() else 0)
        val bars = insets.getInsets(types)
        v.updatePadding(left = bars.left, top = 0, right = bars.right, bottom = bars.bottom)
        toolbar.updatePadding(top = bars.top)
        toolbar.updateLayoutParams { height = toolbarHeight + bars.top }
        insets
    }
}

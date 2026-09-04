package com.example.documentsapp.utils

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding

fun View.applySystemWindowInsetsPadding(
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = false
) {
    val initialPadding = intArrayOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updatePadding(
            left = if (left) initialPadding[0] + systemBars.left else view.paddingLeft,
            top = if (top) initialPadding[1] + systemBars.top else view.paddingTop,
            right = if (right) initialPadding[2] + systemBars.right else view.paddingRight,
            bottom = if (bottom) initialPadding[3] + systemBars.bottom else view.paddingBottom
        )
        insets
    }
}

fun View.applySystemWindowInsetsMargin(
    left: Boolean = false,
    top: Boolean = false,
    right: Boolean = false,
    bottom: Boolean = false
) {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    val initialMargin = intArrayOf(lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin)
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            if (left) leftMargin = initialMargin[0] + systemBars.left
            if (top) topMargin = initialMargin[1] + systemBars.top
            if (right) rightMargin = initialMargin[2] + systemBars.right
            if (bottom) bottomMargin = initialMargin[3] + systemBars.bottom
        }
        insets
    }
}

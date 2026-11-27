package com.floatingtasbih.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface

class TasbihPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var count: Int
        get() = prefs.getInt(KEY_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_COUNT, value.coerceAtLeast(0)).apply()

    var totalCount: Long
        get() = prefs.getLong(KEY_TOTAL_COUNT, 0L)
        set(value) = prefs.edit().putLong(KEY_TOTAL_COUNT, value.coerceAtLeast(0L)).apply()

    var target: Int
        get() = prefs.getInt(KEY_TARGET, 33)
        set(value) = prefs.edit().putInt(KEY_TARGET, value).apply()

    var dhikrIndex: Int
        get() = prefs.getInt(KEY_DHIKR_INDEX, 0).coerceIn(0, PRESET_ADHKAR.lastIndex)
        set(value) = prefs.edit().putInt(KEY_DHIKR_INDEX, value.coerceIn(0, PRESET_ADHKAR.lastIndex)).apply()

    var customDhikr: String
        get() = prefs.getString(KEY_CUSTOM_DHIKR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_DHIKR, value.trim()).apply()

    var useCustomDhikr: Boolean
        get() = prefs.getBoolean(KEY_USE_CUSTOM_DHIKR, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_CUSTOM_DHIKR, value).apply()

    var opacityPercent: Int
        get() = prefs.getInt(KEY_OPACITY_PERCENT, 85).coerceIn(20, 100)
        set(value) = prefs.edit().putInt(KEY_OPACITY_PERCENT, value.coerceIn(20, 100)).apply()

    var bubbleScalePercent: Int
        get() = prefs.getInt(KEY_BUBBLE_SCALE, 100).coerceIn(75, 145)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_SCALE, value.coerceIn(75, 145)).apply()

    var fontStyleIndex: Int
        get() = prefs.getInt(KEY_FONT_STYLE, 0).coerceIn(0, FONT_STYLE_NAMES.lastIndex)
        set(value) = prefs.edit().putInt(KEY_FONT_STYLE, value.coerceIn(0, FONT_STYLE_NAMES.lastIndex)).apply()

    var fontSizeSp: Int
        get() = prefs.getInt(KEY_FONT_SIZE_SP, 15).coerceIn(11, 24)
        set(value) = prefs.edit().putInt(KEY_FONT_SIZE_SP, value.coerceIn(11, 24)).apply()

    var vibrateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

    var overlayX: Int
        get() = prefs.getInt(KEY_OVERLAY_X, 48)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_X, value).apply()

    var overlayY: Int
        get() = prefs.getInt(KEY_OVERLAY_Y, 280)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_Y, value).apply()

    var isOverlayActive: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_ACTIVE, value).apply()

    fun currentDhikrText(): String {
        return if (useCustomDhikr && customDhikr.isNotEmpty()) {
            customDhikr
        } else {
            PRESET_ADHKAR[dhikrIndex]
        }
    }

    fun nextDhikr(): String {
        useCustomDhikr = false
        dhikrIndex = (dhikrIndex + 1) % PRESET_ADHKAR.size
        return PRESET_ADHKAR[dhikrIndex]
    }

    fun currentTypeface(): Typeface {
        return when (fontStyleIndex) {
            1 -> Typeface.SERIF
            2 -> Typeface.MONOSPACE
            3 -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            else -> Typeface.SANS_SERIF
        }
    }

    fun cycleFontStyle(): Int {
        fontStyleIndex = (fontStyleIndex + 1) % FONT_STYLE_NAMES.size
        return fontStyleIndex
    }

    fun cycleFontSize(): Int {
        val next = when {
            fontSizeSp < 14 -> 15
            fontSizeSp < 18 -> 18
            fontSizeSp < 21 -> 22
            else -> 12
        }
        fontSizeSp = next
        return next
    }

    fun cycleBubbleScale(): Int {
        val next = when {
            bubbleScalePercent < 95 -> 100
            bubbleScalePercent < 120 -> 125
            else -> 80
        }
        bubbleScalePercent = next
        return next
    }

    companion object {
        private const val PREFS_NAME = "floating_tasbih_prefs"
        private const val KEY_COUNT = "count"
        private const val KEY_TOTAL_COUNT = "total_count"
        private const val KEY_TARGET = "target"
        private const val KEY_DHIKR_INDEX = "dhikr_index"
        private const val KEY_CUSTOM_DHIKR = "custom_dhikr"
        private const val KEY_USE_CUSTOM_DHIKR = "use_custom_dhikr"
        private const val KEY_OPACITY_PERCENT = "opacity_percent"
        private const val KEY_BUBBLE_SCALE = "bubble_scale"
        private const val KEY_FONT_STYLE = "font_style"
        private const val KEY_FONT_SIZE_SP = "font_size_sp"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_OVERLAY_X = "overlay_x"
        private const val KEY_OVERLAY_Y = "overlay_y"
        private const val KEY_OVERLAY_ACTIVE = "overlay_active"

        val PRESET_ADHKAR = listOf(
            "سُبْحَانَ اللَّهِ",
            "الْحَمْدُ لِلَّهِ",
            "اللَّهُ أَكْبَرُ",
            "لَا إِلَهَ إِلَّا اللَّهُ",
            "أَسْتَغْفِرُ اللَّهَ",
            "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ",
            "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ"
        )

        val FONT_STYLE_NAMES = listOf(
            "عصري (Sans)",
            "نسخي (Serif)",
            "أحادي (Mono)",
            "عريض (Bold)"
        )
    }
}

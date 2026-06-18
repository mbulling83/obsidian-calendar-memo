package com.boxmemo.app.memo

import com.onyx.android.sdk.pen.TouchHelper

/** Maps to the real `TouchHelper.STROKE_STYLE_*` constants — see plan U6 patterns. */
enum class PenType(val strokeStyle: Int) {
    PENCIL(TouchHelper.STROKE_STYLE_PENCIL),
    FOUNTAIN(TouchHelper.STROKE_STYLE_FOUNTAIN),
    MARKER(TouchHelper.STROKE_STYLE_MARKER),
    CHARCOAL(TouchHelper.STROKE_STYLE_CHARCOAL),
}

data class PenSettings(val penType: PenType = PenType.FOUNTAIN, val strokeWidth: Float = 4f)

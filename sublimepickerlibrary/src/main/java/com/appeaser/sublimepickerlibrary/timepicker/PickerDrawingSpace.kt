package com.appeaser.sublimepickerlibrary.timepicker

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View

/**
 * Implementation of [android.widget.Space] that uses normal View drawing
 * rather than a no-op. Useful for dialogs and other places where the base View
 * class is too greedy when measured with AT_MOST.
 */
class PickerDrawingSpace : View {

	@JvmOverloads
	constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(context, attrs, defStyleAttr) {
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
	}

	/**
	 * Compare to: [View.getDefaultSize]
	 *
	 *
	 * If mode is AT_MOST, return the child size instead of the parent size
	 * (unless it is too big).
	 */
	private fun getDefaultSizeNonGreedy(size: Int, measureSpec: Int): Int {
		var result = size
		val specMode = View.MeasureSpec.getMode(measureSpec)
		val specSize = View.MeasureSpec.getSize(measureSpec)

		when (specMode) {
			View.MeasureSpec.UNSPECIFIED -> result = size
			View.MeasureSpec.AT_MOST -> result = Math.min(size, specSize)
			View.MeasureSpec.EXACTLY -> result = specSize
		}
		return result
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		setMeasuredDimension(
				getDefaultSizeNonGreedy(suggestedMinimumWidth, widthMeasureSpec),
				getDefaultSizeNonGreedy(suggestedMinimumHeight, heightMeasureSpec))
	}
}

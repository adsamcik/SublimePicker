package com.appeaser.sublimepickerlibrary.datepicker

/**
 * Created by Admin on 07/02/2016.
 */

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.ViewAnimator

import java.util.ArrayList

/**
 * ViewAnimator with a more reasonable handling of MATCH_PARENT.
 */
class PickerViewAnimator : ViewAnimator {
	private val mMatchParentChildren = ArrayList<View>(1)

	constructor(context: Context) : super(context) {}

	constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val measureMatchParentChildren = View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.EXACTLY || View.MeasureSpec.getMode(heightMeasureSpec) != View.MeasureSpec.EXACTLY

		var maxHeight = 0
		var maxWidth = 0
		var childState = 0

		// First measure all children and record maximum dimensions where the
		// spec isn't MATCH_PARENT.
		val count = childCount
		for (i in 0 until count) {
			val child = getChildAt(i)
			if (measureAllChildren || child.visibility != View.GONE) {
				val lp = child.layoutParams as FrameLayout.LayoutParams
				val matchWidth = lp.width == FrameLayout.LayoutParams.MATCH_PARENT
				val matchHeight = lp.height == FrameLayout.LayoutParams.MATCH_PARENT
				if (measureMatchParentChildren && (matchWidth || matchHeight)) {
					mMatchParentChildren.add(child)
				}

				measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)

				// Measured dimensions only count against the maximum
				// dimensions if they're not MATCH_PARENT.
				var state = 0

				if (measureMatchParentChildren && !matchWidth) {
					maxWidth = Math.max(maxWidth, child.measuredWidth
							+ lp.leftMargin + lp.rightMargin)
					state = state or (child.measuredWidthAndState and View.MEASURED_STATE_MASK)
				}

				if (measureMatchParentChildren && !matchHeight) {
					maxHeight = Math.max(maxHeight, child.measuredHeight
							+ lp.topMargin + lp.bottomMargin)
					state = state or (child.measuredHeightAndState shr View.MEASURED_HEIGHT_STATE_SHIFT and (View.MEASURED_STATE_MASK shr View.MEASURED_HEIGHT_STATE_SHIFT))
				}

				childState = View.combineMeasuredStates(childState, state)
			}
		}

		// Account for padding too.
		maxWidth += paddingLeft + paddingRight
		maxHeight += paddingTop + paddingBottom

		// Check against our minimum height and width.
		maxHeight = Math.max(maxHeight, suggestedMinimumHeight)
		maxWidth = Math.max(maxWidth, suggestedMinimumWidth)

		// Check against our foreground's minimum height and width.
		val drawable = foreground
		if (drawable != null) {
			maxHeight = Math.max(maxHeight, drawable.minimumHeight)
			maxWidth = Math.max(maxWidth, drawable.minimumWidth)
		}

		setMeasuredDimension(View.resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
				View.resolveSizeAndState(maxHeight, heightMeasureSpec,
						childState shl View.MEASURED_HEIGHT_STATE_SHIFT))

		// Measure remaining MATCH_PARENT children again using real dimensions.
		val matchCount = mMatchParentChildren.size
		for (i in 0 until matchCount) {
			val child = mMatchParentChildren[i]
			val lp = child.layoutParams as ViewGroup.MarginLayoutParams

			val childWidthMeasureSpec: Int
			if (lp.width == FrameLayout.LayoutParams.MATCH_PARENT) {
				childWidthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
						measuredWidth - paddingLeft - paddingRight
								- lp.leftMargin - lp.rightMargin,
						View.MeasureSpec.EXACTLY)
			} else {
				childWidthMeasureSpec = ViewGroup.getChildMeasureSpec(widthMeasureSpec,
						paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin,
						lp.width)
			}

			val childHeightMeasureSpec: Int
			if (lp.height == FrameLayout.LayoutParams.MATCH_PARENT) {
				childHeightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
						measuredHeight - paddingTop - paddingBottom
								- lp.topMargin - lp.bottomMargin,
						View.MeasureSpec.EXACTLY)
			} else {
				childHeightMeasureSpec = ViewGroup.getChildMeasureSpec(heightMeasureSpec,
						paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin,
						lp.height)
			}

			child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
		}

		mMatchParentChildren.clear()
	}
}

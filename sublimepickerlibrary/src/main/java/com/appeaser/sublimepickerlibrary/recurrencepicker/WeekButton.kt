/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright 2015 Vikram Kakkar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appeaser.sublimepickerlibrary.recurrencepicker

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ToggleButton

import com.appeaser.sublimepickerlibrary.drawables.CheckableDrawable

class WeekButton : ToggleButton {

	// Drawable that provides animations between
	// 'on' & 'off' states
	private var mDrawable: CheckableDrawable? = null

	// Flag to disable animation on state change
	private var noAnimate = false

	// Syncs state
	private val mCallback = object : CheckableDrawable.OnAnimationDone {
		override fun animationIsDone() {
			setTextColor(if (isChecked) mCheckedTextColor else mDefaultTextColor)
			mDrawable!!.setChecked(isChecked)
		}

		override fun animationHasBeenCancelled() {
			setTextColor(if (isChecked) mCheckedTextColor else mDefaultTextColor)
			mDrawable!!.setChecked(isChecked)
		}
	}

	constructor(context: Context) : super(context) {}

	constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {}

	constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {}

	// Wrapper for 'setChecked(boolean)' that does not trigger
	// state-animation
	fun setCheckedNoAnimate(checked: Boolean) {
		noAnimate = true
		isChecked = checked
		noAnimate = false
	}

	override fun setChecked(checked: Boolean) {
		super.setChecked(checked)

		if (mDrawable != null) {
			if (noAnimate) {
				mDrawable!!.setChecked(checked)
				setTextColor(if (isChecked) mCheckedTextColor else mDefaultTextColor)
			} else {
				// Reset text color for animation
				// The correct state color will be
				// set when animation is done or cancelled
				setTextColor(mCheckedTextColor)
				mDrawable!!.setCheckedOnClick(isChecked, mCallback)
			}
		}
	}

	override fun setBackgroundDrawable(d: Drawable) {
		super.setBackgroundDrawable(d)

		if (d is CheckableDrawable) {
			mDrawable = d
		} else {
			// Reset: in case setBackgroundDrawable
			// is called more than once
			mDrawable = null
		}
	}

	companion object {

		private var mDefaultTextColor: Int = 0
		private var mCheckedTextColor: Int = 0

		// State-dependent text-colors
		fun setStateColors(defaultColor: Int, checkedColor: Int) {
			mDefaultTextColor = defaultColor
			mCheckedTextColor = checkedColor
		}
	}
}

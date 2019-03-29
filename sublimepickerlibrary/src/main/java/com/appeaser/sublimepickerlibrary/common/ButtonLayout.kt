/*
 * Copyright 2015 Vikram Kakkar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.appeaser.sublimepickerlibrary.common

import android.annotation.TargetApi
import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout

import com.appeaser.sublimepickerlibrary.R
import com.appeaser.sublimepickerlibrary.utilities.SUtils

class ButtonLayout : LinearLayout, View.OnClickListener {
	// Can be 'android.widget.Button' or 'android.widget.ImageView'
	internal var mPositiveButton: View
	internal var mNegativeButton: View

	// 'Button' used for switching between 'SublimeDatePicker'
	// and 'SublimeTimePicker'. Also displays the currently
	// selected date/time depending on the visible picker
	internal var mSwitcherButton: Button
	internal var mIconOverlayColor: Int = 0 /* color used with the applied 'ColorFilter' */
	internal var mDisabledAlpha: Int = 0 /* android.R.attr.disabledAlpha * 255 */
	internal var mButtonBarBgColor: Int = 0

	internal var mCallback: ButtonHandler.Callback

	// Returns whether switcher button is being used in this layout
	val isSwitcherButtonEnabled: Boolean
		get() = mSwitcherButton.visibility == View.VISIBLE

	@JvmOverloads
	constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.spButtonLayoutStyle) : super(SUtils.createThemeWrapper(context, R.attr.sublimePickerStyle,
			R.style.SublimePickerStyleLight, R.attr.spButtonLayoutStyle,
			R.style.ButtonLayoutStyle), attrs, defStyleAttr) {
		initialize()
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(SUtils.createThemeWrapper(context, R.attr.sublimePickerStyle,
			R.style.SublimePickerStyleLight, R.attr.spButtonLayoutStyle,
			R.style.ButtonLayoutStyle), attrs, defStyleAttr, defStyleRes) {
		initialize()
	}

	internal fun initialize() {
		val context = context
		val res = resources

		val a = context.obtainStyledAttributes(R.styleable.ButtonLayout)

		if (SUtils.isApi_17_OrHigher) {
			layoutDirection = View.LAYOUT_DIRECTION_LOCALE
		}

		orientation = LinearLayout.HORIZONTAL
		gravity = Gravity.BOTTOM

		setPadding(res.getDimensionPixelSize(R.dimen.sp_button_bar_padding_start),
				res.getDimensionPixelSize(R.dimen.sp_button_bar_padding_top),
				res.getDimensionPixelSize(R.dimen.sp_button_bar_padding_end),
				res.getDimensionPixelSize(R.dimen.sp_button_bar_padding_bottom))

		val inflater = context.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
		inflater.inflate(R.layout.sublime_button_panel_layout, this, true)

		mSwitcherButton = findViewById<View>(R.id.buttonSwitcher) as Button

		val bPositive = findViewById<View>(R.id.buttonPositive) as Button
		val bNegative = findViewById<View>(R.id.buttonNegative) as Button

		val ivPositive = findViewById<View>(R.id.imageViewPositive) as ImageView
		val ivNegative = findViewById<View>(R.id.imageViewNegative) as ImageView

		try {
			// obtain float value held by android.R.attr.disabledAlpha
			val typedValueDisabledAlpha = TypedValue()
			getContext().theme.resolveAttribute(android.R.attr.disabledAlpha,
					typedValueDisabledAlpha, true)

			// defaults to 0.5 ~ 122/255
			mDisabledAlpha = if (typedValueDisabledAlpha.type == TypedValue.TYPE_FLOAT)
				(typedValueDisabledAlpha.float * 255).toInt()
			else
				122

			// buttons or icons?
			val presentation = a.getInt(R.styleable.ButtonLayout_spPresentation, 0)

			val bgColor = a.getColor(R.styleable.ButtonLayout_spButtonBgColor,
					SUtils.COLOR_BUTTON_NORMAL)
			val pressedBgColor = a.getColor(R.styleable.ButtonLayout_spButtonPressedBgColor,
					SUtils.COLOR_CONTROL_HIGHLIGHT)

			mButtonBarBgColor = a.getColor(R.styleable.ButtonLayout_spButtonBarBgColor,
					Color.TRANSPARENT)
			SUtils.setViewBackground(mSwitcherButton,
					SUtils.createButtonBg(context, bgColor,
							pressedBgColor))
			setBackgroundColor(mButtonBarBgColor)

			if (presentation == 0 /* mode: Button */) {
				bPositive.visibility = View.VISIBLE
				bNegative.visibility = View.VISIBLE

				bPositive.text = res.getString(R.string.ok)
				bNegative.text = res.getString(R.string.cancel)

				SUtils.setViewBackground(bPositive,
						SUtils.createButtonBg(context, bgColor,
								pressedBgColor))
				SUtils.setViewBackground(bNegative,
						SUtils.createButtonBg(context, bgColor,
								pressedBgColor))

				mPositiveButton = bPositive
				mNegativeButton = bNegative
			} else
			/* mode: ImageView */ {
				ivPositive.visibility = View.VISIBLE
				ivNegative.visibility = View.VISIBLE

				mIconOverlayColor = a.getColor(R.styleable.ButtonLayout_spIconColor,
						SUtils.COLOR_ACCENT)

				ivPositive.setColorFilter(mIconOverlayColor, PorterDuff.Mode.MULTIPLY)
				ivNegative.setColorFilter(mIconOverlayColor, PorterDuff.Mode.MULTIPLY)

				SUtils.setViewBackground(ivPositive,
						SUtils.createImageViewBg(bgColor,
								pressedBgColor))
				SUtils.setViewBackground(ivNegative,
						SUtils.createImageViewBg(bgColor,
								pressedBgColor))

				mPositiveButton = ivPositive
				mNegativeButton = ivNegative
			}
		} finally {
			a.recycle()
		}

		// set OnClickListeners
		mPositiveButton.setOnClickListener(this)
		mNegativeButton.setOnClickListener(this)
		mSwitcherButton.setOnClickListener(this)
	}

	/**
	 * Initializes state for this layout
	 *
	 * @param switcherRequired Whether the switcher button needs
	 * to be shown.
	 * @param callback         Callback to 'SublimePicker'
	 */
	fun applyOptions(switcherRequired: Boolean, callback: ButtonHandler.Callback) {
		mSwitcherButton.visibility = if (switcherRequired) View.VISIBLE else View.GONE
		mCallback = callback
	}

	// Used when the pickers are switched
	fun updateSwitcherText(text: CharSequence) {
		mSwitcherButton.text = text
	}

	// Disables the positive button as and when the user selected options
	// become invalid.
	fun updateValidity(valid: Boolean) {
		mPositiveButton.isEnabled = valid

		// TODO: Find a better way to do this
		// Disabled state for Icon presentation (only for the positive checkmark icon)
		if (mPositiveButton is ImageView) {
			var color = mIconOverlayColor

			if (!valid) {
				color = mDisabledAlpha shl 24 or (mIconOverlayColor and 0x00FFFFFF)
			}

			(mPositiveButton as ImageView).setColorFilter(color,
					PorterDuff.Mode.MULTIPLY)
		}
	}

	override fun onClick(v: View) {
		if (v === mPositiveButton) {
			mCallback.onOkay()
		} else if (v === mNegativeButton) {
			mCallback.onCancel()
		} else if (v === mSwitcherButton) {
			mCallback.onSwitch()
		}
	}
}

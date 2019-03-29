package com.appeaser.sublimepickerlibrary.common

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.PorterDuff
import android.support.v4.content.ContextCompat
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.ImageView

import com.appeaser.sublimepickerlibrary.R
import com.appeaser.sublimepickerlibrary.SublimePicker
import com.appeaser.sublimepickerlibrary.helpers.SublimeOptions
import com.appeaser.sublimepickerlibrary.utilities.SUtils

/**
 * Created by Admin on 15/02/2016.
 */
class ButtonHandler(sublimePicker: SublimePicker) : View.OnClickListener {

	private val mIsInLandscapeMode: Boolean

	private var mPortraitButtonHandler: ButtonLayout? = null

	// Can be 'android.widget.Button' or 'android.widget.ImageView'
	internal var mPositiveButtonDP: View
	internal var mPositiveButtonTP: View
	internal var mNegativeButtonDP: View
	internal var mNegativeButtonTP: View
	// 'Button' used for switching between 'SublimeDatePicker'
	// and 'SublimeTimePicker'. Also displays the currently
	// selected date/time depending on the visible picker
	internal var mSwitcherButtonDP: Button
	internal var mSwitcherButtonTP: Button

	internal var mCallback: Callback

	internal var mIconOverlayColor: Int = 0 /* color used with the applied 'ColorFilter' */
	internal var mDisabledAlpha: Int = 0 /* android.R.attr.disabledAlpha * 255 */
	internal var mButtonBarBgColor: Int = 0

	// Returns whether switcher button is being used in this layout
	val isSwitcherButtonEnabled: Boolean
		get() = if (mIsInLandscapeMode)
			mSwitcherButtonDP.visibility == View.VISIBLE || mSwitcherButtonTP.visibility == View.VISIBLE
		else
			mPortraitButtonHandler!!.isSwitcherButtonEnabled

	init {
		val context = sublimePicker.context

		mIsInLandscapeMode = context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

		if (mIsInLandscapeMode) {
			initializeForLandscape(sublimePicker)
		} else {
			// Takes care of initialization
			mPortraitButtonHandler = sublimePicker.findViewById<View>(R.id.button_layout) as ButtonLayout
		}
	}

	private fun initializeForLandscape(sublimeMaterialPicker: SublimePicker) {
		val context = SUtils.createThemeWrapper(sublimeMaterialPicker.context,
				R.attr.sublimePickerStyle,
				R.style.SublimePickerStyleLight,
				R.attr.spButtonLayoutStyle,
				R.style.ButtonLayoutStyle)
		val res = context.resources

		val a = context.obtainStyledAttributes(R.styleable.ButtonLayout)

		mSwitcherButtonDP = sublimeMaterialPicker.findViewById<View>(R.id.buttonSwitcherDP) as Button
		mSwitcherButtonTP = sublimeMaterialPicker.findViewById<View>(R.id.buttonSwitcherTP) as Button

		val bPositiveDP = sublimeMaterialPicker.findViewById<View>(R.id.buttonPositiveDP) as Button
		val bPositiveTP = sublimeMaterialPicker.findViewById<View>(R.id.buttonPositiveTP) as Button

		val bNegativeDP = sublimeMaterialPicker.findViewById<View>(R.id.buttonNegativeDP) as Button
		val bNegativeTP = sublimeMaterialPicker.findViewById<View>(R.id.buttonNegativeTP) as Button

		val ivPositiveDP = sublimeMaterialPicker.findViewById<View>(R.id.imageViewPositiveDP) as ImageView
		val ivPositiveTP = sublimeMaterialPicker.findViewById<View>(R.id.imageViewPositiveTP) as ImageView

		val ivNegativeDP = sublimeMaterialPicker.findViewById<View>(R.id.imageViewNegativeDP) as ImageView
		val ivNegativeTP = sublimeMaterialPicker.findViewById<View>(R.id.imageViewNegativeTP) as ImageView

		try {
			// obtain float value held by android.R.attr.disabledAlpha
			val typedValueDisabledAlpha = TypedValue()
			context.theme.resolveAttribute(android.R.attr.disabledAlpha,
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

			val buttonInvertedBgColor = a.getColor(R.styleable.ButtonLayout_spButtonInvertedBgColor,
					SUtils.COLOR_ACCENT)
			val buttonPressedInvertedBgColor = a.getColor(R.styleable.ButtonLayout_spButtonPressedInvertedBgColor,
					ContextCompat.getColor(context, R.color.sp_ripple_material_dark))
			SUtils.setViewBackground(mSwitcherButtonDP,
					SUtils.createButtonBg(context, buttonInvertedBgColor,
							buttonPressedInvertedBgColor))
			SUtils.setViewBackground(mSwitcherButtonTP,
					SUtils.createButtonBg(context, buttonInvertedBgColor,
							buttonPressedInvertedBgColor))

			if (presentation == 0 /* mode: Button */) {
				bPositiveDP.visibility = View.VISIBLE
				bPositiveTP.visibility = View.VISIBLE

				bNegativeDP.visibility = View.VISIBLE
				bNegativeTP.visibility = View.VISIBLE

				bPositiveDP.text = res.getString(R.string.ok)
				bPositiveTP.text = res.getString(R.string.ok)

				bNegativeDP.text = res.getString(R.string.cancel)
				bNegativeTP.text = res.getString(R.string.cancel)

				SUtils.setViewBackground(bPositiveDP,
						SUtils.createButtonBg(context, bgColor,
								pressedBgColor))
				SUtils.setViewBackground(bPositiveTP,
						SUtils.createButtonBg(context, bgColor,
								pressedBgColor))

				SUtils.setViewBackground(bNegativeDP,
						SUtils.createButtonBg(context, bgColor,
								pressedBgColor))
				SUtils.setViewBackground(bNegativeTP,
						SUtils.createButtonBg(context, bgColor,
								pressedBgColor))

				mPositiveButtonDP = bPositiveDP
				mPositiveButtonTP = bPositiveTP

				mNegativeButtonDP = bNegativeDP
				mNegativeButtonTP = bNegativeTP
			} else
			/* mode: ImageView */ {
				ivPositiveDP.visibility = View.VISIBLE
				ivPositiveTP.visibility = View.VISIBLE

				ivNegativeDP.visibility = View.VISIBLE
				ivNegativeTP.visibility = View.VISIBLE

				mIconOverlayColor = a.getColor(R.styleable.ButtonLayout_spIconColor,
						SUtils.COLOR_ACCENT)

				ivPositiveDP.setColorFilter(mIconOverlayColor, PorterDuff.Mode.MULTIPLY)
				ivPositiveTP.setColorFilter(mIconOverlayColor, PorterDuff.Mode.MULTIPLY)

				ivNegativeDP.setColorFilter(mIconOverlayColor, PorterDuff.Mode.MULTIPLY)
				ivNegativeTP.setColorFilter(mIconOverlayColor, PorterDuff.Mode.MULTIPLY)

				SUtils.setViewBackground(ivPositiveDP,
						SUtils.createImageViewBg(bgColor,
								pressedBgColor))
				SUtils.setViewBackground(ivPositiveTP,
						SUtils.createImageViewBg(bgColor,
								pressedBgColor))

				SUtils.setViewBackground(ivNegativeDP,
						SUtils.createImageViewBg(bgColor,
								pressedBgColor))
				SUtils.setViewBackground(ivNegativeTP,
						SUtils.createImageViewBg(bgColor,
								pressedBgColor))

				mPositiveButtonDP = ivPositiveDP
				mPositiveButtonTP = ivPositiveTP

				mNegativeButtonDP = ivNegativeDP
				mNegativeButtonTP = ivNegativeTP
			}
		} finally {
			a.recycle()
		}

		// set OnClickListeners
		mPositiveButtonDP.setOnClickListener(this)
		mPositiveButtonTP.setOnClickListener(this)

		mNegativeButtonDP.setOnClickListener(this)
		mNegativeButtonTP.setOnClickListener(this)

		mSwitcherButtonDP.setOnClickListener(this)
		mSwitcherButtonTP.setOnClickListener(this)
	}

	/**
	 * Initializes state for this layout
	 *
	 * @param switcherRequired Whether the switcher button needs
	 * to be shown.
	 * @param callback         Callback to 'SublimePicker'
	 */
	fun applyOptions(switcherRequired: Boolean, callback: Callback) {
		mCallback = callback

		if (mIsInLandscapeMode) {
			mSwitcherButtonDP.visibility = if (switcherRequired) View.VISIBLE else View.GONE
			mSwitcherButtonTP.visibility = if (switcherRequired) View.VISIBLE else View.GONE
		} else {
			// Let ButtonLayout handle callbacks
			mPortraitButtonHandler!!.applyOptions(switcherRequired, callback)
		}
	}

	// Used when the pickers are switched
	fun updateSwitcherText(displayedPicker: SublimeOptions.Picker, text: CharSequence) {
		if (mIsInLandscapeMode) {
			if (displayedPicker == SublimeOptions.Picker.DATE_PICKER) {
				mSwitcherButtonDP.text = text
			} else if (displayedPicker == SublimeOptions.Picker.TIME_PICKER) {
				mSwitcherButtonTP.text = text
			}
		} else {
			mPortraitButtonHandler!!.updateSwitcherText(text)
		}
	}

	// Disables the positive button as and when the user selected options
	// become invalid.
	fun updateValidity(valid: Boolean) {
		if (mIsInLandscapeMode) {
			mPositiveButtonDP.isEnabled = valid
			mPositiveButtonTP.isEnabled = valid

			// TODO: Find a better way to do this
			// Disabled state for Icon presentation (only for the positive check-mark icon)
			if (mPositiveButtonDP is ImageView) {
				var color = mIconOverlayColor

				if (!valid) {
					color = mDisabledAlpha shl 24 or (mIconOverlayColor and 0x00FFFFFF)
				}

				(mPositiveButtonDP as ImageView).setColorFilter(color,
						PorterDuff.Mode.MULTIPLY)
				(mPositiveButtonTP as ImageView).setColorFilter(color,
						PorterDuff.Mode.MULTIPLY)
			}
		} else {
			mPortraitButtonHandler!!.updateValidity(valid)
		}
	}

	override fun onClick(v: View) {
		if (v === mPositiveButtonDP || v === mPositiveButtonTP) {
			mCallback.onOkay()
		} else if (v === mNegativeButtonDP || v === mNegativeButtonTP) {
			mCallback.onCancel()
		} else if (v === mSwitcherButtonDP || v === mSwitcherButtonTP) {
			mCallback.onSwitch()
		}
	}

	interface Callback {
		fun onOkay()
		fun onCancel()
		fun onSwitch()
	}
}

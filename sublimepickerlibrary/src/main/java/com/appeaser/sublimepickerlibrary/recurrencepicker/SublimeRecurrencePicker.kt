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

package com.appeaser.sublimepickerlibrary.recurrencepicker

import android.annotation.TargetApi
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import android.text.format.Time
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import com.appeaser.sublimepickerlibrary.R
import com.appeaser.sublimepickerlibrary.utilities.SUtils

import java.util.ArrayList
import java.util.TimeZone

class SublimeRecurrencePicker : FrameLayout, View.OnClickListener {

	internal var mCurrentRecurrenceOption: RecurrenceOption
	internal var mCurrentView = CurrentView.RECURRENCE_OPTIONS_MENU

	internal var mSelectedStateTextColor: Int = 0
	internal var mUnselectedStateTextColor: Int = 0
	internal var mPressedStateColor: Int = 0
	internal var mSelectedOptionDrawablePadding: Int = 0

	// Holds pre-defined options {DAILY, WEEKLY etc.} in a menu format
	internal var llRecurrenceOptionsMenu: LinearLayout

	// Callback to communicate with SublimePicker
	internal var mCallback: OnRepeatOptionSetListener? = null

	// This holds the recurrence rule provided by SublimePicker.
	// If the user creates a new recurrence rule (using CUSTOM option),
	// this is updated. `null` is a valid value -> a recurrence rule was
	// not provided by SublimePicker & user did not choose CUSTOM
	// option. If at some point, user creates a CUSTOM rule, and then
	// proceeds to choose a preset {DAILY, WEEKLY, MONTHLY, YEARLY},
	// the CUSTOM rule is kept around and user can switch back to it.
	internal var mRecurrenceRule: String? = null

	// Used to indicate the chosen option
	internal var mCheckmarkDrawable: Drawable? = null

	internal var mRecurrenceOptionCreator: RecurrenceOptionCreator

	// Currently selected date from
	// date-picker to use with RecurrenceOptionCreator
	internal var mCurrentlyChosenTime: Long = 0

	// For easy traversal through 7 options/views.
	internal var mRepeatOptionTextViews: ArrayList<TextView>

	// Listener for RecurrenceOptionCreator
	internal var mOnRecurrenceSetListener: RecurrenceOptionCreator.OnRecurrenceSetListener = object : RecurrenceOptionCreator.OnRecurrenceSetListener {
		override fun onRecurrenceSet(rrule: String?) {
			// Update options
			mRecurrenceRule = rrule
			mCurrentRecurrenceOption = RecurrenceOption.CUSTOM
			mCurrentView = CurrentView.RECURRENCE_OPTIONS_MENU

			// If user has created a RecurrenceRule, bypass this
			// picker and show the previously shown picker (DatePicker
			// or TimePicker).
			if (mCallback != null) {
				mCallback!!.onRepeatOptionSet(RecurrenceOption.CUSTOM, rrule)
			}
		}

		override fun onCancelled() {
			// If cancelled, bring user back to recurrence options menu
			mCurrentView = CurrentView.RECURRENCE_OPTIONS_MENU
			updateView()
		}
	}

	// Pre-defined recurrence options that are shown in a menu
	// format. Choosing 'CUSTOM' takes the user
	// to 'RecurrenceOptionCreator'.
	enum class RecurrenceOption private constructor(private val optionName: String) {
		DOES_NOT_REPEAT("DOES NOT REPEAT"),
		DAILY("DAILY"), WEEKLY("WEEKLY"), MONTHLY("MONTHLY"),
		YEARLY("YEARLY"), CUSTOM("CUSTOM...");

		override fun toString(): String {
			return optionName
		}
	}

	// Used to keep track of currently visible view
	private enum class CurrentView {
		RECURRENCE_OPTIONS_MENU, RECURRENCE_CREATOR
	}

	@JvmOverloads
	constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.spRecurrencePickerStyle) : super(SUtils.createThemeWrapper(context, R.attr.sublimePickerStyle,
			R.style.SublimePickerStyleLight, R.attr.spRecurrencePickerStyle,
			R.style.SublimeRecurrencePickerStyle), attrs, defStyleAttr) {
		initializeLayout()
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(SUtils.createThemeWrapper(context, R.attr.sublimePickerStyle,
			R.style.SublimePickerStyleLight, R.attr.spRecurrencePickerStyle,
			R.style.SublimeRecurrencePickerStyle), attrs, defStyleAttr, defStyleRes) {
		initializeLayout()
	}

	// Initialize UI
	internal fun initializeLayout() {
		val context = context

		LayoutInflater.from(context).inflate(R.layout.sublime_recurrence_picker, this)

		llRecurrenceOptionsMenu = findViewById<View>(R.id.llRecurrenceOptionsMenu) as LinearLayout
		mRecurrenceOptionCreator = findViewById<View>(R.id.recurrenceOptionCreator) as RecurrenceOptionCreator
		val tvRecurrenceHeading = findViewById<View>(R.id.tvHeading) as TextView

		// 'mSelectedOptionDrawablePadding' equals left-padding
		// for option TextViews
		mSelectedOptionDrawablePadding = context.resources
				.getDimensionPixelSize(R.dimen.selected_recurrence_option_drawable_padding)

		val a = context.obtainStyledAttributes(R.styleable.SublimeRecurrencePicker)
		try {
			val headingBgColor = a.getColor(
					R.styleable.SublimeRecurrencePicker_spHeaderBackground,
					SUtils.COLOR_ACCENT)
			val pickerBgColor = a.getColor(
					R.styleable.SublimeRecurrencePicker_spPickerBackground,
					SUtils.COLOR_BACKGROUND)

			// Sets background color for API versions >= Lollipop
			// Sets background drawable with rounded corners on
			// API versions < Lollipop
			if (pickerBgColor != Color.TRANSPARENT)
				SUtils.setViewBackground(this, pickerBgColor, SUtils.CORNERS_ALL)

			SUtils.setViewBackground(tvRecurrenceHeading, headingBgColor,
					SUtils.CORNER_TOP_LEFT or SUtils.CORNER_TOP_RIGHT)

			// State colors
			mSelectedStateTextColor = a.getColor(
					R.styleable.SublimeRecurrencePicker_spSelectedOptionTextColor,
					SUtils.COLOR_ACCENT)
			mUnselectedStateTextColor = a.getColor(
					R.styleable.SublimeRecurrencePicker_spUnselectedOptionsTextColor,
					SUtils.COLOR_TEXT_PRIMARY)
			mPressedStateColor = a.getColor(
					R.styleable.SublimeRecurrencePicker_spPressedOptionBgColor,
					SUtils.COLOR_CONTROL_HIGHLIGHT)

			// Defaults to the included checkmark drawable
			mCheckmarkDrawable = a.getDrawable(R.styleable.SublimeRecurrencePicker_spSelectedOptionDrawable)
			if (mCheckmarkDrawable == null) {
				mCheckmarkDrawable = context.resources
						.getDrawable(R.drawable.checkmark_medium_ff)
			}

			// Android Studio recommends this check :-/
			// Apply color filter to match selected option text color
			if (mCheckmarkDrawable != null)
				mCheckmarkDrawable!!.setColorFilter(mSelectedStateTextColor, PorterDuff.Mode.MULTIPLY)
		} finally {
			a.recycle()
		}

		// Options/Views
		mRepeatOptionTextViews = ArrayList()
		mRepeatOptionTextViews.add(
				findViewById<View>(R.id.tvChosenCustomOption) as TextView)
		mRepeatOptionTextViews.add(
				findViewById<View>(R.id.tvDoesNotRepeat) as TextView)
		mRepeatOptionTextViews.add(
				findViewById<View>(R.id.tvDaily) as TextView)
		mRepeatOptionTextViews.add(
				findViewById<View>(R.id.tvWeekly) as TextView)
		mRepeatOptionTextViews.add(
				findViewById<View>(R.id.tvMonthly) as TextView)
		mRepeatOptionTextViews.add(
				findViewById<View>(R.id.tvYearly) as TextView)
		mRepeatOptionTextViews.add(
				findViewById<View>(R.id.tvCustom) as TextView)

		// Set bg StateListDrawables
		for (v in mRepeatOptionTextViews) {
			SUtils.setViewBackground(v,
					createOptionBg(mPressedStateColor))
		}
	}

	// Called by SublimePicker to initialize state & provide callback
	fun initializeData(callback: OnRepeatOptionSetListener,
	                   initialOption: RecurrenceOption, recurrenceRule: String,
	                   currentlyChosenTime: Long) {
		mCallback = callback
		mRecurrenceRule = recurrenceRule

		mCurrentlyChosenTime = currentlyChosenTime
		mCurrentRecurrenceOption = initialOption

		// Initialize state for RecurrenceOptionCreator
		mRecurrenceOptionCreator.initializeData(mCurrentlyChosenTime, null,
				mRecurrenceRule, mOnRecurrenceSetListener)
	}

	// Controls the visibility of recurrence options menu
	// & recurrence option creator
	fun updateView() {
		if (mCurrentView == CurrentView.RECURRENCE_OPTIONS_MENU) {
			mRecurrenceOptionCreator.visibility = View.GONE
			llRecurrenceOptionsMenu.visibility = View.VISIBLE

			// Current repeat option may have changed
			updateFlowLayout(mCurrentRecurrenceOption)

			// reset `scrollY` to 0
			val scrollView = llRecurrenceOptionsMenu.findViewById<View>(R.id.svRecurrenceOptionsMenu) as ScrollView
			llRecurrenceOptionsMenu.post {
				if (scrollView.scrollY != 0)
					scrollView.fullScroll(ScrollView.FOCUS_UP)
			}
		} else if (mCurrentView == CurrentView.RECURRENCE_CREATOR) {
			llRecurrenceOptionsMenu.visibility = View.GONE
			mRecurrenceOptionCreator.visibility = View.VISIBLE
		}
	}

	internal fun updateFlowLayout(recurrenceOption: RecurrenceOption) {
		// Currently selected recurrence option
		val viewIdToSelect: Int

		when (recurrenceOption) {
			SublimeRecurrencePicker.RecurrenceOption.DOES_NOT_REPEAT -> viewIdToSelect = R.id.tvDoesNotRepeat
			SublimeRecurrencePicker.RecurrenceOption.DAILY -> viewIdToSelect = R.id.tvDaily
			SublimeRecurrencePicker.RecurrenceOption.WEEKLY -> viewIdToSelect = R.id.tvWeekly
			SublimeRecurrencePicker.RecurrenceOption.MONTHLY -> viewIdToSelect = R.id.tvMonthly
			SublimeRecurrencePicker.RecurrenceOption.YEARLY -> viewIdToSelect = R.id.tvYearly
			SublimeRecurrencePicker.RecurrenceOption.CUSTOM -> viewIdToSelect = R.id.tvChosenCustomOption
			else -> viewIdToSelect = R.id.tvDoesNotRepeat
		}

		for (tv in mRepeatOptionTextViews) {
			tv.setOnClickListener(this)

			// If we have a non-empty recurrence rule,
			// display it for easy re-selection
			if (tv.id == R.id.tvChosenCustomOption) {
				if (!TextUtils.isEmpty(mRecurrenceRule)) {
					val eventRecurrence = EventRecurrence()
					eventRecurrence.parse(mRecurrenceRule)
					val startDate = Time(TimeZone.getDefault().id)
					startDate.set(mCurrentlyChosenTime)
					eventRecurrence.setStartDate(startDate)

					tv.visibility = View.VISIBLE

					tv.text = EventRecurrenceFormatter.getRepeatString(
							context, context.resources,
							eventRecurrence, true)
				} else { // hide this TextView since 'mRecurrenceRule' is not available
					tv.visibility = View.GONE
					continue
				}
			}

			// Selected option
			if (tv.id == viewIdToSelect) {
				// Set checkmark drawable & drawable-padding
				tv.setCompoundDrawablesWithIntrinsicBounds(null, null,
						mCheckmarkDrawable, null)
				tv.compoundDrawablePadding = mSelectedOptionDrawablePadding
				tv.setTextColor(mSelectedStateTextColor)

				continue
			}

			// Unselected options
			tv.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
			tv.setTextColor(mUnselectedStateTextColor)
		}
	}

	override fun onClick(v: View) {
		val viewId = v.id

		// Can't use 'switch' here since this is a library module

		if (viewId == R.id.tvChosenCustomOption) {
			// Exit
			// Previously set custom option
			mCurrentRecurrenceOption = RecurrenceOption.CUSTOM

			if (mCallback != null) {
				mCallback!!.onRepeatOptionSet(RecurrenceOption.CUSTOM, mRecurrenceRule)
			}

			return
		} else if (v.id == R.id.tvDoesNotRepeat) {
			mCurrentRecurrenceOption = RecurrenceOption.DOES_NOT_REPEAT
		} else if (v.id == R.id.tvDaily) {
			mCurrentRecurrenceOption = RecurrenceOption.DAILY
		} else if (v.id == R.id.tvWeekly) {
			mCurrentRecurrenceOption = RecurrenceOption.WEEKLY
		} else if (v.id == R.id.tvMonthly) {
			mCurrentRecurrenceOption = RecurrenceOption.MONTHLY
		} else if (v.id == R.id.tvYearly) {
			mCurrentRecurrenceOption = RecurrenceOption.YEARLY
		} else if (v.id == R.id.tvCustom) {
			// Show RecurrenceOptionCreator
			mCurrentView = CurrentView.RECURRENCE_CREATOR
			updateView()
			return
		} else {
			// Default
			mCurrentRecurrenceOption = RecurrenceOption.DOES_NOT_REPEAT
		}

		if (mCallback != null) {
			// A preset value has been picked.
			mCallback!!.onRepeatOptionSet(mCurrentRecurrenceOption, null)
		}
	}

	// Utility for creating API-specific bg drawables
	internal fun createOptionBg(pressedBgColor: Int): Drawable {
		return if (SUtils.isApi_21_OrHigher) {
			createRippleDrawableForOption(pressedBgColor)
		} else {
			createStateListDrawableForOption(pressedBgColor)
		}
	}

	private fun createStateListDrawableForOption(pressedBgColor: Int): Drawable {
		val sld = StateListDrawable()

		sld.addState(intArrayOf(android.R.attr.state_pressed),
				ColorDrawable(pressedBgColor))
		sld.addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))

		return sld
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private fun createRippleDrawableForOption(pressedBgColor: Int): Drawable {
		return RippleDrawable(ColorStateList.valueOf(pressedBgColor), null,
				/* mask */ColorDrawable(Color.BLACK))
	}

	override fun onSaveInstanceState(): Parcelable? {
		return SavedState(super.onSaveInstanceState(), mCurrentView,
				mCurrentRecurrenceOption, mRecurrenceRule)
	}

	override fun onRestoreInstanceState(state: Parcelable) {
		val bss = state as View.BaseSavedState
		super.onRestoreInstanceState(bss.superState)
		val ss = bss as SavedState

		mCurrentView = ss.currentView
		mCurrentRecurrenceOption = ss.currentRepeatOption
		mRecurrenceRule = ss.recurrenceRule
		updateView()
	}

	/**
	 * Class for managing state storing/restoring.
	 */
	private class SavedState : View.BaseSavedState {

		val currentView: CurrentView
		val currentRepeatOption: RecurrenceOption
		val recurrenceRule: String

		/**
		 * Constructor called from [SublimeRecurrencePicker.onSaveInstanceState]
		 */
		private constructor(superState: Parcelable, currentView: CurrentView,
		                    currentRecurrenceOption: RecurrenceOption, recurrenceRule: String) : super(superState) {

			this.currentView = currentView
			currentRepeatOption = currentRecurrenceOption
			this.recurrenceRule = recurrenceRule
		}

		/**
		 * Constructor called from [.CREATOR]
		 */
		private constructor(`in`: Parcel) : super(`in`) {

			currentView = CurrentView.valueOf(`in`.readString())
			currentRepeatOption = RecurrenceOption.valueOf(`in`.readString())
			recurrenceRule = `in`.readString()
		}

		override fun writeToParcel(dest: Parcel, flags: Int) {
			super.writeToParcel(dest, flags)

			dest.writeString(currentView.name)
			dest.writeString(currentRepeatOption.name)
			dest.writeString(recurrenceRule)
		}

		companion object {

			// suppress unused and hiding
			val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {

				override fun createFromParcel(`in`: Parcel): SavedState {
					return SavedState(`in`)
				}

				override fun newArray(size: Int): Array<SavedState> {
					return arrayOfNulls(size)
				}
			}
		}
	}

	interface OnRepeatOptionSetListener {
		/**
		 * User has either selected one of the pre-defined
		 * recurrence options or used RecurrenceOptionCreator
		 * to create a RecurrenceRule
		 *
		 * @param option         chosen repeat option
		 * @param recurrenceRule user-created recurrence-rule
		 * if chosen 'option' is 'RepeatOption.CUSTOM',
		 * 'null' otherwise.
		 */
		fun onRepeatOptionSet(option: RecurrenceOption, recurrenceRule: String?)

		/**
		 * Currently not used.
		 */
		fun onDone()
	}
}

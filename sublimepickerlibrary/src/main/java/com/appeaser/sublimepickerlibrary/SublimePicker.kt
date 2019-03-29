/*
 * Copyright 2016 Vikram Kakkar
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

package com.appeaser.sublimepickerlibrary

import android.animation.LayoutTransition
import android.annotation.TargetApi
import android.content.Context
import android.content.res.TypedArray
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils
import android.text.format.DateUtils
import android.util.AttributeSet
import android.util.SparseArray
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

import com.appeaser.sublimepickerlibrary.common.ButtonHandler
import com.appeaser.sublimepickerlibrary.datepicker.SelectedDate
import com.appeaser.sublimepickerlibrary.datepicker.SublimeDatePicker
import com.appeaser.sublimepickerlibrary.drawables.OverflowDrawable
import com.appeaser.sublimepickerlibrary.helpers.SublimeListenerAdapter
import com.appeaser.sublimepickerlibrary.helpers.SublimeOptions
import com.appeaser.sublimepickerlibrary.recurrencepicker.SublimeRecurrencePicker
import com.appeaser.sublimepickerlibrary.timepicker.SublimeTimePicker
import com.appeaser.sublimepickerlibrary.utilities.SUtils

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A customizable view that provisions picking of a date,
 * time and recurrence option, all from a single user-interface.
 * You can also view 'SublimePicker' as a collection of
 * material-styled (API 23) DatePicker, TimePicker
 * and RecurrencePicker, backported to API 14.
 * You can opt for any combination of these three Pickers.
 */
class SublimePicker : FrameLayout, SublimeDatePicker.OnDateChangedListener, SublimeDatePicker.DatePickerValidationCallback, SublimeTimePicker.TimePickerValidationCallback {

	// Container for 'SublimeDatePicker' & 'SublimeTimePicker'
	private var llMainContentHolder: LinearLayout? = null

	// For access to 'SublimeRecurrencePicker'
	private var ivRecurrenceOptionsDP: ImageView? = null
	private var ivRecurrenceOptionsTP: ImageView? = null

	// Recurrence picker options
	private var mSublimeRecurrencePicker: SublimeRecurrencePicker? = null
	private var mCurrentRecurrenceOption: SublimeRecurrencePicker.RecurrenceOption = SublimeRecurrencePicker.RecurrenceOption.DOES_NOT_REPEAT
	private var mRecurrenceRule: String? = null

	// Keeps track which picker is showing
	private var mCurrentPicker: SublimeOptions.Picker? = null
	private var mHiddenPicker: SublimeOptions.Picker? = null

	// Date picker
	private var mDatePicker: SublimeDatePicker? = null

	// Time picker
	private var mTimePicker: SublimeTimePicker? = null

	// Callback
	private var mListener: SublimeListenerAdapter? = null

	// Client-set options
	private var mOptions: SublimeOptions? = null

	// Ok, cancel & switch button handler
	private var mButtonLayout: ButtonHandler? = null

	// Flags set based on client-set options {SublimeOptions}
	private var mDatePickerValid = true
	private var mTimePickerValid = true
	private var mDatePickerEnabled: Boolean = false
	private var mTimePickerEnabled: Boolean = false
	private var mRecurrencePickerEnabled: Boolean = false
	private var mDatePickerSyncStateCalled: Boolean = false

	// Used if listener returns
	// null/invalid(zero-length, empty) string
	private var mDefaultDateFormatter: DateFormat? = null
	private var mDefaultTimeFormatter: DateFormat? = null

	// Listener for recurrence picker
	private val mRepeatOptionSetListener = object : SublimeRecurrencePicker.OnRepeatOptionSetListener {
		override fun onRepeatOptionSet(option: SublimeRecurrencePicker.RecurrenceOption, recurrenceRule: String?) {
			mCurrentRecurrenceOption = option
			mRecurrenceRule = recurrenceRule
			onDone()
		}

		override fun onDone() {
			if (mDatePickerEnabled || mTimePickerEnabled) {
				updateCurrentPicker()
				updateDisplay()
			} else { /* No other picker is activated. Dismiss. */
				mButtonLayoutCallback.onOkay()
			}
		}
	}

	// Handle ok, cancel & switch button click events
	private val mButtonLayoutCallback = object : ButtonHandler.Callback {
		override fun onOkay() {
			var selectedDate: SelectedDate? = null

			if (mDatePickerEnabled) {
				selectedDate = mDatePicker!!.selectedDate
			}

			var hour = -1
			var minute = -1

			if (mTimePickerEnabled) {
				hour = mTimePicker!!.currentHour
				minute = mTimePicker!!.currentMinute
			}

			var recurrenceOption: SublimeRecurrencePicker.RecurrenceOption = SublimeRecurrencePicker.RecurrenceOption.DOES_NOT_REPEAT
			var recurrenceRule: String? = null

			if (mRecurrencePickerEnabled) {
				recurrenceOption = mCurrentRecurrenceOption

				if (recurrenceOption == SublimeRecurrencePicker.RecurrenceOption.CUSTOM) {
					recurrenceRule = mRecurrenceRule
				}
			}

			mListener!!.onDateTimeRecurrenceSet(this@SublimePicker,
					// DatePicker
					selectedDate,
					// TimePicker
					hour, minute,
					// RecurrencePicker
					recurrenceOption, recurrenceRule)
		}

		override fun onCancel() {
			mListener!!.onCancelled()
		}

		override fun onSwitch() {
			mCurrentPicker = if (mCurrentPicker == SublimeOptions.Picker.DATE_PICKER)
				SublimeOptions.Picker.TIME_PICKER
			else
				SublimeOptions.Picker.DATE_PICKER

			updateDisplay()
		}
	}

	@JvmOverloads
	constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.sublimePickerStyle) : super(createThemeWrapper(context), attrs, defStyleAttr) {
		initializeLayout()
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(createThemeWrapper(context), attrs, defStyleAttr, defStyleRes) {
		initializeLayout()
	}

	private fun initializeLayout() {
		val context = context
		SUtils.initializeResources(context)

		LayoutInflater.from(context).inflate(R.layout.sublime_picker_view_layout,
				this, true)

		mDefaultDateFormatter = DateFormat.getDateInstance(DateFormat.MEDIUM,
				Locale.getDefault())
		mDefaultTimeFormatter = DateFormat.getTimeInstance(DateFormat.SHORT,
				Locale.getDefault())
		mDefaultTimeFormatter!!.timeZone = TimeZone.getTimeZone("GMT+0")

		llMainContentHolder = findViewById<View>(R.id.llMainContentHolder) as LinearLayout
		mButtonLayout = ButtonHandler(this)
		initializeRecurrencePickerSwitch()

		mDatePicker = findViewById<View>(R.id.datePicker) as SublimeDatePicker
		mTimePicker = findViewById<View>(R.id.timePicker) as SublimeTimePicker
		mSublimeRecurrencePicker = findViewById<View>(R.id.repeat_option_picker) as SublimeRecurrencePicker
	}

	fun initializePicker(options: SublimeOptions?, listener: SublimeListenerAdapter?) {
		var options = options
		if (listener == null) {
			throw IllegalArgumentException("Listener cannot be null.")
		}

		if (options != null) {
			options.verifyValidity()
		} else {
			options = SublimeOptions()
		}

		mOptions = options
		mListener = listener

		processOptions()
		updateDisplay()
	}

	// Called before 'RecurrencePicker' is shown
	private fun updateHiddenPicker() {
		if (mDatePickerEnabled && mTimePickerEnabled) {
			mHiddenPicker = if (mDatePicker!!.visibility == View.VISIBLE)
				SublimeOptions.Picker.DATE_PICKER
			else
				SublimeOptions.Picker.TIME_PICKER
		} else if (mDatePickerEnabled) {
			mHiddenPicker = SublimeOptions.Picker.DATE_PICKER
		} else if (mTimePickerEnabled) {
			mHiddenPicker = SublimeOptions.Picker.TIME_PICKER
		} else {
			mHiddenPicker = SublimeOptions.Picker.INVALID
		}
	}

	// 'mHiddenPicker' retains the Picker that was active
	// before 'RecurrencePicker' was shown. On its dismissal,
	// we have an option to show either 'DatePicker' or 'TimePicker'.
	// 'mHiddenPicker' helps identify the correct option.
	private fun updateCurrentPicker() {
		if (mHiddenPicker != SublimeOptions.Picker.INVALID) {
			mCurrentPicker = mHiddenPicker
		} else {
			throw RuntimeException("Logic issue: No valid option for mCurrentPicker")
		}
	}

	private fun updateDisplay() {
		var switchButtonText: CharSequence?

		if (mCurrentPicker == SublimeOptions.Picker.DATE_PICKER) {

			if (mTimePickerEnabled) {
				mTimePicker!!.visibility = View.GONE
			}

			if (mRecurrencePickerEnabled) {
				mSublimeRecurrencePicker!!.visibility = View.GONE
			}

			mDatePicker!!.visibility = View.VISIBLE
			llMainContentHolder!!.visibility = View.VISIBLE

			if (mButtonLayout!!.isSwitcherButtonEnabled) {
				val toFormat = Date(mTimePicker!!.currentHour * DateUtils.HOUR_IN_MILLIS + mTimePicker!!.currentMinute * DateUtils.MINUTE_IN_MILLIS)

				switchButtonText = mListener!!.formatTime(toFormat)

				if (TextUtils.isEmpty(switchButtonText)) {
					switchButtonText = mDefaultTimeFormatter!!.format(toFormat)
				}

				mButtonLayout!!.updateSwitcherText(SublimeOptions.Picker.DATE_PICKER, switchButtonText!!)
			}

			if (!mDatePickerSyncStateCalled) {
				mDatePickerSyncStateCalled = true
			}
		} else if (mCurrentPicker == SublimeOptions.Picker.TIME_PICKER) {
			if (mDatePickerEnabled) {
				mDatePicker!!.visibility = View.GONE
			}

			if (mRecurrencePickerEnabled) {
				mSublimeRecurrencePicker!!.visibility = View.GONE
			}

			mTimePicker!!.visibility = View.VISIBLE
			llMainContentHolder!!.visibility = View.VISIBLE

			if (mButtonLayout!!.isSwitcherButtonEnabled) {
				val selectedDate = mDatePicker!!.selectedDate
				switchButtonText = mListener!!.formatDate(selectedDate)

				if (TextUtils.isEmpty(switchButtonText)) {
					if (selectedDate.type == SelectedDate.Type.SINGLE) {
						val toFormat = Date(mDatePicker!!.selectedDateInMillis)
						switchButtonText = mDefaultDateFormatter!!.format(toFormat)
					} else if (selectedDate.type == SelectedDate.Type.RANGE) {
						switchButtonText = formatDateRange(selectedDate)
					}
				}

				mButtonLayout!!.updateSwitcherText(SublimeOptions.Picker.TIME_PICKER, switchButtonText)
			}
		} else if (mCurrentPicker == SublimeOptions.Picker.REPEAT_OPTION_PICKER) {
			updateHiddenPicker()
			mSublimeRecurrencePicker!!.updateView()

			if (mDatePickerEnabled || mTimePickerEnabled) {
				llMainContentHolder!!.visibility = View.GONE
			}

			mSublimeRecurrencePicker!!.visibility = View.VISIBLE
		}
	}

	private fun formatDateRange(selectedDate: SelectedDate): String {
		val startDate = selectedDate.startDate
		val endDate = selectedDate.endDate

		startDate.set(Calendar.MILLISECOND, 0)
		startDate.set(Calendar.SECOND, 0)
		startDate.set(Calendar.MINUTE, 0)
		startDate.set(Calendar.HOUR, 0)

		endDate.set(Calendar.MILLISECOND, 0)
		endDate.set(Calendar.SECOND, 0)
		endDate.set(Calendar.MINUTE, 0)
		endDate.set(Calendar.HOUR, 0)
		// Move to next day since we are nulling out the time fields
		endDate.add(Calendar.DAY_OF_MONTH, 1)

		val elapsedTime = (endDate.timeInMillis - startDate.timeInMillis).toFloat()

		when {
			elapsedTime >= DateUtils.YEAR_IN_MILLIS -> {
				val years = elapsedTime / DateUtils.YEAR_IN_MILLIS

				val roundUp = years - years.toInt() > 0.5f
				val yearsVal = if (roundUp) (years + 1).toInt() else years.toInt()

				return "~" + yearsVal + " " + if (yearsVal == 1) "year" else "years"
			}
			elapsedTime >= MONTH_IN_MILLIS -> {
				val months = elapsedTime / MONTH_IN_MILLIS

				val roundUp = months - months.toInt() > 0.5f
				val monthsVal = if (roundUp) (months + 1).toInt() else months.toInt()

				return "~" + monthsVal + " " + if (monthsVal == 1) "month" else "months"
			}
			else -> {
				val days = elapsedTime / DateUtils.DAY_IN_MILLIS

				val roundUp = days - days.toInt() > 0.5f
				val daysVal = if (roundUp) (days + 1).toInt() else days.toInt()

				return "~" + daysVal + " " + if (daysVal == 1) "day" else "days"
			}
		}
	}

	private fun initializeRecurrencePickerSwitch() {
		ivRecurrenceOptionsDP = findViewById<View>(R.id.ivRecurrenceOptionsDP) as ImageView
		ivRecurrenceOptionsTP = findViewById<View>(R.id.ivRecurrenceOptionsTP) as ImageView

		val iconColor: Int
		val pressedStateBgColor: Int

		val typedArray = context.obtainStyledAttributes(R.styleable.SublimePicker)
		try {
			iconColor = typedArray.getColor(R.styleable.SublimePicker_spOverflowIconColor,
					SUtils.COLOR_TEXT_PRIMARY_INVERSE)
			pressedStateBgColor = typedArray.getColor(R.styleable.SublimePicker_spOverflowIconPressedBgColor,
					SUtils.COLOR_TEXT_PRIMARY)
		} finally {
			typedArray.recycle()
		}

		ivRecurrenceOptionsDP!!.setImageDrawable(
				OverflowDrawable(context, iconColor))
		SUtils.setViewBackground(ivRecurrenceOptionsDP!!,
				SUtils.createOverflowButtonBg(pressedStateBgColor))

		ivRecurrenceOptionsTP!!.setImageDrawable(
				OverflowDrawable(context, iconColor))
		SUtils.setViewBackground(ivRecurrenceOptionsTP!!,
				SUtils.createOverflowButtonBg(pressedStateBgColor))

		ivRecurrenceOptionsDP!!.setOnClickListener {
			mCurrentPicker = SublimeOptions.Picker.REPEAT_OPTION_PICKER
			updateDisplay()
		}

		ivRecurrenceOptionsTP!!.setOnClickListener {
			mCurrentPicker = SublimeOptions.Picker.REPEAT_OPTION_PICKER
			updateDisplay()
		}
	}

	override fun onSaveInstanceState(): Parcelable? {
		return SavedState(super.onSaveInstanceState()!!, mCurrentPicker, mHiddenPicker,
				mCurrentRecurrenceOption, mRecurrenceRule)
	}

	override fun onRestoreInstanceState(state: Parcelable) {
		val bss = state as View.BaseSavedState
		super.onRestoreInstanceState(bss.superState)
		val ss = bss as SavedState

		mCurrentPicker = ss.currentPicker
		mCurrentRecurrenceOption = ss.currentRepeatOption
		mRecurrenceRule = ss.recurrenceRule

		mHiddenPicker = ss.hiddenPicker
	}

	override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
		super.dispatchRestoreInstanceState(container)
		updateDisplay()
	}

	/**
	 * Class for managing state storing/restoring.
	 */
	private class SavedState : View.BaseSavedState {

		val currentPicker: SublimeOptions.Picker
		/*One of DatePicker/TimePicker*/ val hiddenPicker: SublimeOptions.Picker
		val currentRepeatOption: SublimeRecurrencePicker.RecurrenceOption
		val recurrenceRule: String

		/**
		 * Constructor called from [SublimePicker.onSaveInstanceState]
		 */
		internal constructor(superState: Parcelable, currentPicker: SublimeOptions.Picker,
		                    hiddenPicker: SublimeOptions.Picker,
		                    recurrenceOption: SublimeRecurrencePicker.RecurrenceOption,
		                    recurrenceRule: String) : super(superState) {

			this.currentPicker = currentPicker
			this.hiddenPicker = hiddenPicker
			currentRepeatOption = recurrenceOption
			this.recurrenceRule = recurrenceRule
		}

		/**
		 * Constructor called from [.CREATOR]
		 */
		private constructor(`in`: Parcel) : super(`in`) {

			currentPicker = SublimeOptions.Picker.valueOf(`in`.readString())
			hiddenPicker = SublimeOptions.Picker.valueOf(`in`.readString())
			currentRepeatOption = SublimeRecurrencePicker.RecurrenceOption.valueOf(`in`.readString())
			recurrenceRule = `in`.readString()
		}

		override fun writeToParcel(dest: Parcel, flags: Int) {
			super.writeToParcel(dest, flags)

			dest.writeString(currentPicker.name)
			dest.writeString(hiddenPicker.name)
			dest.writeString(currentRepeatOption.name)
			dest.writeString(recurrenceRule)
		}

		companion object {

			// suppress unused and hiding
			@JvmField
			val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {

				override fun createFromParcel(`in`: Parcel): SavedState {
					return SavedState(`in`)
				}

				override fun newArray(size: Int): Array<SavedState?> {
					return arrayOfNulls(size)
				}
			}
		}
	}

	private fun processOptions() {
		if (mOptions!!.animateLayoutChanges()) {
			// Basic Layout Change Animation(s)
			val layoutTransition = LayoutTransition()
			if (SUtils.isApi_16_OrHigher) {
				layoutTransition.enableTransitionType(LayoutTransition.CHANGING)
			}
			setLayoutTransition(layoutTransition)
		} else {
			layoutTransition = null
		}

		mDatePickerEnabled = mOptions!!.isDatePickerActive
		mTimePickerEnabled = mOptions!!.isTimePickerActive
		mRecurrencePickerEnabled = mOptions!!.isRecurrencePickerActive

		if (mDatePickerEnabled) {
			//int[] dateParams = mOptions.getDateParams();
			//mDatePicker.init(dateParams[0] /* year */,
			//        dateParams[1] /* month of year */,
			//        dateParams[2] /* day of month */,
			//        mOptions.canPickDateRange(),
			//        this);
			mDatePicker!!.init(mOptions!!.dateParams, mOptions!!.canPickDateRange(), this)

			val dateRange = mOptions!!.dateRange

			if (dateRange[0] /* min date */ != java.lang.Long.MIN_VALUE) {
				mDatePicker!!.setMinDate(dateRange[0])
			}

			if (dateRange[1] /* max date */ != java.lang.Long.MIN_VALUE) {
				mDatePicker!!.setMaxDate(dateRange[1])
			}

			mDatePicker!!.setValidationCallback(this)

			ivRecurrenceOptionsDP!!.visibility = if (mRecurrencePickerEnabled)
				View.VISIBLE
			else
				View.GONE
		} else {
			llMainContentHolder!!.removeView(mDatePicker)
			mDatePicker = null
		}

		if (mTimePickerEnabled) {
			val timeParams = mOptions!!.timeParams
			mTimePicker!!.currentHour = timeParams[0] /* hour of day */
			mTimePicker!!.currentMinute = timeParams[1] /* minute */
			mTimePicker!!.is24HourView = mOptions!!.is24HourView
			mTimePicker!!.setValidationCallback(this)

			ivRecurrenceOptionsTP!!.visibility = if (mRecurrencePickerEnabled)
				View.VISIBLE
			else
				View.GONE
		} else {
			llMainContentHolder!!.removeView(mTimePicker)
			mTimePicker = null
		}

		if (mDatePickerEnabled && mTimePickerEnabled) {
			mButtonLayout!!.applyOptions(true /* show switch button */,
					mButtonLayoutCallback)
		} else {
			mButtonLayout!!.applyOptions(false /* hide switch button */,
					mButtonLayoutCallback)
		}

		if (!mDatePickerEnabled && !mTimePickerEnabled) {
			removeView(llMainContentHolder)
			llMainContentHolder = null
			mButtonLayout = null
		}

		mCurrentRecurrenceOption = mOptions!!.recurrenceOption
		mRecurrenceRule = mOptions!!.recurrenceRule

		if (mRecurrencePickerEnabled) {
			val cal = if (mDatePickerEnabled)
				mDatePicker!!.selectedDate.startDate
			else
				SUtils.getCalendarForLocale(null, Locale.getDefault())

			mSublimeRecurrencePicker!!.initializeData(mRepeatOptionSetListener,
					mCurrentRecurrenceOption, mRecurrenceRule,
					cal.timeInMillis)
		} else {
			removeView(mSublimeRecurrencePicker)
			mSublimeRecurrencePicker = null
		}

		mCurrentPicker = mOptions!!.pickerToShow
		// Updated from 'updateDisplay()' when 'RecurrencePicker' is chosen
		mHiddenPicker = SublimeOptions.Picker.INVALID
	}

	private fun reassessValidity() {
		mButtonLayout!!.updateValidity(mDatePickerValid && mTimePickerValid)
	}

	override fun onDateChanged(view: SublimeDatePicker, selectedDate: SelectedDate) {
		// TODO: Consider removing this propagation of date change event altogether
		//mDatePicker.init(selectedDate.getStartDate().get(Calendar.YEAR),
		//selectedDate.getStartDate().get(Calendar.MONTH),
		//selectedDate.getStartDate().get(Calendar.DAY_OF_MONTH),
		//mOptions.canPickDateRange(), this);
		mDatePicker!!.init(selectedDate, mOptions!!.canPickDateRange(), this)
	}

	override fun onDatePickerValidationChanged(valid: Boolean) {
		mDatePickerValid = valid
		reassessValidity()
	}

	override fun onTimePickerValidationChanged(valid: Boolean) {
		mTimePickerValid = valid
		reassessValidity()
	}

	companion object {
		private val TAG = SublimePicker::class.java.getSimpleName()

		// Used for formatting date range
		private const val MONTH_IN_MILLIS = DateUtils.YEAR_IN_MILLIS / 12

		private fun createThemeWrapper(context: Context): ContextThemeWrapper {
			val forParent = context.obtainStyledAttributes(
					intArrayOf(R.attr.sublimePickerStyle))
			val parentStyle = forParent.getResourceId(0, R.style.SublimePickerStyleLight)
			forParent.recycle()

			return ContextThemeWrapper(context, parentStyle)
		}
	}
}

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

package com.appeaser.sublimepickerlibrary.helpers

import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils

import com.appeaser.sublimepickerlibrary.datepicker.SelectedDate
import com.appeaser.sublimepickerlibrary.recurrencepicker.SublimeRecurrencePicker
import com.appeaser.sublimepickerlibrary.utilities.SUtils

import java.util.Calendar
import java.util.Locale

/**
 * Options to initialize 'SublimePicker'
 */
class SublimeOptions : Parcelable {

	private var mDisplayOptions =
			ACTIVATE_DATE_PICKER or ACTIVATE_TIME_PICKER or ACTIVATE_RECURRENCE_PICKER

	// Date & Time params
	private var mStartYear = -1
	private var mStartMonth = -1
	private var mStartDayOfMonth = -1
	private var mEndYear = -1
	private var mEndMonth = -1
	private var mEndDayOfMonth = -1
	private var mHourOfDay = -1
	private var mMinute = -1
	//private int mYear = -1, mMonthOfYear = -1, mDayOfMonth = -1, mHourOfDay = -1, mMinute = -1;
	private var mMinDate = java.lang.Long.MIN_VALUE
	private var mMaxDate = java.lang.Long.MIN_VALUE
	private var mAnimateLayoutChanges: Boolean = false
	var is24HourView: Boolean = false
		private set

	private var mRecurrenceOption: SublimeRecurrencePicker.RecurrenceOption? = SublimeRecurrencePicker.RecurrenceOption.DOES_NOT_REPEAT
	private var mRecurrenceRule: String? = ""

	// Allow date range selection
	private var mCanPickDateRange: Boolean = false

	// Defaults
	private var mPickerToShow: Picker? = Picker.DATE_PICKER

	val recurrenceRule: String
		get() = if (mRecurrenceRule == null)
			""
		else
			mRecurrenceRule

	val recurrenceOption: SublimeRecurrencePicker.RecurrenceOption
		get() = if (mRecurrenceOption == null)
			SublimeRecurrencePicker.RecurrenceOption.DOES_NOT_REPEAT
		else
			mRecurrenceOption

	val isDatePickerActive: Boolean
		get() = mDisplayOptions and ACTIVATE_DATE_PICKER == ACTIVATE_DATE_PICKER

	val isTimePickerActive: Boolean
		get() = mDisplayOptions and ACTIVATE_TIME_PICKER == ACTIVATE_TIME_PICKER

	val isRecurrencePickerActive: Boolean
		get() = mDisplayOptions and ACTIVATE_RECURRENCE_PICKER == ACTIVATE_RECURRENCE_PICKER

	/*public int[] getDateParams() {
        if (mYear == -1 || mMonthOfYear == -1 || mDayOfMonth == -1) {
            Calendar cal = SUtils.getCalendarForLocale(null, Locale.getDefault());
            mYear = cal.get(Calendar.YEAR);
            mMonthOfYear = cal.get(Calendar.MONTH);
            mDayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        }

        return new int[]{mYear, mMonthOfYear, mDayOfMonth};
    }*/

	val dateParams: SelectedDate
		get() {
			val startCal = SUtils.getCalendarForLocale(null, Locale.getDefault())
			if (mStartYear == -1 || mStartMonth == -1 || mStartDayOfMonth == -1) {
				mStartYear = startCal.get(Calendar.YEAR)
				mStartMonth = startCal.get(Calendar.MONTH)
				mStartDayOfMonth = startCal.get(Calendar.DAY_OF_MONTH)
			} else {
				startCal.set(mStartYear, mStartMonth, mStartDayOfMonth)
			}

			val endCal = SUtils.getCalendarForLocale(null, Locale.getDefault())
			if (mEndYear == -1 || mEndMonth == -1 || mEndDayOfMonth == -1) {
				mEndYear = endCal.get(Calendar.YEAR)
				mEndMonth = endCal.get(Calendar.MONTH)
				mEndDayOfMonth = endCal.get(Calendar.DAY_OF_MONTH)
			} else {
				endCal.set(mEndYear, mEndMonth, mEndDayOfMonth)
			}

			return SelectedDate(startCal, endCal)
		}

	val dateRange: LongArray
		get() = longArrayOf(mMinDate, mMaxDate)

	val timeParams: IntArray
		get() {
			if (mHourOfDay == -1 || mMinute == -1) {
				val cal = SUtils.getCalendarForLocale(null, Locale.getDefault())
				mHourOfDay = cal.get(Calendar.HOUR_OF_DAY)
				mMinute = cal.get(Calendar.MINUTE)
			}

			return intArrayOf(mHourOfDay, mMinute)
		}

	enum class Picker {
		DATE_PICKER, TIME_PICKER, REPEAT_OPTION_PICKER, INVALID
	}

	constructor() {
		// Nothing
	}

	private constructor(`in`: Parcel) {
		readFromParcel(`in`)
	}

	// Use 'LayoutTransition'
	fun setAnimateLayoutChanges(animateLayoutChanges: Boolean): SublimeOptions {
		mAnimateLayoutChanges = animateLayoutChanges
		return this
	}

	fun animateLayoutChanges(): Boolean {
		return mAnimateLayoutChanges
	}

	// Set the Picker that will be shown
	// when 'SublimePicker' is displayed
	fun setPickerToShow(picker: Picker): SublimeOptions {
		mPickerToShow = picker
		return this
	}

	private fun isPickerActive(picker: Picker): Boolean {
		when (picker) {
			SublimeOptions.Picker.DATE_PICKER -> return isDatePickerActive
			SublimeOptions.Picker.TIME_PICKER -> return isTimePickerActive
			SublimeOptions.Picker.REPEAT_OPTION_PICKER -> return isRecurrencePickerActive
		}

		return false
	}

	fun getPickerToShow(): Picker? {
		return mPickerToShow
	}

	// Activate pickers
	fun setDisplayOptions(displayOptions: Int): SublimeOptions {
		if (!areValidDisplayOptions(displayOptions)) {
			throw IllegalArgumentException("Invalid display options.")
		}

		mDisplayOptions = displayOptions
		return this
	}

	private fun areValidDisplayOptions(displayOptions: Int): Boolean {
		val flags = ACTIVATE_DATE_PICKER or ACTIVATE_TIME_PICKER or ACTIVATE_RECURRENCE_PICKER
		return displayOptions and flags.inv() == 0
	}

	// Provide initial date parameters
	fun setDateParams(year: Int, month: Int, dayOfMonth: Int): SublimeOptions {
		return setDateParams(year, month, dayOfMonth, year, month, dayOfMonth)
	}

	// Provide initial date parameters
	fun setDateParams(startYear: Int, startMonth: Int, startDayOfMonth: Int,
	                  endYear: Int, endMonth: Int, endDayOfMonth: Int): SublimeOptions {
		mStartYear = startYear
		mStartMonth = startMonth
		mStartDayOfMonth = startDayOfMonth

		mEndYear = endYear
		mEndMonth = endMonth
		mEndDayOfMonth = endDayOfMonth

		return this
	}

	// Provide initial date parameters
	fun setDateParams(calendar: Calendar): SublimeOptions {
		return setDateParams(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
				calendar.get(Calendar.DAY_OF_MONTH),
				calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
				calendar.get(Calendar.DAY_OF_MONTH))
	}

	// Provide initial date parameters
	fun setDateParams(startCal: Calendar, endCal: Calendar): SublimeOptions {
		return setDateParams(startCal.get(Calendar.YEAR), startCal.get(Calendar.MONTH),
				startCal.get(Calendar.DAY_OF_MONTH),
				endCal.get(Calendar.YEAR), endCal.get(Calendar.MONTH),
				endCal.get(Calendar.DAY_OF_MONTH))
	}

	// Provide initial date parameters
	fun setDateParams(selectedDate: SelectedDate): SublimeOptions {
		return setDateParams(selectedDate.startDate.get(Calendar.YEAR),
				selectedDate.startDate.get(Calendar.MONTH),
				selectedDate.startDate.get(Calendar.DAY_OF_MONTH),
				selectedDate.endDate.get(Calendar.YEAR),
				selectedDate.endDate.get(Calendar.MONTH),
				selectedDate.endDate.get(Calendar.DAY_OF_MONTH))
	}

	// Set date range
	// Pass '-1L' for 'minDate'/'maxDate' for default
	fun setDateRange(minDate: Long, maxDate: Long): SublimeOptions {
		mMinDate = minDate
		mMaxDate = maxDate
		return this
	}

	// Provide initial time parameters
	fun setTimeParams(hourOfDay: Int, minute: Int, is24HourView: Boolean): SublimeOptions {
		mHourOfDay = hourOfDay
		mMinute = minute
		this.is24HourView = is24HourView
		return this
	}

	// Provide initial Recurrence-rule
	fun setRecurrenceParams(recurrenceOption: SublimeRecurrencePicker.RecurrenceOption?, recurrenceRule: String?): SublimeOptions {
		var recurrenceOption = recurrenceOption
		var recurrenceRule = recurrenceRule

		// If passed recurrence option is null, take it as the does_not_repeat option.
		// If passed recurrence option is custom, but the passed recurrence rule is null/empty,
		// take it as the does_not_repeat option.
		// If passed recurrence option is not custom, nullify the recurrence rule.
		if (recurrenceOption == null || recurrenceOption == SublimeRecurrencePicker.RecurrenceOption.CUSTOM && TextUtils.isEmpty(recurrenceRule)) {
			recurrenceOption = SublimeRecurrencePicker.RecurrenceOption.DOES_NOT_REPEAT
			recurrenceRule = null
		} else if (recurrenceOption != SublimeRecurrencePicker.RecurrenceOption.CUSTOM) {
			recurrenceRule = null
		}

		mRecurrenceOption = recurrenceOption
		mRecurrenceRule = recurrenceRule
		return this
	}

	// Verifies if the supplied options are valid
	fun verifyValidity() {
		if (mPickerToShow == null || mPickerToShow == Picker.INVALID) {
			throw InvalidOptionsException("The picker set using setPickerToShow(Picker) " + "cannot be null or Picker.INVALID.")
		}

		if (!isPickerActive(mPickerToShow!!)) {
			throw InvalidOptionsException("The picker you have " +
					"requested to show(" + mPickerToShow!!.name + ") is not activated. " +
					"Use setDisplayOptions(int) " +
					"to activate it, or use an activated Picker with setPickerToShow(Picker).")
		}

		// TODO: Validation? mMinDate < mMaxDate
	}

	fun setCanPickDateRange(canPickDateRange: Boolean): SublimeOptions {
		mCanPickDateRange = canPickDateRange
		return this
	}

	fun canPickDateRange(): Boolean {
		return mCanPickDateRange
	}

	override fun describeContents(): Int {
		return 0
	}

	private fun readFromParcel(`in`: Parcel) {
		mAnimateLayoutChanges = `in`.readByte().toInt() != 0
		mPickerToShow = Picker.valueOf(`in`.readString())
		mDisplayOptions = `in`.readInt()
		mStartYear = `in`.readInt()
		mStartMonth = `in`.readInt()
		mStartDayOfMonth = `in`.readInt()
		mEndYear = `in`.readInt()
		mEndMonth = `in`.readInt()
		mEndDayOfMonth = `in`.readInt()
		mHourOfDay = `in`.readInt()
		mMinute = `in`.readInt()
		is24HourView = `in`.readByte().toInt() != 0
		mRecurrenceRule = `in`.readString()
		mCanPickDateRange = `in`.readByte().toInt() != 0
	}

	override fun writeToParcel(dest: Parcel, flags: Int) {
		dest.writeByte((if (mAnimateLayoutChanges) 1 else 0).toByte())
		dest.writeString(mPickerToShow!!.name)
		dest.writeInt(mDisplayOptions)
		dest.writeInt(mStartYear)
		dest.writeInt(mStartMonth)
		dest.writeInt(mStartDayOfMonth)
		dest.writeInt(mEndYear)
		dest.writeInt(mEndMonth)
		dest.writeInt(mEndDayOfMonth)
		dest.writeInt(mHourOfDay)
		dest.writeInt(mMinute)
		dest.writeByte((if (is24HourView) 1 else 0).toByte())
		dest.writeString(mRecurrenceRule)
		dest.writeByte((if (mCanPickDateRange) 1 else 0).toByte())
	}

	// Thrown if supplied 'SublimeOptions' are not valid
	inner class InvalidOptionsException(detailMessage: String) : RuntimeException(detailMessage)

	companion object {

		// make DatePicker available
		val ACTIVATE_DATE_PICKER = 0x01

		// make TimePicker available
		val ACTIVATE_TIME_PICKER = 0x02

		// make RecurrencePicker available
		val ACTIVATE_RECURRENCE_PICKER = 0x04

		val CREATOR: Parcelable.Creator<SublimeOptions> = object : Parcelable.Creator<SublimeOptions> {
			override fun createFromParcel(`in`: Parcel): SublimeOptions {
				return SublimeOptions(`in`)
			}

			override fun newArray(size: Int): Array<SublimeOptions> {
				return arrayOfNulls(size)
			}
		}
	}
}

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

import android.annotation.TargetApi
import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.support.v4.content.ContextCompat
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.text.format.Time
import android.util.AttributeSet
import android.util.Log
import android.util.TimeFormatException
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.DatePicker
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TextView
import android.widget.Toast

import com.appeaser.sublimepickerlibrary.R
import com.appeaser.sublimepickerlibrary.common.DecisionButtonLayout
import com.appeaser.sublimepickerlibrary.datepicker.RecurrenceEndDatePicker
import com.appeaser.sublimepickerlibrary.drawables.CheckableDrawable
import com.appeaser.sublimepickerlibrary.utilities.RecurrenceUtils
import com.appeaser.sublimepickerlibrary.utilities.SUtils

import java.text.DateFormat
import java.text.DateFormatSymbols
import java.util.ArrayList
import java.util.Arrays
import java.util.Calendar
import java.util.Locale

/**
 * Helps create a custom recurrence-rule.
 */
class RecurrenceOptionCreator : FrameLayout, AdapterView.OnItemSelectedListener, RadioGroup.OnCheckedChangeListener, CompoundButton.OnCheckedChangeListener, View.OnClickListener, RecurrenceEndDatePicker.OnDateSetListener {

	// Stripped down version of 'SublimeMaterialDatePicker'
	//private DatePickerView mDateOnlyPicker;
	private var mDateOnlyPicker: RecurrenceEndDatePicker? = null
	private var mRecurrencePicker: View? = null

	// OK/Cancel buttons
	private var mButtonLayout: DecisionButtonLayout? = null

	// Uses either to DateFormat.SHORT or DateFormat.MEDIUM
	// to format the supplied end date. The option can only be
	// set in RecurrenceOptionCreator's style-definition
	private var mEndDateFormatter: DateFormat? = null

	private var mResources: Resources? = null
	private val mRecurrence = EventRecurrence()
	private val mTime = Time() // TODO timezone?
	private var mModel = RecurrenceModel()
	private var mToast: Toast? = null

	private val TIME_DAY_TO_CALENDAR_DAY = intArrayOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)

	// Call mStringBuilder.setLength(0) before formatting any string or else the
	// formatted text will accumulate.
	// private final StringBuilder mStringBuilder = new StringBuilder();
	// private Formatter mFormatter = new Formatter(mStringBuilder);

	private var mFreqSpinner: Spinner? = null

	private var mInterval: EditText? = null
	private var mIntervalPreText: TextView? = null
	private var mIntervalPostText: TextView? = null

	private var mIntervalResId = -1

	private var mEndSpinner: Spinner? = null
	private var mEndDateTextView: TextView? = null
	private var mEndCount: EditText? = null
	private var mPostEndCount: TextView? = null
	private var mHidePostEndCount: Boolean = false

	private val mEndSpinnerArray = ArrayList<CharSequence>(3)
	private var mEndSpinnerAdapter: EndSpinnerAdapter? = null
	private var mEndNeverStr: String? = null
	private var mEndDateLabel: String? = null
	private var mEndCountLabel: String? = null

	/**
	 * Hold toggle buttons in the order per user's first day of week preference
	 */
	private var mWeekGroup: LinearLayout? = null
	private var mWeekGroup2: LinearLayout? = null
	// Sun = 0
	private val mWeekByDayButtons = arrayOfNulls<WeekButton>(7)
	/**
	 * A double array of Strings to hold the 7x5 list of possible strings of the form:
	 * "on every [Nth] [DAY_OF_WEEK]", e.g. "on every second Monday",
	 * where [Nth] can be [first, second, third, fourth, last]
	 */
	private var mMonthRepeatByDayOfWeekStrs: Array<Array<String>>? = null

	private var mMonthRepeatByRadioGroup: RadioGroup? = null
	private var mRepeatMonthlyByNthDayOfWeek: RadioButton? = null
	private var mRepeatMonthlyByNthDayOfMonth: RadioButton? = null
	private var mMonthRepeatByDayOfWeekStr: String? = null

	private var mRecurrenceSetListener: OnRecurrenceSetListener? = null
	internal var mHeaderBackgroundColor: Int = 0

	private val mButtonLayoutCallback = object : DecisionButtonLayout.Callback {
		override fun onOkay() {
			val rrule: String?
			if (mModel.recurrenceState == RecurrenceModel.STATE_NO_RECURRENCE) {
				rrule = null
			} else {
				copyModelToEventRecurrence(mModel, mRecurrence)
				rrule = mRecurrence.toString()
			}

			mRecurrenceSetListener!!.onRecurrenceSet(rrule)
		}

		override fun onCancel() {
			mRecurrenceSetListener!!.onCancelled()
		}
	}

	// Used to keep track of currently visible view - the view that
	// will be restored on screen rotation.
	private enum class CurrentView {
		RECURRENCE_PICKER, DATE_ONLY_PICKER
	}

	// { WIP: Provide a synonym for chosen custom option. }
	//
	// Eg: if 'freq' is 'WEEKLY' and all seven days of the week
	// are selected, the chosen option is equivalent to 'Repeats Daily'
	// Actual Recurrence Rule string is 'Repeats Weekly every
	// SUN, MON, TUE, WED, THU, FRI, SAT'. More options are possible.
	//
	// Another possible extension is - if 'freq' is 'YEARLY' and
	// 'interval' is set to 1, the custom option 'EVERY YEAR' is
	// already present in the 'SublimeRecurrencePicker' menu. Use
	// that instead of showing 'REPEATS YEARLY' at the top.
	internal fun resolveRepeatOption(): SublimeRecurrencePicker.RecurrenceOption {
		if (mModel.freq == RecurrenceModel.FREQ_DAILY) {
			if (mModel.interval == INTERVAL_DEFAULT && mModel.end == RecurrenceModel.END_NEVER) {
				return SublimeRecurrencePicker.RecurrenceOption.DAILY
			}
		} /*else if (mModel.freq == RecurrenceModel.FREQ_WEEKLY) {

        }*/

		return SublimeRecurrencePicker.RecurrenceOption.CUSTOM
	}

	private inner class RecurrenceModel : Parcelable {

		internal var recurrenceState: Int = 0

		/**
		 * FREQ: Repeat pattern
		 */
		internal var freq = FREQ_WEEKLY

		/**
		 * INTERVAL: Every n days/weeks/months/years. n >= 1
		 */
		internal var interval = INTERVAL_DEFAULT

		/**
		 * UNTIL and COUNT: How does the the event end?
		 */
		internal var end: Int = 0

		/**
		 * UNTIL: Date of the last recurrence. Used when until == END_BY_DATE
		 */
		internal var endDate: Time? = null

		/**
		 * COUNT: Times to repeat. Use when until == END_BY_COUNT
		 */
		internal var endCount = COUNT_DEFAULT

		/**
		 * BYDAY: Days of the week to be repeated. Sun = 0, Mon = 1, etc
		 */
		internal var weeklyByDayOfWeek = BooleanArray(7)

		/**
		 * BYDAY AND BYMONTHDAY: How to repeat monthly events? Same date of the
		 * month or Same nth day of week.
		 */
		internal var monthlyRepeat: Int = 0

		/**
		 * Day of the month to repeat. Used when monthlyRepeat ==
		 * MONTHLY_BY_DATE
		 */
		internal var monthlyByMonthDay: Int = 0

		/**
		 * Day of the week to repeat. Used when monthlyRepeat ==
		 * MONTHLY_BY_NTH_DAY_OF_WEEK
		 */
		internal var monthlyByDayOfWeek: Int = 0

		/**
		 * Nth day of the week to repeat. Used when monthlyRepeat ==
		 * MONTHLY_BY_NTH_DAY_OF_WEEK 0=undefined, -1=Last, 1=1st, 2=2nd, ..., 5=5th
		 *
		 *
		 * We support 5th, just to handle backwards capabilities with old bug, but it
		 * gets converted to -1 once edited.
		 */
		internal var monthlyByNthDayOfWeek: Int = 0

		// suppress unused and hiding
		val CREATOR: Parcelable.Creator<RecurrenceModel> = object : Parcelable.Creator<RecurrenceModel> {

			override fun createFromParcel(`in`: Parcel): RecurrenceModel {
				return RecurrenceModel(`in`)
			}

			override fun newArray(size: Int): Array<RecurrenceModel> {
				return arrayOfNulls(size)
			}
		}

		/*
         * (generated method)
         */
		override fun toString(): String {
			return ("Model [freq=" + freq + ", interval=" + interval + ", end=" + end + ", endDate="
					+ endDate + ", endCount=" + endCount + ", weeklyByDayOfWeek="
					+ Arrays.toString(weeklyByDayOfWeek) + ", monthlyRepeat=" + monthlyRepeat
					+ ", monthlyByMonthDay=" + monthlyByMonthDay + ", monthlyByDayOfWeek="
					+ monthlyByDayOfWeek + ", monthlyByNthDayOfWeek=" + monthlyByNthDayOfWeek + "]")
		}

		override fun describeContents(): Int {
			return 0
		}

		constructor() {}

		constructor(`in`: Parcel) {
			readFromParcel(`in`)
		}

		override fun writeToParcel(dest: Parcel, flags: Int) {
			dest.writeInt(freq)
			dest.writeInt(interval)
			dest.writeInt(end)
			dest.writeInt(endDate!!.year)
			dest.writeInt(endDate!!.month)
			dest.writeInt(endDate!!.monthDay)
			dest.writeInt(endCount)
			dest.writeBooleanArray(weeklyByDayOfWeek)
			dest.writeInt(monthlyRepeat)
			dest.writeInt(monthlyByMonthDay)
			dest.writeInt(monthlyByDayOfWeek)
			dest.writeInt(monthlyByNthDayOfWeek)
			dest.writeInt(recurrenceState)
		}

		private fun readFromParcel(`in`: Parcel) {
			freq = `in`.readInt()
			interval = `in`.readInt()
			end = `in`.readInt()
			endDate = Time()
			endDate!!.year = `in`.readInt()
			endDate!!.month = `in`.readInt()
			endDate!!.monthDay = `in`.readInt()
			endCount = `in`.readInt()
			`in`.readBooleanArray(weeklyByDayOfWeek)
			monthlyRepeat = `in`.readInt()
			monthlyByMonthDay = `in`.readInt()
			monthlyByDayOfWeek = `in`.readInt()
			monthlyByNthDayOfWeek = `in`.readInt()
			recurrenceState = `in`.readInt()
		}

		companion object {

			// Should match EventRecurrence.DAILY, etc
			internal val FREQ_DAILY = 0
			internal val FREQ_WEEKLY = 1
			internal val FREQ_MONTHLY = 2
			internal val FREQ_YEARLY = 3

			internal val END_NEVER = 0
			internal val END_BY_DATE = 1
			internal val END_BY_COUNT = 2

			internal val MONTHLY_BY_DATE = 0
			internal val MONTHLY_BY_NTH_DAY_OF_WEEK = 1

			internal val STATE_NO_RECURRENCE = 0
			internal val STATE_RECURRENCE = 1
		}
	}

	internal open inner class minMaxTextWatcher(private val mMin: Int, private val mDefault: Int, private val mMax: Int) : TextWatcher {

		override fun afterTextChanged(s: Editable) {

			var updated = false
			var value: Int
			try {
				value = Integer.parseInt(s.toString())
			} catch (e: NumberFormatException) {
				value = mDefault
			}

			if (value < mMin) {
				value = mMin
				updated = true
			} else if (value > mMax) {
				updated = true
				value = mMax
			}

			// Update UI
			if (updated) {
				s.clear()
				s.append(Integer.toString(value))
			}

			updateDoneButtonState()
			onChange(value)
		}

		/**
		 * Override to be called after each key stroke
		 */
		internal open fun onChange(value: Int) {}

		override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

		override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
	}

	@JvmOverloads
	constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.spRecurrenceOptionCreatorStyle) : super(SUtils.createThemeWrapper(context, R.attr.sublimePickerStyle,
			R.style.SublimePickerStyleLight, R.attr.spRecurrenceOptionCreatorStyle,
			R.style.RecurrenceOptionCreatorStyle), attrs, defStyleAttr) {
		initializeLayout()
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(SUtils.createThemeWrapper(context, R.attr.sublimePickerStyle,
			R.style.SublimePickerStyleLight, R.attr.spRecurrenceOptionCreatorStyle,
			R.style.RecurrenceOptionCreatorStyle), attrs, defStyleAttr, defStyleRes) {
		initializeLayout()
	}

	// Initialize UI
	internal fun initializeLayout() {
		val weekButtonUnselectedTextColor: Int
		val weekButtonSelectedTextColor: Int
		val weekButtonSelectedCircleColor: Int

		val a = context
				.obtainStyledAttributes(R.styleable.RecurrenceOptionCreator)
		try {
			mHeaderBackgroundColor = a.getColor(R.styleable.RecurrenceOptionCreator_spHeaderBackground, 0)

			val endDateFormat = a.getInt(R.styleable.RecurrenceOptionCreator_spEndDateFormat, 1)

			mEndDateFormatter = DateFormat.getDateInstance(
					if (endDateFormat == 0)
						DateFormat.SHORT
					else
						DateFormat.MEDIUM,
					Locale.getDefault())

			weekButtonUnselectedTextColor = a.getColor(R.styleable.RecurrenceOptionCreator_spWeekButtonUnselectedTextColor,
					SUtils.COLOR_ACCENT)
			weekButtonSelectedTextColor = a.getColor(R.styleable.RecurrenceOptionCreator_spWeekButtonSelectedTextColor,
					SUtils.COLOR_TEXT_PRIMARY_INVERSE)
			weekButtonSelectedCircleColor = a.getColor(R.styleable.RecurrenceOptionCreator_spWeekButtonSelectedCircleColor,
					SUtils.COLOR_ACCENT)
		} finally {
			a.recycle()
		}

		mResources = resources

		LayoutInflater.from(context).inflate(R.layout.recurrence_picker, this)

		mRecurrencePicker = findViewById(R.id.recurrence_picker)

		mDateOnlyPicker = findViewById<View>(R.id.date_only_picker) as RecurrenceEndDatePicker
		mDateOnlyPicker!!.visibility = View.GONE

		// OK/Cancel buttons
		mButtonLayout = findViewById<View>(R.id.roc_decision_button_layout) as DecisionButtonLayout
		mButtonLayout!!.applyOptions(mButtonLayoutCallback)

		SUtils.setViewBackground(findViewById(R.id.freqSpinnerHolder), mHeaderBackgroundColor,
				SUtils.CORNER_TOP_LEFT or SUtils.CORNER_TOP_RIGHT)

		/** EFrequency Spinner {Repeat daily, Repeat weekly, Repeat monthly, Repeat yearly}  */

		mFreqSpinner = findViewById<View>(R.id.freqSpinner) as Spinner
		mFreqSpinner!!.onItemSelectedListener = this

		val freqAdapter = ArrayAdapter.createFromResource(context,
				R.array.recurrence_freq, R.layout.roc_freq_spinner_item)
		freqAdapter.setDropDownViewResource(R.layout.roc_spinner_dropdown_item)
		mFreqSpinner!!.adapter = freqAdapter

		val freqSpinnerBg = ContextCompat.getDrawable(context, R.drawable.abc_spinner_mtrl_am_alpha)
		val cfFreqSpinner = PorterDuffColorFilter(SUtils.COLOR_TEXT_PRIMARY_INVERSE,
				PorterDuff.Mode.SRC_IN)
		if (freqSpinnerBg != null) {
			freqSpinnerBg.colorFilter = cfFreqSpinner
			SUtils.setViewBackground(mFreqSpinner!!, freqSpinnerBg)
		}

		mInterval = findViewById<View>(R.id.interval) as EditText
		mInterval!!.addTextChangedListener(object : minMaxTextWatcher(1, INTERVAL_DEFAULT, INTERVAL_MAX) {
			override fun onChange(v: Int) {
				if (mIntervalResId != -1 && mInterval!!.text.toString().length > 0) {
					mModel.interval = v
					updateIntervalText()
					mInterval!!.requestLayout()
				}
			}
		})
		mIntervalPreText = findViewById<View>(R.id.intervalPreText) as TextView
		mIntervalPostText = findViewById<View>(R.id.intervalPostText) as TextView

		/** End Spinner {Forever, Until a date, For a number of events}  */

		mEndNeverStr = mResources!!.getString(R.string.recurrence_end_continously)
		mEndDateLabel = mResources!!.getString(R.string.recurrence_end_date_label)
		mEndCountLabel = mResources!!.getString(R.string.recurrence_end_count_label)

		mEndSpinnerArray.add(mEndNeverStr)
		mEndSpinnerArray.add(mEndDateLabel)
		mEndSpinnerArray.add(mEndCountLabel)
		mEndSpinner = findViewById<View>(R.id.endSpinner) as Spinner
		mEndSpinner!!.onItemSelectedListener = this

		mEndSpinnerAdapter = EndSpinnerAdapter(context, mEndSpinnerArray,
				R.layout.roc_end_spinner_item, R.id.spinner_item, R.layout.roc_spinner_dropdown_item)
		mEndSpinner!!.adapter = mEndSpinnerAdapter

		mEndCount = findViewById<View>(R.id.endCount) as EditText
		mEndCount!!.addTextChangedListener(object : minMaxTextWatcher(1, COUNT_DEFAULT, COUNT_MAX) {
			override fun onChange(v: Int) {
				if (mModel.endCount != v) {
					mModel.endCount = v
					updateEndCountText()
					mEndCount!!.requestLayout()
				}
			}
		})
		mPostEndCount = findViewById<View>(R.id.postEndCount) as TextView

		mEndDateTextView = findViewById<View>(R.id.endDate) as TextView
		mEndDateTextView!!.setOnClickListener(this)

		SUtils.setViewBackground(mEndDateTextView!!,
				SUtils.createButtonBg(context, SUtils.COLOR_BUTTON_NORMAL,
						SUtils.COLOR_CONTROL_HIGHLIGHT))

		// set default & checked state colors
		WeekButton.setStateColors(weekButtonUnselectedTextColor, weekButtonSelectedTextColor)

		// AOSP code handled this differently. It has been refactored to
		// let Android decide if we have enough space to show
		// all seven 'WeekButtons' inline. In this case, 'mWeekGroup2'
		// will be null (see @layout-w460dp/week_buttons).
		mWeekGroup = findViewById<View>(R.id.weekGroup) as LinearLayout
		mWeekGroup2 = findViewById<View>(R.id.weekGroup2) as LinearLayout

		// Only non-null when available width is < 460dp
		// Used only for positioning 'WeekButtons' in two rows
		// of 4 & 3.
		val eighthWeekDay = findViewById<View>(R.id.week_day_8)
		if (eighthWeekDay != null)
			eighthWeekDay.visibility = View.INVISIBLE

		// In Calendar.java day of week order e.g Sun = 1 ... Sat = 7
		//String[] dayOfWeekString = new DateFormatSymbols().getWeekdays();

		mMonthRepeatByDayOfWeekStrs = arrayOfNulls(7)
		// from Time.SUNDAY as 0 through Time.SATURDAY as 6
		mMonthRepeatByDayOfWeekStrs[0] = mResources!!.getStringArray(R.array.repeat_by_nth_sun)
		mMonthRepeatByDayOfWeekStrs[1] = mResources!!.getStringArray(R.array.repeat_by_nth_mon)
		mMonthRepeatByDayOfWeekStrs[2] = mResources!!.getStringArray(R.array.repeat_by_nth_tues)
		mMonthRepeatByDayOfWeekStrs[3] = mResources!!.getStringArray(R.array.repeat_by_nth_wed)
		mMonthRepeatByDayOfWeekStrs[4] = mResources!!.getStringArray(R.array.repeat_by_nth_thurs)
		mMonthRepeatByDayOfWeekStrs[5] = mResources!!.getStringArray(R.array.repeat_by_nth_fri)
		mMonthRepeatByDayOfWeekStrs[6] = mResources!!.getStringArray(R.array.repeat_by_nth_sat)

		// In Time.java day of week order e.g. Sun = 0
		var idx = RecurrenceUtils.firstDayOfWeek

		// In Calendar.java day of week order e.g Sun = 1 ... Sat = 7
		val dayOfWeekString = DateFormatSymbols().shortWeekdays

		// CheckableDrawable's width & height
		val expandedWidthHeight = mResources!!
				.getDimensionPixelSize(R.dimen.week_button_state_on_circle_size)

		val tempWeekButtons = arrayOfNulls<WeekButton>(7)
		tempWeekButtons[0] = findViewById<View>(R.id.week_day_1) as WeekButton
		tempWeekButtons[1] = findViewById<View>(R.id.week_day_2) as WeekButton
		tempWeekButtons[2] = findViewById<View>(R.id.week_day_3) as WeekButton
		tempWeekButtons[3] = findViewById<View>(R.id.week_day_4) as WeekButton
		tempWeekButtons[4] = findViewById<View>(R.id.week_day_5) as WeekButton
		tempWeekButtons[5] = findViewById<View>(R.id.week_day_6) as WeekButton
		tempWeekButtons[6] = findViewById<View>(R.id.week_day_7) as WeekButton

		for (i in mWeekByDayButtons.indices) {
			mWeekByDayButtons[idx] = tempWeekButtons[i]
			SUtils.setViewBackground(mWeekByDayButtons[idx],
					CheckableDrawable(weekButtonSelectedCircleColor,
							false, expandedWidthHeight))
			mWeekByDayButtons[idx].setTextColor(weekButtonUnselectedTextColor)
			mWeekByDayButtons[idx].setTextOff(dayOfWeekString[TIME_DAY_TO_CALENDAR_DAY[idx]])
			mWeekByDayButtons[idx].setTextOn(dayOfWeekString[TIME_DAY_TO_CALENDAR_DAY[idx]])
			mWeekByDayButtons[idx].setOnCheckedChangeListener(this)

			if (++idx >= 7) {
				idx = 0
			}
		}

		mMonthRepeatByRadioGroup = findViewById<View>(R.id.monthGroup) as RadioGroup
		mMonthRepeatByRadioGroup!!.setOnCheckedChangeListener(this)
		mRepeatMonthlyByNthDayOfWeek = findViewById<View>(R.id.repeatMonthlyByNthDayOfTheWeek) as RadioButton
		mRepeatMonthlyByNthDayOfMonth = findViewById<View>(R.id.repeatMonthlyByNthDayOfMonth) as RadioButton
	}

	fun initializeData(currentlyChosenTime: Long,
	                   timeZone: String, recurrenceRule: String,
	                   callback: OnRecurrenceSetListener) {
		mRecurrence.wkst = EventRecurrence.timeDay2Day(RecurrenceUtils.firstDayOfWeek)
		mRecurrenceSetListener = callback

		mTime.set(currentlyChosenTime)

		if (!TextUtils.isEmpty(timeZone)) {
			mTime.timezone = timeZone
		}
		mTime.normalize(false)

		// Time days of week: Sun=0, Mon=1, etc
		mModel.weeklyByDayOfWeek[mTime.weekDay] = true

		if (!TextUtils.isEmpty(recurrenceRule)) {
			mModel.recurrenceState = RecurrenceModel.STATE_RECURRENCE
			mRecurrence.parse(recurrenceRule)
			copyEventRecurrenceToModel(mRecurrence, mModel)
			// Leave today's day of week as checked by default in weekly view.
			if (mRecurrence.bydayCount == 0) {
				mModel.weeklyByDayOfWeek[mTime.weekDay] = true
			}
		} else {
			// Default
			mModel.recurrenceState = RecurrenceModel.STATE_RECURRENCE
		}

		if (mModel.endDate == null) {
			mModel.endDate = Time(mTime)
			when (mModel.freq) {
				RecurrenceModel.FREQ_DAILY, RecurrenceModel.FREQ_WEEKLY -> mModel.endDate!!.month += 1
				RecurrenceModel.FREQ_MONTHLY -> mModel.endDate!!.month += 3
				RecurrenceModel.FREQ_YEARLY -> mModel.endDate!!.year += 3
			}
			mModel.endDate!!.normalize(false)
		}

		togglePickerOptions()
		updateDialog()
		showRecurrencePicker()
	}

	private fun togglePickerOptions() {
		if (mModel.recurrenceState == RecurrenceModel.STATE_NO_RECURRENCE) {
			mFreqSpinner!!.isEnabled = false
			mEndSpinner!!.isEnabled = false
			mIntervalPreText!!.isEnabled = false
			mInterval!!.isEnabled = false
			mIntervalPostText!!.isEnabled = false
			mMonthRepeatByRadioGroup!!.isEnabled = false
			mEndCount!!.isEnabled = false
			mPostEndCount!!.isEnabled = false
			mEndDateTextView!!.isEnabled = false
			mRepeatMonthlyByNthDayOfWeek!!.isEnabled = false
			mRepeatMonthlyByNthDayOfMonth!!.isEnabled = false
			for (button in mWeekByDayButtons) {
				button.setEnabled(false)
			}
		} else {
			findViewById<View>(R.id.options).isEnabled = true
			mFreqSpinner!!.isEnabled = true
			mEndSpinner!!.isEnabled = true
			mIntervalPreText!!.isEnabled = true
			mInterval!!.isEnabled = true
			mIntervalPostText!!.isEnabled = true
			mMonthRepeatByRadioGroup!!.isEnabled = true
			mEndCount!!.isEnabled = true
			mPostEndCount!!.isEnabled = true
			mEndDateTextView!!.isEnabled = true
			mRepeatMonthlyByNthDayOfWeek!!.isEnabled = true
			mRepeatMonthlyByNthDayOfMonth!!.isEnabled = true
			for (button in mWeekByDayButtons) {
				button.setEnabled(true)
			}
		}
		updateDoneButtonState()
	}

	private fun updateDoneButtonState() {
		if (mModel.recurrenceState == RecurrenceModel.STATE_NO_RECURRENCE) {
			mButtonLayout!!.updateValidity(true)
			return
		}

		if (mInterval!!.text.toString().length == 0) {
			mButtonLayout!!.updateValidity(false)
			return
		}

		if (mEndCount!!.visibility == View.VISIBLE && mEndCount!!.text.toString().length == 0) {
			mButtonLayout!!.updateValidity(false)
			return
		}

		if (mModel.freq == RecurrenceModel.FREQ_WEEKLY) {
			for (b in mWeekByDayButtons) {
				if (b.isChecked()) {
					mButtonLayout!!.updateValidity(true)
					return
				}
			}
			mButtonLayout!!.updateValidity(false)
			return
		}
		mButtonLayout!!.updateValidity(true)
	}

	public override fun onSaveInstanceState(): Parcelable? {
		val superState = super.onSaveInstanceState()
		return SavedState(superState, mModel, mEndCount!!.hasFocus(),
				if (mRecurrencePicker!!.visibility == View.VISIBLE)
					CurrentView.RECURRENCE_PICKER
				else
					CurrentView.DATE_ONLY_PICKER)
	}

	override fun onRestoreInstanceState(state: Parcelable) {
		val bss = state as View.BaseSavedState
		super.onRestoreInstanceState(bss.superState)
		val ss = bss as SavedState

		val endCountHasFocus = ss.endCountHasFocus
		val m = ss.recurrenceModel
		if (m != null) {
			mModel = m
		}

		mRecurrence.wkst = EventRecurrence.timeDay2Day(RecurrenceUtils.firstDayOfWeek)

		togglePickerOptions()
		updateDialog()

		if (ss.currentView == CurrentView.RECURRENCE_PICKER) {
			showRecurrencePicker()
			post {
				if (mEndCount != null && endCountHasFocus) {
					mEndCount!!.requestFocus()
				}
			}
		} else {
			showDateOnlyPicker()
		}
	}

	/**
	 * Class for managing state storing/restoring.
	 */
	private class SavedState : View.BaseSavedState {

		val recurrenceModel: RecurrenceModel?
		val endCountHasFocus: Boolean
		val currentView: CurrentView

		/**
		 * Constructor called from [DatePicker.onSaveInstanceState]
		 */
		private constructor(superState: Parcelable,
		                    recurrenceModel: RecurrenceModel, endCountHasFocus: Boolean,
		                    currentView: CurrentView) : super(superState) {
			this.recurrenceModel = recurrenceModel
			this.endCountHasFocus = endCountHasFocus
			this.currentView = currentView
		}

		/**
		 * Constructor called from [.CREATOR]
		 */
		private constructor(`in`: Parcel) : super(`in`) {
			recurrenceModel = `in`.readParcelable(RecurrenceModel::class.java!!.getClassLoader())
			endCountHasFocus = `in`.readByte().toInt() != 0
			currentView = CurrentView.valueOf(`in`.readString())
		}

		override fun writeToParcel(dest: Parcel, flags: Int) {
			super.writeToParcel(dest, flags)
			dest.writeParcelable(recurrenceModel, flags)
			dest.writeByte((if (endCountHasFocus) 1 else 0).toByte())
			dest.writeString(currentView.name)
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

	fun updateDialog() {
		// Interval
		// Checking before setting because this causes infinite recursion
		// in afterTextWatcher
		val intervalStr = Integer.toString(mModel.interval)
		if (intervalStr != mInterval!!.text.toString()) {
			mInterval!!.setText(intervalStr)
		}

		mFreqSpinner!!.setSelection(mModel.freq)
		mWeekGroup!!.visibility = if (mModel.freq == RecurrenceModel.FREQ_WEEKLY) View.VISIBLE else View.GONE

		// mWeekGroup2 will be null when available width >= 460dp
		if (mWeekGroup2 != null) {
			mWeekGroup2!!.visibility = if (mModel.freq == RecurrenceModel.FREQ_WEEKLY) View.VISIBLE else View.GONE
		}

		mMonthRepeatByRadioGroup!!.visibility = if (mModel.freq == RecurrenceModel.FREQ_MONTHLY) View.VISIBLE else View.GONE

		when (mModel.freq) {
			RecurrenceModel.FREQ_DAILY -> mIntervalResId = R.plurals.recurrence_interval_daily

			RecurrenceModel.FREQ_WEEKLY -> {
				mIntervalResId = R.plurals.recurrence_interval_weekly
				for (i in 0..6) {
					mWeekByDayButtons[i].setCheckedNoAnimate(mModel.weeklyByDayOfWeek[i])
				}
			}

			RecurrenceModel.FREQ_MONTHLY -> {
				mIntervalResId = R.plurals.recurrence_interval_monthly

				if (mModel.monthlyRepeat == RecurrenceModel.MONTHLY_BY_DATE) {
					mMonthRepeatByRadioGroup!!.check(R.id.repeatMonthlyByNthDayOfMonth)
				} else if (mModel.monthlyRepeat == RecurrenceModel.MONTHLY_BY_NTH_DAY_OF_WEEK) {
					mMonthRepeatByRadioGroup!!.check(R.id.repeatMonthlyByNthDayOfTheWeek)
				}

				if (mMonthRepeatByDayOfWeekStr == null) {
					if (mModel.monthlyByNthDayOfWeek == 0) {
						mModel.monthlyByNthDayOfWeek = (mTime.monthDay + 6) / 7
						// Since not all months have 5 weeks, we convert 5th NthDayOfWeek to
						// -1 for last monthly day of the week
						if (mModel.monthlyByNthDayOfWeek >= FIFTH_WEEK_IN_A_MONTH) {
							mModel.monthlyByNthDayOfWeek = LAST_NTH_DAY_OF_WEEK
						}
						mModel.monthlyByDayOfWeek = mTime.weekDay
					}

					val monthlyByNthDayOfWeekStrs = mMonthRepeatByDayOfWeekStrs!![mModel.monthlyByDayOfWeek]

					// TODO(psliwowski): Find a better way handle -1 indexes
					val msgIndex = if (mModel.monthlyByNthDayOfWeek < 0)
						FIFTH_WEEK_IN_A_MONTH
					else
						mModel.monthlyByNthDayOfWeek
					mMonthRepeatByDayOfWeekStr = monthlyByNthDayOfWeekStrs[msgIndex - 1]
					mRepeatMonthlyByNthDayOfWeek!!.text = mMonthRepeatByDayOfWeekStr
				}
			}

			RecurrenceModel.FREQ_YEARLY -> mIntervalResId = R.plurals.recurrence_interval_yearly
		}
		updateIntervalText()
		updateDoneButtonState()

		mEndSpinner!!.setSelection(mModel.end)
		if (mModel.end == RecurrenceModel.END_BY_DATE) {
			mEndDateTextView!!.text = mEndDateFormatter!!.format(mModel.endDate!!.toMillis(false))
		} else {
			if (mModel.end == RecurrenceModel.END_BY_COUNT) {
				// Checking before setting because this causes infinite
				// recursion
				// in afterTextWatcher
				val countStr = Integer.toString(mModel.endCount)
				if (countStr != mEndCount!!.text.toString()) {
					mEndCount!!.setText(countStr)
				}
			}
		}
	}

	/**
	 * @param endDateString String for end date option
	 * displayed in End Spinner
	 */
	private fun setEndSpinnerEndDateStr(endDateString: String) {
		mEndSpinnerArray[1] = endDateString
		mEndSpinnerAdapter!!.notifyDataSetChanged()
	}

	private fun doToast() {
		Log.e(TAG, "Model = $mModel")
		val rrule: String
		if (mModel.recurrenceState == RecurrenceModel.STATE_NO_RECURRENCE) {
			rrule = "Not repeating"
		} else {
			copyModelToEventRecurrence(mModel, mRecurrence)
			rrule = mRecurrence.toString()
		}

		if (mToast != null) {
			mToast!!.cancel()
		}
		mToast = Toast.makeText(context, rrule,
				Toast.LENGTH_LONG)
		mToast!!.show()
	}

	// TODO Test and update for Right-to-Left
	private fun updateIntervalText() {
		if (mIntervalResId == -1) {
			return
		}

		val INTERVAL_COUNT_MARKER = "%d"
		val intervalString = mResources!!.getQuantityString(mIntervalResId, mModel.interval)
		val markerStart = intervalString.indexOf(INTERVAL_COUNT_MARKER)

		if (markerStart != -1) {
			val postTextStart = markerStart + INTERVAL_COUNT_MARKER.length
			mIntervalPostText!!.setText(intervalString.substring(postTextStart,
					intervalString.length).trim({ it <= ' ' }))
			mIntervalPreText!!.setText(intervalString.substring(0, markerStart).trim({ it <= ' ' }))
		}
	}

	/**
	 * Update the "Repeat for N events" end option with the proper string values
	 * based on the value that has been entered for N.
	 */
	private fun updateEndCountText() {
		val END_COUNT_MARKER = "%d"
		val endString = mResources!!.getQuantityString(R.plurals.recurrence_end_count,
				mModel.endCount)
		val markerStart = endString.indexOf(END_COUNT_MARKER)

		if (markerStart != -1) {
			if (markerStart == 0) {
				Log.e(TAG, "No text to put in to recurrence's end spinner.")
			} else {
				val postTextStart = markerStart + END_COUNT_MARKER.length
				mPostEndCount!!.setText(endString.substring(postTextStart,
						endString.length).trim({ it <= ' ' }))
			}
		}
	}

	// Implements OnItemSelectedListener interface
	// Freq spinner
	// End spinner
	override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
		if (parent === mFreqSpinner) {
			mModel.freq = position
		} else if (parent === mEndSpinner) {
			when (position) {
				RecurrenceModel.END_NEVER -> mModel.end = RecurrenceModel.END_NEVER
				RecurrenceModel.END_BY_DATE -> mModel.end = RecurrenceModel.END_BY_DATE
				RecurrenceModel.END_BY_COUNT -> {
					mModel.end = RecurrenceModel.END_BY_COUNT

					if (mModel.endCount <= 1) {
						mModel.endCount = 1
					} else if (mModel.endCount > COUNT_MAX) {
						mModel.endCount = COUNT_MAX
					}
					updateEndCountText()
				}
			}
			mEndCount!!.visibility = if (mModel.end == RecurrenceModel.END_BY_COUNT)
				View.VISIBLE
			else
				View.GONE
			mEndDateTextView!!.visibility = if (mModel.end == RecurrenceModel.END_BY_DATE)
				View.VISIBLE
			else
				View.GONE
			mPostEndCount!!.visibility = if (mModel.end == RecurrenceModel.END_BY_COUNT && !mHidePostEndCount)
				View.VISIBLE
			else
				View.GONE

		}
		updateDialog()
	}

	// Implements OnItemSelectedListener interface
	override fun onNothingSelected(arg0: AdapterView<*>) {}

	override fun onDateSet(view: RecurrenceEndDatePicker, year: Int, monthOfYear: Int, dayOfMonth: Int) {
		showRecurrencePicker()

		if (mModel.endDate == null) {
			mModel.endDate = Time(mTime.timezone)
			mModel.endDate!!.second = 0
			mModel.endDate!!.minute = mModel.endDate!!.second
			mModel.endDate!!.hour = mModel.endDate!!.minute
		}
		mModel.endDate!!.year = year
		mModel.endDate!!.month = monthOfYear
		mModel.endDate!!.monthDay = dayOfMonth
		mModel.endDate!!.normalize(false)
		updateDialog()
	}

	override fun onDateOnlyPickerCancelled(view: RecurrenceEndDatePicker) {
		showRecurrencePicker()
	}

	// Implements OnCheckedChangeListener interface
	// Week repeat by day of week
	override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
		var itemIdx = -1
		for (i in 0..6) {
			if (itemIdx == -1 && buttonView === mWeekByDayButtons[i]) {
				itemIdx = i
				mModel.weeklyByDayOfWeek[i] = isChecked
			}
		}
		updateDialog()
	}

	// Implements android.widget.RadioGroup.OnCheckedChangeListener interface
	// Month repeat by radio buttons
	override fun onCheckedChanged(group: RadioGroup, checkedId: Int) {
		if (checkedId == R.id.repeatMonthlyByNthDayOfMonth) {
			mModel.monthlyRepeat = RecurrenceModel.MONTHLY_BY_DATE
		} else if (checkedId == R.id.repeatMonthlyByNthDayOfTheWeek) {
			mModel.monthlyRepeat = RecurrenceModel.MONTHLY_BY_NTH_DAY_OF_WEEK
		}
		updateDialog()
	}

	// Implements OnClickListener interface
	// EndDate button
	// Done button
	override fun onClick(v: View) {
		if (mEndDateTextView === v) {
			showDateOnlyPicker()
		}
	}

	private fun showRecurrencePicker() {
		mDateOnlyPicker!!.visibility = View.GONE
		mRecurrencePicker!!.visibility = View.VISIBLE
	}

	private fun showDateOnlyPicker() {
		mDateOnlyPicker!!.init(mModel.endDate!!.year,
				mModel.endDate!!.month, mModel.endDate!!.monthDay, this)
		mDateOnlyPicker!!.firstDayOfWeek = RecurrenceUtils.firstDayOfWeekAsCalendar

		mRecurrencePicker!!.visibility = View.GONE
		mDateOnlyPicker!!.visibility = View.VISIBLE
	}

	interface OnRecurrenceSetListener {
		fun onRecurrenceSet(rrule: String?)

		fun onCancelled()
	}

	private inner class EndSpinnerAdapter
	/**
	 * @param context          Context
	 * @param strings          {Forever, Until a date, For a number of events}
	 * @param itemLayoutId     @Layout resource used for displaying
	 * selected option
	 * @param textResourceId   ViewID for the 'TextView' in 'itemLayoutId'
	 * @param dropDownLayoutId @Layout resource used for displaying
	 * available options in the dropdown menu
	 */
	(context: Context, private val mStrings: ArrayList<CharSequence>,
	 private val mItemLayoutId: Int, private val mTextResourceId: Int, private val mDropDownLayoutId: Int) : ArrayAdapter<CharSequence>(context, mItemLayoutId, mStrings) {
		internal val END_DATE_MARKER = "%s"
		internal val END_COUNT_MARKER = "%d"

		private val mInflater: LayoutInflater
		private val mEndDateString: String
		private var mUseFormStrings: Boolean = false

		init {
			mInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
			mEndDateString = resources.getString(R.string.recurrence_end_date)

			// If either date or count strings don't translate well, such that we aren't assured
			// to have some text available to be placed in the spinner, then we'll have to use
			// the more form-like versions of both strings instead.
			var markerStart = mEndDateString.indexOf(END_DATE_MARKER)
			if (markerStart <= 0) {
				// The date string does not have any text before the "%s" so we'll have to use the
				// more form-like strings instead.
				mUseFormStrings = true
			} else {
				val countEndStr = resources.getQuantityString(
						R.plurals.recurrence_end_count, 1)
				markerStart = countEndStr.indexOf(END_COUNT_MARKER)
				if (markerStart <= 0) {
					// The count string does not have any text before the "%d" so we'll have to use
					// the more form-like strings instead.
					mUseFormStrings = true
				}
			}

			if (mUseFormStrings) {
				// We'll have to set the layout for the spinner to be weight=0 so it doesn't
				// take up too much space.
				mEndSpinner!!.layoutParams = TableLayout.LayoutParams(0, FrameLayout.LayoutParams.WRAP_CONTENT, 1f)
			}
		}

		override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
			var v: View?
			// Check if we can recycle the view
			if (convertView == null) {
				v = mInflater.inflate(mItemLayoutId, parent, false)
			} else {
				v = convertView
			}

			val item = v!!.findViewById<View>(mTextResourceId) as TextView
			var markerStart: Int
			when (position) {
				RecurrenceModel.END_NEVER -> item.text = mStrings[RecurrenceModel.END_NEVER]
				RecurrenceModel.END_BY_DATE -> {
					markerStart = mEndDateString.indexOf(END_DATE_MARKER)

					if (markerStart != -1) {
						if (mUseFormStrings || markerStart == 0) {
							// If we get here, the translation of "Until" doesn't work correctly,
							// so we'll just set the whole "Until a date" string.
							item.text = mEndDateLabel
						} else {
							item.setText(mEndDateString.substring(0, markerStart).trim({ it <= ' ' }))
						}
					}
				}
				RecurrenceModel.END_BY_COUNT -> {
					val endString = mResources!!.getQuantityString(R.plurals.recurrence_end_count,
							mModel.endCount)
					markerStart = endString.indexOf(END_COUNT_MARKER)

					if (markerStart != -1) {
						if (mUseFormStrings || markerStart == 0) {
							// If we get here, the translation of "For" doesn't work correctly,
							// so we'll just set the whole "For a number of events" string.
							item.text = mEndCountLabel
							// Also, we'll hide the " events" that would have been at the end.
							mPostEndCount!!.visibility = View.GONE
							// Use this flag so the onItemSelected knows whether to show it later.
							mHidePostEndCount = true
						} else {
							val postTextStart = markerStart + END_COUNT_MARKER.length
							mPostEndCount!!.setText(endString.substring(postTextStart,
									endString.length).trim({ it <= ' ' }))
							// In case it's a recycled view that wasn't visible.
							if (mModel.end == RecurrenceModel.END_BY_COUNT) {
								mPostEndCount!!.visibility = View.VISIBLE
							}
							if (endString[markerStart - 1] == ' ') {
								markerStart--
							}
							item.setText(endString.substring(0, markerStart).trim({ it <= ' ' }))
						}
					}
				}
				else -> v = null
			}

			return v
		}

		override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
			val v: View
			// Check if we can recycle the view
			if (convertView == null) {
				v = mInflater.inflate(mDropDownLayoutId, parent, false)
			} else {
				v = convertView
			}

			val item = v.findViewById<View>(mTextResourceId) as TextView
			item.text = mStrings[position]

			return v
		}
	}

	companion object {

		private val TAG = "RecurrenceOptionCreator"

		// Update android:maxLength in EditText as needed
		private val INTERVAL_MAX = 99
		private val INTERVAL_DEFAULT = 1
		// Update android:maxLength in EditText as needed
		private val COUNT_MAX = 730
		private val COUNT_DEFAULT = 5

		// Special cases in monthlyByNthDayOfWeek
		private val FIFTH_WEEK_IN_A_MONTH = 5
		private val LAST_NTH_DAY_OF_WEEK = -1
		private val mFreqModelToEventRecurrence = intArrayOf(EventRecurrence.DAILY, EventRecurrence.WEEKLY, EventRecurrence.MONTHLY, EventRecurrence.YEARLY)

		fun isSupportedMonthlyByNthDayOfWeek(num: Int): Boolean {
			// We only support monthlyByNthDayOfWeek when it is greater then 0 but less then 5.
			// Or if -1 when it is the last monthly day of the week.
			return num > 0 && num <= FIFTH_WEEK_IN_A_MONTH || num == LAST_NTH_DAY_OF_WEEK
		}

		fun canHandleRecurrenceRule(er: EventRecurrence): Boolean {
			when (er.freq) {
				EventRecurrence.DAILY, EventRecurrence.MONTHLY, EventRecurrence.YEARLY, EventRecurrence.WEEKLY -> {
				}
				else -> return false
			}

			if (er.count > 0 && !TextUtils.isEmpty(er.until)) {
				return false
			}

			// Weekly: For "repeat by day of week", the day of week to repeat is in
			// er.byday[]

			/*
         * Monthly: For "repeat by nth day of week" the day of week to repeat is
         * in er.byday[] and the "nth" is stored in er.bydayNum[]. Currently we
         * can handle only one and only in monthly
         */
			var numOfByDayNum = 0
			for (i in 0 until er.bydayCount) {
				if (isSupportedMonthlyByNthDayOfWeek(er.bydayNum[i])) {
					++numOfByDayNum
				}
			}

			if (numOfByDayNum > 1) {
				return false
			}

			if (numOfByDayNum > 0 && er.freq != EventRecurrence.MONTHLY) {
				return false
			}

			// The UI only handle repeat by one day of month i.e. not 9th and 10th
			// of every month
			if (er.bymonthdayCount > 1) {
				return false
			}

			if (er.freq == EventRecurrence.MONTHLY) {
				if (er.bydayCount > 1) {
					return false
				}
				if (er.bydayCount > 0 && er.bymonthdayCount > 0) {
					return false
				}
			}

			return true
		}

		// TODO don't lose data when getting data that our UI can't handle
		private fun copyEventRecurrenceToModel(er: EventRecurrence,
		                                       model: RecurrenceModel) {
			// Freq:
			when (er.freq) {
				EventRecurrence.DAILY -> model.freq = RecurrenceModel.FREQ_DAILY
				EventRecurrence.MONTHLY -> model.freq = RecurrenceModel.FREQ_MONTHLY
				EventRecurrence.YEARLY -> model.freq = RecurrenceModel.FREQ_YEARLY
				EventRecurrence.WEEKLY -> model.freq = RecurrenceModel.FREQ_WEEKLY
				else -> throw IllegalStateException("freq=" + er.freq)
			}

			// Interval:
			if (er.interval > 0) {
				model.interval = er.interval
			}

			// End:
			// End by count:
			model.endCount = er.count
			if (model.endCount > 0) {
				model.end = RecurrenceModel.END_BY_COUNT
			}

			// End by date:
			if (!TextUtils.isEmpty(er.until)) {
				if (model.endDate == null) {
					model.endDate = Time()
				}

				try {
					model.endDate!!.parse(er.until)
				} catch (e: TimeFormatException) {
					model.endDate = null
				}

				// LIMITATION: The UI can only handle END_BY_DATE or END_BY_COUNT
				if (model.end == RecurrenceModel.END_BY_COUNT && model.endDate != null) {
					throw IllegalStateException("freq=" + er.freq)
				}

				model.end = RecurrenceModel.END_BY_DATE
			}

			// Weekly: repeat by day of week or Monthly: repeat by nth day of week
			// in the month
			Arrays.fill(model.weeklyByDayOfWeek, false)
			if (er.bydayCount > 0) {
				var count = 0
				for (i in 0 until er.bydayCount) {
					val dayOfWeek = EventRecurrence.day2TimeDay(er.byday[i])
					model.weeklyByDayOfWeek[dayOfWeek] = true

					if (model.freq == RecurrenceModel.FREQ_MONTHLY && isSupportedMonthlyByNthDayOfWeek(er.bydayNum[i])) {
						// LIMITATION: Can handle only (one) weekDayNum in nth or last and only
						// when
						// monthly
						model.monthlyByDayOfWeek = dayOfWeek
						model.monthlyByNthDayOfWeek = er.bydayNum[i]
						model.monthlyRepeat = RecurrenceModel.MONTHLY_BY_NTH_DAY_OF_WEEK
						count++
					}
				}

				if (model.freq == RecurrenceModel.FREQ_MONTHLY) {
					if (er.bydayCount != 1) {
						// Can't handle 1st Monday and 2nd Wed
						throw IllegalStateException("Can handle only 1 byDayOfWeek in monthly")
					}
					if (count != 1) {
						throw IllegalStateException(
								"Didn't specify which nth day of week to repeat for a monthly")
					}
				}
			}

			// Monthly by day of month
			if (model.freq == RecurrenceModel.FREQ_MONTHLY) {
				if (er.bymonthdayCount == 1) {
					if (model.monthlyRepeat == RecurrenceModel.MONTHLY_BY_NTH_DAY_OF_WEEK) {
						throw IllegalStateException(
								"Can handle only by monthday or by nth day of week, not both")
					}
					model.monthlyByMonthDay = er.bymonthday[0]
					model.monthlyRepeat = RecurrenceModel.MONTHLY_BY_DATE
				} else if (er.bymonthCount > 1) {
					// LIMITATION: Can handle only one month day
					throw IllegalStateException("Can handle only one bymonthday")
				}
			}
		}

		private fun copyModelToEventRecurrence(model: RecurrenceModel,
		                                       er: EventRecurrence) {
			if (model.recurrenceState == RecurrenceModel.STATE_NO_RECURRENCE) {
				throw IllegalStateException("There's no recurrence")
			}

			// Freq
			er.freq = mFreqModelToEventRecurrence[model.freq]

			// Interval
			if (model.interval <= 1) {
				er.interval = 0
			} else {
				er.interval = model.interval
			}

			// End
			when (model.end) {
				RecurrenceModel.END_BY_DATE -> if (model.endDate != null) {
					model.endDate!!.switchTimezone(Time.TIMEZONE_UTC)
					model.endDate!!.normalize(false)
					er.until = model.endDate!!.format2445()
					er.count = 0
				} else {
					throw IllegalStateException("end = END_BY_DATE but endDate is null")
				}
				RecurrenceModel.END_BY_COUNT -> {
					er.count = model.endCount
					er.until = null
					if (er.count <= 0) {
						throw IllegalStateException("count is " + er.count)
					}
				}
				else -> {
					er.count = 0
					er.until = null
				}
			}

			// Weekly && monthly repeat patterns
			er.bydayCount = 0
			er.bymonthdayCount = 0

			when (model.freq) {
				RecurrenceModel.FREQ_MONTHLY -> if (model.monthlyRepeat == RecurrenceModel.MONTHLY_BY_DATE) {
					if (model.monthlyByMonthDay > 0) {
						if (er.bymonthday == null || er.bymonthdayCount < 1) {
							er.bymonthday = IntArray(1)
						}
						er.bymonthday[0] = model.monthlyByMonthDay
						er.bymonthdayCount = 1
					}
				} else if (model.monthlyRepeat == RecurrenceModel.MONTHLY_BY_NTH_DAY_OF_WEEK) {
					if (!isSupportedMonthlyByNthDayOfWeek(model.monthlyByNthDayOfWeek)) {
						throw IllegalStateException("month repeat by nth week but n is " + model.monthlyByNthDayOfWeek)
					}
					val count = 1
					if (er.bydayCount < count || er.byday == null || er.bydayNum == null) {
						er.byday = IntArray(count)
						er.bydayNum = IntArray(count)
					}
					er.bydayCount = count
					er.byday[0] = EventRecurrence.timeDay2Day(model.monthlyByDayOfWeek)
					er.bydayNum[0] = model.monthlyByNthDayOfWeek
				}
				RecurrenceModel.FREQ_WEEKLY -> {
					var count = 0
					for (i in 0..6) {
						if (model.weeklyByDayOfWeek[i]) {
							count++
						}
					}

					if (er.bydayCount < count || er.byday == null || er.bydayNum == null) {
						er.byday = IntArray(count)
						er.bydayNum = IntArray(count)
					}
					er.bydayCount = count

					for (i in 6 downTo 0) {
						if (model.weeklyByDayOfWeek[i]) {
							er.bydayNum[--count] = 0
							er.byday[count] = EventRecurrence.timeDay2Day(i)
						}
					}
				}
			}

			if (!canHandleRecurrenceRule(er)) {
				throw IllegalStateException("UI generated recurrence that it can't handle. ER:"
						+ er.toString() + " Model: " + model.toString())
			}
		}
	}
}


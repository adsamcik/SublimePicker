/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.appeaser.sublimepickerlibrary.datepicker

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.TypedArray
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.AlignmentSpan
import android.text.style.RelativeSizeSpan
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ViewAnimator

import com.appeaser.sublimepickerlibrary.R
import com.appeaser.sublimepickerlibrary.common.DateTimePatternHelper
import com.appeaser.sublimepickerlibrary.utilities.AccessibilityUtils
import com.appeaser.sublimepickerlibrary.utilities.Config
import com.appeaser.sublimepickerlibrary.utilities.SUtils
import com.appeaser.sublimepickerlibrary.utilities.TextColorHelper

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SublimeDatePicker : FrameLayout {

	private var mContext: Context? = null

	private var mYearFormat: SimpleDateFormat? = null
	private var mMonthDayFormat: SimpleDateFormat? = null

	// Top-level container.
	private var mContainer: ViewGroup? = null

	// Header views.
	private var llHeaderDateSingleCont: LinearLayout? = null
	private var mHeaderYear: TextView? = null
	private var mHeaderMonthDay: TextView? = null
	private var llHeaderDateRangeCont: LinearLayout? = null
	private var tvHeaderDateStart: TextView? = null
	private var tvHeaderDateEnd: TextView? = null
	private var ivHeaderDateReset: ImageView? = null

	// Picker views.
	private var mAnimator: ViewAnimator? = null
	private var mDayPickerView: DayPickerView? = null
	private var mYearPickerView: YearPickerView? = null

	// Accessibility strings.
	private var mSelectDay: String? = null
	private var mSelectYear: String? = null

	private var mDateChangedListener: SublimeDatePicker.OnDateChangedListener? = null

	private var mCurrentView = UNINITIALIZED

	private var mCurrentDate: SelectedDate? = null
	private var mTempDate: Calendar? = null
	/**
	 * Gets the minimal date supported by this [SublimeDatePicker] in
	 * milliseconds since January 1, 1970 00:00:00 in
	 * [java.util.TimeZone.getDefault] time zone.
	 * Note: The default minimal date is 01/01/1900.
	 *
	 * @return The minimal supported date.
	 */
	var minDate: Calendar? = null
		private set
	/**
	 * Gets the maximal date supported by this [SublimeDatePicker] in
	 * milliseconds since January 1, 1970 00:00:00 in
	 * [java.util.TimeZone.getDefault] time zone.
	 * Note: The default maximal date is 12/31/2100.
	 *
	 * @return The maximal supported date.
	 */
	var maxDate: Calendar? = null
		private set

	var firstDayOfWeek: Int = 0
		set(firstDayOfWeek) {
			var firstDayOfWeek = firstDayOfWeek
			if (firstDayOfWeek < Calendar.SUNDAY || firstDayOfWeek > Calendar.SATURDAY) {
				if (Config.DEBUG) {
					Log.e(TAG, "Provided `firstDayOfWeek` is invalid - it must be between 1 and 7. " +
							"Given value: " + firstDayOfWeek + " Picker will use the default value for the given locale.")
				}

				firstDayOfWeek = mCurrentDate!!.firstDate!!.firstDayOfWeek
			}

			field = firstDayOfWeek
			mDayPickerView!!.firstDayOfWeek = firstDayOfWeek
		}

	private var mCurrentLocale: Locale? = null

	private var mValidationCallback: DatePickerValidationCallback? = null

	private var mCurrentlyActivatedRangeItem = RANGE_ACTIVATED_NONE

	private var mIsInLandscapeMode: Boolean = false

	/**
	 * Listener called when the user selects a day in the day picker view.
	 */
	private val mProxyDaySelectionEventListener = object : DayPickerView.ProxyDaySelectionEventListener {
		override fun onDaySelected(view: DayPickerView, day: Calendar?) {
			if (Config.DEBUG) {
				Log.i(TAG, "tvHeaderDateStart is activated? " + tvHeaderDateStart!!.isActivated)
				Log.i(TAG, "tvHeaderDateEnd is activated? " + tvHeaderDateEnd!!.isActivated)
			}

			var goToPosition = true

			if (llHeaderDateRangeCont!!.visibility == View.VISIBLE) {
				// We're in Range selection mode
				if (tvHeaderDateStart!!.isActivated) {
					if (SelectedDate.compareDates(day!!, mCurrentDate!!.endDate) > 0) {
						mCurrentDate = SelectedDate(day)
					} else {
						goToPosition = false
						mCurrentDate = SelectedDate(day, mCurrentDate!!.endDate)
					}
				} else if (tvHeaderDateEnd!!.isActivated) {
					if (SelectedDate.compareDates(day!!, mCurrentDate!!.startDate) < 0) {
						mCurrentDate = SelectedDate(day)
					} else {
						goToPosition = false
						mCurrentDate = SelectedDate(mCurrentDate!!.startDate, day)
					}
				} else { // Should never happen
					if (Config.DEBUG) {
						Log.i(TAG, "onDaySelected: Neither tvDateStart, nor tvDateEnd is activated")
					}
				}
			} else {
				mCurrentDate = SelectedDate(day)
			}

			onDateChanged(true, false, goToPosition)
		}

		override fun onDateRangeSelectionStarted(selectedDate: SelectedDate) {
			mCurrentDate = SelectedDate(selectedDate)
			onDateChanged(false, false, false)
		}

		override fun onDateRangeSelectionEnded(selectedDate: SelectedDate?) {
			if (selectedDate != null) {
				mCurrentDate = SelectedDate(selectedDate)
				onDateChanged(false, false, false)
			}
		}

		override fun onDateRangeSelectionUpdated(selectedDate: SelectedDate) {
			if (Config.DEBUG) {
				Log.i(TAG, "onDateRangeSelectionUpdated: $selectedDate")
			}

			mCurrentDate = SelectedDate(selectedDate)
			onDateChanged(false, false, false)
		}
	}

	/**
	 * Listener called when the user selects a year in the year picker view.
	 */
	private val mOnYearSelectedListener = YearPickerView.OnYearSelectedListener { view, year ->
		// If the newly selected month / year does not contain the
		// currently selected day number, change the selected day number
		// to the last day of the selected month or year.
		// e.g. Switching from Mar to Apr when Mar 31 is selected -> Apr 30
		// e.g. Switching from 2012 to 2013 when Feb 29, 2012 is selected -> Feb 28, 2013
		val day = mCurrentDate!!.startDate.get(Calendar.DAY_OF_MONTH)
		val month = mCurrentDate!!.startDate.get(Calendar.MONTH)
		val daysInMonth = SUtils.getDaysInMonth(month, year)
		if (day > daysInMonth) {
			mCurrentDate!!.set(Calendar.DAY_OF_MONTH, daysInMonth)
		}

		mCurrentDate!!.set(Calendar.YEAR, year)
		onDateChanged(true, true, true)

		// Automatically switch to day picker.
		setCurrentView(VIEW_MONTH_DAY)
	}

	/**
	 * Listener called when the user clicks on a header item.
	 */
	private val mOnHeaderClickListener = OnClickListener { v ->
		SUtils.vibrateForDatePicker(this@SublimeDatePicker)

		if (v.id == R.id.date_picker_header_year) {
			setCurrentView(VIEW_YEAR)
		} else if (v.id == R.id.date_picker_header_date) {
			setCurrentView(VIEW_MONTH_DAY)
		} else if (v.id == R.id.tv_header_date_start) {
			mCurrentlyActivatedRangeItem = RANGE_ACTIVATED_START
			tvHeaderDateStart!!.isActivated = true
			tvHeaderDateEnd!!.isActivated = false
		} else if (v.id == R.id.tv_header_date_end) {
			mCurrentlyActivatedRangeItem = RANGE_ACTIVATED_END
			tvHeaderDateStart!!.isActivated = false
			tvHeaderDateEnd!!.isActivated = true
		} else if (v.id == R.id.iv_header_date_reset) {
			mCurrentDate = SelectedDate(mCurrentDate!!.startDate)
			onDateChanged(true, false, true)
		}
	}

	val selectedDate: SelectedDate
		get() = SelectedDate(mCurrentDate)

	val selectedDateInMillis: Long
		get() = mCurrentDate!!.startDate.timeInMillis

	@JvmOverloads
	constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.spDatePickerStyle) : super(context, attrs, defStyleAttr) {
		initializeLayout(attrs, defStyleAttr, R.style.SublimeDatePickerStyle)
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(context: Context, attrs: AttributeSet,
	            defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
		initializeLayout(attrs, defStyleAttr, defStyleRes)
	}

	private fun initializeLayout(attrs: AttributeSet?,
	                             defStyleAttr: Int, defStyleRes: Int) {
		mContext = context
		mIsInLandscapeMode = mContext!!.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

		setCurrentLocale(Locale.getDefault())
		mCurrentDate = SelectedDate(Calendar.getInstance(mCurrentLocale))
		mTempDate = Calendar.getInstance(mCurrentLocale)
		minDate = Calendar.getInstance(mCurrentLocale)
		maxDate = Calendar.getInstance(mCurrentLocale)

		minDate!!.set(DEFAULT_START_YEAR, Calendar.JANUARY, 1)
		maxDate!!.set(DEFAULT_END_YEAR, Calendar.DECEMBER, 31)

		val res = resources
		val a = mContext!!.obtainStyledAttributes(attrs,
				R.styleable.SublimeDatePicker, defStyleAttr, defStyleRes)
		val inflater = mContext!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
		val layoutResourceId = R.layout.date_picker_layout

		try {
			// Set up and attach container.
			mContainer = inflater.inflate(layoutResourceId, this, false) as ViewGroup
		} catch (e: Exception) {
			e.printStackTrace()
		}

		addView(mContainer)

		// Set up header views.
		val header = mContainer!!.findViewById<View>(R.id.date_picker_header) as ViewGroup
		llHeaderDateSingleCont = header.findViewById<View>(R.id.ll_header_date_single_cont) as LinearLayout
		mHeaderYear = header.findViewById<View>(R.id.date_picker_header_year) as TextView
		mHeaderYear!!.setOnClickListener(mOnHeaderClickListener)
		mHeaderMonthDay = header.findViewById<View>(R.id.date_picker_header_date) as TextView
		mHeaderMonthDay!!.setOnClickListener(mOnHeaderClickListener)

		llHeaderDateRangeCont = header.findViewById<View>(R.id.ll_header_date_range_cont) as LinearLayout
		tvHeaderDateStart = header.findViewById<View>(R.id.tv_header_date_start) as TextView
		tvHeaderDateStart!!.setOnClickListener(mOnHeaderClickListener)
		tvHeaderDateEnd = header.findViewById<View>(R.id.tv_header_date_end) as TextView
		tvHeaderDateEnd!!.setOnClickListener(mOnHeaderClickListener)
		ivHeaderDateReset = header.findViewById<View>(R.id.iv_header_date_reset) as ImageView
		ivHeaderDateReset!!.setOnClickListener(mOnHeaderClickListener)

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

		SUtils.setImageTintList(ivHeaderDateReset, ColorStateList.valueOf(iconColor))
		SUtils.setViewBackground(ivHeaderDateReset!!, SUtils.createOverflowButtonBg(pressedStateBgColor))

		var headerTextColor = a.getColorStateList(R.styleable.SublimeDatePicker_spHeaderTextColor)

		if (headerTextColor == null) {
			headerTextColor = TextColorHelper.resolveMaterialHeaderTextColor()
		}

		if (headerTextColor != null) {
			mHeaderYear!!.setTextColor(headerTextColor)
			mHeaderMonthDay!!.setTextColor(headerTextColor)
		}

		// Set up header background, if available.
		if (SUtils.isApi_22_OrHigher) {
			if (a.hasValueOrEmpty(R.styleable.SublimeDatePicker_spHeaderBackground)) {
				SUtils.setViewBackground(header,
						a.getDrawable(R.styleable.SublimeDatePicker_spHeaderBackground))
			}
		} else {
			if (a.hasValue(R.styleable.SublimeDatePicker_spHeaderBackground)) {
				SUtils.setViewBackground(header, a.getDrawable(R.styleable.SublimeDatePicker_spHeaderBackground))
			}
		}

		var firstDayOfWeek = a.getInt(R.styleable.SublimeDatePicker_spFirstDayOfWeek,
				mCurrentDate!!.firstDate!!.firstDayOfWeek)

		val minDate = a.getString(R.styleable.SublimeDatePicker_spMinDate)
		val maxDate = a.getString(R.styleable.SublimeDatePicker_spMaxDate)

		// Set up min and max dates.
		val tempDate = Calendar.getInstance()

		if (!SUtils.parseDate(minDate, tempDate)) {
			tempDate.set(DEFAULT_START_YEAR, Calendar.JANUARY, 1)
		}

		val minDateMillis = tempDate.timeInMillis

		if (!SUtils.parseDate(maxDate, tempDate)) {
			tempDate.set(DEFAULT_END_YEAR, Calendar.DECEMBER, 31)
		}

		val maxDateMillis = tempDate.timeInMillis

		if (maxDateMillis < minDateMillis) {
			throw IllegalArgumentException("maxDate must be >= minDate")
		}

		val setDateMillis = SUtils.constrain(
				System.currentTimeMillis(), minDateMillis, maxDateMillis)

		this.minDate!!.timeInMillis = minDateMillis
		this.maxDate!!.timeInMillis = maxDateMillis
		mCurrentDate!!.setTimeInMillis(setDateMillis)

		a.recycle()

		// Set up picker container.
		mAnimator = mContainer!!.findViewById<View>(R.id.animator) as ViewAnimator

		// Set up day picker view.
		mDayPickerView = mAnimator!!.findViewById<View>(R.id.date_picker_day_picker) as DayPickerView
		firstDayOfWeek = firstDayOfWeek
		mDayPickerView!!.minDate = this.minDate!!.timeInMillis
		mDayPickerView!!.maxDate = this.maxDate!!.timeInMillis
		mDayPickerView!!.date = mCurrentDate
		mDayPickerView!!.setProxyDaySelectionEventListener(mProxyDaySelectionEventListener)

		// Set up year picker view.
		mYearPickerView = mAnimator!!.findViewById<View>(R.id.date_picker_year_picker) as YearPickerView
		mYearPickerView!!.setRange(this.minDate, this.maxDate)
		mYearPickerView!!.setOnYearSelectedListener(mOnYearSelectedListener)

		// Set up content descriptions.
		mSelectDay = res.getString(R.string.select_day)
		mSelectYear = res.getString(R.string.select_year)

		// Initialize for current locale. This also initializes the date, so no
		// need to call onDateChanged.
		onLocaleChanged(mCurrentLocale)

		setCurrentView(VIEW_MONTH_DAY)
	}

	private fun onLocaleChanged(locale: Locale?) {
		val headerYear = mHeaderYear
				?: // Abort, we haven't initialized yet. This method will get called
				// again later after everything has been set up.
				return

		// Update the date formatter.
		val datePattern: String

		if (SUtils.isApi_18_OrHigher) {
			datePattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EMMMd")
		} else {
			datePattern = DateTimePatternHelper.getBestDateTimePattern(locale!!, DateTimePatternHelper.PATTERN_EMMMd)
		}

		mMonthDayFormat = SimpleDateFormat(datePattern, locale!!)
		mYearFormat = SimpleDateFormat("y", locale)

		// Update the header text.
		onCurrentDateChanged(false)
	}

	private fun onCurrentDateChanged(announce: Boolean) {
		if (mHeaderYear == null) {
			// Abort, we haven't initialized yet. This method will get called
			// again later after everything has been set up.
			return
		}

		val year = mYearFormat!!.format(mCurrentDate!!.startDate.time)
		mHeaderYear!!.text = year

		val monthDay = mMonthDayFormat!!.format(mCurrentDate!!.startDate.time)
		mHeaderMonthDay!!.text = monthDay

		val yearStrStart = mYearFormat!!.format(mCurrentDate!!.startDate.time)
		val monthDayStrStart = mMonthDayFormat!!.format(mCurrentDate!!.startDate.time)
		val dateStrStart = yearStrStart + "\n" + monthDayStrStart

		val yearStrEnd = mYearFormat!!.format(mCurrentDate!!.endDate.time)
		val monthDayStrEnd = mMonthDayFormat!!.format(mCurrentDate!!.endDate.time)
		val dateStrEnd = yearStrEnd + "\n" + monthDayStrEnd

		val spDateStart = SpannableString(dateStrStart)
		// If textSize is 34dp for land, use 0.47f
		//spDateStart.setSpan(new RelativeSizeSpan(mIsInLandscapeMode ? 0.47f : 0.7f),
		spDateStart.setSpan(RelativeSizeSpan(0.7f),
				0, yearStrStart.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

		val spDateEnd = SpannableString(dateStrEnd)
		spDateEnd.setSpan(RelativeSizeSpan(0.7f),
				0, yearStrEnd.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

		// API <= 16
		if (!mIsInLandscapeMode && !SUtils.isApi_17_OrHigher) {
			spDateEnd.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE),
					0, dateStrEnd.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		}

		tvHeaderDateStart!!.text = spDateStart
		tvHeaderDateEnd!!.text = spDateEnd

		// TODO: This should use live regions.
		if (announce) {
			val millis = mCurrentDate!!.startDate.timeInMillis
			val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR
			val fullDateText = DateUtils.formatDateTime(mContext, millis, flags)
			AccessibilityUtils.makeAnnouncement(mAnimator, fullDateText)
		}
	}

	private fun setCurrentView(viewIndex: Int) {
		when (viewIndex) {
			VIEW_MONTH_DAY -> {
				mDayPickerView!!.date = mCurrentDate

				if (mCurrentDate!!.type == SelectedDate.Type.SINGLE) {
					switchToSingleDateView()
				} else if (mCurrentDate!!.type == SelectedDate.Type.RANGE) {
					switchToDateRangeView()
				}

				if (mCurrentView != viewIndex) {
					if (mAnimator!!.displayedChild != VIEW_MONTH_DAY) {
						mAnimator!!.displayedChild = VIEW_MONTH_DAY
					}
					mCurrentView = viewIndex
				}

				AccessibilityUtils.makeAnnouncement(mAnimator, mSelectDay)
			}
			VIEW_YEAR -> {
				if (mCurrentView != viewIndex) {
					mHeaderMonthDay!!.isActivated = false
					mHeaderYear!!.isActivated = true
					mAnimator!!.displayedChild = VIEW_YEAR
					mCurrentView = viewIndex
				}

				AccessibilityUtils.makeAnnouncement(mAnimator, mSelectYear)
			}
		}
	}

	/**
	 * Initialize the state. If the provided values designate an inconsistent
	 * date the values are normalized before updating the spinners.
	 *
	 * @param selectedDate  The initial date or date range.
	 * @param canPickRange  Enable/disable date range selection
	 * @param callback      How user is notified date is changed by
	 * user, can be null.
	 */
	//public void init(int year, int monthOfYear, int dayOfMonth, boolean canPickRange,
	fun init(selectedDate: SelectedDate, canPickRange: Boolean,
	         callback: SublimeDatePicker.OnDateChangedListener) {
		//mCurrentDate.set(Calendar.YEAR, year);
		//mCurrentDate.set(Calendar.MONTH, monthOfYear);
		//mCurrentDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
		mCurrentDate = SelectedDate(selectedDate)

		mDayPickerView!!.setCanPickRange(canPickRange)
		mDateChangedListener = callback

		onDateChanged(false, false, true)
	}

	/**
	 * Update the current date.
	 *
	 * @param year       The year.
	 * @param month      The month which is **starting from zero**.
	 * @param dayOfMonth The day of the month.
	 */
	fun updateDate(year: Int, month: Int, dayOfMonth: Int) {
		mCurrentDate!!.set(Calendar.YEAR, year)
		mCurrentDate!!.set(Calendar.MONTH, month)
		mCurrentDate!!.set(Calendar.DAY_OF_MONTH, dayOfMonth)

		onDateChanged(false, true, true)
	}

	// callbackToClient is useless for now & gives us an unnecessary round-trip
	// by calling init(...)
	private fun onDateChanged(fromUser: Boolean, callbackToClient: Boolean, goToPosition: Boolean) {
		val year = mCurrentDate!!.startDate.get(Calendar.YEAR)

		if (callbackToClient && mDateChangedListener != null) {
			mDateChangedListener!!.onDateChanged(this, mCurrentDate)
		}

		updateHeaderViews()

		mDayPickerView!!.setDate(SelectedDate(mCurrentDate), false, goToPosition)

		if (mCurrentDate!!.type == SelectedDate.Type.SINGLE) {
			mYearPickerView!!.setYear(year)
		}

		onCurrentDateChanged(fromUser)

		if (fromUser) {
			SUtils.vibrateForDatePicker(this@SublimeDatePicker)
		}
	}

	private fun updateHeaderViews() {
		if (Config.DEBUG) {
			Log.i(TAG, "updateHeaderViews(): First Date: "
					+ mCurrentDate!!.firstDate!!.timeInMillis
					+ " Second Date: "
					+ mCurrentDate!!.secondDate!!.timeInMillis)
		}

		if (mCurrentDate!!.type == SelectedDate.Type.SINGLE) {
			switchToSingleDateView()
		} else if (mCurrentDate!!.type == SelectedDate.Type.RANGE) {
			switchToDateRangeView()
		}
	}

	private fun switchToSingleDateView() {
		mCurrentlyActivatedRangeItem = RANGE_ACTIVATED_NONE

		ivHeaderDateReset!!.visibility = View.GONE
		llHeaderDateRangeCont!!.visibility = View.INVISIBLE
		llHeaderDateSingleCont!!.visibility = View.VISIBLE

		mHeaderMonthDay!!.isActivated = true
		mHeaderYear!!.isActivated = false
	}

	private fun switchToDateRangeView() {
		if (mCurrentlyActivatedRangeItem == RANGE_ACTIVATED_NONE) {
			mCurrentlyActivatedRangeItem = RANGE_ACTIVATED_START
		}

		llHeaderDateSingleCont!!.visibility = View.INVISIBLE
		ivHeaderDateReset!!.visibility = View.VISIBLE
		llHeaderDateRangeCont!!.visibility = View.VISIBLE

		tvHeaderDateStart!!.isActivated = mCurrentlyActivatedRangeItem == RANGE_ACTIVATED_START
		tvHeaderDateEnd!!.isActivated = mCurrentlyActivatedRangeItem == RANGE_ACTIVATED_END
	}

	/**
	 * Sets the minimal date supported by this [SublimeDatePicker] in
	 * milliseconds since January 1, 1970 00:00:00 in
	 * [java.util.TimeZone.getDefault] time zone.
	 *
	 * @param minDate The minimal supported date.
	 */
	fun setMinDate(minDate: Long) {
		mTempDate!!.timeInMillis = minDate
		if (mTempDate!!.get(Calendar.YEAR) == this.minDate!!.get(Calendar.YEAR) && mTempDate!!.get(Calendar.DAY_OF_YEAR) != this.minDate!!.get(Calendar.DAY_OF_YEAR)) {
			return
		}
		if (mCurrentDate!!.startDate.before(mTempDate)) {
			mCurrentDate!!.startDate.timeInMillis = minDate
			onDateChanged(false, true, true)
		}
		this.minDate!!.timeInMillis = minDate
		mDayPickerView!!.minDate = minDate
		mYearPickerView!!.setRange(this.minDate, maxDate)
	}

	/**
	 * Sets the maximal date supported by this [SublimeDatePicker] in
	 * milliseconds since January 1, 1970 00:00:00 in
	 * [java.util.TimeZone.getDefault] time zone.
	 *
	 * @param maxDate The maximal supported date.
	 */
	fun setMaxDate(maxDate: Long) {
		mTempDate!!.timeInMillis = maxDate
		if (mTempDate!!.get(Calendar.YEAR) == this.maxDate!!.get(Calendar.YEAR) && mTempDate!!.get(Calendar.DAY_OF_YEAR) != this.maxDate!!.get(Calendar.DAY_OF_YEAR)) {
			return
		}
		if (mCurrentDate!!.endDate.after(mTempDate)) {
			mCurrentDate!!.endDate.timeInMillis = maxDate
			onDateChanged(false, true, true)
		}
		this.maxDate!!.timeInMillis = maxDate
		mDayPickerView!!.maxDate = maxDate
		mYearPickerView!!.setRange(minDate, this.maxDate)
	}

	override fun setEnabled(enabled: Boolean) {
		if (isEnabled == enabled) {
			return
		}

		mContainer!!.isEnabled = enabled
		mDayPickerView!!.isEnabled = enabled
		mYearPickerView!!.isEnabled = enabled
		mHeaderYear!!.isEnabled = enabled
		mHeaderMonthDay!!.isEnabled = enabled
	}

	override fun isEnabled(): Boolean {
		return mContainer!!.isEnabled
	}

	public override fun onConfigurationChanged(newConfig: Configuration) {
		setCurrentLocale(newConfig.locale)
	}

	private fun setCurrentLocale(locale: Locale) {
		if (locale != mCurrentLocale) {
			mCurrentLocale = locale
			onLocaleChanged(locale)
		}
	}

	public override fun onSaveInstanceState(): Parcelable? {
		val superState = super.onSaveInstanceState()

		var listPosition = -1
		var listPositionOffset = -1

		if (mCurrentView == VIEW_MONTH_DAY) {
			listPosition = mDayPickerView!!.mostVisiblePosition
		} else if (mCurrentView == VIEW_YEAR) {
			listPosition = mYearPickerView!!.firstVisiblePosition
			listPositionOffset = mYearPickerView!!.firstPositionOffset
		}

		return SavedState(superState, mCurrentDate!!, minDate!!.timeInMillis,
				maxDate!!.timeInMillis, mCurrentView, listPosition,
				listPositionOffset, mCurrentlyActivatedRangeItem)
	}

	@SuppressLint("NewApi")
	public override fun onRestoreInstanceState(state: Parcelable) {
		val bss = state as View.BaseSavedState
		super.onRestoreInstanceState(bss.superState)
		val ss = bss as SavedState

		val startDate = Calendar.getInstance(mCurrentLocale)
		val endDate = Calendar.getInstance(mCurrentLocale)

		startDate.set(ss.selectedYearStart, ss.selectedMonthStart, ss.selectedDayStart)
		endDate.set(ss.selectedYearEnd, ss.selectedMonthEnd, ss.selectedDayEnd)

		mCurrentDate!!.firstDate = startDate
		mCurrentDate!!.secondDate = endDate

		val currentView = ss.currentView
		minDate!!.timeInMillis = ss.minDate
		maxDate!!.timeInMillis = ss.maxDate

		mCurrentlyActivatedRangeItem = ss.currentlyActivatedRangeItem

		onCurrentDateChanged(false)
		setCurrentView(currentView)

		val listPosition = ss.listPosition

		if (listPosition != -1) {
			if (currentView == VIEW_MONTH_DAY) {
				mDayPickerView!!.setPosition(listPosition)
			} else if (currentView == VIEW_YEAR) {
				val listPositionOffset = ss.listPositionOffset
				mYearPickerView!!.setSelectionFromTop(listPosition, listPositionOffset)
			}
		}
	}

	override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
		onPopulateAccessibilityEvent(event)
		return true
	}

	override fun onPopulateAccessibilityEvent(event: AccessibilityEvent) {
		super.onPopulateAccessibilityEvent(event)
		event.text.add(mCurrentDate!!.startDate.time.toString())
	}

	override fun getAccessibilityClassName(): CharSequence {
		return SublimeDatePicker::class.java.getName()
	}

	fun setValidationCallback(callback: DatePickerValidationCallback) {
		mValidationCallback = callback
	}

	protected fun onValidationChanged(valid: Boolean) {
		if (mValidationCallback != null) {
			mValidationCallback!!.onDatePickerValidationChanged(valid)
		}
	}

	/**
	 * A callback interface for updating input validity when the date picker
	 * when included into a dialog.
	 */
	interface DatePickerValidationCallback {
		fun onDatePickerValidationChanged(valid: Boolean)
	}

	/**
	 * Class for managing state storing/restoring.
	 */
	private class SavedState : View.BaseSavedState {

		val selectedYearStart: Int
		val selectedMonthStart: Int
		val selectedDayStart: Int
		val selectedYearEnd: Int
		val selectedMonthEnd: Int
		val selectedDayEnd: Int
		val minDate: Long
		val maxDate: Long
		val currentView: Int
		val listPosition: Int
		val listPositionOffset: Int
		val currentlyActivatedRangeItem: Int

		/**
		 * Constructor called from [SublimeDatePicker.onSaveInstanceState]
		 */
		private constructor(superState: Parcelable, selectedDate: SelectedDate,
		                    minDate: Long, maxDate: Long, currentView: Int, listPosition: Int,
		                    listPositionOffset: Int, currentlyActivatedRangeItem: Int) : super(superState) {
			selectedYearStart = selectedDate.startDate.get(Calendar.YEAR)
			selectedMonthStart = selectedDate.startDate.get(Calendar.MONTH)
			selectedDayStart = selectedDate.startDate.get(Calendar.DAY_OF_MONTH)
			selectedYearEnd = selectedDate.endDate.get(Calendar.YEAR)
			selectedMonthEnd = selectedDate.endDate.get(Calendar.MONTH)
			selectedDayEnd = selectedDate.endDate.get(Calendar.DAY_OF_MONTH)
			this.minDate = minDate
			this.maxDate = maxDate
			this.currentView = currentView
			this.listPosition = listPosition
			this.listPositionOffset = listPositionOffset
			this.currentlyActivatedRangeItem = currentlyActivatedRangeItem
		}

		/**
		 * Constructor called from [.CREATOR]
		 */
		private constructor(`in`: Parcel) : super(`in`) {
			selectedYearStart = `in`.readInt()
			selectedMonthStart = `in`.readInt()
			selectedDayStart = `in`.readInt()
			selectedYearEnd = `in`.readInt()
			selectedMonthEnd = `in`.readInt()
			selectedDayEnd = `in`.readInt()
			minDate = `in`.readLong()
			maxDate = `in`.readLong()
			currentView = `in`.readInt()
			listPosition = `in`.readInt()
			listPositionOffset = `in`.readInt()
			currentlyActivatedRangeItem = `in`.readInt()
		}

		override fun writeToParcel(dest: Parcel, flags: Int) {
			super.writeToParcel(dest, flags)
			dest.writeInt(selectedYearStart)
			dest.writeInt(selectedMonthStart)
			dest.writeInt(selectedDayStart)
			dest.writeInt(selectedYearEnd)
			dest.writeInt(selectedMonthEnd)
			dest.writeInt(selectedDayEnd)
			dest.writeLong(minDate)
			dest.writeLong(maxDate)
			dest.writeInt(currentView)
			dest.writeInt(listPosition)
			dest.writeInt(listPositionOffset)
			dest.writeInt(currentlyActivatedRangeItem)
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

	/**
	 * The callback used to indicate the user changed the date.
	 */
	interface OnDateChangedListener {

		/**
		 * Called upon a date change.
		 *
		 * @param view         The view associated with this listener.
		 * @param selectedDate The date that was set.
		 */
		fun onDateChanged(view: SublimeDatePicker, selectedDate: SelectedDate)
	}

	companion object {
		private val TAG = SublimeDatePicker::class.java.getSimpleName()

		private val UNINITIALIZED = -1
		private val VIEW_MONTH_DAY = 0
		private val VIEW_YEAR = 1

		private val RANGE_ACTIVATED_NONE = 0
		private val RANGE_ACTIVATED_START = 1
		private val RANGE_ACTIVATED_END = 2

		private val DEFAULT_START_YEAR = 1900
		private val DEFAULT_END_YEAR = 2100
	}
}

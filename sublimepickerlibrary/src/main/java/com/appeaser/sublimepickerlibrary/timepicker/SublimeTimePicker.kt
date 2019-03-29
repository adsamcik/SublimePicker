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

package com.appeaser.sublimepickerlibrary.timepicker

import android.annotation.TargetApi
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.TypedArray
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.style.TtsSpan
import android.util.AttributeSet
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.CheckedTextView
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView

import com.appeaser.sublimepickerlibrary.R
import com.appeaser.sublimepickerlibrary.common.DateTimePatternHelper
import com.appeaser.sublimepickerlibrary.utilities.AccessibilityUtils
import com.appeaser.sublimepickerlibrary.utilities.SUtils

import java.text.DateFormatSymbols
import java.util.ArrayList
import java.util.Calendar
import java.util.Locale

class SublimeTimePicker : FrameLayout, RadialTimePickerView.OnValueSelectedListener {

	private var mContext: Context? = null
	private var mCurrentLocale: Locale? = null

	private var mHeaderView: View? = null
	private var mHourView: TextView? = null
	private var mMinuteView: TextView? = null
	private var mAmPmLayout: View? = null
	private var mAmLabel: CheckedTextView? = null
	private var mPmLabel: CheckedTextView? = null
	private var mRadialTimePickerView: RadialTimePickerView? = null
	private var mSeparatorView: TextView? = null

	private var mAmText: String? = null
	private var mPmText: String? = null

	private var mIsEnabled = true
	private var mAllowAutoAdvance: Boolean = false
	private var mInitialHourOfDay: Int = 0
	private var mInitialMinute: Int = 0
	private var mIs24HourView: Boolean = false
	private var mIsAmPmAtStart: Boolean = false

	// For hardware IME input.
	private var mPlaceholderText: Char = ' '
	private var mDoublePlaceholderText: String? = null
	private var mDeletedKeyFormat: String? = null
	private var mInKbMode: Boolean = false
	/**
	 * @return an array of typed times
	 */
	private var typedTimes = ArrayList<Int>()
	private var mLegalTimesTree: Node? = null
	private var mAmKeyCode: Int = 0
	private var mPmKeyCode: Int = 0

	// Accessibility strings.
	private var mSelectHours: String? = null
	private var mSelectMinutes: String? = null

	// Most recent time announcement values for accessibility.
	private var mLastAnnouncedText: CharSequence? = null
	private var mLastAnnouncedIsHour: Boolean = false

	private var mTempCalendar: Calendar? = null

	// Callbacks
	private var mOnTimeChangedListener: OnTimeChangedListener? = null
	private var mValidationCallback: TimePickerValidationCallback? = null

	/**
	 * @return The current hour in the range (0-23).
	 */
	/**
	 * Set the current hour.
	 */
	var currentHour: Int
		get() {
			val currentHour = mRadialTimePickerView!!.currentHour
			return if (mIs24HourView) {
				currentHour
			} else {
				when (mRadialTimePickerView!!.amOrPm) {
					PM -> currentHour % HOURS_IN_HALF_DAY + HOURS_IN_HALF_DAY
					AM -> currentHour % HOURS_IN_HALF_DAY
					else -> currentHour % HOURS_IN_HALF_DAY
				}
			}
		}
		set(currentHour) {
			if (mInitialHourOfDay == currentHour) {
				return
			}
			mInitialHourOfDay = currentHour
			updateHeaderHour(currentHour, true)
			updateHeaderAmPm()
			mRadialTimePickerView!!.currentHour = currentHour
			mRadialTimePickerView!!.amOrPm = if (mInitialHourOfDay < 12) AM else PM
			invalidate()
			onTimeChanged()
		}

	/**
	 * @return The current minute.
	 */
	/**
	 * Set the current minute (0-59).
	 */
	var currentMinute: Int
		get() = mRadialTimePickerView!!.currentMinute
		set(currentMinute) {
			if (mInitialMinute == currentMinute) {
				return
			}
			mInitialMinute = currentMinute
			updateHeaderMinute(currentMinute, true)
			mRadialTimePickerView!!.currentMinute = currentMinute
			invalidate()
			onTimeChanged()
		}

	/**
	 * @return true if this is in 24 hour view else false.
	 */
	/**
	 * Set whether in 24 hour or AM/PM mode.
	 *
	 * @param is24HourView True = 24 hour mode. False = AM/PM.
	 */
	var is24HourView: Boolean
		get() = mIs24HourView
		set(is24HourView) {
			if (is24HourView == mIs24HourView) {
				return
			}
			mIs24HourView = is24HourView
			generateLegalTimesTree()
			val hour = mRadialTimePickerView!!.currentHour
			mInitialHourOfDay = hour
			updateHeaderHour(hour, false)
			updateHeaderAmPm()
			updateRadialPicker(mRadialTimePickerView!!.currentItemShowing)
			invalidate()
		}

	/**
	 * @return the index of the current item showing
	 */
	private val currentItemShowing: Int
		get() = mRadialTimePickerView!!.currentItemShowing

	/**
	 * Traverse the tree to see if the keys that have been typed so far are legal as is,
	 * or may become legal as more keys are typed (excluding backspace).
	 */
	private val isTypedTimeLegalSoFar: Boolean
		get() {
			var node = mLegalTimesTree
			for (keyCode in typedTimes) {
				node = node!!.canReach(keyCode)
				if (node == null) {
					return false
				}
			}
			return true
		}

	/**
	 * Check if the time that has been typed so far is completely legal, as is.
	 */
	private// For 24-hour mode, the time is legal if the hours and minutes are each legal. Note:
	// getEnteredTime() will ONLY call isTypedTimeFullyLegal() when NOT in 24hour mode.
	// For AM/PM mode, the time is legal if it contains an AM or PM, as those can only be
	// legally added at specific times based on the tree's algorithm.
	val isTypedTimeFullyLegal: Boolean
		get() {
			if (mIs24HourView) {
				val values = getEnteredTime(null)
				return values[0] >= 0 && values[1] >= 0 && values[1] < 60
			} else {
				return typedTimes.contains(getAmOrPmKeyCode(AM)) || typedTimes.contains(getAmOrPmKeyCode(PM))
			}
		}

	private val mClickListener = OnClickListener { v ->
		if (v.id == R.id.am_label) {
			setAmOrPm(AM)
		} else if (v.id == R.id.pm_label) {
			setAmOrPm(PM)
		} else if (v.id == R.id.hours) {
			setCurrentItemShowing(HOUR_INDEX, true, true)
		} else if (v.id == R.id.minutes) {
			setCurrentItemShowing(MINUTE_INDEX, true, true)
		} else {
			// Failed to handle this click, don't vibrate.
			return@OnClickListener
		}

		SUtils.vibrateForTimePicker(this@SublimeTimePicker)
	}

	private val mKeyListener = OnKeyListener { v, keyCode, event -> event.action == KeyEvent.ACTION_UP && processKeyUp(keyCode) }

	private val mFocusListener = OnFocusChangeListener { v, hasFocus ->
		if (!hasFocus && mInKbMode && isTypedTimeFullyLegal) {
			finishKbMode()

			if (mOnTimeChangedListener != null) {
				mOnTimeChangedListener!!.onTimeChanged(this@SublimeTimePicker,
						mRadialTimePickerView!!.currentHour,
						mRadialTimePickerView!!.currentMinute)
			}
		}
	}

	@JvmOverloads
	constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.spTimePickerStyle) : super(SUtils.createThemeWrapper(context, R.attr.sublimePickerStyle,
			R.style.SublimePickerStyleLight, R.attr.spTimePickerStyle,
			R.style.SublimeTimePickerStyle), attrs, defStyleAttr) {
		initializeLayout()
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(SUtils.createThemeWrapper(context, R.attr.sublimePickerStyle,
			R.style.SublimePickerStyleLight, R.attr.spTimePickerStyle,
			R.style.SublimeTimePickerStyle), attrs, defStyleAttr, defStyleRes) {
		initializeLayout()
	}

	private fun initializeLayout() {
		mContext = context
		setCurrentLocale(Locale.getDefault())

		// process style attributes
		val a = mContext!!.obtainStyledAttributes(R.styleable.SublimeTimePicker)
		val inflater = mContext!!.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
		val res = mContext!!.resources

		mSelectHours = res.getString(R.string.select_hours)
		mSelectMinutes = res.getString(R.string.select_minutes)

		val dfs = DateFormatSymbols.getInstance(mCurrentLocale)
		val amPmStrings = dfs.amPmStrings/*{"AM", "PM"}*/

		if (amPmStrings.size == 2
				&& !TextUtils.isEmpty(amPmStrings[0]) && !TextUtils.isEmpty(amPmStrings[1])) {
			mAmText = if (amPmStrings[0].length > 2)
				amPmStrings[0].substring(0, 2)
			else
				amPmStrings[0]
			mPmText = if (amPmStrings[1].length > 2)
				amPmStrings[1].substring(0, 2)
			else
				amPmStrings[1]
		} else {
			// Defaults
			mAmText = "AM"
			mPmText = "PM"
		}

		val layoutResourceId = R.layout.time_picker_layout
		val mainView = inflater.inflate(layoutResourceId, this)

		mHeaderView = mainView.findViewById(R.id.time_header)

		// Set up hour/minute labels.
		mHourView = mainView.findViewById<View>(R.id.hours) as TextView
		mHourView!!.setOnClickListener(mClickListener)

		ViewCompat.setAccessibilityDelegate(mHourView, ClickActionDelegate(mContext!!, R.string.select_hours))

		mSeparatorView = mainView.findViewById<View>(R.id.separator) as TextView

		mMinuteView = mainView.findViewById<View>(R.id.minutes) as TextView
		mMinuteView!!.setOnClickListener(mClickListener)

		ViewCompat.setAccessibilityDelegate(mMinuteView, ClickActionDelegate(mContext!!, R.string.select_minutes))

		// Now that we have text appearances out of the way, make sure the hour
		// and minute views are correctly sized.
		mHourView!!.minWidth = computeStableWidth(mHourView, 24)
		mMinuteView!!.minWidth = computeStableWidth(mMinuteView, 60)

		// Set up AM/PM labels.
		mAmPmLayout = mainView.findViewById(R.id.ampm_layout)
		mAmLabel = mAmPmLayout!!.findViewById<View>(R.id.am_label) as CheckedTextView
		mAmLabel!!.text = obtainVerbatim(amPmStrings[0])
		mAmLabel!!.setOnClickListener(mClickListener)
		mPmLabel = mAmPmLayout!!.findViewById<View>(R.id.pm_label) as CheckedTextView
		mPmLabel!!.text = obtainVerbatim(amPmStrings[1])
		mPmLabel!!.setOnClickListener(mClickListener)

		val headerTextColor = a.getColorStateList(R.styleable.SublimeTimePicker_spHeaderTextColor)

		if (headerTextColor != null) {
			mHourView!!.setTextColor(headerTextColor)
			mSeparatorView!!.setTextColor(headerTextColor)
			mMinuteView!!.setTextColor(headerTextColor)
			mAmLabel!!.setTextColor(headerTextColor)
			mPmLabel!!.setTextColor(headerTextColor)
		}

		// Set up header background, if available.
		if (SUtils.isApi_22_OrHigher) {
			if (a.hasValueOrEmpty(R.styleable.SublimeTimePicker_spHeaderBackground)) {
				SUtils.setViewBackground(mHeaderView!!,
						a.getDrawable(R.styleable.SublimeTimePicker_spHeaderBackground))
			}
		} else {
			if (a.hasValue(R.styleable.SublimeTimePicker_spHeaderBackground)) {
				SUtils.setViewBackground(mHeaderView!!,
						a.getDrawable(R.styleable.SublimeTimePicker_spHeaderBackground))
			}
		}

		a.recycle()

		mRadialTimePickerView = mainView.findViewById<View>(R.id.radial_picker) as RadialTimePickerView

		setupListeners()

		mAllowAutoAdvance = true

		// Set up for keyboard mode.
		mDoublePlaceholderText = res.getString(R.string.time_placeholder)
		mDeletedKeyFormat = res.getString(R.string.deleted_key)
		mPlaceholderText = mDoublePlaceholderText!![0]
		mPmKeyCode = -1
		mAmKeyCode = mPmKeyCode
		generateLegalTimesTree()

		// Initialize with current time
		val calendar = Calendar.getInstance(mCurrentLocale)
		val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
		val currentMinute = calendar.get(Calendar.MINUTE)
		initialize(currentHour, currentMinute, false /* 12h */, HOUR_INDEX)
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private fun obtainVerbatim(text: String): CharSequence {
		return if (SUtils.isApi_21_OrHigher)
			SpannableStringBuilder().append(text,
					TtsSpan.VerbatimBuilder(text).build(), 0)
		else
			text
	}

	private class ClickActionDelegate(context: Context, resId: Int) : AccessibilityDelegateCompat() {
		private val mClickAction: AccessibilityNodeInfoCompat.AccessibilityActionCompat

		init {
			val label = context.getString(resId)
			mClickAction = AccessibilityNodeInfoCompat.AccessibilityActionCompat(AccessibilityNodeInfoCompat.ACTION_CLICK,
					label)
		}

		override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
			super.onInitializeAccessibilityNodeInfo(host, info)
			info.addAction(mClickAction)
		}
	}

	private fun computeStableWidth(v: TextView, maxNumber: Int): Int {
		var maxWidth = 0

		for (i in 0 until maxNumber) {
			val text = String.format("%02d", i)
			v.setText(text)
			v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

			val width = v.measuredWidth
			if (width > maxWidth) {
				maxWidth = width
			}
		}

		return maxWidth
	}

	private fun initialize(hourOfDay: Int, minute: Int, is24HourView: Boolean, index: Int) {
		mInitialHourOfDay = hourOfDay
		mInitialMinute = minute
		mIs24HourView = is24HourView
		mInKbMode = false
		updateUI(index)
	}

	private fun setupListeners() {
		mHeaderView!!.setOnKeyListener(mKeyListener)
		mHeaderView!!.onFocusChangeListener = mFocusListener
		mHeaderView!!.isFocusable = true

		mRadialTimePickerView!!.setOnValueSelectedListener(this)
	}

	private fun updateUI(index: Int) {
		// Update RadialPicker values
		updateRadialPicker(index)
		// Enable or disable the AM/PM view.
		updateHeaderAmPm()
		// Update Hour and Minutes
		updateHeaderHour(mInitialHourOfDay, false)
		// Update time separator
		updateHeaderSeparator()
		// Update Minutes
		updateHeaderMinute(mInitialMinute, false)
		// Invalidate everything
		invalidate()
	}

	private fun updateRadialPicker(index: Int) {
		mRadialTimePickerView!!.initialize(mInitialHourOfDay, mInitialMinute, mIs24HourView)
		setCurrentItemShowing(index, false, true)
	}

	private fun updateHeaderAmPm() {
		if (mIs24HourView) {
			mAmPmLayout!!.visibility = View.GONE
		} else {
			// Ensure that AM/PM layout is in the correct position.
			val timePattern: String

			// Available on API >= 18
			if (SUtils.isApi_18_OrHigher) {
				timePattern = DateFormat.getBestDateTimePattern(mCurrentLocale, "hm")
			} else {
				timePattern = DateTimePatternHelper.getBestDateTimePattern(mCurrentLocale!!,
						DateTimePatternHelper.PATTERN_hm)
			}

			val isAmPmAtStart = timePattern.startsWith("a")
			setAmPmAtStart(isAmPmAtStart)

			updateAmPmLabelStates(if (mInitialHourOfDay < 12) AM else PM)
		}
	}

	private fun setAmPmAtStart(isAmPmAtStart: Boolean) {
		if (mIsAmPmAtStart != isAmPmAtStart) {
			mIsAmPmAtStart = isAmPmAtStart

			val params = mAmPmLayout!!.layoutParams as RelativeLayout.LayoutParams
			val rules = params.rules

			if (rules[RelativeLayout.RIGHT_OF] != 0 || rules[RelativeLayout.LEFT_OF] != 0) {
				if (isAmPmAtStart) {
					params.addRule(RelativeLayout.RIGHT_OF, 0)
					params.addRule(RelativeLayout.LEFT_OF, mHourView!!.id)
				} else {
					params.addRule(RelativeLayout.LEFT_OF, 0)
					params.addRule(RelativeLayout.RIGHT_OF, mMinuteView!!.id)
				}
			}

			mAmPmLayout!!.layoutParams = params
		}
	}

	fun setOnTimeChangedListener(callback: OnTimeChangedListener) {
		mOnTimeChangedListener = callback
	}

	override fun setEnabled(enabled: Boolean) {
		mHourView!!.isEnabled = enabled
		mMinuteView!!.isEnabled = enabled
		mAmLabel!!.isEnabled = enabled
		mPmLabel!!.isEnabled = enabled
		mRadialTimePickerView!!.isEnabled = enabled
		mIsEnabled = enabled
	}

	override fun isEnabled(): Boolean {
		return mIsEnabled
	}

	override fun getBaseline(): Int {
		// does not support baseline alignment
		return -1
	}

	public override fun onConfigurationChanged(newConfig: Configuration) {
		updateUI(mRadialTimePickerView!!.currentItemShowing)
	}

	public override fun onSaveInstanceState(): Parcelable? {
		return SavedState(super.onSaveInstanceState(), currentHour, currentMinute,
				is24HourView, inKbMode(), typedTimes, currentItemShowing)
	}

	public override fun onRestoreInstanceState(state: Parcelable) {
		val bss = state as View.BaseSavedState
		super.onRestoreInstanceState(bss.superState)
		val ss = bss as SavedState
		setInKbMode(ss.inKbMode())
		typedTimes = ss.typesTimes
		initialize(ss.hour, ss.minute, ss.is24HourMode, ss.currentItemShowing)
		mRadialTimePickerView!!.invalidate()
		if (mInKbMode) {
			tryStartingKbMode(-1)
			mHourView!!.invalidate()
		}
	}

	override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
		onPopulateAccessibilityEvent(event)
		return true
	}

	override fun onPopulateAccessibilityEvent(event: AccessibilityEvent) {
		super.onPopulateAccessibilityEvent(event)
		var flags = DateUtils.FORMAT_SHOW_TIME

		// The deprecation status does not show up in the documentation and
		// source code does not outline the alternative.
		// Leaving this as is for now.
		if (mIs24HourView) {

			flags = flags or DateUtils.FORMAT_24HOUR
		} else {

			flags = flags or DateUtils.FORMAT_12HOUR
		}
		mTempCalendar!!.set(Calendar.HOUR_OF_DAY, currentHour)
		mTempCalendar!!.set(Calendar.MINUTE, currentMinute)
		val selectedDate = DateUtils.formatDateTime(mContext,
				mTempCalendar!!.timeInMillis, flags)
		event.text.add(selectedDate)
	}

	/**
	 * Set whether in keyboard mode or not.
	 *
	 * @param inKbMode True means in keyboard mode.
	 */
	private fun setInKbMode(inKbMode: Boolean) {
		mInKbMode = inKbMode
	}

	/**
	 * @return true if in keyboard mode
	 */
	private fun inKbMode(): Boolean {
		return mInKbMode
	}

	/**
	 * Propagate the time change
	 */
	private fun onTimeChanged() {
		sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
		if (mOnTimeChangedListener != null) {
			mOnTimeChangedListener!!.onTimeChanged(this,
					currentHour, currentMinute)
		}
	}

	/**
	 * Used to save / restore state of time picker
	 */
	private class SavedState : View.BaseSavedState {

		val hour: Int
		val minute: Int
		val is24HourMode: Boolean
		private val mInKbMode: Boolean
		val typesTimes: ArrayList<Int>
		val currentItemShowing: Int

		private constructor(superState: Parcelable, hour: Int, minute: Int, is24HourMode: Boolean,
		                    isKbMode: Boolean, typedTimes: ArrayList<Int>,
		                    currentItemShowing: Int) : super(superState) {
			this.hour = hour
			this.minute = minute
			this.is24HourMode = is24HourMode
			mInKbMode = isKbMode
			typesTimes = typedTimes
			this.currentItemShowing = currentItemShowing
		}

		private constructor(`in`: Parcel) : super(`in`) {
			hour = `in`.readInt()
			minute = `in`.readInt()
			is24HourMode = `in`.readInt() == 1
			mInKbMode = `in`.readInt() == 1

			typesTimes = `in`.readArrayList(javaClass.getClassLoader())
			currentItemShowing = `in`.readInt()
		}

		fun inKbMode(): Boolean {
			return mInKbMode
		}

		override fun writeToParcel(dest: Parcel, flags: Int) {
			super.writeToParcel(dest, flags)
			dest.writeInt(hour)
			dest.writeInt(minute)
			dest.writeInt(if (is24HourMode) 1 else 0)
			dest.writeInt(if (mInKbMode) 1 else 0)
			dest.writeList(typesTimes)
			dest.writeInt(currentItemShowing)
		}

		companion object {

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

	private fun updateAmPmLabelStates(amOrPm: Int) {
		val isAm = amOrPm == AM
		mAmLabel!!.isActivated = isAm
		mAmLabel!!.isChecked = isAm

		val isPm = amOrPm == PM
		mPmLabel!!.isActivated = isPm
		mPmLabel!!.isChecked = isPm
	}

	/**
	 * Called by the picker for updating the header display.
	 */
	override fun onValueSelected(pickerIndex: Int, newValue: Int, autoAdvance: Boolean) {
		when (pickerIndex) {
			HOUR_INDEX -> if (mAllowAutoAdvance && autoAdvance) {
				updateHeaderHour(newValue, false)
				setCurrentItemShowing(MINUTE_INDEX, true, false)
				AccessibilityUtils.makeAnnouncement(this, "$newValue. $mSelectMinutes")
			} else {
				updateHeaderHour(newValue, true)
			}
			MINUTE_INDEX -> updateHeaderMinute(newValue, true)
			AMPM_INDEX -> updateAmPmLabelStates(newValue)
			ENABLE_PICKER_INDEX -> {
				if (!isTypedTimeFullyLegal) {
					typedTimes.clear()
				}
				finishKbMode()
			}
		}

		if (mOnTimeChangedListener != null) {
			mOnTimeChangedListener!!.onTimeChanged(this, currentHour, currentMinute)
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
	private fun updateHeaderHour(value: Int, announce: Boolean) {
		var value = value
		val timePattern: String

		if (SUtils.isApi_18_OrHigher) {
			timePattern = DateFormat.getBestDateTimePattern(mCurrentLocale,
					if (mIs24HourView) "Hm" else "hm")
		} else {
			timePattern = DateTimePatternHelper.getBestDateTimePattern(mCurrentLocale!!,
					if (mIs24HourView)
						DateTimePatternHelper.PATTERN_Hm
					else
						DateTimePatternHelper.PATTERN_hm)
		}

		val lengthPattern = timePattern.length
		var hourWithTwoDigit = false
		var hourFormat = '\u0000'
		// Check if the returned pattern is single or double 'H', 'h', 'K', 'k'. We also save
		// the hour format that we found.
		for (i in 0 until lengthPattern) {
			val c = timePattern[i]
			if (c == 'H' || c == 'h' || c == 'K' || c == 'k') {
				hourFormat = c
				if (i + 1 < lengthPattern && c == timePattern[i + 1]) {
					hourWithTwoDigit = true
				}
				break
			}
		}
		val format: String
		if (hourWithTwoDigit) {
			format = "%02d"
		} else {
			format = "%d"
		}
		if (mIs24HourView) {
			// 'k' means 1-24 hour
			if (hourFormat == 'k' && value == 0) {
				value = 24
			}
		} else {
			// 'K' means 0-11 hour
			value = modulo12(value, hourFormat == 'K')
		}
		val text = String.format(format, value)
		mHourView!!.setText(text)
		if (announce) {
			tryAnnounceForAccessibility(text, true)
		}
	}

	private fun tryAnnounceForAccessibility(text: CharSequence, isHour: Boolean) {
		if (mLastAnnouncedIsHour != isHour || text != mLastAnnouncedText) {
			// TODO: Find a better solution, potentially live regions?
			AccessibilityUtils.makeAnnouncement(this, text)
			mLastAnnouncedText = text
			mLastAnnouncedIsHour = isHour
		}
	}

	/**
	 * The time separator is defined in the Unicode CLDR and cannot be supposed to be ":".
	 *
	 *
	 * See http://unicode.org/cldr/trac/browser/trunk/common/main
	 *
	 *
	 * We pass the correct "skeleton" depending on 12 or 24 hours view and then extract the
	 * separator as the character which is just after the hour marker in the returned pattern.
	 */
	private fun updateHeaderSeparator() {
		val timePattern: String

		// Available on API >= 18
		if (SUtils.isApi_18_OrHigher) {
			timePattern = DateFormat.getBestDateTimePattern(mCurrentLocale,
					if (mIs24HourView) "Hm" else "hm")
		} else {
			timePattern = DateTimePatternHelper.getBestDateTimePattern(mCurrentLocale!!,
					if (mIs24HourView)
						DateTimePatternHelper.PATTERN_Hm
					else
						DateTimePatternHelper.PATTERN_hm)
		}

		val separatorText: String
		// See http://www.unicode.org/reports/tr35/tr35-dates.html for hour formats
		val hourFormats = charArrayOf('H', 'h', 'K', 'k')
		val hIndex = lastIndexOfAny(timePattern, hourFormats)
		if (hIndex == -1) {
			// Default case
			separatorText = ":"
		} else {
			separatorText = Character.toString(timePattern[hIndex + 1])
		}
		mSeparatorView!!.text = separatorText
	}

	private fun updateHeaderMinute(value: Int, announceForAccessibility: Boolean) {
		var value = value
		if (value == 60) {
			value = 0
		}
		val text = String.format(mCurrentLocale, "%02d", value)
		mMinuteView!!.setText(text)
		if (announceForAccessibility) {
			tryAnnounceForAccessibility(text, false)
		}
	}

	/**
	 * Show either Hours or Minutes.
	 */
	private fun setCurrentItemShowing(index: Int, animateCircle: Boolean, announce: Boolean) {
		mRadialTimePickerView!!.setCurrentItemShowing(index, animateCircle)

		if (index == HOUR_INDEX) {
			if (announce) {
				AccessibilityUtils.makeAnnouncement(this, mSelectHours)
			}
		} else {
			if (announce) {
				AccessibilityUtils.makeAnnouncement(this, mSelectMinutes)
			}
		}

		mHourView!!.isActivated = index == HOUR_INDEX
		mMinuteView!!.isActivated = index == MINUTE_INDEX
	}

	private fun setAmOrPm(amOrPm: Int) {
		updateAmPmLabelStates(amOrPm)
		mRadialTimePickerView!!.amOrPm = amOrPm
	}

	/**
	 * For keyboard mode, processes key events.
	 *
	 * @param keyCode the pressed key.
	 * @return true if the key was successfully processed, false otherwise.
	 */
	private fun processKeyUp(keyCode: Int): Boolean {
		if (keyCode == KeyEvent.KEYCODE_DEL) {
			if (mInKbMode) {
				if (!typedTimes.isEmpty()) {
					val deleted = deleteLastTypedKey()
					val deletedKeyStr: String?
					if (deleted == getAmOrPmKeyCode(AM)) {
						deletedKeyStr = mAmText
					} else if (deleted == getAmOrPmKeyCode(PM)) {
						deletedKeyStr = mPmText
					} else {
						deletedKeyStr = String.format("%d", getValFromKeyCode(deleted))
					}

					AccessibilityUtils.makeAnnouncement(this, String.format(mDeletedKeyFormat!!, deletedKeyStr))
					updateDisplay(true)
				}
			}
		} else if (keyCode == KeyEvent.KEYCODE_0 || keyCode == KeyEvent.KEYCODE_1
				|| keyCode == KeyEvent.KEYCODE_2 || keyCode == KeyEvent.KEYCODE_3
				|| keyCode == KeyEvent.KEYCODE_4 || keyCode == KeyEvent.KEYCODE_5
				|| keyCode == KeyEvent.KEYCODE_6 || keyCode == KeyEvent.KEYCODE_7
				|| keyCode == KeyEvent.KEYCODE_8 || keyCode == KeyEvent.KEYCODE_9
				|| !mIs24HourView && (keyCode == getAmOrPmKeyCode(AM) || keyCode == getAmOrPmKeyCode(PM))) {
			if (!mInKbMode) {
				if (mRadialTimePickerView == null) {
					// Something is wrong, because time picker should definitely not be null.
					Log.e(TAG, "Unable to initiate keyboard mode, TimePicker was null.")
					return true
				}
				typedTimes.clear()
				tryStartingKbMode(keyCode)
				return true
			}
			// We're already in keyboard mode.
			if (addKeyIfLegal(keyCode)) {
				updateDisplay(false)
			}
			return true
		}
		return false
	}

	/**
	 * Try to start keyboard mode with the specified key.
	 *
	 * @param keyCode The key to use as the first press. Keyboard mode will not be started if the
	 * key is not legal to start with. Or, pass in -1 to get into keyboard mode without a starting
	 * key.
	 */
	private fun tryStartingKbMode(keyCode: Int) {
		if (keyCode == -1 || addKeyIfLegal(keyCode)) {
			mInKbMode = true
			onValidationChanged(false)
			updateDisplay(false)
			mRadialTimePickerView!!.setInputEnabled(false)
		}
	}

	private fun addKeyIfLegal(keyCode: Int): Boolean {
		// If we're in 24hour mode, we'll need to check if the input is full. If in AM/PM mode,
		// we'll need to see if AM/PM have been typed.
		if (mIs24HourView && typedTimes.size == 4 || !mIs24HourView && isTypedTimeFullyLegal) {
			return false
		}

		typedTimes.add(keyCode)
		if (!isTypedTimeLegalSoFar) {
			deleteLastTypedKey()
			return false
		}

		val `val` = getValFromKeyCode(keyCode)
		AccessibilityUtils.makeAnnouncement(this, String.format("%d", `val`))

		// Automatically fill in 0's if AM or PM was legally entered.
		if (isTypedTimeFullyLegal) {
			if (!mIs24HourView && typedTimes.size <= 3) {
				typedTimes.add(typedTimes.size - 1, KeyEvent.KEYCODE_0)
				typedTimes.add(typedTimes.size - 1, KeyEvent.KEYCODE_0)
			}
			onValidationChanged(true)
		}

		return true
	}

	private fun deleteLastTypedKey(): Int {
		val deleted = typedTimes.removeAt(typedTimes.size - 1)
		if (!isTypedTimeFullyLegal) {
			onValidationChanged(false)
		}
		return deleted
	}

	/**
	 * Get out of keyboard mode. If there is nothing in typedTimes, revert to TimePicker's time.
	 */
	private fun finishKbMode() {
		mInKbMode = false
		if (!typedTimes.isEmpty()) {
			val values = getEnteredTime(null)
			mRadialTimePickerView!!.currentHour = values[0]
			mRadialTimePickerView!!.currentMinute = values[1]
			if (!mIs24HourView) {
				mRadialTimePickerView!!.amOrPm = values[2]
			}
			typedTimes.clear()
		}
		updateDisplay(false)
		mRadialTimePickerView!!.setInputEnabled(true)
	}

	/**
	 * Update the hours, minutes, and AM/PM displays with the typed times. If the typedTimes is
	 * empty, either show an empty display (filled with the placeholder text), or update from
	 * timepicker's values.
	 *
	 * @param allowEmptyDisplay if true, then if the typedTimes is empty, use the placeholder text.
	 * Otherwise, revert to the timepicker's values.
	 */
	private fun updateDisplay(allowEmptyDisplay: Boolean) {
		if (!allowEmptyDisplay && typedTimes.isEmpty()) {
			val hour = mRadialTimePickerView!!.currentHour
			val minute = mRadialTimePickerView!!.currentMinute
			updateHeaderHour(hour, false)
			updateHeaderMinute(minute, false)
			if (!mIs24HourView) {
				updateAmPmLabelStates(if (hour < 12) AM else PM)
			}
			setCurrentItemShowing(mRadialTimePickerView!!.currentItemShowing, true, true)
			onValidationChanged(true)
		} else {
			val enteredZeros = booleanArrayOf(false, false)
			val values = getEnteredTime(enteredZeros)
			val hourFormat = if (enteredZeros[0]) "%02d" else "%2d"
			val minuteFormat = if (enteredZeros[1]) "%02d" else "%2d"
			val hourStr = if (values[0] == -1)
				mDoublePlaceholderText
			else
				String.format(hourFormat, values[0]).replace(' ', mPlaceholderText)
			val minuteStr = if (values[1] == -1)
				mDoublePlaceholderText
			else
				String.format(minuteFormat, values[1]).replace(' ', mPlaceholderText)
			mHourView!!.text = hourStr
			mHourView!!.isActivated = false
			mMinuteView!!.text = minuteStr
			mMinuteView!!.isActivated = false
			if (!mIs24HourView) {
				updateAmPmLabelStates(values[2])
			}
		}
	}

	fun setValidationCallback(callback: TimePickerValidationCallback) {
		mValidationCallback = callback
	}

	protected fun onValidationChanged(valid: Boolean) {
		if (mValidationCallback != null) {
			mValidationCallback!!.onTimePickerValidationChanged(valid)
		}
	}

	fun setCurrentLocale(locale: Locale) {
		if (locale == mCurrentLocale) {
			return
		}
		mCurrentLocale = locale

		mTempCalendar = Calendar.getInstance(locale)
	}

	private fun getValFromKeyCode(keyCode: Int): Int {
		when (keyCode) {
			KeyEvent.KEYCODE_0 -> return 0
			KeyEvent.KEYCODE_1 -> return 1
			KeyEvent.KEYCODE_2 -> return 2
			KeyEvent.KEYCODE_3 -> return 3
			KeyEvent.KEYCODE_4 -> return 4
			KeyEvent.KEYCODE_5 -> return 5
			KeyEvent.KEYCODE_6 -> return 6
			KeyEvent.KEYCODE_7 -> return 7
			KeyEvent.KEYCODE_8 -> return 8
			KeyEvent.KEYCODE_9 -> return 9
			else -> return -1
		}
	}

	/**
	 * Get the currently-entered time, as integer values of the hours and minutes typed.
	 *
	 * @param enteredZeros A size-2 boolean array, which the caller should initialize, and which
	 * may then be used for the caller to know whether zeros had been explicitly entered as either
	 * hours of minutes. This is helpful for deciding whether to show the dashes, or actual 0's.
	 * @return A size-3 int array. The first value will be the hours, the second value will be the
	 * minutes, and the third will be either AM or PM.
	 */
	private fun getEnteredTime(enteredZeros: BooleanArray?): IntArray {
		var amOrPm = -1
		var startIndex = 1
		if (!mIs24HourView && isTypedTimeFullyLegal) {
			val keyCode = typedTimes[typedTimes.size - 1]
			if (keyCode == getAmOrPmKeyCode(AM)) {
				amOrPm = AM
			} else if (keyCode == getAmOrPmKeyCode(PM)) {
				amOrPm = PM
			}
			startIndex = 2
		}
		var minute = -1
		var hour = -1
		for (i in startIndex..typedTimes.size) {
			val `val` = getValFromKeyCode(typedTimes[typedTimes.size - i])
			if (i == startIndex) {
				minute = `val`
			} else if (i == startIndex + 1) {
				minute += 10 * `val`
				if (enteredZeros != null && `val` == 0) {
					enteredZeros[1] = true
				}
			} else if (i == startIndex + 2) {
				hour = `val`
			} else if (i == startIndex + 3) {
				hour += 10 * `val`
				if (enteredZeros != null && `val` == 0) {
					enteredZeros[0] = true
				}
			}
		}

		return intArrayOf(hour, minute, amOrPm)
	}

	/**
	 * Get the keycode value for AM and PM in the current language.
	 */
	private fun getAmOrPmKeyCode(amOrPm: Int): Int {
		// Cache the codes.
		if (mAmKeyCode == -1 || mPmKeyCode == -1) {
			// Find the first character in the AM/PM text that is unique.
			val kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
			val amText = mAmText!!.toLowerCase(mCurrentLocale!!)
			val pmText = mPmText!!.toLowerCase(mCurrentLocale!!)
			val N = Math.min(amText.length, pmText.length)
			for (i in 0 until N) {
				val amChar = amText.get(i)
				val pmChar = pmText.get(i)
				if (amChar != pmChar) {
					// There should be 4 events: a down and up for both AM and PM.
					val events = kcm.getEvents(charArrayOf(amChar, pmChar))
					if (events != null && events.size == 4) {
						mAmKeyCode = events[0].keyCode
						mPmKeyCode = events[2].keyCode
					} else {
						Log.e(TAG, "Unable to find keycodes for AM and PM.")
					}
					break
				}
			}
		}

		if (amOrPm == AM) {
			return mAmKeyCode
		} else if (amOrPm == PM) {
			return mPmKeyCode
		}

		return -1
	}

	/**
	 * Create a tree for deciding what keys can legally be typed.
	 */
	private fun generateLegalTimesTree() {
		// Create a quick cache of numbers to their keycodes.
		val k0 = KeyEvent.KEYCODE_0
		val k1 = KeyEvent.KEYCODE_1
		val k2 = KeyEvent.KEYCODE_2
		val k3 = KeyEvent.KEYCODE_3
		val k4 = KeyEvent.KEYCODE_4
		val k5 = KeyEvent.KEYCODE_5
		val k6 = KeyEvent.KEYCODE_6
		val k7 = KeyEvent.KEYCODE_7
		val k8 = KeyEvent.KEYCODE_8
		val k9 = KeyEvent.KEYCODE_9

		// The root of the tree doesn't contain any numbers.
		mLegalTimesTree = Node()
		if (mIs24HourView) {
			// We'll be re-using these nodes, so we'll save them.
			val minuteFirstDigit = Node(k0, k1, k2, k3, k4, k5)
			val minuteSecondDigit = Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9)
			// The first digit must be followed by the second digit.
			minuteFirstDigit.addChild(minuteSecondDigit)

			// The first digit may be 0-1.
			var firstDigit = Node(k0, k1)
			mLegalTimesTree!!.addChild(firstDigit)

			// When the first digit is 0-1, the second digit may be 0-5.
			var secondDigit = Node(k0, k1, k2, k3, k4, k5)
			firstDigit.addChild(secondDigit)
			// We may now be followed by the first minute digit. E.g. 00:09, 15:58.
			secondDigit.addChild(minuteFirstDigit)

			// When the first digit is 0-1, and the second digit is 0-5, the third digit may be 6-9.
			val thirdDigit = Node(k6, k7, k8, k9)
			// The time must now be finished. E.g. 0:55, 1:08.
			secondDigit.addChild(thirdDigit)

			// When the first digit is 0-1, the second digit may be 6-9.
			secondDigit = Node(k6, k7, k8, k9)
			firstDigit.addChild(secondDigit)
			// We must now be followed by the first minute digit. E.g. 06:50, 18:20.
			secondDigit.addChild(minuteFirstDigit)

			// The first digit may be 2.
			firstDigit = Node(k2)
			mLegalTimesTree!!.addChild(firstDigit)

			// When the first digit is 2, the second digit may be 0-3.
			secondDigit = Node(k0, k1, k2, k3)
			firstDigit.addChild(secondDigit)
			// We must now be followed by the first minute digit. E.g. 20:50, 23:09.
			secondDigit.addChild(minuteFirstDigit)

			// When the first digit is 2, the second digit may be 4-5.
			secondDigit = Node(k4, k5)
			firstDigit.addChild(secondDigit)
			// We must now be followd by the last minute digit. E.g. 2:40, 2:53.
			secondDigit.addChild(minuteSecondDigit)

			// The first digit may be 3-9.
			firstDigit = Node(k3, k4, k5, k6, k7, k8, k9)
			mLegalTimesTree!!.addChild(firstDigit)
			// We must now be followed by the first minute digit. E.g. 3:57, 8:12.
			firstDigit.addChild(minuteFirstDigit)
		} else {
			// We'll need to use the AM/PM node a lot.
			// Set up AM and PM to respond to "a" and "p".
			val ampm = Node(getAmOrPmKeyCode(AM), getAmOrPmKeyCode(PM))

			// The first hour digit may be 1.
			var firstDigit = Node(k1)
			mLegalTimesTree!!.addChild(firstDigit)
			// We'll allow quick input of on-the-hour times. E.g. 1pm.
			firstDigit.addChild(ampm)

			// When the first digit is 1, the second digit may be 0-2.
			var secondDigit = Node(k0, k1, k2)
			firstDigit.addChild(secondDigit)
			// Also for quick input of on-the-hour times. E.g. 10pm, 12am.
			secondDigit.addChild(ampm)

			// When the first digit is 1, and the second digit is 0-2, the third digit may be 0-5.
			var thirdDigit = Node(k0, k1, k2, k3, k4, k5)
			secondDigit.addChild(thirdDigit)
			// The time may be finished now. E.g. 1:02pm, 1:25am.
			thirdDigit.addChild(ampm)

			// When the first digit is 1, the second digit is 0-2, and the third digit is 0-5,
			// the fourth digit may be 0-9.
			val fourthDigit = Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9)
			thirdDigit.addChild(fourthDigit)
			// The time must be finished now. E.g. 10:49am, 12:40pm.
			fourthDigit.addChild(ampm)

			// When the first digit is 1, and the second digit is 0-2, the third digit may be 6-9.
			thirdDigit = Node(k6, k7, k8, k9)
			secondDigit.addChild(thirdDigit)
			// The time must be finished now. E.g. 1:08am, 1:26pm.
			thirdDigit.addChild(ampm)

			// When the first digit is 1, the second digit may be 3-5.
			secondDigit = Node(k3, k4, k5)
			firstDigit.addChild(secondDigit)

			// When the first digit is 1, and the second digit is 3-5, the third digit may be 0-9.
			thirdDigit = Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9)
			secondDigit.addChild(thirdDigit)
			// The time must be finished now. E.g. 1:39am, 1:50pm.
			thirdDigit.addChild(ampm)

			// The hour digit may be 2-9.
			firstDigit = Node(k2, k3, k4, k5, k6, k7, k8, k9)
			mLegalTimesTree!!.addChild(firstDigit)
			// We'll allow quick input of on-the-hour-times. E.g. 2am, 5pm.
			firstDigit.addChild(ampm)

			// When the first digit is 2-9, the second digit may be 0-5.
			secondDigit = Node(k0, k1, k2, k3, k4, k5)
			firstDigit.addChild(secondDigit)

			// When the first digit is 2-9, and the second digit is 0-5, the third digit may be 0-9.
			thirdDigit = Node(k0, k1, k2, k3, k4, k5, k6, k7, k8, k9)
			secondDigit.addChild(thirdDigit)
			// The time must be finished now. E.g. 2:57am, 9:30pm.
			thirdDigit.addChild(ampm)
		}
	}

	/**
	 * Simple node class to be used for traversal to check for legal times.
	 * mLegalKeys represents the keys that can be typed to get to the node.
	 * mChildren are the children that can be reached from this node.
	 */
	private inner class Node(vararg legalKeys: Int) {
		private val mLegalKeys: IntArray
		private val mChildren: ArrayList<Node>?

		init {
			mLegalKeys = legalKeys
			mChildren = ArrayList()
		}

		fun addChild(child: Node) {
			mChildren!!.add(child)
		}

		fun containsKey(key: Int): Boolean {
			for (legalKey in mLegalKeys) {
				if (legalKey == key) {
					return true
				}
			}

			return false
		}

		fun canReach(key: Int): Node? {
			if (mChildren == null) {
				return null
			}
			for (child in mChildren) {
				if (child.containsKey(key)) {
					return child
				}
			}
			return null
		}
	}

	/**
	 * The callback interface used to indicate the time has been adjusted.
	 */
	interface OnTimeChangedListener {

		/**
		 * @param view      The view associated with this listener.
		 * @param hourOfDay The current hour.
		 * @param minute    The current minute.
		 */
		fun onTimeChanged(view: SublimeTimePicker, hourOfDay: Int, minute: Int)
	}

	/**
	 * A callback interface for updating input validity when the TimePicker
	 * when included into a Dialog.
	 */
	interface TimePickerValidationCallback {
		fun onTimePickerValidationChanged(valid: Boolean)
	}

	companion object {

		private val TAG = SublimeTimePicker::class.java.getSimpleName()

		// Index used by RadialPickerLayout
		private val HOUR_INDEX = 0
		private val MINUTE_INDEX = 1

		// NOT a real index for the purpose of what's showing.
		private val AMPM_INDEX = 2

		// Also NOT a real index, just used for keyboard mode.
		private val ENABLE_PICKER_INDEX = 3

		// LayoutLib relies on these constants. Change TimePickerClockDelegate_Delegate if
		// modifying these.
		private val AM = 0
		private val PM = 1

		private val HOURS_IN_HALF_DAY = 12

		private fun modulo12(n: Int, startWithZero: Boolean): Int {
			var value = n % 12
			if (value == 0 && !startWithZero) {
				value = 12
			}
			return value
		}

		private fun lastIndexOfAny(str: String, any: CharArray): Int {
			val lengthAny = any.size
			if (lengthAny > 0) {
				for (i in str.length - 1 downTo 0) {
					val c = str[i]
					for (anyChar in any) {
						if (c == anyChar) {
							return i
						}
					}
				}
			}
			return -1
		}
	}
}

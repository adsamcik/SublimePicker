/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import androidx.viewpager.widget.ViewPager
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.ImageButton

import com.appeaser.sublimepickerlibrary.R
import com.appeaser.sublimepickerlibrary.utilities.Config
import com.appeaser.sublimepickerlibrary.utilities.SUtils

import java.util.Calendar

/**
 * This displays a list of months in a calendar format with selectable days.
 */
internal class DayPickerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.spDayPickerStyle) : ViewGroup(SUtils.createThemeWrapper(context, R.attr.sublimePickerStyle,
		R.style.SublimePickerStyleLight, defStyleAttr,
		R.style.DayPickerViewStyle), attrs) {

	private var mSelectedDay: SelectedDate? = null
	private val mMinDate = Calendar.getInstance()
	private val mMaxDate = Calendar.getInstance()

	private val mAccessibilityManager: AccessibilityManager

	private val mViewPager: DayPickerViewPager
	private val mPrevButton: ImageButton
	private val mNextButton: ImageButton

	private val mAdapter: DayPickerPagerAdapter

	/**
	 * Temporary calendar used for date calculations.
	 */
	private var mTempCalendar: Calendar? = null

	private var mProxyDaySelectionEventListener: ProxyDaySelectionEventListener? = null

	var dayOfWeekTextAppearance: Int
		get() = mAdapter.dayOfWeekTextAppearance
		set(resId) {
			mAdapter.dayOfWeekTextAppearance = resId
		}

	var dayTextAppearance: Int
		get() = mAdapter.dayTextAppearance
		set(resId) {
			mAdapter.dayTextAppearance = resId
		}

	/**
	 * Sets the currently selected date to the specified timestamp. Jumps
	 * immediately to the new date. To animate to the new date, use
	 * [.setDate].
	 *
	 *
	 * //@param timeInMillis the target day in milliseconds
	 */
	var date: SelectedDate?
		get() = mSelectedDay
		set(date) = setDate(date, false)

	var firstDayOfWeek: Int
		get() = mAdapter.firstDayOfWeek
		set(firstDayOfWeek) {
			mAdapter.firstDayOfWeek = firstDayOfWeek
		}

	var minDate: Long
		get() = mMinDate.timeInMillis
		set(timeInMillis) {
			mMinDate.timeInMillis = timeInMillis
			onRangeChanged()
		}

	var maxDate: Long
		get() = mMaxDate.timeInMillis
		set(timeInMillis) {
			mMaxDate.timeInMillis = timeInMillis
			onRangeChanged()
		}

	/**
	 * Gets the position of the view that is most prominently displayed within the list view.
	 */
	val mostVisiblePosition: Int
		get() = mViewPager.currentItem

	init {
		var context = context

		context = getContext()

		mAccessibilityManager = context.getSystemService(
				Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

		val a = context.obtainStyledAttributes(attrs,
				R.styleable.DayPickerView, defStyleAttr, R.style.DayPickerViewStyle)

		val monthTextAppearanceResId = a.getResourceId(
				R.styleable.DayPickerView_spMonthTextAppearance,
				R.style.SPMonthLabelTextAppearance)
		// verified
		val dayOfWeekTextAppearanceResId = a.getResourceId(
				R.styleable.DayPickerView_spWeekDayTextAppearance,
				R.style.SPWeekDayLabelTextAppearance)
		// verified
		val dayTextAppearanceResId = a.getResourceId(
				R.styleable.DayPickerView_spDateTextAppearance,
				R.style.SPDayTextAppearance)

		val daySelectorColor = a.getColorStateList(
				R.styleable.DayPickerView_spDaySelectorColor)

		a.recycle()

		if (Config.DEBUG) {
			Log.i(TAG, "MDayPickerView_spmMonthTextAppearance: $monthTextAppearanceResId")
			Log.i(TAG, "MDayPickerView_spmWeekDayTextAppearance: $dayOfWeekTextAppearanceResId")
			Log.i(TAG, "MDayPickerView_spmDateTextAppearance: $dayTextAppearanceResId")
		}

		// Set up adapter.
		mAdapter = DayPickerPagerAdapter(context,
				R.layout.date_picker_month_item, R.id.month_view)
		mAdapter.setMonthTextAppearance(monthTextAppearanceResId)
		mAdapter.dayOfWeekTextAppearance = dayOfWeekTextAppearanceResId
		mAdapter.dayTextAppearance = dayTextAppearanceResId
		mAdapter.setDaySelectorColor(daySelectorColor)

		val inflater = LayoutInflater.from(context)

		val layoutIdToUse: Int
		val viewPagerIdToUse: Int

		if (tag != null && tag is String
				&& resources.getString(R.string.recurrence_end_date_picker_tag) == tag) {
			layoutIdToUse = R.layout.day_picker_content_redp
			viewPagerIdToUse = R.id.redp_view_pager
		} else {
			layoutIdToUse = R.layout.day_picker_content_sdp
			viewPagerIdToUse = R.id.sdp_view_pager
		}

		inflater.inflate(layoutIdToUse, this, true)

		val onClickListener = OnClickListener { v ->
			val direction: Int
			if (v === mPrevButton) {
				direction = -1
			} else if (v === mNextButton) {
				direction = 1
			} else {
				return@OnClickListener
			}

			// Animation is expensive for accessibility services since it sends
			// lots of scroll and content change events.
			val animate = !mAccessibilityManager.isEnabled

			// ViewPager clamps input values, so we don't need to worry
			// about passing invalid indices.
			val nextItem = mViewPager.currentItem + direction
			mViewPager.setCurrentItem(nextItem, animate)
		}

		mPrevButton = findViewById<View>(R.id.prev) as ImageButton
		mPrevButton.setOnClickListener(onClickListener)

		mNextButton = findViewById<View>(R.id.next) as ImageButton
		mNextButton.setOnClickListener(onClickListener)

		val onPageChangedListener = object : androidx.viewpager.widget.ViewPager.OnPageChangeListener {
			override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
				val alpha = Math.abs(0.5f - positionOffset) * 2.0f
				mPrevButton.alpha = alpha
				mNextButton.alpha = alpha
			}

			override fun onPageScrollStateChanged(state: Int) {}

			override fun onPageSelected(position: Int) {
				updateButtonVisibility(position)
			}
		}

		mViewPager = findViewById<View>(viewPagerIdToUse) as DayPickerViewPager
		mViewPager.adapter = mAdapter
		mViewPager.addOnPageChangeListener(onPageChangedListener)

		// Proxy the month text color into the previous and next buttons.
		if (monthTextAppearanceResId != 0) {
			val ta = context.obtainStyledAttributes(null,
					ATTRS_TEXT_COLOR, 0, monthTextAppearanceResId)
			val monthColor = ta.getColorStateList(0)
			if (monthColor != null) {
				SUtils.setImageTintList(mPrevButton, monthColor)
				SUtils.setImageTintList(mNextButton, monthColor)
			}
			ta.recycle()
		}

		// Proxy selection callbacks to our own listener.
		mAdapter.setDaySelectionEventListener(object : DayPickerPagerAdapter.DaySelectionEventListener {
			override fun onDaySelected(adapter: DayPickerPagerAdapter, day: Calendar?) {
				if (mProxyDaySelectionEventListener != null) {
					mProxyDaySelectionEventListener!!.onDaySelected(this@DayPickerView, day)
				}
			}

			override fun onDateRangeSelectionStarted(selectedDate: SelectedDate) {
				if (mProxyDaySelectionEventListener != null) {
					mProxyDaySelectionEventListener!!.onDateRangeSelectionStarted(selectedDate)
				}
			}

			override fun onDateRangeSelectionEnded(selectedDate: SelectedDate?) {
				if (mProxyDaySelectionEventListener != null) {
					mProxyDaySelectionEventListener!!.onDateRangeSelectionEnded(selectedDate)
				}
			}

			override fun onDateRangeSelectionUpdated(selectedDate: SelectedDate) {
				if (mProxyDaySelectionEventListener != null) {
					mProxyDaySelectionEventListener!!.onDateRangeSelectionUpdated(selectedDate)
				}
			}
		})
	}

	fun setCanPickRange(canPickRange: Boolean) {
		mViewPager.setCanPickRange(canPickRange)
	}

	private fun updateButtonVisibility(position: Int) {
		val hasPrev = position > 0
		val hasNext = position < mAdapter.count - 1
		mPrevButton.visibility = if (hasPrev) View.VISIBLE else View.INVISIBLE
		mNextButton.visibility = if (hasNext) View.VISIBLE else View.INVISIBLE
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val viewPager = mViewPager
		measureChild(viewPager, widthMeasureSpec, heightMeasureSpec)

		val measuredWidthAndState = viewPager.measuredWidthAndState
		val measuredHeightAndState = viewPager.measuredHeightAndState
		setMeasuredDimension(measuredWidthAndState, measuredHeightAndState)

		val pagerWidth = viewPager.measuredWidth
		val pagerHeight = viewPager.measuredHeight
		val buttonWidthSpec = View.MeasureSpec.makeMeasureSpec(pagerWidth, View.MeasureSpec.AT_MOST)
		val buttonHeightSpec = View.MeasureSpec.makeMeasureSpec(pagerHeight, View.MeasureSpec.AT_MOST)
		mPrevButton.measure(buttonWidthSpec, buttonHeightSpec)
		mNextButton.measure(buttonWidthSpec, buttonHeightSpec)
	}

	override fun onRtlPropertiesChanged(/*@ResolvedLayoutDir*/layoutDirection: Int) {
		super.onRtlPropertiesChanged(layoutDirection)

		requestLayout()
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		val leftButton: ImageButton
		val rightButton: ImageButton
		if (SUtils.isLayoutRtlCompat(this)) {
			leftButton = mNextButton
			rightButton = mPrevButton
		} else {
			leftButton = mPrevButton
			rightButton = mNextButton
		}

		val width = right - left
		val height = bottom - top
		mViewPager.layout(0, 0, width, height)

		val monthView = mViewPager.getChildAt(0)
				.findViewById<View>(R.id.month_view) as SimpleMonthView
		val monthHeight = monthView.monthHeight
		val cellWidth = monthView.cellWidth

		// Vertically center the previous/next buttons within the month
		// header, horizontally center within the day cell.
		val leftDW = leftButton.measuredWidth
		val leftDH = leftButton.measuredHeight
		val leftIconTop = monthView.paddingTop + (monthHeight - leftDH) / 2
		val leftIconLeft = monthView.paddingLeft + (cellWidth - leftDW) / 2
		leftButton.layout(leftIconLeft, leftIconTop, leftIconLeft + leftDW, leftIconTop + leftDH)

		val rightDW = rightButton.measuredWidth
		val rightDH = rightButton.measuredHeight
		val rightIconTop = monthView.paddingTop + (monthHeight - rightDH) / 2
		val rightIconRight = width - monthView.paddingRight - (cellWidth - rightDW) / 2
		rightButton.layout(rightIconRight - rightDW, rightIconTop,
				rightIconRight, rightIconTop + rightDH)
	}

	/**
	 * Sets the currently selected date to the specified timestamp. Jumps
	 * immediately to the new date, optionally animating the transition.
	 *
	 *
	 * //@param timeInMillis the target day in milliseconds
	 *
	 * @param animate whether to smooth scroll to the new position
	 */
	fun setDate(date: SelectedDate, animate: Boolean) {
		setDate(date, animate, true, true)
	}

	/**
	 * Sets the currently selected date to the specified timestamp. Jumps
	 * immediately to the new date, optionally animating the transition.
	 *
	 *
	 * //@param timeInMillis the target day in milliseconds
	 *
	 * @param animate whether to smooth scroll to the new position
	 */
	fun setDate(date: SelectedDate, animate: Boolean, goToPosition: Boolean) {
		setDate(date, animate, true, goToPosition)
	}

	/**
	 * Moves to the month containing the specified day, optionally setting the
	 * day as selected.
	 *
	 *
	 * //@param timeInMillis the target day in milliseconds
	 *
	 * @param animate     whether to smooth scroll to the new position
	 * @param setSelected whether to set the specified day as selected
	 */
	private fun setDate(date: SelectedDate?, animate: Boolean, setSelected: Boolean, goToPosition: Boolean) {
		if (setSelected) {
			mSelectedDay = date
		}

		val position = getPositionFromDay(
				if (mSelectedDay == null)
					Calendar.getInstance().timeInMillis
				else
					mSelectedDay!!.startDate.timeInMillis)

		if (goToPosition && position != mViewPager.currentItem) {
			mViewPager.setCurrentItem(position, animate)
		}

		mAdapter.setSelectedDay(SelectedDate(mSelectedDay))
	}

	/**
	 * Handles changes to date range.
	 */
	private fun onRangeChanged() {
		mAdapter.setRange(mMinDate, mMaxDate)

		// Changing the min/max date changes the selection position since we
		// don't really have stable IDs. Jumps immediately to the new position.
		setDate(mSelectedDay, false, false, true)

		updateButtonVisibility(mViewPager.currentItem)
	}

	/**
	 * Sets the listener to call when the user selects a day.
	 *
	 * @param listener The listener to call.
	 */
	fun setProxyDaySelectionEventListener(listener: ProxyDaySelectionEventListener) {
		mProxyDaySelectionEventListener = listener
	}

	private fun getDiffMonths(start: Calendar, end: Calendar): Int {
		val diffYears = end.get(Calendar.YEAR) - start.get(Calendar.YEAR)
		return end.get(Calendar.MONTH) - start.get(Calendar.MONTH) + 12 * diffYears
	}

	private fun getPositionFromDay(timeInMillis: Long): Int {
		val diffMonthMax = getDiffMonths(mMinDate, mMaxDate)
		val diffMonth = getDiffMonths(mMinDate, getTempCalendarForTime(timeInMillis))
		return SUtils.constrain(diffMonth, 0, diffMonthMax)
	}

	private fun getTempCalendarForTime(timeInMillis: Long): Calendar {
		if (mTempCalendar == null) {
			mTempCalendar = Calendar.getInstance()
		}
		mTempCalendar!!.timeInMillis = timeInMillis
		return mTempCalendar
	}

	fun setPosition(position: Int) {
		mViewPager.setCurrentItem(position, false)
	}

	interface ProxyDaySelectionEventListener {
		fun onDaySelected(view: DayPickerView, day: Calendar?)

		fun onDateRangeSelectionStarted(selectedDate: SelectedDate)

		fun onDateRangeSelectionEnded(selectedDate: SelectedDate?)

		fun onDateRangeSelectionUpdated(selectedDate: SelectedDate)
	}

	companion object {
		private val TAG = DayPickerView::class.java.getSimpleName()

		private val ATTRS_TEXT_COLOR = intArrayOf(android.R.attr.textColor)
	}
}

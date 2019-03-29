/*
 * Copyright (C) 2015 The Android Open Source Project
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
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.viewpager.widget.PagerAdapter
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.appeaser.sublimepickerlibrary.R
import com.appeaser.sublimepickerlibrary.utilities.Config
import com.appeaser.sublimepickerlibrary.utilities.SUtils

import java.util.Calendar

/**
 * An adapter for a list of [SimpleMonthView] items.
 */
internal class DayPickerPagerAdapter(context: Context, @param:LayoutRes private val mLayoutResId: Int,
                                     @param:IdRes private val mCalendarViewId: Int) : androidx.viewpager.widget.PagerAdapter() {

	private val mMinDate = Calendar.getInstance()
	private val mMaxDate = Calendar.getInstance()

	private val mItems = SparseArray<ViewHolder>()

	private val mInflater: LayoutInflater

	private var mSelectedDay: SelectedDate? = null

	private var mMonthTextAppearance: Int = 0
	var dayOfWeekTextAppearance: Int = 0
	var dayTextAppearance: Int = 0

	private var mCalendarTextColor: ColorStateList? = null
	private var mDaySelectorColor: ColorStateList? = null
	private val mDayHighlightColor: ColorStateList?

	private var mDaySelectionEventListener: DaySelectionEventListener? = null

	private var mCount: Int = 0
	/**
	 * Sets the first day of the week.
	 *
	 * @param weekStart which day the week should start on, valid values are
	 * [Calendar.SUNDAY] through [Calendar.SATURDAY]
	 */
	// Update displayed views.
	var firstDayOfWeek: Int = 0
		set(weekStart) {
			field = weekStart
			val count = mItems.size()
			for (i in 0 until count) {
				val monthView = mItems.valueAt(i).calendar
				monthView!!.setFirstDayOfWeek(weekStart)
			}
		}

	// used in resolving start/end dates during range selection
	private val mTempSelectedDay = SelectedDate(Calendar.getInstance())

	private val mOnDayClickListener = SimpleMonthView.OnDayClickListener { view, day ->
		if (day != null) {
			if (mDaySelectionEventListener != null) {
				mDaySelectionEventListener!!.onDaySelected(this@DayPickerPagerAdapter, day)
			}
		}
	}

	init {
		mInflater = LayoutInflater.from(context)

		val ta = context.obtainStyledAttributes(intArrayOf(R.attr.colorControlHighlight))
		mDayHighlightColor = ta.getColorStateList(0)
		ta.recycle()
	}

	fun setRange(min: Calendar, max: Calendar) {
		mMinDate.timeInMillis = min.timeInMillis
		mMaxDate.timeInMillis = max.timeInMillis

		val diffYear = mMaxDate.get(Calendar.YEAR) - mMinDate.get(Calendar.YEAR)
		val diffMonth = mMaxDate.get(Calendar.MONTH) - mMinDate.get(Calendar.MONTH)
		mCount = diffMonth + MONTHS_IN_YEAR * diffYear + 1

		// Positions are now invalid, clear everything and start over.
		notifyDataSetChanged()
	}

	/**
	 * Sets the selected day.
	 *
	 * @param day the selected day
	 */
	fun setSelectedDay(day: SelectedDate?) {
		val oldPosition = getPositionsForDay(mSelectedDay)
		val newPosition = getPositionsForDay(day)

		val shouldClearOldPosition = oldPosition != null

		// Clear the old position if necessary.
		if (shouldClearOldPosition) {
			for (i in oldPosition!![0]..oldPosition[oldPosition.size - 1]) {
				val oldMonthView = mItems.get(i, null)
				if (oldMonthView != null) {
					oldMonthView.calendar!!.setSelectedDays(-1, -1, SelectedDate.Type.SINGLE)

				}
			}
		}

		// Set the new position.
		if (newPosition != null) {
			if (newPosition.size == 1) {
				val newMonthView = mItems.get(newPosition[0], null)
				if (newMonthView != null) {
					val dayOfMonth = day!!.firstDate!!.get(Calendar.DAY_OF_MONTH)
					newMonthView.calendar!!.setSelectedDays(dayOfMonth, dayOfMonth, SelectedDate.Type.SINGLE)
				}
			} else if (newPosition.size == 2) {
				val rangeIsInSameMonth = newPosition[0] == newPosition[1]

				if (rangeIsInSameMonth) {
					val newMonthView = mItems.get(newPosition[0], null)
					if (newMonthView != null) {
						val startDayOfMonth = day!!.firstDate!!.get(Calendar.DAY_OF_MONTH)
						val endDayOfMonth = day.secondDate!!.get(Calendar.DAY_OF_MONTH)

						newMonthView.calendar!!.setSelectedDays(startDayOfMonth, endDayOfMonth, SelectedDate.Type.RANGE)
					}
				} else {
					// Deal with starting month
					val newMonthViewStart = mItems.get(newPosition[0], null)
					if (newMonthViewStart != null) {
						val startDayOfMonth = day!!.firstDate!!.get(Calendar.DAY_OF_MONTH)
						// TODO: Check this
						val endDayOfMonth = day.firstDate!!.getActualMaximum(Calendar.DATE)

						newMonthViewStart.calendar!!.setSelectedDays(startDayOfMonth, endDayOfMonth, SelectedDate.Type.RANGE)
					}

					for (i in newPosition[0] + 1 until newPosition[1]) {
						val newMonthView = mItems.get(i, null)
						if (newMonthView != null) {
							newMonthView.calendar!!.selectAllDays()
						}
					}

					// Deal with ending month
					val newMonthViewEnd = mItems.get(newPosition[1], null)
					if (newMonthViewEnd != null) {
						val startDayOfMonth = day!!.secondDate!!.getMinimum(Calendar.DATE)
						// TODO: Check this
						val endDayOfMonth = day.secondDate!!.get(Calendar.DAY_OF_MONTH)

						newMonthViewEnd.calendar!!.setSelectedDays(startDayOfMonth, endDayOfMonth, SelectedDate.Type.RANGE)
					}
				}
			}
		}

		mSelectedDay = day
	}

	/**
	 * Sets the listener to call when the user selects a day.
	 *
	 * @param listener The listener to call.
	 */
	fun setDaySelectionEventListener(listener: DaySelectionEventListener) {
		mDaySelectionEventListener = listener
	}

	fun setCalendarTextColor(calendarTextColor: ColorStateList) {
		mCalendarTextColor = calendarTextColor
	}

	fun setDaySelectorColor(selectorColor: ColorStateList) {
		mDaySelectorColor = selectorColor
	}

	fun setMonthTextAppearance(resId: Int) {
		mMonthTextAppearance = resId
	}

	override fun getCount(): Int {
		return mCount
	}

	override fun isViewFromObject(view: View, `object`: Any): Boolean {
		val holder = `object` as ViewHolder
		return view === holder.container
	}

	private fun getMonthForPosition(position: Int): Int {
		return (position + mMinDate.get(Calendar.MONTH)) % MONTHS_IN_YEAR
	}

	private fun getYearForPosition(position: Int): Int {
		val yearOffset = (position + mMinDate.get(Calendar.MONTH)) / MONTHS_IN_YEAR
		return yearOffset + mMinDate.get(Calendar.YEAR)
	}

	private fun getPositionForDay(day: Calendar?): Int {
		if (day == null) {
			return -1
		}

		val yearOffset = day.get(Calendar.YEAR) - mMinDate.get(Calendar.YEAR)
		val monthOffset = day.get(Calendar.MONTH) - mMinDate.get(Calendar.MONTH)
		return yearOffset * MONTHS_IN_YEAR + monthOffset
	}

	private fun getPositionsForDay(day: SelectedDate?): IntArray? {
		if (day == null) {
			return null
		}

		val typeOfDay = day.type
		var positions: IntArray? = null

		if (typeOfDay == SelectedDate.Type.SINGLE) {
			positions = IntArray(1)
			val yearOffset = day.firstDate!!.get(Calendar.YEAR) - mMinDate.get(Calendar.YEAR)
			val monthOffset = day.firstDate!!.get(Calendar.MONTH) - mMinDate.get(Calendar.MONTH)
			positions[0] = yearOffset * MONTHS_IN_YEAR + monthOffset
		} else if (typeOfDay == SelectedDate.Type.RANGE) {
			positions = IntArray(2)
			val yearOffsetFirstDate = day.firstDate!!.get(Calendar.YEAR) - mMinDate.get(Calendar.YEAR)
			val monthOffsetFirstDate = day.firstDate!!.get(Calendar.MONTH) - mMinDate.get(Calendar.MONTH)
			positions[0] = yearOffsetFirstDate * MONTHS_IN_YEAR + monthOffsetFirstDate

			val yearOffsetSecondDate = day.secondDate!!.get(Calendar.YEAR) - mMinDate.get(Calendar.YEAR)
			val monthOffsetSecondDate = day.secondDate!!.get(Calendar.MONTH) - mMinDate.get(Calendar.MONTH)
			positions[1] = yearOffsetSecondDate * MONTHS_IN_YEAR + monthOffsetSecondDate
		}

		return positions
	}

	override fun instantiateItem(container: ViewGroup, position: Int): Any {
		val itemView = mInflater.inflate(mLayoutResId, container, false)

		val v = itemView.findViewById<View>(mCalendarViewId) as SimpleMonthView
		v.setOnDayClickListener(mOnDayClickListener)
		v.setMonthTextAppearance(mMonthTextAppearance)
		v.setDayOfWeekTextAppearance(dayOfWeekTextAppearance)
		v.setDayTextAppearance(dayTextAppearance)

		if (mDaySelectorColor != null) {
			v.setDaySelectorColor(mDaySelectorColor)
		}

		if (mDayHighlightColor != null) {
			v.setDayHighlightColor(mDayHighlightColor)
		}

		if (mCalendarTextColor != null) {
			v.setMonthTextColor(mCalendarTextColor)
			v.setDayOfWeekTextColor(mCalendarTextColor)
			v.setDayTextColor(mCalendarTextColor)
		}

		val month = getMonthForPosition(position)
		val year = getYearForPosition(position)

		val selectedDay = resolveSelectedDayBasedOnType(month, year)

		val enabledDayRangeStart: Int
		if (mMinDate.get(Calendar.MONTH) == month && mMinDate.get(Calendar.YEAR) == year) {
			enabledDayRangeStart = mMinDate.get(Calendar.DAY_OF_MONTH)
		} else {
			enabledDayRangeStart = 1
		}

		val enabledDayRangeEnd: Int
		if (mMaxDate.get(Calendar.MONTH) == month && mMaxDate.get(Calendar.YEAR) == year) {
			enabledDayRangeEnd = mMaxDate.get(Calendar.DAY_OF_MONTH)
		} else {
			enabledDayRangeEnd = 31
		}

		if (Config.DEBUG) {
			Log.i(TAG, "mSelectedDay.getType(): " + if (mSelectedDay != null) mSelectedDay!!.type else null)
		}

		v.setMonthParams(month, year, firstDayOfWeek,
				enabledDayRangeStart, enabledDayRangeEnd, selectedDay[0], selectedDay[1],
				if (mSelectedDay != null) mSelectedDay!!.type else null)

		val holder = ViewHolder(position, itemView, v)
		mItems.put(position, holder)

		container.addView(itemView)

		return holder
	}

	override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
		val holder = `object` as ViewHolder
		container.removeView(holder.container)

		mItems.remove(position)
	}

	override fun getItemPosition(`object`: Any): Int {
		val holder = `object` as ViewHolder
		return holder.position
	}

	override fun getPageTitle(position: Int): CharSequence? {
		val v = mItems.get(position).calendar
		return v?.title
	}

	private class ViewHolder(val position: Int, val container: View, val calendar: SimpleMonthView?)

	fun resolveStartDateForRange(x: Int, y: Int, position: Int): SelectedDate? {
		if (position >= 0) {
			val newMonthView = mItems.get(position, null)
			if (newMonthView != null) {
				val dayOfMonth = newMonthView.calendar!!.getDayAtLocation(x, y)
				val selectedDayStart = newMonthView.calendar.composeDate(dayOfMonth)
				if (selectedDayStart != null) {
					mTempSelectedDay.setDate(selectedDayStart)
					return mTempSelectedDay
				}
			}
		}

		return null
	}

	fun resolveEndDateForRange(x: Int, y: Int, position: Int, updateIfNecessary: Boolean): SelectedDate? {
		if (position >= 0) {
			val newMonthView = mItems.get(position, null)
			if (newMonthView != null) {
				val dayOfMonth = newMonthView.calendar!!.getDayAtLocation(x, y)
				val selectedDayEnd = newMonthView.calendar.composeDate(dayOfMonth)

				if (selectedDayEnd != null && (!updateIfNecessary || mSelectedDay!!.secondDate!!.timeInMillis != selectedDayEnd.timeInMillis)) {
					mTempSelectedDay.secondDate = selectedDayEnd
					return mTempSelectedDay
				}
			}
		}

		return null
	}

	private fun resolveSelectedDayBasedOnType(month: Int, year: Int): IntArray {
		if (mSelectedDay == null) {
			return intArrayOf(-1, -1)
		}

		if (mSelectedDay!!.type == SelectedDate.Type.SINGLE) {
			return resolveSelectedDayForTypeSingle(month, year)
		} else if (mSelectedDay!!.type == SelectedDate.Type.RANGE) {
			return resolveSelectedDayForTypeRange(month, year)
		}

		return intArrayOf(-1, -1)
	}

	private fun resolveSelectedDayForTypeSingle(month: Int, year: Int): IntArray {
		if (mSelectedDay!!.firstDate!!.get(Calendar.MONTH) == month && mSelectedDay!!.firstDate!!.get(Calendar.YEAR) == year) {
			val resolvedDay = mSelectedDay!!.firstDate!!.get(Calendar.DAY_OF_MONTH)
			return intArrayOf(resolvedDay, resolvedDay)
		}

		return intArrayOf(-1, -1)
	}

	private fun resolveSelectedDayForTypeRange(month: Int, year: Int): IntArray {
		// Quan: "year.month" Eg: Feb, 2015 ==> 2015.02, Dec, 2000 ==> 2000.12
		val startDateQuan = mSelectedDay!!.startDate.get(Calendar.YEAR) + (mSelectedDay!!.startDate.get(Calendar.MONTH) + 1) / 100f
		val endDateQuan = mSelectedDay!!.endDate.get(Calendar.YEAR) + (mSelectedDay!!.endDate.get(Calendar.MONTH) + 1) / 100f

		val dateQuan = year + (month + 1) / 100f

		if (dateQuan >= startDateQuan && dateQuan <= endDateQuan) {
			val startDay: Int
			val endDay: Int
			if (dateQuan == startDateQuan) {
				startDay = mSelectedDay!!.startDate.get(Calendar.DAY_OF_MONTH)
			} else {
				startDay = 1
			}

			if (dateQuan == endDateQuan) {
				endDay = mSelectedDay!!.endDate.get(Calendar.DAY_OF_MONTH)
			} else {
				endDay = SUtils.getDaysInMonth(month, year)
			}

			return intArrayOf(startDay, endDay)
		}

		return intArrayOf(-1, -1)
	}

	fun onDateRangeSelectionStarted(selectedDate: SelectedDate) {
		if (mDaySelectionEventListener != null) {
			mDaySelectionEventListener!!.onDateRangeSelectionStarted(selectedDate)
		}
	}

	fun onDateRangeSelectionEnded(selectedDate: SelectedDate) {
		if (mDaySelectionEventListener != null) {
			mDaySelectionEventListener!!.onDateRangeSelectionEnded(selectedDate)
		}
	}

	fun onDateRangeSelectionUpdated(selectedDate: SelectedDate) {
		if (mDaySelectionEventListener != null) {
			mDaySelectionEventListener!!.onDateRangeSelectionUpdated(selectedDate)
		}
	}

	interface DaySelectionEventListener {
		fun onDaySelected(view: DayPickerPagerAdapter, day: Calendar?)

		fun onDateRangeSelectionStarted(selectedDate: SelectedDate)

		fun onDateRangeSelectionEnded(selectedDate: SelectedDate?)

		fun onDateRangeSelectionUpdated(selectedDate: SelectedDate)
	}

	companion object {

		private val TAG = DayPickerPagerAdapter::class.java.getSimpleName()

		private val MONTHS_IN_YEAR = 12
	}
}

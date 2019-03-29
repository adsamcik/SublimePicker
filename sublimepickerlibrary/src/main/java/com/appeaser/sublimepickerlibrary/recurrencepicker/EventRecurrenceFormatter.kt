/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.content.Context
import android.content.res.Resources
import android.text.format.DateUtils
import android.text.format.Time
import android.util.TimeFormatException

import com.appeaser.sublimepickerlibrary.R

import java.util.Calendar

object EventRecurrenceFormatter {

	private var mMonthRepeatByDayOfWeekIds: IntArray? = null
	private var mMonthRepeatByDayOfWeekStrs: Array<Array<String>>? = null

	fun getRepeatString(context: Context, r: Resources, recurrence: EventRecurrence,
	                    includeEndString: Boolean): String? {
		var endString = ""
		if (includeEndString) {
			val sb = StringBuilder()
			if (recurrence.until != null) {
				try {
					val t = Time()
					t.parse(recurrence.until)
					val dateStr = DateUtils.formatDateTime(context,
							t.toMillis(false), DateUtils.FORMAT_NUMERIC_DATE)
					sb.append(r.getString(R.string.endByDate, dateStr))
				} catch (e: TimeFormatException) {
				}

			}

			if (recurrence.count > 0) {
				sb.append(r.getQuantityString(R.plurals.endByCount, recurrence.count,
						recurrence.count))
			}
			endString = sb.toString()
		}

		val interval = if (recurrence.interval <= 1) 1 else recurrence.interval
		when (recurrence.freq) {
			EventRecurrence.DAILY -> return r.getQuantityString(R.plurals.daily, interval, interval) + endString
			EventRecurrence.WEEKLY -> {
				if (recurrence.repeatsOnEveryWeekDay()) {
					return r.getString(R.string.every_weekday) + endString
				} else {
					val string: String

					var dayOfWeekLength = DateUtils.LENGTH_MEDIUM
					if (recurrence.bydayCount == 1) {
						dayOfWeekLength = DateUtils.LENGTH_LONG
					}

					val days = StringBuilder()

					// Do one less iteration in the loop so the last element is added out of the
					// loop. This is done so the comma is not placed after the last item.

					if (recurrence.bydayCount > 0) {
						val count = recurrence.bydayCount - 1
						for (i in 0 until count) {
							days.append(dayToString(recurrence.byday[i], dayOfWeekLength))
							days.append(", ")
						}
						days.append(dayToString(recurrence.byday[count], dayOfWeekLength))

						string = days.toString()
					} else {
						// There is no "BYDAY" specifier, so use the day of the
						// first event.  For this to work, the setStartDate()
						// method must have been used by the caller to set the
						// date of the first event in the recurrence.
						if (recurrence.startDate == null) {
							return null
						}

						val day = EventRecurrence.timeDay2Day(recurrence.startDate!!.weekDay)
						string = dayToString(day, DateUtils.LENGTH_LONG)
					}
					return r.getQuantityString(R.plurals.weekly, interval, interval, string) + endString
				}
			}
			EventRecurrence.MONTHLY -> {
				val monthlyStart = if (interval == 1)
					r.getString(R.string.monthly)
				else
					r.getQuantityString(R.plurals.recurrence_interval_monthly,
							interval, interval)
				if (recurrence.bydayCount == 1) {
					val weekday = recurrence.startDate!!.weekDay
					// Cache this stuff so we won't have to redo work again later.
					cacheMonthRepeatStrings(r, weekday)
					val dayNumber = (recurrence.startDate!!.monthDay - 1) / 7
					val sb = StringBuilder()
					sb.append(monthlyStart)
					sb.append(" (")
					sb.append(mMonthRepeatByDayOfWeekStrs!![weekday][dayNumber])
					sb.append(")")
					sb.append(endString)
					return sb.toString()
				}

				return monthlyStart + endString
			}
			EventRecurrence.YEARLY -> {
				val yearlyStart = if (interval == 1)
					r.getString(R.string.yearly_plain)
				else
					r.getQuantityString(R.plurals.recurrence_interval_yearly,
							interval, interval)

				return yearlyStart + endString
			}
		}

		return null
	}

	private fun cacheMonthRepeatStrings(r: Resources, weekday: Int) {
		if (mMonthRepeatByDayOfWeekIds == null) {
			mMonthRepeatByDayOfWeekIds = IntArray(7)
			mMonthRepeatByDayOfWeekIds[0] = R.array.repeat_by_nth_sun
			mMonthRepeatByDayOfWeekIds[1] = R.array.repeat_by_nth_mon
			mMonthRepeatByDayOfWeekIds[2] = R.array.repeat_by_nth_tues
			mMonthRepeatByDayOfWeekIds[3] = R.array.repeat_by_nth_wed
			mMonthRepeatByDayOfWeekIds[4] = R.array.repeat_by_nth_thurs
			mMonthRepeatByDayOfWeekIds[5] = R.array.repeat_by_nth_fri
			mMonthRepeatByDayOfWeekIds[6] = R.array.repeat_by_nth_sat
		}
		if (mMonthRepeatByDayOfWeekStrs == null) {
			mMonthRepeatByDayOfWeekStrs = arrayOfNulls(7)
		}
		if (mMonthRepeatByDayOfWeekStrs!![weekday] == null) {
			mMonthRepeatByDayOfWeekStrs[weekday] = r.getStringArray(mMonthRepeatByDayOfWeekIds!![weekday])
		}
	}

	/**
	 * Converts day of week to a String.
	 * @param day a EventRecurrence constant
	 * @return day of week as a string
	 */
	private fun dayToString(day: Int, dayOfWeekLength: Int): String {
		return DateUtils.getDayOfWeekString(dayToUtilDay(day), dayOfWeekLength)
	}

	/**
	 * Converts EventRecurrence's day of week to DateUtil's day of week.
	 * @param day of week as an EventRecurrence value
	 * @return day of week as a DateUtil value.
	 */
	private fun dayToUtilDay(day: Int): Int {
		when (day) {
			EventRecurrence.SU -> return Calendar.SUNDAY
			EventRecurrence.MO -> return Calendar.MONDAY
			EventRecurrence.TU -> return Calendar.TUESDAY
			EventRecurrence.WE -> return Calendar.WEDNESDAY
			EventRecurrence.TH -> return Calendar.THURSDAY
			EventRecurrence.FR -> return Calendar.FRIDAY
			EventRecurrence.SA -> return Calendar.SATURDAY
			else -> throw IllegalArgumentException("bad day argument: $day")
		}
	}
}

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

package com.appeaser.sublimepickerlibrary.utilities

import android.text.format.Time

import java.util.Calendar

object RecurrenceUtils {

	/**
	 * Get first day of week as android.text.format.Time constant.
	 *
	 * @return the first day of week in android.text.format.Time
	 */
	val firstDayOfWeek: Int
		get() {
			val startDay = Calendar.getInstance().firstDayOfWeek

			return if (startDay == Calendar.SATURDAY) {
				Time.SATURDAY
			} else if (startDay == Calendar.MONDAY) {
				Time.MONDAY
			} else {
				Time.SUNDAY
			}
		}

	/**
	 * Get first day of week as java.util.Calendar constant.
	 *
	 * @return the first day of week as a java.util.Calendar constant
	 */
	val firstDayOfWeekAsCalendar: Int
		get() = convertDayOfWeekFromTimeToCalendar(firstDayOfWeek)

	/**
	 * Converts the day of the week from android.text.format.Time to java.util.Calendar
	 */
	fun convertDayOfWeekFromTimeToCalendar(timeDayOfWeek: Int): Int {
		when (timeDayOfWeek) {
			Time.MONDAY -> return Calendar.MONDAY
			Time.TUESDAY -> return Calendar.TUESDAY
			Time.WEDNESDAY -> return Calendar.WEDNESDAY
			Time.THURSDAY -> return Calendar.THURSDAY
			Time.FRIDAY -> return Calendar.FRIDAY
			Time.SATURDAY -> return Calendar.SATURDAY
			Time.SUNDAY -> return Calendar.SUNDAY
			else -> throw IllegalArgumentException("Argument must be between Time.SUNDAY and " + "Time.SATURDAY")
		}
	}
}

package com.appeaser.sublimepickerlibrary.datepicker

import java.text.DateFormat
import java.util.Calendar

/**
 * Created by Admin on 25/02/2016.
 */
class SelectedDate {

	var firstDate: Calendar? = null
	var secondDate: Calendar? = null

	val startDate: Calendar
		get() = if (compareDates(firstDate!!, secondDate!!) == -1) firstDate else secondDate

	val endDate: Calendar
		get() = if (compareDates(firstDate!!, secondDate!!) == 1) firstDate else secondDate

	val type: Type
		get() = if (compareDates(firstDate!!, secondDate!!) == 0) Type.SINGLE else Type.RANGE

	enum class Type {
		SINGLE, RANGE
	}

	constructor(startDate: Calendar, endDate: Calendar) {
		firstDate = startDate
		secondDate = endDate
	}

	constructor(date: Calendar) {
		secondDate = date
		firstDate = secondDate
	}

	// TODO: Should be requiring Locale
	constructor(date: SelectedDate?) {
		firstDate = Calendar.getInstance()
		secondDate = Calendar.getInstance()

		if (date != null) {
			firstDate!!.timeInMillis = date.startDate.timeInMillis
			secondDate!!.timeInMillis = date.endDate.timeInMillis
		}
	}

	fun setDate(date: Calendar) {
		firstDate = date
		secondDate = date
	}

	fun setTimeInMillis(timeInMillis: Long) {
		firstDate!!.timeInMillis = timeInMillis
		secondDate!!.timeInMillis = timeInMillis
	}

	operator fun set(field: Int, value: Int) {
		firstDate!!.set(field, value)
		secondDate!!.set(field, value)
	}

	override fun toString(): String {
		val toReturn = StringBuilder()

		if (firstDate != null) {
			toReturn.append(DateFormat.getDateInstance().format(firstDate!!.time))
			toReturn.append("\n")
		}

		if (secondDate != null) {
			toReturn.append(DateFormat.getDateInstance().format(secondDate!!.time))
		}

		return toReturn.toString()
	}

	companion object {

		// a & b should never be null, so don't perform a null check here.
		// Let the source of error identify itself.
		fun compareDates(a: Calendar, b: Calendar): Int {
			val aYear = a.get(Calendar.YEAR)
			val bYear = b.get(Calendar.YEAR)

			val aMonth = a.get(Calendar.MONTH)
			val bMonth = b.get(Calendar.MONTH)

			val aDayOfMonth = a.get(Calendar.DAY_OF_MONTH)
			val bDayOfMonth = b.get(Calendar.DAY_OF_MONTH)

			return if (aYear < bYear) {
				-1
			} else if (aYear > bYear) {
				1
			} else {
				if (aMonth < bMonth) {
					-1
				} else if (aMonth > bMonth) {
					1
				} else {
					if (aDayOfMonth < bDayOfMonth) {
						-1
					} else if (aDayOfMonth > bDayOfMonth) {
						1
					} else {
						0
					}
				}
			}
		}
	}
}

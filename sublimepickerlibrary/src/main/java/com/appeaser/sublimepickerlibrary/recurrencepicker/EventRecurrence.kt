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

import android.text.TextUtils
import android.text.format.Time
import android.util.Log
import android.util.TimeFormatException

import java.util.Calendar
import java.util.HashMap

/**
 * Event recurrence utility functions.
 */
class EventRecurrence {

	var startDate: Time? = null     // set by setStartDate(), not parse()

	var freq: Int = 0          // SECONDLY, MINUTELY, etc.
	var until: String? = null
	var count: Int = 0
	var interval: Int = 0
	var wkst: Int = 0          // SU, MO, TU, etc.

	/* lists with zero entries may be null references */
	var bysecond: IntArray
	var bysecondCount: Int = 0
	var byminute: IntArray
	var byminuteCount: Int = 0
	var byhour: IntArray
	var byhourCount: Int = 0
	var byday: IntArray
	var bydayNum: IntArray
	var bydayCount: Int = 0
	var bymonthday: IntArray
	var bymonthdayCount: Int = 0
	var byyearday: IntArray
	var byyeardayCount: Int = 0
	var byweekno: IntArray
	var byweeknoCount: Int = 0
	var bymonth: IntArray
	var bymonthCount: Int = 0
	var bysetpos: IntArray
	var bysetposCount: Int = 0


	/**
	 * Thrown when a recurrence string provided can not be parsed according
	 * to RFC2445.
	 */
	class InvalidFormatException internal constructor(s: String) : RuntimeException(s)


	fun setStartDate(date: Time) {
		startDate = date
	}

	private fun appendByDay(s: StringBuilder, i: Int) {
		val n = this.bydayNum[i]
		if (n != 0) {
			s.append(n)
		}

		val str = day2String(this.byday[i])
		s.append(str)
	}

	override fun toString(): String {
		val s = StringBuilder()

		s.append("FREQ=")
		when (this.freq) {
			SECONDLY -> s.append("SECONDLY")
			MINUTELY -> s.append("MINUTELY")
			HOURLY -> s.append("HOURLY")
			DAILY -> s.append("DAILY")
			WEEKLY -> s.append("WEEKLY")
			MONTHLY -> s.append("MONTHLY")
			YEARLY -> s.append("YEARLY")
		}

		if (!TextUtils.isEmpty(this.until)) {
			s.append(";UNTIL=")
			s.append(until)
		}

		if (this.count != 0) {
			s.append(";COUNT=")
			s.append(this.count)
		}

		if (this.interval != 0) {
			s.append(";INTERVAL=")
			s.append(this.interval)
		}

		if (this.wkst != 0) {
			s.append(";WKST=")
			s.append(day2String(this.wkst))
		}

		appendNumbers(s, ";BYSECOND=", this.bysecondCount, this.bysecond)
		appendNumbers(s, ";BYMINUTE=", this.byminuteCount, this.byminute)
		appendNumbers(s, ";BYSECOND=", this.byhourCount, this.byhour)

		// day
		var count = this.bydayCount
		if (count > 0) {
			s.append(";BYDAY=")
			count--
			for (i in 0 until count) {
				appendByDay(s, i)
				s.append(",")
			}
			appendByDay(s, count)
		}

		appendNumbers(s, ";BYMONTHDAY=", this.bymonthdayCount, this.bymonthday)
		appendNumbers(s, ";BYYEARDAY=", this.byyeardayCount, this.byyearday)
		appendNumbers(s, ";BYWEEKNO=", this.byweeknoCount, this.byweekno)
		appendNumbers(s, ";BYMONTH=", this.bymonthCount, this.bymonth)
		appendNumbers(s, ";BYSETPOS=", this.bysetposCount, this.bysetpos)

		return s.toString()
	}

	fun repeatsOnEveryWeekDay(): Boolean {
		if (this.freq != WEEKLY) {
			return false
		}

		val count = this.bydayCount
		if (count != 5) {
			return false
		}

		for (i in 0 until count) {
			val day = byday[i]
			if (day == SU || day == SA) {
				return false
			}
		}

		return true
	}

	/**
	 * Determines whether this rule specifies a simple monthly rule by weekday, such as
	 * "FREQ=MONTHLY;BYDAY=3TU" (the 3rd Tuesday of every month).
	 * Negative days, e.g. "FREQ=MONTHLY;BYDAY=-1TU" (the last Tuesday of every month),
	 * will cause "false" to be returned.
	 * Rules that fire every week, such as "FREQ=MONTHLY;BYDAY=TU" (every Tuesday of every
	 * month) will cause "false" to be returned.  (Note these are usually expressed as
	 * WEEKLY rules, and hence are uncommon.)
	 *
	 * @return true if this rule is of the appropriate form
	 */
	fun repeatsMonthlyOnDayCount(): Boolean {
		if (this.freq != MONTHLY) {
			return false
		}

		if (bydayCount != 1 || bymonthdayCount != 0) {
			return false
		}

		return if (bydayNum[0] <= 0) {
			false
		} else true

	}

	override fun equals(obj: Any?): Boolean {
		if (this === obj) {
			return true
		}
		if (obj !is EventRecurrence) {
			return false
		}

		val er = obj as EventRecurrence?
		return (if (startDate == null)
			er!!.startDate == null
		else
			Time.compare(startDate, er!!.startDate) == 0) &&
				freq == er.freq &&
				(if (until == null) er.until == null else until == er.until) &&
				count == er.count &&
				interval == er.interval &&
				wkst == er.wkst &&
				arraysEqual(bysecond, bysecondCount, er.bysecond, er.bysecondCount) &&
				arraysEqual(byminute, byminuteCount, er.byminute, er.byminuteCount) &&
				arraysEqual(byhour, byhourCount, er.byhour, er.byhourCount) &&
				arraysEqual(byday, bydayCount, er.byday, er.bydayCount) &&
				arraysEqual(bydayNum, bydayCount, er.bydayNum, er.bydayCount) &&
				arraysEqual(bymonthday, bymonthdayCount, er.bymonthday, er.bymonthdayCount) &&
				arraysEqual(byyearday, byyeardayCount, er.byyearday, er.byyeardayCount) &&
				arraysEqual(byweekno, byweeknoCount, er.byweekno, er.byweeknoCount) &&
				arraysEqual(bymonth, bymonthCount, er.bymonth, er.bymonthCount) &&
				arraysEqual(bysetpos, bysetposCount, er.bysetpos, er.bysetposCount)
	}

	override fun hashCode(): Int {
		// We overrode equals, so we must override hashCode().  Nobody seems to need this though.
		throw UnsupportedOperationException()
	}

	/**
	 * Resets parser-modified fields to their initial state.  Does not alter startDate.
	 * The original parser always set all of the "count" fields, "wkst", and "until",
	 * essentially allowing the same object to be used multiple times by calling parse().
	 * It's unclear whether this behavior was intentional.  For now, be paranoid and
	 * preserve the existing behavior by resetting the fields.
	 * We don't need to touch the integer arrays; they will either be ignored or
	 * overwritten.  The "startDate" field is not set by the parser, so we ignore it here.
	 */
	private fun resetFields() {
		until = null
		bysetposCount = 0
		bymonthCount = bysetposCount
		byweeknoCount = bymonthCount
		byyeardayCount = byweeknoCount
		bymonthdayCount = byyeardayCount
		bydayCount = bymonthdayCount
		byhourCount = bydayCount
		byminuteCount = byhourCount
		bysecondCount = byminuteCount
		interval = bysecondCount
		count = interval
		freq = count
	}

	/**
	 * Parses an rfc2445 recurrence rule string into its component pieces.  Attempting to parse
	 * malformed input will result in an EventRecurrence.InvalidFormatException.
	 *
	 * @param recur The recurrence rule to parse (in un-folded form).
	 */
	fun parse(recur: String) {
		/*
         * From RFC 2445 section 4.3.10:
         *
         * recur = "FREQ"=freq *(
         *       ; either UNTIL or COUNT may appear in a 'recur',
         *       ; but UNTIL and COUNT MUST NOT occur in the same 'recur'
         *
         *       ( ";" "UNTIL" "=" enddate ) /
         *       ( ";" "COUNT" "=" 1*DIGIT ) /
         *
         *       ; the rest of these keywords are optional,
         *       ; but MUST NOT occur more than once
         *
         *       ( ";" "INTERVAL" "=" 1*DIGIT )          /
         *       ( ";" "BYSECOND" "=" byseclist )        /
         *       ( ";" "BYMINUTE" "=" byminlist )        /
         *       ( ";" "BYHOUR" "=" byhrlist )           /
         *       ( ";" "BYDAY" "=" bywdaylist )          /
         *       ( ";" "BYMONTHDAY" "=" bymodaylist )    /
         *       ( ";" "BYYEARDAY" "=" byyrdaylist )     /
         *       ( ";" "BYWEEKNO" "=" bywknolist )       /
         *       ( ";" "BYMONTH" "=" bymolist )          /
         *       ( ";" "BYSETPOS" "=" bysplist )         /
         *       ( ";" "WKST" "=" weekday )              /
         *       ( ";" x-name "=" text )
         *       )
         *
         *  The rule parts are not ordered in any particular sequence.
         *
         * Examples:
         *   FREQ=MONTHLY;INTERVAL=2;COUNT=10;BYDAY=1SU,-1SU
         *   FREQ=YEARLY;INTERVAL=4;BYMONTH=11;BYDAY=TU;BYMONTHDAY=2,3,4,5,6,7,8
         *
         * Strategy:
         * (1) Split the string at ';' boundaries to get an array of rule "parts".
         * (2) For each part, find substrings for left/right sides of '=' (name/value).
         * (3) Call a <name>-specific parsing function to parse the <value> into an
         *     output field.
         *
         * By keeping track of which names we've seen in a bit vector, we can verify the
         * constraints indicated above (FREQ appears first, none of them appear more than once --
         * though x-[name] would require special treatment), and we have either UNTIL or COUNT
         * but not both.
         *
         * In general, RFC 2445 property names (e.g. "FREQ") and enumerations ("TU") must
         * be handled in a case-insensitive fashion, but case may be significant for other
         * properties.  We don't have any case-sensitive values in RRULE, except possibly
         * for the custom "X-" properties, but we ignore those anyway.  Thus, we can trivially
         * convert the entire string to upper case and then use simple comparisons.
         *
         * Differences from previous version:
         * - allows lower-case property and enumeration values [optional]
         * - enforces that FREQ appears first
         * - enforces that only one of UNTIL and COUNT may be specified
         * - allows (but ignores) X-* parts
         * - improved validation on various values (e.g. UNTIL timestamps)
         * - error messages are more specific
         *
         * TODO: enforce additional constraints listed in RFC 5545, notably the "N/A" entries
         * in section 3.3.10.  For example, if FREQ=WEEKLY, we should reject a rule that
         * includes a BYMONTHDAY part.
         */

		/* TODO: replace with "if (freq != 0) throw" if nothing requires this */
		resetFields()

		var parseFlags = 0
		val parts: Array<String>
		if (ALLOW_LOWER_CASE) {
			parts = recur.toUpperCase().split(";".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
		} else {
			parts = recur.split(";".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
		}
		for (part in parts) {
			// allow empty part (e.g., double semicolon ";;")
			if (TextUtils.isEmpty(part)) {
				continue
			}
			val equalIndex = part.indexOf('=')
			if (equalIndex <= 0) {
				/* no '=' or no LHS */
				throw InvalidFormatException("Missing LHS in $part")
			}

			val lhs = part.substring(0, equalIndex)
			val rhs = part.substring(equalIndex + 1)
			if (rhs.length == 0) {
				throw InvalidFormatException("Missing RHS in $part")
			}

			/*
             * In lieu of a "switch" statement that allows string arguments, we use a
             * map from strings to parsing functions.
             */
			val parser = sParsePartMap!![lhs]
			if (parser == null) {
				if (lhs.startsWith("X-")) {
					//Log.d(TAG, "Ignoring custom part " + lhs);
					continue
				}
				throw InvalidFormatException("Couldn't find parser for $lhs")
			} else {
				val flag = parser.parsePart(rhs, this)
				if (parseFlags and flag != 0) {
					throw InvalidFormatException("Part $lhs was specified twice")
				}
				parseFlags = parseFlags or flag
			}
		}

		// If not specified, week starts on Monday.
		if (parseFlags and PARSED_WKST == 0) {
			wkst = MO
		}

		// FREQ is mandatory.
		if (parseFlags and PARSED_FREQ == 0) {
			throw InvalidFormatException("Must specify a FREQ value")
		}

		// Can't have both UNTIL and COUNT.
		if (parseFlags and (PARSED_UNTIL or PARSED_COUNT) == PARSED_UNTIL or PARSED_COUNT) {
			if (ONLY_ONE_UNTIL_COUNT) {
				throw InvalidFormatException("Must not specify both UNTIL and COUNT: $recur")
			} else {
				Log.w(TAG, "Warning: rrule has both UNTIL and COUNT: $recur")
			}
		}
	}

	/**
	 * Base class for the RRULE part parsers.
	 */
	internal abstract class PartParser {
		/**
		 * Parses a single part.
		 *
		 * @param value The right-hand-side of the part.
		 * @param er    The EventRecurrence into which the result is stored.
		 * @return A bit value indicating which part was parsed.
		 */
		abstract fun parsePart(value: String, er: EventRecurrence): Int

		companion object {

			/**
			 * Parses an integer, with range-checking.
			 *
			 * @param str       The string to parse.
			 * @param minVal    Minimum allowed value.
			 * @param maxVal    Maximum allowed value.
			 * @param allowZero Is 0 allowed?
			 * @return The parsed value.
			 */
			fun parseIntRange(str: String, minVal: Int, maxVal: Int, allowZero: Boolean): Int {
				var str = str
				try {
					if (str[0] == '+') {
						// Integer.parseInt does not allow a leading '+', so skip it manually.
						str = str.substring(1)
					}
					val `val` = Integer.parseInt(str)
					if (`val` < minVal || `val` > maxVal || `val` == 0 && !allowZero) {
						throw InvalidFormatException("Integer value out of range: $str")
					}
					return `val`
				} catch (nfe: NumberFormatException) {
					throw InvalidFormatException("Invalid integer value: $str")
				}

			}

			/**
			 * Parses a comma-separated list of integers, with range-checking.
			 *
			 * @param listStr   The string to parse.
			 * @param minVal    Minimum allowed value.
			 * @param maxVal    Maximum allowed value.
			 * @param allowZero Is 0 allowed?
			 * @return A new array with values, sized to hold the exact number of elements.
			 */
			fun parseNumberList(listStr: String, minVal: Int, maxVal: Int,
			                    allowZero: Boolean): IntArray {
				val values: IntArray

				if (listStr.indexOf(",") < 0) {
					// Common case: only one entry, skip split() overhead.
					values = IntArray(1)
					values[0] = parseIntRange(listStr, minVal, maxVal, allowZero)
				} else {
					val valueStrs = listStr.split(",".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
					val len = valueStrs.size
					values = IntArray(len)
					for (i in 0 until len) {
						values[i] = parseIntRange(valueStrs[i], minVal, maxVal, allowZero)
					}
				}
				return values
			}
		}
	}

	/**
	 * parses FREQ={SECONDLY,MINUTELY,...}
	 */
	private class ParseFreq : PartParser() {
		override fun parsePart(value: String, er: EventRecurrence): Int {
			val freq = sParseFreqMap[value]
					?: throw InvalidFormatException("Invalid FREQ value: $value")
			er.freq = freq
			return PARSED_FREQ
		}
	}

	/**
	 * parses UNTIL=enddate, e.g. "19970829T021400"
	 */
	private class ParseUntil : PartParser() {
		override fun parsePart(value: String, er: EventRecurrence): Int {
			if (VALIDATE_UNTIL) {
				try {
					// Parse the time to validate it.  The result isn't retained.
					val until = Time()
					until.parse(value)
				} catch (tfe: TimeFormatException) {
					throw InvalidFormatException("Invalid UNTIL value: $value")
				}

			}
			er.until = value
			return PARSED_UNTIL
		}
	}

	/**
	 * parses COUNT=[non-negative-integer]
	 */
	private class ParseCount : PartParser() {
		override fun parsePart(value: String, er: EventRecurrence): Int {
			er.count = EventRecurrence.PartParser.parseIntRange(value, Integer.MIN_VALUE, Integer.MAX_VALUE, true)
			if (er.count < 0) {
				Log.d(TAG, "Invalid Count. Forcing COUNT to 1 from $value")
				er.count = 1 // invalid count. assume one time recurrence.
			}
			return PARSED_COUNT
		}
	}

	/**
	 * parses INTERVAL=[non-negative-integer]
	 */
	private class ParseInterval : PartParser() {
		override fun parsePart(value: String, er: EventRecurrence): Int {
			er.interval = EventRecurrence.PartParser.parseIntRange(value, Integer.MIN_VALUE, Integer.MAX_VALUE, true)
			if (er.interval < 1) {
				Log.d(TAG, "Invalid Interval. Forcing INTERVAL to 1 from $value")
				er.interval = 1
			}
			return PARSED_INTERVAL
		}
	}

	/**
	 * parses BYSECOND=byseclist
	 */
	private class ParseBySecond : PartParser() {
		override fun parsePart(value: String, er: EventRecurrence): Int {
			val bysecond = EventRecurrence.PartParser.parseNumberList(value, 0, 59, true)
			er.bysecond = bysecond
			er.bysecondCount = bysecond.size
			return PARSED_BYSECOND
		}
	}

	/**
	 * parses BYMINUTE=byminlist
	 */
	private class ParseByMinute : PartParser() {
		override fun parsePart(value: String, er: EventRecurrence): Int {
			val byminute = EventRecurrence.PartParser.parseNumberList(value, 0, 59, true)
			er.byminute = byminute
			er.byminuteCount = byminute.size
			return PARSED_BYMINUTE
		}
	}

	/**
	 * parses BYHOUR=byhrlist
	 */
	private class ParseByHour : PartParser() {
		override fun parsePart(value: String, er: EventRecurrence): Int {
			val byhour = EventRecurrence.PartParser.parseNumberList(value, 0, 23, true)
			er.byhour = byhour
			er.byhourCount = byhour.size
			return PARSED_BYHOUR
		}
	}

	/**
	 * parses BYDAY=bywdaylist, e.g. "1SU,-1SU"
	 */
	private class ParseByDay : PartParser() {
		override fun parsePart(value: String, er: EventRecurrence): Int {
			val byday: IntArray
			val bydayNum: IntArray
			val bydayCount: Int

			if (value.indexOf(",") < 0) {
				/* only one entry, skip split() overhead */
				bydayCount = 1
				byday = IntArray(1)
				bydayNum = IntArray(1)
				parseWday(value, byday, bydayNum, 0)
			} else {
				val wdays = value.split(",".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
				val len = wdays.size
				bydayCount = len
				byday = IntArray(len)
				bydayNum = IntArray(len)
				for (i in 0 until len) {
					parseWday(wdays[i], byday, bydayNum, i)
				}
			}
			er.byday = byday
			er.bydayNum = bydayNum
			er.bydayCount = bydayCount
			return PARSED_BYDAY
		}

		/**
		 * parses [int]weekday, putting the pieces into parallel array entries
		 */
		private fun parseWday(str: String, byday: IntArray, bydayNum: IntArray, index: Int) {
			val wdayStrStart = str.length - 2
			val wdayStr: String

			if (wdayStrStart > 0) {
				/* number is included; parse it out and advance to weekday */
				val numPart = str.substring(0, wdayStrStart)
				val num = EventRecurrence.PartParser.parseIntRange(numPart, -53, 53, false)
				bydayNum[index] = num
				wdayStr = str.substring(wdayStrStart)
			} else {
				/* just the weekday string */
				wdayStr = str
			}
			val wday = sParseWeekdayMap[wdayStr]
					?: throw InvalidFormatException("Invalid BYDAY value: $str")
			byday[index] = wday
		}
	}

	/**
	 * parses BYMONTHDAY=bymodaylist
	 */
	private class ParseByMonthDay : PartParser() {
		override fun parsePart(value: String, er: EventRecurrence): Int {
			val bymonthday = EventRecurrence.PartParser.parseNumberList(value, -31, 31, false)
			er.bymonthday = bymonthday
			er.bymonthdayCount = bymonthday.size
			return PARSED_BYMONTHDAY
		}
	}

	/**
	 * parses BYYEARDAY=byyrdaylist
	 */
	private class ParseByYearDay : PartParser() {
		override fun parsePart(value: String, er: EventRecurrence): Int {
			val byyearday = EventRecurrence.PartParser.parseNumberList(value, -366, 366, false)
			er.byyearday = byyearday
			er.byyeardayCount = byyearday.size
			return PARSED_BYYEARDAY
		}
	}

	/**
	 * parses BYWEEKNO=bywknolist
	 */
	private class ParseByWeekNo : PartParser() {
		override fun parsePart(value: String, er: EventRecurrence): Int {
			val byweekno = EventRecurrence.PartParser.parseNumberList(value, -53, 53, false)
			er.byweekno = byweekno
			er.byweeknoCount = byweekno.size
			return PARSED_BYWEEKNO
		}
	}

	/**
	 * parses BYMONTH=bymolist
	 */
	private class ParseByMonth : PartParser() {
		override fun parsePart(value: String, er: EventRecurrence): Int {
			val bymonth = EventRecurrence.PartParser.parseNumberList(value, 1, 12, false)
			er.bymonth = bymonth
			er.bymonthCount = bymonth.size
			return PARSED_BYMONTH
		}
	}

	/**
	 * parses BYSETPOS=bysplist
	 */
	private class ParseBySetPos : PartParser() {
		override fun parsePart(value: String, er: EventRecurrence): Int {
			val bysetpos = EventRecurrence.PartParser.parseNumberList(value, Integer.MIN_VALUE, Integer.MAX_VALUE, true)
			er.bysetpos = bysetpos
			er.bysetposCount = bysetpos.size
			return PARSED_BYSETPOS
		}
	}

	/**
	 * parses WKST={SU,MO,...}
	 */
	private class ParseWkst : PartParser() {
		override fun parsePart(value: String, er: EventRecurrence): Int {
			val wkst = sParseWeekdayMap[value]
					?: throw InvalidFormatException("Invalid WKST value: $value")
			er.wkst = wkst
			return PARSED_WKST
		}
	}

	companion object {
		private val TAG = EventRecurrence::class.java!!.getSimpleName()

		val SECONDLY = 1
		val MINUTELY = 2
		val HOURLY = 3
		val DAILY = 4
		val WEEKLY = 5
		val MONTHLY = 6
		val YEARLY = 7

		val SU = 0x00010000
		val MO = 0x00020000
		val TU = 0x00040000
		val WE = 0x00080000
		val TH = 0x00100000
		val FR = 0x00200000
		val SA = 0x00400000

		/**
		 * maps a part string to a parser object
		 */
		private var sParsePartMap: HashMap<String, PartParser>? = null

		init {
			sParsePartMap = HashMap()
			sParsePartMap!!["FREQ"] = ParseFreq()
			sParsePartMap!!["UNTIL"] = ParseUntil()
			sParsePartMap!!["COUNT"] = ParseCount()
			sParsePartMap!!["INTERVAL"] = ParseInterval()
			sParsePartMap!!["BYSECOND"] = ParseBySecond()
			sParsePartMap!!["BYMINUTE"] = ParseByMinute()
			sParsePartMap!!["BYHOUR"] = ParseByHour()
			sParsePartMap!!["BYDAY"] = ParseByDay()
			sParsePartMap!!["BYMONTHDAY"] = ParseByMonthDay()
			sParsePartMap!!["BYYEARDAY"] = ParseByYearDay()
			sParsePartMap!!["BYWEEKNO"] = ParseByWeekNo()
			sParsePartMap!!["BYMONTH"] = ParseByMonth()
			sParsePartMap!!["BYSETPOS"] = ParseBySetPos()
			sParsePartMap!!["WKST"] = ParseWkst()
		}

		/* values for bit vector that keeps track of what we have already seen */
		private val PARSED_FREQ = 1 shl 0
		private val PARSED_UNTIL = 1 shl 1
		private val PARSED_COUNT = 1 shl 2
		private val PARSED_INTERVAL = 1 shl 3
		private val PARSED_BYSECOND = 1 shl 4
		private val PARSED_BYMINUTE = 1 shl 5
		private val PARSED_BYHOUR = 1 shl 6
		private val PARSED_BYDAY = 1 shl 7
		private val PARSED_BYMONTHDAY = 1 shl 8
		private val PARSED_BYYEARDAY = 1 shl 9
		private val PARSED_BYWEEKNO = 1 shl 10
		private val PARSED_BYMONTH = 1 shl 11
		private val PARSED_BYSETPOS = 1 shl 12
		private val PARSED_WKST = 1 shl 13

		/**
		 * maps a FREQ value to an integer constant
		 */
		private val sParseFreqMap = HashMap<String, Int>()

		init {
			sParseFreqMap["SECONDLY"] = SECONDLY
			sParseFreqMap["MINUTELY"] = MINUTELY
			sParseFreqMap["HOURLY"] = HOURLY
			sParseFreqMap["DAILY"] = DAILY
			sParseFreqMap["WEEKLY"] = WEEKLY
			sParseFreqMap["MONTHLY"] = MONTHLY
			sParseFreqMap["YEARLY"] = YEARLY
		}

		/**
		 * maps a two-character weekday string to an integer constant
		 */
		private val sParseWeekdayMap = HashMap<String, Int>()

		init {
			sParseWeekdayMap["SU"] = SU
			sParseWeekdayMap["MO"] = MO
			sParseWeekdayMap["TU"] = TU
			sParseWeekdayMap["WE"] = WE
			sParseWeekdayMap["TH"] = TH
			sParseWeekdayMap["FR"] = FR
			sParseWeekdayMap["SA"] = SA
		}

		/**
		 * If set, allow lower-case recurrence rule strings.  Minor performance impact.
		 */
		private val ALLOW_LOWER_CASE = true

		/**
		 * If set, validate the value of UNTIL parts.  Minor performance impact.
		 */
		private val VALIDATE_UNTIL = false

		/**
		 * If set, require that only one of {UNTIL,COUNT} is present.  Breaks compat w/ old parser.
		 */
		private val ONLY_ONE_UNTIL_COUNT = false

		/**
		 * Converts one of the Calendar.SUNDAY constants to the SU, MO, etc.
		 * constants.  btw, I think we should switch to those here too, to
		 * get rid of this function, if possible.
		 */
		fun calendarDay2Day(day: Int): Int {
			when (day) {
				Calendar.SUNDAY -> return SU
				Calendar.MONDAY -> return MO
				Calendar.TUESDAY -> return TU
				Calendar.WEDNESDAY -> return WE
				Calendar.THURSDAY -> return TH
				Calendar.FRIDAY -> return FR
				Calendar.SATURDAY -> return SA
				else -> throw RuntimeException("bad day of week: $day")
			}
		}

		fun timeDay2Day(day: Int): Int {
			when (day) {
				Time.SUNDAY -> return SU
				Time.MONDAY -> return MO
				Time.TUESDAY -> return TU
				Time.WEDNESDAY -> return WE
				Time.THURSDAY -> return TH
				Time.FRIDAY -> return FR
				Time.SATURDAY -> return SA
				else -> throw RuntimeException("bad day of week: $day")
			}
		}

		fun day2TimeDay(day: Int): Int {
			when (day) {
				SU -> return Time.SUNDAY
				MO -> return Time.MONDAY
				TU -> return Time.TUESDAY
				WE -> return Time.WEDNESDAY
				TH -> return Time.THURSDAY
				FR -> return Time.FRIDAY
				SA -> return Time.SATURDAY
				else -> throw RuntimeException("bad day of week: $day")
			}
		}

		/**
		 * Converts one of the SU, MO, etc. constants to the Calendar.SUNDAY
		 * constants.  btw, I think we should switch to those here too, to
		 * get rid of this function, if possible.
		 */
		fun day2CalendarDay(day: Int): Int {
			when (day) {
				SU -> return Calendar.SUNDAY
				MO -> return Calendar.MONDAY
				TU -> return Calendar.TUESDAY
				WE -> return Calendar.WEDNESDAY
				TH -> return Calendar.THURSDAY
				FR -> return Calendar.FRIDAY
				SA -> return Calendar.SATURDAY
				else -> throw RuntimeException("bad day of week: $day")
			}
		}

		/**
		 * Converts one of the internal day constants (SU, MO, etc.) to the
		 * two-letter string representing that constant.
		 *
		 * @param day one the internal constants SU, MO, etc.
		 * @return the two-letter string for the day ("SU", "MO", etc.)
		 * @throws IllegalArgumentException Thrown if the day argument is not one of
		 * the defined day constants.
		 */
		private fun day2String(day: Int): String {
			when (day) {
				SU -> return "SU"
				MO -> return "MO"
				TU -> return "TU"
				WE -> return "WE"
				TH -> return "TH"
				FR -> return "FR"
				SA -> return "SA"
				else -> throw IllegalArgumentException("bad day argument: $day")
			}
		}

		private fun appendNumbers(s: StringBuilder, label: String,
		                          count: Int, values: IntArray) {
			var count = count
			if (count > 0) {
				s.append(label)
				count--
				for (i in 0 until count) {
					s.append(values[i])
					s.append(",")
				}
				s.append(values[count])
			}
		}

		/**
		 * Determines whether two integer arrays contain identical elements.
		 * The native implementation over-allocated the arrays (and may have stuff left over from
		 * a previous run), so we can't just check the arrays -- the separately-maintained count
		 * field also matters.  We assume that a null array will have a count of zero, and that the
		 * array can hold as many elements as the associated count indicates.
		 * TODO: replace this with Arrays.equals() when the old parser goes away.
		 */
		private fun arraysEqual(array1: IntArray, count1: Int, array2: IntArray, count2: Int): Boolean {
			if (count1 != count2) {
				return false
			}

			for (i in 0 until count1) {
				if (array1[i] != array2[i])
					return false
			}

			return true
		}
	}
}

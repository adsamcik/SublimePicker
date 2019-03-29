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

package com.appeaser.sublimepickerlibrary.utilities

import android.annotation.TargetApi
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RoundRectShape
import android.os.Build
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView

import com.appeaser.sublimepickerlibrary.R

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Utilities
 */
object SUtils {

	private val TAG = SUtils::class.java.getSimpleName()

	// Frequently used theme-dependent colors
	var COLOR_ACCENT: Int = 0
	var COLOR_CONTROL_HIGHLIGHT: Int = 0
	var COLOR_CONTROL_ACTIVATED: Int = 0
	var COLOR_BUTTON_NORMAL: Int = 0
	var COLOR_TEXT_PRIMARY: Int = 0
	var COLOR_TEXT_PRIMARY_INVERSE: Int = 0
	var COLOR_PRIMARY: Int = 0
	var COLOR_PRIMARY_DARK: Int = 0
	var COLOR_TEXT_SECONDARY: Int = 0
	var COLOR_BACKGROUND: Int = 0
	var COLOR_TEXT_SECONDARY_INVERSE: Int = 0

	// Corner radius for drawables
	var CORNER_RADIUS: Int = 0

	// flags for corners that need to be rounded
	val CORNER_TOP_LEFT = 0x01
	val CORNER_TOP_RIGHT = 0x02
	val CORNER_BOTTOM_RIGHT = 0x04
	val CORNER_BOTTOM_LEFT = 0x08
	val CORNERS_ALL = 0x0f

	val isApi_16_OrHigher: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN

	val isApi_17_OrHigher: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1

	val isApi_18_OrHigher: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2

	val isApi_21_OrHigher: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

	val isApi_22_OrHigher: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1

	val isApi_23_OrHigher: Boolean
		get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

	val STATE_ENABLED = 1
	val STATE_ACTIVATED = 1 shl 1
	val STATE_PRESSED = 1 shl 2

	private val STATE_SETS = arrayOfNulls<IntArray>(8)

	/**
	 * String for parsing dates.
	 */
	private val DATE_FORMAT = "MM/dd/yyyy"

	/**
	 * Date format for parsing dates.
	 */
	private val DATE_FORMATTER = SimpleDateFormat(DATE_FORMAT)

	private val CHANGE_YEAR = 1582

	fun initializeResources(context: Context) {
		val a = context.obtainStyledAttributes(
				intArrayOf(R.attr.colorAccent, R.attr.colorControlHighlight, R.attr.colorControlActivated, R.attr.colorButtonNormal, android.R.attr.textColorPrimary, android.R.attr.textColorPrimaryInverse, R.attr.colorPrimary, R.attr.colorPrimaryDark, android.R.attr.textColorSecondary, android.R.attr.colorBackground, android.R.attr.textColorSecondaryInverse))

		if (a.hasValue(0))
			COLOR_ACCENT = a.getColor(0, Color.TRANSPARENT)

		if (a.hasValue(1))
			COLOR_CONTROL_HIGHLIGHT = a.getColor(1, Color.TRANSPARENT)

		if (a.hasValue(2))
			COLOR_CONTROL_ACTIVATED = a.getColor(2, Color.TRANSPARENT)

		if (a.hasValue(3))
			COLOR_BUTTON_NORMAL = a.getColor(3, Color.TRANSPARENT)

		if (a.hasValue(4))
			COLOR_TEXT_PRIMARY = a.getColor(4, Color.TRANSPARENT)

		if (a.hasValue(5))
			COLOR_TEXT_PRIMARY_INVERSE = a.getColor(5, Color.TRANSPARENT)

		if (a.hasValue(6))
			COLOR_PRIMARY = a.getColor(6, Color.TRANSPARENT)

		if (a.hasValue(7))
			COLOR_PRIMARY_DARK = a.getColor(7, Color.TRANSPARENT)

		if (a.hasValue(8))
			COLOR_TEXT_SECONDARY = a.getColor(8, Color.TRANSPARENT)

		if (a.hasValue(9))
			COLOR_BACKGROUND = a.getColor(9, Color.TRANSPARENT)

		if (a.hasValue(10))
			COLOR_TEXT_SECONDARY_INVERSE = a.getColor(10, Color.TRANSPARENT)

		a.recycle()

		CORNER_RADIUS = context.resources
				.getDimensionPixelSize(R.dimen.control_corner_material)

		if (Config.DEBUG) {
			Log.i(TAG, "COLOR_ACCENT: " + Integer.toHexString(COLOR_ACCENT))
			Log.i(TAG, "COLOR_CONTROL_HIGHLIGHT: " + Integer.toHexString(COLOR_CONTROL_HIGHLIGHT))
			Log.i(TAG, "COLOR_CONTROL_ACTIVATED: " + Integer.toHexString(COLOR_CONTROL_ACTIVATED))
			Log.i(TAG, "COLOR_BUTTON_NORMAL: " + Integer.toHexString(COLOR_BUTTON_NORMAL))
			Log.i(TAG, "COLOR_TEXT_PRIMARY: " + Integer.toHexString(COLOR_TEXT_PRIMARY))
			Log.i(TAG, "COLOR_TEXT_PRIMARY_INVERSE: " + Integer.toHexString(COLOR_TEXT_PRIMARY_INVERSE))
			Log.i(TAG, "COLOR_PRIMARY: " + Integer.toHexString(COLOR_PRIMARY))
			Log.i(TAG, "COLOR_PRIMARY_DARK: " + Integer.toHexString(COLOR_PRIMARY_DARK))
			Log.i(TAG, "COLOR_TEXT_SECONDARY: " + Integer.toHexString(COLOR_TEXT_SECONDARY))
			Log.i(TAG, "COLOR_BACKGROUND: " + Integer.toHexString(COLOR_BACKGROUND))
			Log.i(TAG, "COLOR_TEXT_SECONDARY_INVERSE: " + Integer.toHexString(COLOR_TEXT_SECONDARY_INVERSE))
		}
	}

	fun setViewBackground(view: View, bg: Drawable) {
		val paddingL = view.paddingLeft
		val paddingT = view.paddingTop
		val paddingR = view.paddingRight
		val paddingB = view.paddingBottom

		if (isApi_16_OrHigher) {
			view.background = bg
		} else {

			view.setBackgroundDrawable(bg)
		}

		view.setPadding(paddingL, paddingT, paddingR, paddingB)
	}

	// Returns material styled button bg
	fun createButtonBg(context: Context,
	                   colorButtonNormal: Int,
	                   colorControlHighlight: Int): Drawable {
		return if (isApi_21_OrHigher) {
			createButtonRippleBg(context, colorButtonNormal,
					colorControlHighlight)
		} else createButtonNormalBg(context, colorControlHighlight)

	}

	// Button bg for API versions >= Lollipop
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private fun createButtonRippleBg(context: Context,
	                                 colorButtonNormal: Int,
	                                 colorControlHighlight: Int): Drawable {
		return RippleDrawable(ColorStateList.valueOf(colorControlHighlight), null, createButtonShape(context, colorButtonNormal))
	}

	// Button bg for API version < Lollipop
	private fun createButtonNormalBg(context: Context, colorControlHighlight: Int): Drawable {
		val sld = StateListDrawable()
		sld.addState(intArrayOf(android.R.attr.state_pressed),
				createButtonShape(context, colorControlHighlight))
		sld.addState(intArrayOf(),
				ColorDrawable(Color.TRANSPARENT))
		return sld
	}

	// Base button shape
	private fun createButtonShape(context: Context, color: Int): Drawable {
		// Translation of Lollipop's xml button-bg definition to Java
		val paddingH = context.resources
				.getDimensionPixelSize(R.dimen.button_padding_horizontal_material)
		val paddingV = context.resources
				.getDimensionPixelSize(R.dimen.button_padding_vertical_material)
		val insetH = context.resources
				.getDimensionPixelSize(R.dimen.button_inset_horizontal_material)
		val insetV = context.resources
				.getDimensionPixelSize(R.dimen.button_inset_vertical_material)

		val outerRadii = FloatArray(8)
		Arrays.fill(outerRadii, CORNER_RADIUS.toFloat())

		val r = RoundRectShape(outerRadii, null, null)

		val shapeDrawable = ShapeDrawable(r)
		shapeDrawable.paint.color = color
		shapeDrawable.setPadding(paddingH, paddingV, paddingH, paddingV)

		return InsetDrawable(shapeDrawable,
				insetH, insetV, insetH, insetV)
	}

	// Drawable for icons in 'ButtonLayout'
	fun createImageViewBg(colorButtonNormal: Int, colorControlHighlight: Int): Drawable {
		return if (isApi_21_OrHigher) {
			createImageViewRippleBg(colorButtonNormal, colorControlHighlight)
		} else createImageViewNormalBg(colorControlHighlight)

	}

	// Icon bg for API versions >= Lollipop
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private fun createImageViewRippleBg(colorButtonNormal: Int, colorControlHighlight: Int): Drawable {
		return RippleDrawable(ColorStateList.valueOf(colorControlHighlight), null, createImageViewShape(colorButtonNormal))
	}

	// Icon bg for API versions < Lollipop
	private fun createImageViewNormalBg(colorControlHighlight: Int): Drawable {
		val sld = StateListDrawable()
		sld.addState(intArrayOf(android.R.attr.state_pressed),
				createImageViewShape(colorControlHighlight))
		sld.addState(intArrayOf(),
				ColorDrawable(Color.TRANSPARENT))
		return sld
	}

	// Base icon bg shape
	private fun createImageViewShape(color: Int): Drawable {
		val ovalShape = OvalShape()

		val shapeDrawable = ShapeDrawable(ovalShape)
		shapeDrawable.paint.color = color

		return shapeDrawable
	}

	// Borrowed from MathUtils
	fun constrain(amount: Int, low: Int, high: Int): Int {
		return if (amount < low) low else if (amount > high) high else amount
	}

	// Borrowed from MathUtils
	fun constrain(amount: Long, low: Long, high: Long): Long {
		return if (amount < low) low else if (amount > high) high else amount
	}

	fun isLayoutRtlCompat(view: View): Boolean {
		return ViewCompat.getLayoutDirection(view) == ViewCompat.LAYOUT_DIRECTION_RTL
	}

	// Creates a drawable with the supplied color and corner radii
	fun createBgDrawable(color: Int, rTopLeft: Int,
	                     rTopRight: Int, rBottomRight: Int,
	                     rBottomLeft: Int): Drawable {
		val outerRadii = FloatArray(8)
		outerRadii[0] = rTopLeft.toFloat()
		outerRadii[1] = rTopLeft.toFloat()
		outerRadii[2] = rTopRight.toFloat()
		outerRadii[3] = rTopRight.toFloat()
		outerRadii[4] = rBottomRight.toFloat()
		outerRadii[5] = rBottomRight.toFloat()
		outerRadii[6] = rBottomLeft.toFloat()
		outerRadii[7] = rBottomLeft.toFloat()

		val r = RoundRectShape(outerRadii, null, null)

		val shapeDrawable = ShapeDrawable(r)
		shapeDrawable.paint.color = color

		return shapeDrawable
	}

	fun createOverflowButtonBg(pressedStateColor: Int): Drawable {
		return if (SUtils.isApi_21_OrHigher) {
			createOverflowButtonBgLollipop(pressedStateColor)
		} else createOverflowButtonBgBC(pressedStateColor)

	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private fun createOverflowButtonBgLollipop(pressedStateColor: Int): Drawable {
		return RippleDrawable(
				ColorStateList.valueOf(pressedStateColor), null, null)
	}

	private fun createOverflowButtonBgBC(pressedStateColor: Int): Drawable {
		val sld = StateListDrawable()
		sld.addState(intArrayOf(android.R.attr.state_pressed),
				createBgDrawable(pressedStateColor,
						0, CORNER_RADIUS, 0, 0))
		sld.addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
		return sld
	}

	/**
	 * Gets a calendar for locale bootstrapped with the value of a given calendar.
	 *
	 * @param oldCalendar The old calendar.
	 * @param locale      The locale.
	 */
	fun getCalendarForLocale(oldCalendar: Calendar?, locale: Locale): Calendar {
		if (oldCalendar == null) {
			return Calendar.getInstance(locale)
		} else {
			val currentTimeMillis = oldCalendar.timeInMillis
			val newCalendar = Calendar.getInstance(locale)
			newCalendar.timeInMillis = currentTimeMillis
			return newCalendar
		}
	}

	fun createThemeWrapper(context: Context,
	                       parentStyleAttr: Int, parentDefaultStyle: Int, childStyleAttr: Int,
	                       childDefaultStyle: Int): ContextThemeWrapper {
		val forParent = context.obtainStyledAttributes(
				intArrayOf(parentStyleAttr))
		val parentStyle = forParent.getResourceId(0, parentDefaultStyle)
		forParent.recycle()

		val forChild = context.obtainStyledAttributes(parentStyle,
				intArrayOf(childStyleAttr))
		val childStyleId = forChild.getResourceId(0, childDefaultStyle)
		forChild.recycle()

		return ContextThemeWrapper(context, childStyleId)
	}

	fun setViewBackground(view: View, bgColor: Int, corners: Int) {
		if (SUtils.isApi_21_OrHigher) {
			view.setBackgroundColor(bgColor)
		} else {
			SUtils.setViewBackground(view,
					SUtils.createBgDrawable(bgColor,
							if (corners and CORNER_TOP_LEFT != 0) CORNER_RADIUS else 0,
							if (corners and CORNER_TOP_RIGHT != 0) CORNER_RADIUS else 0,
							if (corners and CORNER_BOTTOM_RIGHT != 0) CORNER_RADIUS else 0,
							if (corners and CORNER_BOTTOM_LEFT != 0) CORNER_RADIUS else 0))
		}
	}

	fun setImageTintList(imageView: ImageView, colorStateList: ColorStateList) {
		if (isApi_21_OrHigher) {
			imageView.imageTintList = colorStateList
		} else {
			val drawable = imageView.drawable

			if (drawable != null) {
				val wrapped = DrawableCompat.wrap(drawable)
				DrawableCompat.setTintList(wrapped, colorStateList)
				imageView.setImageDrawable(wrapped)
			}
		}
	}

	init {
		STATE_SETS[0] = intArrayOf(0)
		STATE_SETS[1] = intArrayOf(android.R.attr.state_enabled)
		STATE_SETS[2] = intArrayOf(android.R.attr.state_activated)
		STATE_SETS[3] = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_activated)
		STATE_SETS[4] = intArrayOf(android.R.attr.state_pressed)
		STATE_SETS[5] = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed)
		STATE_SETS[6] = intArrayOf(android.R.attr.state_activated, android.R.attr.state_pressed)
		STATE_SETS[7] = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_activated, android.R.attr.state_pressed)
	}

	fun resolveStateSet(mask: Int): IntArray {
		return STATE_SETS[mask]
	}

	fun parseDate(date: String?, outDate: Calendar): Boolean {
		if (date == null || date.isEmpty()) {
			return false
		}

		try {
			val parsedDate = DATE_FORMATTER.parse(date)
			outDate.time = parsedDate
			return true
		} catch (e: ParseException) {
			Log.w(TAG, "Date: $date not in format: $DATE_FORMAT")
			return false
		}

	}

	/**
	 * Borrowed from [java.util.GregorianCalendar]
	 *
	 * @param year Year to check
	 * @return true if given `year` is a leap year, false otherwise
	 */
	private fun isLeapYear(year: Int): Boolean {
		return if (year > CHANGE_YEAR) {
			year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
		} else year % 4 == 0

	}

	fun getDaysInMonth(month: Int, year: Int): Int {
		when (month) {
			Calendar.JANUARY, Calendar.MARCH, Calendar.MAY, Calendar.JULY, Calendar.AUGUST, Calendar.OCTOBER, Calendar.DECEMBER -> return 31
			Calendar.APRIL, Calendar.JUNE, Calendar.SEPTEMBER, Calendar.NOVEMBER -> return 30
			Calendar.FEBRUARY ->
				// This is not correct. See isLeapYear(int) above
				//return (year % 4 == 0) ? 29 : 28;
				return if (isLeapYear(year)) 29 else 28
			else -> throw IllegalArgumentException("Invalid Month")
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	fun vibrateForDatePicker(view: View) {
		// Using a different haptic feedback constant
		view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY
				/*(5) - HapticFeedbackConstants.CALENDAR_DATE*/)
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	fun vibrateForTimePicker(view: View) {
		view.performHapticFeedback(if (isApi_21_OrHigher)
			HapticFeedbackConstants.CLOCK_TICK
		else
			HapticFeedbackConstants.VIRTUAL_KEY
		)
	}
}

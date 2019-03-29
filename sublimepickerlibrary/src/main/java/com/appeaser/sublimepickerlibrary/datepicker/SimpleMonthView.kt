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

import android.annotation.TargetApi
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.support.v4.view.ViewCompat
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat
import android.support.v4.widget.ExploreByTouchHelper
import android.text.TextPaint
import android.text.format.DateFormat
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView

import com.appeaser.sublimepickerlibrary.R
import com.appeaser.sublimepickerlibrary.common.DateTimePatternHelper
import com.appeaser.sublimepickerlibrary.utilities.Config
import com.appeaser.sublimepickerlibrary.utilities.SUtils

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * A calendar-like view displaying a specified month and the appropriate selectable day numbers
 * within the specified month.
 */
internal class SimpleMonthView : View {

	private val DRAW_RECT = 0
	private val DRAW_RECT_WITH_CURVE_ON_LEFT = 1
	private val DRAW_RECT_WITH_CURVE_ON_RIGHT = 2

	private val mMonthPaint = TextPaint()
	private val mDayOfWeekPaint = TextPaint()
	private val mDayPaint = TextPaint()
	private val mDaySelectorPaint = Paint()
	private val mDayHighlightPaint = Paint()
	private val mDayRangeSelectorPaint = Paint()

	private val mCalendar = Calendar.getInstance()
	private val mDayOfWeekLabelCalendar = Calendar.getInstance()

	private var mTouchHelper: MonthViewTouchHelper? = null

	private var mTitleFormatter: SimpleDateFormat? = null
	private var mDayOfWeekFormatter: SimpleDateFormat? = null
	private var mDayFormatter: NumberFormat? = null

	// Desired dimensions.
	private var mDesiredMonthHeight: Int = 0
	private var mDesiredDayOfWeekHeight: Int = 0
	private var mDesiredDayHeight: Int = 0
	private var mDesiredCellWidth: Int = 0
	private var mDesiredDaySelectorRadius: Int = 0

	private var mTitle: CharSequence? = null

	private var mMonth: Int = 0
	private var mYear: Int = 0

	// Dimensions as laid out.
	var monthHeight: Int = 0
		private set
	private var mDayOfWeekHeight: Int = 0
	private var mDayHeight: Int = 0
	var cellWidth: Int = 0
		private set
	private var mDaySelectorRadius: Int = 0

	private var mPaddedWidth: Int = 0
	private var mPaddedHeight: Int = 0

	/**
	 * The day of month for the selected day, or -1 if no day is selected.
	 */
	// private int mActivatedDay = -1;

	private val mActivatedDays = ActivatedDays()

	/**
	 * The day of month for today, or -1 if the today is not in the current
	 * month.
	 */
	private var mToday = DEFAULT_SELECTED_DAY

	/**
	 * The first day of the week (ex. Calendar.SUNDAY).
	 */
	private var mWeekStart = DEFAULT_WEEK_START

	/**
	 * The number of days (ex. 28) in the current month.
	 */
	private var mDaysInMonth: Int = 0

	/**
	 * The day of week (ex. Calendar.SUNDAY) for the first day of the current
	 * month.
	 */
	private var mDayOfWeekStart: Int = 0

	/**
	 * The day of month for the first (inclusive) enabled day.
	 */
	private var mEnabledDayStart = 1

	/**
	 * The day of month for the last (inclusive) enabled day.
	 */
	private var mEnabledDayEnd = 31

	/**
	 * Optional listener for handling day click actions.
	 */
	private var mOnDayClickListener: OnDayClickListener? = null

	private var mDayTextColor: ColorStateList? = null

	private var mTouchedItem = -1

	private var mContext: Context? = null

	private var mTouchSlopSquared: Int = 0

	private var mPaddingRangeIndicator: Float = 0.toFloat()

	val title: CharSequence
		get() {
			if (mTitle == null) {
				mTitle = mTitleFormatter!!.format(mCalendar.time)
			}
			return mTitle
		}

	private var mPendingCheckForTap: CheckForTap? = null
	private var mInitialTarget = -1
	private var mDownX: Int = 0
	private var mDownY: Int = 0

	@JvmOverloads
	constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.spMonthViewStyle) : super(context, attrs, defStyleAttr) {

		init()
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {

		init()
	}

	private fun init() {
		mContext = context

		mTouchSlopSquared = ViewConfiguration.get(mContext).scaledTouchSlop * ViewConfiguration.get(mContext).scaledTouchSlop

		val res = mContext!!.resources
		mDesiredMonthHeight = res.getDimensionPixelSize(R.dimen.sp_date_picker_month_height)
		mDesiredDayOfWeekHeight = res.getDimensionPixelSize(R.dimen.sp_date_picker_day_of_week_height)
		mDesiredDayHeight = res.getDimensionPixelSize(R.dimen.sp_date_picker_day_height)
		mDesiredCellWidth = res.getDimensionPixelSize(R.dimen.sp_date_picker_day_width)
		mDesiredDaySelectorRadius = res.getDimensionPixelSize(
				R.dimen.sp_date_picker_day_selector_radius)
		mPaddingRangeIndicator = res.getDimensionPixelSize(R.dimen.sp_month_view_range_padding).toFloat()

		// Set up accessibility components.
		mTouchHelper = MonthViewTouchHelper(this)

		ViewCompat.setAccessibilityDelegate(this, mTouchHelper)
		ViewCompat.setImportantForAccessibility(this,
				ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES)

		val locale = res.configuration.locale

		val titleFormat: String

		if (SUtils.isApi_18_OrHigher) {
			titleFormat = DateFormat.getBestDateTimePattern(locale, DEFAULT_TITLE_FORMAT)
		} else {
			titleFormat = DateTimePatternHelper.getBestDateTimePattern(locale,
					DateTimePatternHelper.PATTERN_MMMMy)
		}

		mTitleFormatter = SimpleDateFormat(titleFormat, locale)
		mDayOfWeekFormatter = SimpleDateFormat(DAY_OF_WEEK_FORMAT, locale)
		mDayFormatter = NumberFormat.getIntegerInstance(locale)

		initPaints(res)
	}

	/**
	 * Applies the specified text appearance resource to a paint, returning the
	 * text color if one is set in the text appearance.
	 *
	 * @param p     the paint to modify
	 * @param resId the resource ID of the text appearance
	 * @return the text color, if available
	 */
	private fun applyTextAppearance(p: Paint, resId: Int): ColorStateList? {
		// Workaround for inaccessible R.styleable.TextAppearance_*
		val tv = TextView(mContext)
		if (SUtils.isApi_23_OrHigher) {
			tv.setTextAppearance(resId)
		} else {

			tv.setTextAppearance(mContext, resId)
		}

		p.typeface = tv.typeface
		p.textSize = tv.textSize

		val textColor = tv.textColors

		if (textColor != null) {
			val enabledColor = textColor.getColorForState(View.ENABLED_STATE_SET, 0)
			p.color = enabledColor
		}

		return textColor
	}

	fun setMonthTextAppearance(resId: Int) {
		applyTextAppearance(mMonthPaint, resId)

		invalidate()
	}

	fun setDayOfWeekTextAppearance(resId: Int) {
		applyTextAppearance(mDayOfWeekPaint, resId)
		invalidate()
	}

	fun setDayTextAppearance(resId: Int) {
		val textColor = applyTextAppearance(mDayPaint, resId)
		if (textColor != null) {
			mDayTextColor = textColor
		}

		invalidate()
	}

	/**
	 * Sets up the text and style properties for painting.
	 */
	private fun initPaints(res: Resources) {
		val monthTypeface = res.getString(R.string.sp_date_picker_month_typeface)
		val dayOfWeekTypeface = res.getString(R.string.sp_date_picker_day_of_week_typeface)
		val dayTypeface = res.getString(R.string.sp_date_picker_day_typeface)

		val monthTextSize = res.getDimensionPixelSize(
				R.dimen.sp_date_picker_month_text_size)
		val dayOfWeekTextSize = res.getDimensionPixelSize(
				R.dimen.sp_date_picker_day_of_week_text_size)
		val dayTextSize = res.getDimensionPixelSize(
				R.dimen.sp_date_picker_day_text_size)

		mMonthPaint.isAntiAlias = true
		mMonthPaint.textSize = monthTextSize.toFloat()
		mMonthPaint.typeface = Typeface.create(monthTypeface, 0)
		mMonthPaint.textAlign = Paint.Align.CENTER
		mMonthPaint.style = Paint.Style.FILL

		mDayOfWeekPaint.isAntiAlias = true
		mDayOfWeekPaint.textSize = dayOfWeekTextSize.toFloat()
		mDayOfWeekPaint.typeface = Typeface.create(dayOfWeekTypeface, 0)
		mDayOfWeekPaint.textAlign = Paint.Align.CENTER
		mDayOfWeekPaint.style = Paint.Style.FILL

		mDaySelectorPaint.isAntiAlias = true
		mDaySelectorPaint.style = Paint.Style.FILL

		mDayHighlightPaint.isAntiAlias = true
		mDayHighlightPaint.style = Paint.Style.FILL

		mDayRangeSelectorPaint.isAntiAlias = true
		mDayRangeSelectorPaint.style = Paint.Style.FILL

		mDayPaint.isAntiAlias = true
		mDayPaint.textSize = dayTextSize.toFloat()
		mDayPaint.typeface = Typeface.create(dayTypeface, 0)
		mDayPaint.textAlign = Paint.Align.CENTER
		mDayPaint.style = Paint.Style.FILL
	}

	fun setMonthTextColor(monthTextColor: ColorStateList) {
		val enabledColor = monthTextColor.getColorForState(View.ENABLED_STATE_SET, 0)
		mMonthPaint.color = enabledColor
		invalidate()
	}

	fun setDayOfWeekTextColor(dayOfWeekTextColor: ColorStateList) {
		val enabledColor = dayOfWeekTextColor.getColorForState(View.ENABLED_STATE_SET, 0)
		mDayOfWeekPaint.color = enabledColor
		invalidate()
	}

	fun setDayTextColor(dayTextColor: ColorStateList) {
		mDayTextColor = dayTextColor
		invalidate()
	}

	fun setDaySelectorColor(dayBackgroundColor: ColorStateList) {
		val activatedColor = dayBackgroundColor.getColorForState(
				SUtils.resolveStateSet(SUtils.STATE_ENABLED or SUtils.STATE_ACTIVATED), 0)
		mDaySelectorPaint.color = activatedColor
		mDayRangeSelectorPaint.color = activatedColor
		// TODO: expose as attr?
		mDayRangeSelectorPaint.alpha = 150

		invalidate()
	}

	fun setDayHighlightColor(dayHighlightColor: ColorStateList) {
		val pressedColor = dayHighlightColor.getColorForState(
				SUtils.resolveStateSet(SUtils.STATE_ENABLED or SUtils.STATE_PRESSED), 0)
		mDayHighlightPaint.color = pressedColor
		invalidate()
	}

	fun setOnDayClickListener(listener: OnDayClickListener) {
		mOnDayClickListener = listener
	}

	public override fun dispatchHoverEvent(event: MotionEvent): Boolean {
		// First right-of-refusal goes the touch exploration helper.
		return mTouchHelper!!.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
	}

	private fun isStillAClick(x: Int, y: Int): Boolean {
		return (x - mDownX) * (x - mDownX) + (y - mDownY) * (y - mDownY) <= mTouchSlopSquared
	}

	private inner class CheckForTap : Runnable {

		override fun run() {
			mTouchedItem = getDayAtLocation(mDownX, mDownY)
			invalidate()
		}
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		val x = (event.x + 0.5f).toInt()
		val y = (event.y + 0.5f).toInt()

		val action = event.action
		when (action) {
			MotionEvent.ACTION_DOWN -> {
				mDownX = x
				mDownY = y

				mInitialTarget = getDayAtLocation(mDownX, mDownY)

				if (mInitialTarget < 0) {
					return false
				}

				if (mPendingCheckForTap == null) {
					mPendingCheckForTap = CheckForTap()
				}
				postDelayed(mPendingCheckForTap, ViewConfiguration.getTapTimeout().toLong())
			}
			MotionEvent.ACTION_MOVE -> if (!isStillAClick(x, y)) {
				if (mPendingCheckForTap != null) {
					removeCallbacks(mPendingCheckForTap)
				}

				mInitialTarget = -1

				if (mTouchedItem >= 0) {
					mTouchedItem = -1
					invalidate()
				}
			}
			MotionEvent.ACTION_UP -> {
				onDayClicked(mInitialTarget)
				if (mPendingCheckForTap != null) {
					removeCallbacks(mPendingCheckForTap)
				}
				// Reset touched day on stream end.
				mTouchedItem = -1
				mInitialTarget = -1
				invalidate()
			}
			// Fall through.
			MotionEvent.ACTION_CANCEL -> {
				if (mPendingCheckForTap != null) {
					removeCallbacks(mPendingCheckForTap)
				}
				mTouchedItem = -1
				mInitialTarget = -1
				invalidate()
			}
		}
		return true
	}

	override fun onDraw(canvas: Canvas) {
		if (Config.DEBUG) {
			Log.i(TAG, "onDraw(Canvas)")
		}

		val paddingLeft = paddingLeft
		val paddingTop = paddingTop
		canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())

		drawMonth(canvas)
		drawDaysOfWeek(canvas)
		drawDays(canvas)

		canvas.translate((-paddingLeft).toFloat(), (-paddingTop).toFloat())
	}

	private fun drawMonth(canvas: Canvas) {
		val x = mPaddedWidth / 2f

		// Vertically centered within the month header height.
		val lineHeight = mMonthPaint.ascent() + mMonthPaint.descent()
		val y = (monthHeight - lineHeight) / 2f

		canvas.drawText(title.toString(), x, y, mMonthPaint)
	}

	private fun drawDaysOfWeek(canvas: Canvas) {
		val p = mDayOfWeekPaint
		val headerHeight = monthHeight
		val rowHeight = mDayOfWeekHeight
		val colWidth = cellWidth

		// Text is vertically centered within the day of week height.
		val halfLineHeight = (p.ascent() + p.descent()) / 2f
		val rowCenter = headerHeight + rowHeight / 2

		for (col in 0 until DAYS_IN_WEEK) {
			val colCenter = colWidth * col + colWidth / 2
			val colCenterRtl: Int
			if (SUtils.isLayoutRtlCompat(this)) {
				colCenterRtl = mPaddedWidth - colCenter
			} else {
				colCenterRtl = colCenter
			}

			val dayOfWeek = (col + mWeekStart) % DAYS_IN_WEEK
			val label = getDayOfWeekLabel(dayOfWeek)
			canvas.drawText(label, colCenterRtl.toFloat(), rowCenter - halfLineHeight, p)
		}
	}

	private fun getDayOfWeekLabel(dayOfWeek: Int): String {
		mDayOfWeekLabelCalendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
		return mDayOfWeekFormatter!!.format(mDayOfWeekLabelCalendar.time)
	}

	/**
	 * Draws the month days.
	 */
	private fun drawDays(canvas: Canvas) {
		val p = mDayPaint
		val headerHeight = monthHeight + mDayOfWeekHeight
		//final int rowHeight = mDayHeight;
		val rowHeight = mDayHeight.toFloat()
		//final int colWidth = mCellWidth;
		val colWidth = cellWidth.toFloat()

		// Text is vertically centered within the row height.
		val halfLineHeight = (p.ascent() + p.descent()) / 2f
		//int rowCenter = headerHeight + rowHeight / 2;
		var rowCenter = headerHeight + rowHeight / 2f

		var day = 1
		var col = findDayOffset()
		while (day <= mDaysInMonth) {
			//final int colCenter = colWidth * col + colWidth / 2;
			val colCenter = colWidth * col + colWidth / 2f
			//final int colCenterRtl;
			val colCenterRtl: Float
			if (SUtils.isLayoutRtlCompat(this)) {
				colCenterRtl = mPaddedWidth - colCenter
			} else {
				colCenterRtl = colCenter
			}

			var stateMask = 0

			val isDayEnabled = isDayEnabled(day)
			if (isDayEnabled) {
				stateMask = stateMask or SUtils.STATE_ENABLED
			}

			val isDayInActivatedRange = mActivatedDays.isValid && mActivatedDays.isActivated(day)
			val isSelected = mActivatedDays.isSelected(day)

			if (isSelected) {
				stateMask = stateMask or SUtils.STATE_ACTIVATED
				canvas.drawCircle(colCenterRtl, rowCenter, mDaySelectorRadius.toFloat(), mDaySelectorPaint)
			} else if (isDayInActivatedRange) {
				stateMask = stateMask or SUtils.STATE_ACTIVATED

				var bgShape = DRAW_RECT

				if (mActivatedDays.isSingleDay) {
					if (mActivatedDays.isStartOfMonth) {
						bgShape = DRAW_RECT_WITH_CURVE_ON_RIGHT
					} else {
						bgShape = DRAW_RECT_WITH_CURVE_ON_LEFT
					}
				} else if (mActivatedDays.isStartingDayOfRange(day)) {
					bgShape = DRAW_RECT_WITH_CURVE_ON_LEFT
				} else if (mActivatedDays.isEndingDayOfRange(day)) {
					bgShape = DRAW_RECT_WITH_CURVE_ON_RIGHT
				}

				// Use height to constrain the protrusion of the arc
				val constrainProtrusion = colWidth > rowHeight - 2 * mPaddingRangeIndicator
				val horDistFromCenter = if (constrainProtrusion)
					rowHeight / 2f - mPaddingRangeIndicator
				else
					colWidth / 2f

				when (bgShape) {
					DRAW_RECT_WITH_CURVE_ON_LEFT -> {
						val leftRectArcLeft = if ((colCenterRtl - horDistFromCenter).toInt() % 2 == 1)
							(colCenterRtl - horDistFromCenter).toInt() + 1
						else
							(colCenterRtl - horDistFromCenter).toInt()

						val leftRectArcRight = if ((colCenterRtl + horDistFromCenter).toInt() % 2 == 1)
							(colCenterRtl + horDistFromCenter).toInt() + 1
						else
							(colCenterRtl + horDistFromCenter).toInt()

						val leftArcRect = RectF(leftRectArcLeft.toFloat(),
								rowCenter - rowHeight / 2f + mPaddingRangeIndicator,
								leftRectArcRight.toFloat(),
								rowCenter + rowHeight / 2f - mPaddingRangeIndicator)

						canvas.drawArc(leftArcRect, 90f, 180f, true, mDayRangeSelectorPaint)

						canvas.drawRect(RectF(leftArcRect.centerX(),
								rowCenter - rowHeight / 2f + mPaddingRangeIndicator,
								colCenterRtl + colWidth / 2f,
								rowCenter + rowHeight / 2f - mPaddingRangeIndicator),
								mDayRangeSelectorPaint)
					}
					DRAW_RECT_WITH_CURVE_ON_RIGHT -> {
						val rightRectArcLeft = if ((colCenterRtl - horDistFromCenter).toInt() % 2 == 1)
							(colCenterRtl - horDistFromCenter).toInt() + 1
						else
							(colCenterRtl - horDistFromCenter).toInt()

						val rightRectArcRight = if ((colCenterRtl + horDistFromCenter).toInt() % 2 == 1)
							(colCenterRtl + horDistFromCenter).toInt() + 1
						else
							(colCenterRtl + horDistFromCenter).toInt()

						val rightArcRect = RectF(rightRectArcLeft.toFloat(),
								rowCenter - rowHeight / 2f + mPaddingRangeIndicator,
								rightRectArcRight.toFloat(),
								rowCenter + rowHeight / 2f - mPaddingRangeIndicator)

						canvas.drawArc(rightArcRect, 270f, 180f, true, mDayRangeSelectorPaint)

						canvas.drawRect(RectF(colCenterRtl - colWidth / 2f,
								rowCenter - rowHeight / 2f + mPaddingRangeIndicator,
								rightArcRect.centerX(),
								rowCenter + rowHeight / 2f - mPaddingRangeIndicator),
								mDayRangeSelectorPaint)
					}
					else -> canvas.drawRect(RectF(colCenterRtl - colWidth / 2f,
							rowCenter - rowHeight / 2f + mPaddingRangeIndicator,
							colCenterRtl + colWidth / 2f,
							rowCenter + rowHeight / 2f - mPaddingRangeIndicator),
							mDayRangeSelectorPaint)
				}
			}

			if (mTouchedItem == day) {
				stateMask = stateMask or SUtils.STATE_PRESSED

				if (isDayEnabled) {
					canvas.drawCircle(colCenterRtl, rowCenter,
							mDaySelectorRadius.toFloat(), mDayHighlightPaint)
				}
			}

			val isDayToday = mToday == day
			val dayTextColor: Int

			if (isDayToday && !isDayInActivatedRange) {
				dayTextColor = mDaySelectorPaint.color
			} else {
				val stateSet = SUtils.resolveStateSet(stateMask)
				dayTextColor = mDayTextColor!!.getColorForState(stateSet, 0)
			}
			p.color = dayTextColor

			canvas.drawText(mDayFormatter!!.format(day.toLong()), colCenterRtl, rowCenter - halfLineHeight, p)

			col++

			if (col == DAYS_IN_WEEK) {
				col = 0
				rowCenter += rowHeight
			}
			day++
		}
	}

	private fun isDayEnabled(day: Int): Boolean {
		return day >= mEnabledDayStart && day <= mEnabledDayEnd
	}

	private fun isValidDayOfMonth(day: Int): Boolean {
		return day >= 1 && day <= mDaysInMonth
	}

	fun selectAllDays() {
		setSelectedDays(1, SUtils.getDaysInMonth(mMonth, mYear), SelectedDate.Type.RANGE)
	}

	fun setSelectedDays(selectedDayStart: Int, selectedDayEnd: Int, selectedDateType: SelectedDate.Type) {
		mActivatedDays.startingDay = selectedDayStart
		mActivatedDays.endingDay = selectedDayEnd
		mActivatedDays.selectedDateType = selectedDateType

		// Invalidate cached accessibility information.
		mTouchHelper!!.invalidateRoot()
		invalidate()
	}

	/**
	 * Sets the first day of the week.
	 *
	 * @param weekStart which day the week should start on, valid values are
	 * [Calendar.SUNDAY] through [Calendar.SATURDAY]
	 */
	fun setFirstDayOfWeek(weekStart: Int) {
		if (isValidDayOfWeek(weekStart)) {
			mWeekStart = weekStart
		} else {
			mWeekStart = mCalendar.firstDayOfWeek
		}

		// Invalidate cached accessibility information.
		mTouchHelper!!.invalidateRoot()
		invalidate()
	}

	/**
	 * Sets all the parameters for displaying this week.
	 *
	 *
	 * Parameters have a default value and will only update if a new value is
	 * included, except for focus month, which will always default to no focus
	 * month if no value is passed in. The only required parameter is the week
	 * start.
	 *
	 * @param month            the month
	 * @param year             the year
	 * @param weekStart        which day the week should start on, valid values are
	 * [Calendar.SUNDAY] through [Calendar.SATURDAY]
	 * @param enabledDayStart  the first enabled day
	 * @param enabledDayEnd    the last enabled day
	 * @param selectedDayStart the start of the selected date range, or -1 for no selection
	 * @param selectedDayEnd   the end of the selected date range, or -1 for no selection
	 * @param selectedDateType RANGE or SINGLE
	 */
	fun setMonthParams(month: Int, year: Int, weekStart: Int, enabledDayStart: Int,
	                   enabledDayEnd: Int, selectedDayStart: Int, selectedDayEnd: Int,
	                   selectedDateType: SelectedDate.Type) {
		if (isValidMonth(month)) {
			mMonth = month
		}
		mYear = year

		mCalendar.set(Calendar.MONTH, mMonth)
		mCalendar.set(Calendar.YEAR, mYear)
		mCalendar.set(Calendar.DAY_OF_MONTH, 1)
		mDayOfWeekStart = mCalendar.get(Calendar.DAY_OF_WEEK)

		if (isValidDayOfWeek(weekStart)) {
			mWeekStart = weekStart
		} else {
			mWeekStart = mCalendar.firstDayOfWeek
		}

		// Figure out what day today is.
		val today = Calendar.getInstance()
		mToday = -1
		mDaysInMonth = SUtils.getDaysInMonth(mMonth, mYear)
		for (i in 0 until mDaysInMonth) {
			val day = i + 1
			if (sameDay(day, today)) {
				mToday = day
			}
		}

		mEnabledDayStart = SUtils.constrain(enabledDayStart, 1, mDaysInMonth)
		mEnabledDayEnd = SUtils.constrain(enabledDayEnd, mEnabledDayStart, mDaysInMonth)

		// Invalidate the old title.
		mTitle = null

		mActivatedDays.startingDay = selectedDayStart
		mActivatedDays.endingDay = selectedDayEnd
		mActivatedDays.selectedDateType = selectedDateType

		// Invalidate cached accessibility information.
		mTouchHelper!!.invalidateRoot()
	}

	private fun sameDay(day: Int, today: Calendar): Boolean {
		return (mYear == today.get(Calendar.YEAR) && mMonth == today.get(Calendar.MONTH)
				&& day == today.get(Calendar.DAY_OF_MONTH))
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val preferredHeight = (mDesiredDayHeight * MAX_WEEKS_IN_MONTH
				+ mDesiredDayOfWeekHeight + mDesiredMonthHeight
				+ paddingTop + paddingBottom)

		val preferredWidth = (mDesiredCellWidth * DAYS_IN_WEEK
				+ (if (SUtils.isApi_17_OrHigher) paddingStart else paddingLeft)
				+ if (SUtils.isApi_17_OrHigher) paddingEnd else paddingRight)
		val resolvedWidth = View.resolveSize(preferredWidth, widthMeasureSpec)
		val resolvedHeight = View.resolveSize(preferredHeight, heightMeasureSpec)
		setMeasuredDimension(resolvedWidth, resolvedHeight)
	}

	override fun onRtlPropertiesChanged(/*@ResolvedLayoutDir*/layoutDirection: Int) {
		super.onRtlPropertiesChanged(layoutDirection)

		requestLayout()
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		if (!changed) {
			return
		}

		// Let's initialize a completely reasonable number of variables.
		val w = right - left
		val h = bottom - top
		val paddingLeft = paddingLeft
		val paddingTop = paddingTop
		val paddingRight = paddingRight
		val paddingBottom = paddingBottom
		val paddedRight = w - paddingRight
		val paddedBottom = h - paddingBottom
		val paddedWidth = paddedRight - paddingLeft
		val paddedHeight = paddedBottom - paddingTop
		if (paddedWidth == mPaddedWidth || paddedHeight == mPaddedHeight) {
			return
		}

		mPaddedWidth = paddedWidth
		mPaddedHeight = paddedHeight

		// We may have been laid out smaller than our preferred size. If so,
		// scale all dimensions to fit.
		val measuredPaddedHeight = measuredHeight - paddingTop - paddingBottom
		val scaleH = paddedHeight / measuredPaddedHeight.toFloat()
		val monthHeight = (mDesiredMonthHeight * scaleH).toInt()
		val cellWidth = mPaddedWidth / DAYS_IN_WEEK
		this.monthHeight = monthHeight
		mDayOfWeekHeight = (mDesiredDayOfWeekHeight * scaleH).toInt()
		mDayHeight = (mDesiredDayHeight * scaleH).toInt()
		this.cellWidth = cellWidth

		// Compute the largest day selector radius that's still within the clip
		// bounds and desired selector radius.
		val maxSelectorWidth = cellWidth / 2 + Math.min(paddingLeft, paddingRight)
		val maxSelectorHeight = mDayHeight / 2 + paddingBottom
		mDaySelectorRadius = Math.min(mDesiredDaySelectorRadius,
				Math.min(maxSelectorWidth, maxSelectorHeight))

		// Invalidate cached accessibility information.
		mTouchHelper!!.invalidateRoot()
	}

	private fun findDayOffset(): Int {
		val offset = mDayOfWeekStart - mWeekStart
		return if (mDayOfWeekStart < mWeekStart) {
			offset + DAYS_IN_WEEK
		} else offset
	}

	/**
	 * Calculates the day of the month at the specified touch position. Returns
	 * the day of the month or -1 if the position wasn't in a valid day.
	 *
	 * @param x the x position of the touch event
	 * @param y the y position of the touch event
	 * @return the day of the month at (x, y), or -1 if the position wasn't in
	 * a valid day
	 */
	//private int getDayAtLocation(int x, int y) {
	fun getDayAtLocation(x: Int, y: Int): Int {
		val paddedX = x - paddingLeft
		if (paddedX < 0 || paddedX >= mPaddedWidth) {
			return -1
		}

		val headerHeight = monthHeight + mDayOfWeekHeight
		val paddedY = y - paddingTop
		if (paddedY < headerHeight || paddedY >= mPaddedHeight) {
			return -1
		}

		// Adjust for RTL after applying padding.
		val paddedXRtl: Int
		if (SUtils.isLayoutRtlCompat(this)) {
			paddedXRtl = mPaddedWidth - paddedX
		} else {
			paddedXRtl = paddedX
		}

		val row = (paddedY - headerHeight) / mDayHeight
		val col = paddedXRtl * DAYS_IN_WEEK / mPaddedWidth
		val index = col + row * DAYS_IN_WEEK
		val day = index + 1 - findDayOffset()
		return if (!isValidDayOfMonth(day)) {
			-1
		} else day

	}

	/**
	 * Calculates the bounds of the specified day.
	 *
	 * @param id        the day of the month
	 * @param outBounds the rect to populate with bounds
	 */
	private fun getBoundsForDay(id: Int, outBounds: Rect): Boolean {
		if (!isValidDayOfMonth(id)) {
			return false
		}

		val index = id - 1 + findDayOffset()

		// Compute left edge, taking into account RTL.
		val col = index % DAYS_IN_WEEK
		val colWidth = cellWidth
		val left: Int
		if (SUtils.isLayoutRtlCompat(this)) {
			left = width - paddingRight - (col + 1) * colWidth
		} else {
			left = paddingLeft + col * colWidth
		}

		// Compute top edge.
		val row = index / DAYS_IN_WEEK
		val rowHeight = mDayHeight
		val headerHeight = monthHeight + mDayOfWeekHeight
		val top = paddingTop + headerHeight + row * rowHeight

		outBounds.set(left, top, left + colWidth, top + rowHeight)

		return true
	}

	/**
	 * Called when the user clicks on a day. Handles callbacks to the
	 * [OnDayClickListener] if one is set.
	 *
	 * @param day the day that was clicked
	 */
	private fun onDayClicked(day: Int): Boolean {
		if (!isValidDayOfMonth(day) || !isDayEnabled(day)) {
			return false
		}

		if (mOnDayClickListener != null) {
			val date = Calendar.getInstance()
			date.set(mYear, mMonth, day)

			mOnDayClickListener!!.onDayClick(this, date)
		}

		// This is a no-op if accessibility is turned off.
		mTouchHelper!!.sendEventForVirtualView(day, AccessibilityEvent.TYPE_VIEW_CLICKED)
		return true
	}

	private inner class MonthViewTouchHelper(forView: View) : ExploreByTouchHelper(forView) {

		private val mTempRect = Rect()
		private val mTempCalendar = Calendar.getInstance()

		override fun getVirtualViewAt(x: Float, y: Float): Int {
			val day = getDayAtLocation((x + 0.5f).toInt(), (y + 0.5f).toInt())
			return if (day != -1) {
				day
			} else ExploreByTouchHelper.INVALID_ID
		}

		override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
			for (day in 1..mDaysInMonth) {
				virtualViewIds.add(day)
			}
		}

		override fun onPopulateEventForVirtualView(virtualViewId: Int, event: AccessibilityEvent) {
			event.contentDescription = getDayDescription(virtualViewId)
		}

		override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
			val hasBounds = getBoundsForDay(virtualViewId, mTempRect)

			if (!hasBounds) {
				// The day is invalid, kill the node.
				mTempRect.setEmpty()
				node.contentDescription = ""
				node.setBoundsInParent(mTempRect)
				node.isVisibleToUser = false
				return
			}

			node.text = getDayText(virtualViewId)
			node.contentDescription = getDayDescription(virtualViewId)
			node.setBoundsInParent(mTempRect)

			val isDayEnabled = isDayEnabled(virtualViewId)
			if (isDayEnabled) {
				node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
			}

			node.isEnabled = isDayEnabled

			if (mActivatedDays.isValid && mActivatedDays.isActivated(virtualViewId)) {
				// TODO: This should use activated once that's supported.
				node.isChecked = true
			}
		}

		override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
			when (action) {
				AccessibilityNodeInfoCompat.ACTION_CLICK -> return onDayClicked(virtualViewId)
			}

			return false
		}

		/**
		 * Generates a description for a given virtual view.
		 *
		 * @param id the day to generate a description for
		 * @return a description of the virtual view
		 */
		private fun getDayDescription(id: Int): CharSequence {
			if (isValidDayOfMonth(id)) {
				mTempCalendar.set(mYear, mMonth, id)
				return DateFormat.format(DATE_FORMAT, mTempCalendar.timeInMillis)
			}

			return ""
		}

		/**
		 * Generates displayed text for a given virtual view.
		 *
		 * @param id the day to generate text for
		 * @return the visible text of the virtual view
		 */
		private fun getDayText(id: Int): CharSequence? {
			return if (isValidDayOfMonth(id)) {
				mDayFormatter!!.format(id.toLong())
			} else null

		}

		companion object {

			private val DATE_FORMAT = "dd MMMM yyyy"
		}
	}

	fun composeDate(day: Int): Calendar? {
		if (!isValidDayOfMonth(day) || !isDayEnabled(day)) {
			return null
		}

		val date = Calendar.getInstance()
		date.set(mYear, mMonth, day)
		return date
	}

	inner class ActivatedDays {
		var startingDay = -1
		var endingDay = -1
		var selectedDateType: SelectedDate.Type? = null

		val isValid: Boolean
			get() = startingDay != -1 && endingDay != -1

		val isSingleDay: Boolean
			get() = startingDay == endingDay

		// experimental
		val selectedDay: Int
			get() = if (selectedDateType == SelectedDate.Type.SINGLE && startingDay == endingDay) {
				startingDay
			} else -1

		/**
		 * Kind of a hack. Used in conjunction with isSingleDay() to determine
		 * the side on which the curved surface will fall.
		 * We assume that if this is the starting day of
		 * this month, its also the end of selected date range. If this returns false,
		 * we consider the selectedDay to be the beginning of selected date range.
		 *
		 * @return true if startingDay is the first day of the month, false otherwise.
		 */
		val isStartOfMonth: Boolean
			get() = startingDay == 1

		fun reset() {
			endingDay = -1
			startingDay = endingDay
		}

		fun isActivated(day: Int): Boolean {
			return day >= startingDay && day <= endingDay
		}

		fun isStartingDayOfRange(day: Int): Boolean {
			return day == startingDay
		}

		fun isEndingDayOfRange(day: Int): Boolean {
			return day == endingDay
		}

		fun isSelected(day: Int): Boolean {
			return (selectedDateType == SelectedDate.Type.SINGLE
					&& startingDay == day
					&& endingDay == day)
		}

		fun hasSelectedDay(): Boolean {
			return (selectedDateType == SelectedDate.Type.SINGLE
					&& startingDay == endingDay && startingDay != -1)
		}
	}

	/**
	 * Handles callbacks when the user clicks on a time object.
	 */
	interface OnDayClickListener {
		fun onDayClick(view: SimpleMonthView, day: Calendar)
	}

	companion object {
		private val TAG = SimpleMonthView::class.java!!.getSimpleName()

		private val DAYS_IN_WEEK = 7
		private val MAX_WEEKS_IN_MONTH = 6

		private val DEFAULT_SELECTED_DAY = -1
		private val DEFAULT_WEEK_START = Calendar.SUNDAY

		private val DEFAULT_TITLE_FORMAT = "MMMMy"
		private val DAY_OF_WEEK_FORMAT: String

		init {
			// Deals with the change in usage of `EEEEE` pattern.
			// See method `SimpleDateFormat#appendDayOfWeek(...)` for more details.
			if (SUtils.isApi_18_OrHigher) {
				DAY_OF_WEEK_FORMAT = "EEEEE"
			} else {
				DAY_OF_WEEK_FORMAT = "E"
			}
		}

		private fun isValidDayOfWeek(day: Int): Boolean {
			return day >= Calendar.SUNDAY && day <= Calendar.SATURDAY
		}

		private fun isValidMonth(month: Int): Boolean {
			return month >= Calendar.JANUARY && month <= Calendar.DECEMBER
		}
	}
}

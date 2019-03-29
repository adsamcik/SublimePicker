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

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.TargetApi
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Region
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

import com.appeaser.sublimepickerlibrary.R
import com.appeaser.sublimepickerlibrary.utilities.SUtils

import java.util.ArrayList
import java.util.Calendar
import java.util.Locale

/**
 * View to show a clock circle picker (with one or two picking circles)
 */
class RadialTimePickerView : View {

	private val mInvalidateUpdateListener = InvalidateUpdateListener()

	private val mHours12Texts = arrayOfNulls<String>(12)
	private val mOuterHours24Texts = arrayOfNulls<String>(12)
	private val mInnerHours24Texts = arrayOfNulls<String>(12)
	private val mMinutesTexts = arrayOfNulls<String>(12)

	private val mPaint = arrayOfNulls<Paint>(2)
	private val mAlpha = arrayOfNulls<IntHolder>(2)

	private val mPaintCenter = Paint()

	private val mPaintSelector = Array<Array<Paint>>(2) { arrayOfNulls(3) }

	private var mSelectorColor: Int = 0
	private var mSelectorDotColor: Int = 0

	private val mPaintBackground = Paint()

	private var mTypeface: Typeface? = null

	private val mTextColor = arrayOfNulls<ColorStateList>(3)
	private val mTextSize = IntArray(3)
	private val mTextInset = IntArray(3)

	private val mOuterTextX = Array(2) { FloatArray(12) }
	private val mOuterTextY = Array(2) { FloatArray(12) }

	private val mInnerTextX = FloatArray(12)
	private val mInnerTextY = FloatArray(12)

	private val mSelectionDegrees = IntArray(2)

	private val mHoursToMinutesAnims = ArrayList<Animator>()
	private val mMinuteToHoursAnims = ArrayList<Animator>()

	private var mTouchHelper: RadialPickerTouchHelper? = null

	private val mSelectorPath = Path()

	private var mIs24HourMode: Boolean = false
	private var mShowHours: Boolean = false

	/**
	 * When in 24-hour mode, indicates that the current hour is between
	 * 1 and 12 (inclusive).
	 */
	private var mIsOnInnerCircle: Boolean = false

	private var mSelectorRadius: Int = 0
	private var mSelectorStroke: Int = 0
	private var mSelectorDotRadius: Int = 0
	private var mCenterDotRadius: Int = 0

	private var mXCenter: Int = 0
	private var mYCenter: Int = 0
	private var mCircleRadius: Int = 0

	private var mMinDistForInnerNumber: Int = 0
	private var mMaxDistForOuterNumber: Int = 0
	private var mHalfwayDist: Int = 0

	private var mOuterTextHours: Array<String>? = null
	private var mInnerTextHours: Array<String>? = null
	private var mMinutesText: Array<String>? = null
	private var mTransition: AnimatorSet? = null

	private var mAmOrPm: Int = 0

	private var mDisabledAlpha: Float = 0.toFloat()

	private var mListener: OnValueSelectedListener? = null

	private var mInputEnabled = true

	val currentItemShowing: Int
		get() = if (mShowHours) HOURS else MINUTES

	/**
	 * Returns the current hour in 24-hour time.
	 *
	 * @return the current hour between 0 and 23 (inclusive)
	 */
	/**
	 * Sets the current hour in 24-hour time.
	 *
	 * @param hour the current hour between 0 and 23 (inclusive)
	 */
	var currentHour: Int
		get() = getHourForDegrees(mSelectionDegrees[HOURS], mIsOnInnerCircle)
		set(hour) = setCurrentHourInternal(hour, true, false)

	// Returns minutes in 0-59 range
	var currentMinute: Int
		get() = getMinuteForDegrees(mSelectionDegrees[MINUTES])
		set(minute) = setCurrentMinuteInternal(minute, true)

	var amOrPm: Int
		get() = mAmOrPm
		set(`val`) {
			mAmOrPm = `val` % 2
			invalidate()
			mTouchHelper!!.invalidateRoot()
		}

	private var mChangedDuringTouch = false

	interface OnValueSelectedListener {
		fun onValueSelected(pickerIndex: Int, newValue: Int, autoAdvance: Boolean)
	}

	constructor(context: Context) : this(context, null) {}

	@JvmOverloads
	constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int = R.attr.spRadialTimePickerStyle) : super(context, attrs, defStyleAttr) {
		init(attrs, defStyleAttr, R.style.RadialTimePickerViewStyle)
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs) {
		init(attrs, defStyleAttr, defStyleRes)
	}

	private fun init(attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
		val context = context

		// Pull disabled alpha from theme.
		val outValue = TypedValue()
		context.theme.resolveAttribute(android.R.attr.disabledAlpha, outValue, true)
		mDisabledAlpha = outValue.float

		// process style attributes
		val res = resources
		val a = context.obtainStyledAttributes(attrs, R.styleable.RadialTimePickerView,
				defStyleAttr, defStyleRes)

		mTypeface = Typeface.create("sans-serif", Typeface.NORMAL)

		// Initialize all alpha values to opaque.
		for (i in mAlpha.indices) {
			mAlpha[i] = IntHolder(ALPHA_OPAQUE)
		}

		mTextColor[HOURS] = a.getColorStateList(R.styleable.RadialTimePickerView_spNumbersTextColor)
		mTextColor[HOURS_INNER] = a.getColorStateList(R.styleable.RadialTimePickerView_spNumbersInnerTextColor)
		mTextColor[MINUTES] = mTextColor[HOURS]

		mPaint[HOURS] = Paint()
		mPaint[HOURS].setAntiAlias(true)
		mPaint[HOURS].setTextAlign(Paint.Align.CENTER)

		mPaint[MINUTES] = Paint()
		mPaint[MINUTES].setAntiAlias(true)
		mPaint[MINUTES].setTextAlign(Paint.Align.CENTER)

		val selectorColors = a.getColorStateList(
				R.styleable.RadialTimePickerView_spNumbersSelectorColor)

		var selectorActivatedColor = Color.BLACK
		if (selectorColors != null) {
			selectorActivatedColor = selectorColors.getColorForState(
					SUtils.resolveStateSet(SUtils.STATE_ENABLED or SUtils.STATE_ACTIVATED), 0)
		}

		mPaintCenter.color = selectorActivatedColor
		mPaintCenter.isAntiAlias = true

		val activatedStateSet = SUtils.resolveStateSet(SUtils.STATE_ENABLED or SUtils.STATE_ACTIVATED)

		mSelectorColor = selectorActivatedColor
		mSelectorDotColor = mTextColor[HOURS].getColorForState(activatedStateSet, 0)

		mPaintSelector[HOURS][SELECTOR_CIRCLE] = Paint()
		mPaintSelector[HOURS][SELECTOR_CIRCLE].isAntiAlias = true

		mPaintSelector[HOURS][SELECTOR_DOT] = Paint()
		mPaintSelector[HOURS][SELECTOR_DOT].isAntiAlias = true

		mPaintSelector[HOURS][SELECTOR_LINE] = Paint()
		mPaintSelector[HOURS][SELECTOR_LINE].isAntiAlias = true
		mPaintSelector[HOURS][SELECTOR_LINE].strokeWidth = 2f

		mPaintSelector[MINUTES][SELECTOR_CIRCLE] = Paint()
		mPaintSelector[MINUTES][SELECTOR_CIRCLE].isAntiAlias = true

		mPaintSelector[MINUTES][SELECTOR_DOT] = Paint()
		mPaintSelector[MINUTES][SELECTOR_DOT].isAntiAlias = true

		mPaintSelector[MINUTES][SELECTOR_LINE] = Paint()
		mPaintSelector[MINUTES][SELECTOR_LINE].isAntiAlias = true
		mPaintSelector[MINUTES][SELECTOR_LINE].strokeWidth = 2f

		mPaintBackground.color = a.getColor(R.styleable.RadialTimePickerView_spNumbersBackgroundColor,
				ContextCompat.getColor(context, R.color.timepicker_default_numbers_background_color_material))
		mPaintBackground.isAntiAlias = true

		mSelectorRadius = res.getDimensionPixelSize(R.dimen.sp_timepicker_selector_radius)
		mSelectorStroke = res.getDimensionPixelSize(R.dimen.sp_timepicker_selector_stroke)
		mSelectorDotRadius = res.getDimensionPixelSize(R.dimen.sp_timepicker_selector_dot_radius)
		mCenterDotRadius = res.getDimensionPixelSize(R.dimen.sp_timepicker_center_dot_radius)

		mTextSize[HOURS] = res.getDimensionPixelSize(R.dimen.sp_timepicker_text_size_normal)
		mTextSize[MINUTES] = res.getDimensionPixelSize(R.dimen.sp_timepicker_text_size_normal)
		mTextSize[HOURS_INNER] = res.getDimensionPixelSize(R.dimen.sp_timepicker_text_size_inner)

		mTextInset[HOURS] = res.getDimensionPixelSize(R.dimen.sp_timepicker_text_inset_normal)
		mTextInset[MINUTES] = res.getDimensionPixelSize(R.dimen.sp_timepicker_text_inset_normal)
		mTextInset[HOURS_INNER] = res.getDimensionPixelSize(R.dimen.sp_timepicker_text_inset_inner)

		mShowHours = true
		mIs24HourMode = false
		mAmOrPm = AM

		// Set up accessibility components.
		mTouchHelper = RadialPickerTouchHelper()
		ViewCompat.setAccessibilityDelegate(this, mTouchHelper)

		if (ViewCompat.getImportantForAccessibility(this) == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
			ViewCompat.setImportantForAccessibility(this, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES)
		}

		initHoursAndMinutesText()
		initData()

		a.recycle()

		// Initial values
		val calendar = Calendar.getInstance(Locale.getDefault())
		val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
		val currentMinute = calendar.get(Calendar.MINUTE)

		setCurrentHourInternal(currentHour, false, false)
		setCurrentMinuteInternal(currentMinute, false)

		isHapticFeedbackEnabled = true
	}

	fun initialize(hour: Int, minute: Int, is24HourMode: Boolean) {
		if (mIs24HourMode != is24HourMode) {
			mIs24HourMode = is24HourMode
			initData()
		}

		setCurrentHourInternal(hour, false, false)
		setCurrentMinuteInternal(minute, false)
	}

	fun setCurrentItemShowing(item: Int, animate: Boolean) {
		when (item) {
			HOURS -> showHours(animate)
			MINUTES -> showMinutes(animate)
			else -> Log.e(TAG, "ClockView does not support showing item $item")
		}
	}

	fun setOnValueSelectedListener(listener: OnValueSelectedListener) {
		mListener = listener
	}

	/**
	 * Sets the current hour.
	 *
	 * @param hour        The current hour
	 * @param callback    Whether the value listener should be invoked
	 * @param autoAdvance Whether the listener should auto-advance to the next
	 * selection mode, e.g. hour to minutes
	 */
	private fun setCurrentHourInternal(hour: Int, callback: Boolean, autoAdvance: Boolean) {
		val degrees = hour % 12 * DEGREES_FOR_ONE_HOUR
		mSelectionDegrees[HOURS] = degrees

		// 0 is 12 AM (midnight) and 12 is 12 PM (noon).
		val amOrPm = if (hour == 0 || hour % 24 < 12) AM else PM
		val isOnInnerCircle = getInnerCircleForHour(hour)
		if (mAmOrPm != amOrPm || mIsOnInnerCircle != isOnInnerCircle) {
			mAmOrPm = amOrPm
			mIsOnInnerCircle = isOnInnerCircle

			initData()
			mTouchHelper!!.invalidateRoot()
		}

		invalidate()

		if (callback && mListener != null) {
			mListener!!.onValueSelected(HOURS, hour, autoAdvance)
		}
	}

	private fun getHourForDegrees(degrees: Int, innerCircle: Boolean): Int {
		var hour = degrees / DEGREES_FOR_ONE_HOUR % 12
		if (mIs24HourMode) {
			// Convert the 12-hour value into 24-hour time based on where the
			// selector is positioned.
			if (!innerCircle && hour == 0) {
				// Outer circle is 1 through 12.
				hour = 12
			} else if (innerCircle && hour != 0) {
				// Inner circle is 13 through 23 and 0.
				hour += 12
			}
		} else if (mAmOrPm == PM) {
			hour += 12
		}
		return hour
	}

	/**
	 * @param hour the hour in 24-hour time or 12-hour time
	 */
	private fun getDegreesForHour(hour: Int): Int {
		var hour = hour
		// Convert to be 0-11.
		if (mIs24HourMode) {
			if (hour >= 12) {
				hour -= 12
			}
		} else if (hour == 12) {
			hour = 0
		}
		return hour * DEGREES_FOR_ONE_HOUR
	}

	/**
	 * @param hour the hour in 24-hour time or 12-hour time
	 */
	private fun getInnerCircleForHour(hour: Int): Boolean {
		return mIs24HourMode && (hour == 0 || hour > 12)
	}

	private fun setCurrentMinuteInternal(minute: Int, callback: Boolean) {
		mSelectionDegrees[MINUTES] = minute % MINUTES_IN_CIRCLE * DEGREES_FOR_ONE_MINUTE

		invalidate()

		if (callback && mListener != null) {
			mListener!!.onValueSelected(MINUTES, minute, false)
		}
	}

	private fun getMinuteForDegrees(degrees: Int): Int {
		return degrees / DEGREES_FOR_ONE_MINUTE
	}

	private fun getDegreesForMinute(minute: Int): Int {
		return minute * DEGREES_FOR_ONE_MINUTE
	}

	private fun showHours(animate: Boolean) {
		if (mShowHours) {
			return
		}

		mShowHours = true

		if (animate) {
			startMinutesToHoursAnimation()
		}

		initData()
		invalidate()
		mTouchHelper!!.invalidateRoot()
	}

	private fun showMinutes(animate: Boolean) {
		if (!mShowHours) {
			return
		}

		mShowHours = false

		if (animate) {
			startHoursToMinutesAnimation()
		}

		initData()
		invalidate()
		mTouchHelper!!.invalidateRoot()
	}

	private fun initHoursAndMinutesText() {
		// Initialize the hours and minutes numbers.
		for (i in 0..11) {
			mHours12Texts[i] = String.format("%d", HOURS_NUMBERS[i])
			mInnerHours24Texts[i] = String.format("%02d", HOURS_NUMBERS_24[i])
			mOuterHours24Texts[i] = String.format("%d", HOURS_NUMBERS[i])
			mMinutesTexts[i] = String.format("%02d", MINUTES_NUMBERS[i])
		}
	}

	private fun initData() {
		if (mIs24HourMode) {
			mOuterTextHours = mOuterHours24Texts
			mInnerTextHours = mInnerHours24Texts
		} else {
			mOuterTextHours = mHours12Texts
			mInnerTextHours = mHours12Texts
		}

		mMinutesText = mMinutesTexts

		val hoursAlpha = if (mShowHours) ALPHA_OPAQUE else ALPHA_TRANSPARENT
		mAlpha[HOURS].value = hoursAlpha

		val minutesAlpha = if (mShowHours) ALPHA_TRANSPARENT else ALPHA_OPAQUE
		mAlpha[MINUTES].value = minutesAlpha
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		if (!changed) {
			return
		}

		mXCenter = width / 2
		mYCenter = height / 2
		mCircleRadius = Math.min(mXCenter, mYCenter)

		mMinDistForInnerNumber = mCircleRadius - mTextInset[HOURS_INNER] - mSelectorRadius
		mMaxDistForOuterNumber = mCircleRadius - mTextInset[HOURS] + mSelectorRadius
		mHalfwayDist = mCircleRadius - (mTextInset[HOURS] + mTextInset[HOURS_INNER]) / 2

		calculatePositionsHours()
		calculatePositionsMinutes()

		mTouchHelper!!.invalidateRoot()
	}

	public override fun onDraw(canvas: Canvas) {
		val alphaMod = if (mInputEnabled) 1 else mDisabledAlpha

		drawCircleBackground(canvas)
		drawHours(canvas, alphaMod)
		drawMinutes(canvas, alphaMod)
		drawCenter(canvas, alphaMod)
	}

	private fun drawCircleBackground(canvas: Canvas) {
		canvas.drawCircle(mXCenter.toFloat(), mYCenter.toFloat(), mCircleRadius.toFloat(), mPaintBackground)
	}

	private fun drawHours(canvas: Canvas, alphaMod: Float) {
		val hoursAlpha = (mAlpha[HOURS].value * alphaMod + 0.5f).toInt()
		if (hoursAlpha > 0) {
			// Draw the hour selector under the elements.
			drawSelector(canvas, if (mIsOnInnerCircle) HOURS_INNER else HOURS, null, alphaMod)

			// Draw outer hours.
			drawTextElements(canvas, mTextSize[HOURS].toFloat(), mTypeface, mTextColor[HOURS],
					mOuterTextHours, mOuterTextX[HOURS], mOuterTextY[HOURS], mPaint[HOURS],
					hoursAlpha, !mIsOnInnerCircle, mSelectionDegrees[HOURS], false)

			// Draw inner hours (13-00) for 24-hour time.
			if (mIs24HourMode && mInnerTextHours != null) {
				drawTextElements(canvas, mTextSize[HOURS_INNER].toFloat(), mTypeface, mTextColor[HOURS_INNER],
						mInnerTextHours, mInnerTextX, mInnerTextY, mPaint[HOURS], hoursAlpha,
						mIsOnInnerCircle, mSelectionDegrees[HOURS], false)
			}
		}
	}

	private fun drawMinutes(canvas: Canvas, alphaMod: Float) {
		val minutesAlpha = (mAlpha[MINUTES].value * alphaMod + 0.5f).toInt()
		if (minutesAlpha > 0) {
			// Draw the minute selector under the elements.
			drawSelector(canvas, MINUTES, mSelectorPath, alphaMod)

			// Exclude the selector region, then draw minutes with no
			// activated states.
			canvas.save(Canvas.CLIP_SAVE_FLAG)
			canvas.clipPath(mSelectorPath, Region.Op.DIFFERENCE)
			drawTextElements(canvas, mTextSize[MINUTES].toFloat(), mTypeface, mTextColor[MINUTES],
					mMinutesText, mOuterTextX[MINUTES], mOuterTextY[MINUTES], mPaint[MINUTES],
					minutesAlpha, false, 0, false)
			canvas.restore()

			// Intersect the selector region, then draw minutes with only
			// activated states.
			canvas.save(Canvas.CLIP_SAVE_FLAG)
			canvas.clipPath(mSelectorPath, Region.Op.INTERSECT)
			drawTextElements(canvas, mTextSize[MINUTES].toFloat(), mTypeface, mTextColor[MINUTES],
					mMinutesText, mOuterTextX[MINUTES], mOuterTextY[MINUTES], mPaint[MINUTES],
					minutesAlpha, true, mSelectionDegrees[MINUTES], true)
			canvas.restore()
		}
	}

	private fun drawCenter(canvas: Canvas, alphaMod: Float) {
		mPaintCenter.alpha = (255 * alphaMod + 0.5f).toInt()
		canvas.drawCircle(mXCenter.toFloat(), mYCenter.toFloat(), mCenterDotRadius.toFloat(), mPaintCenter)
	}

	private fun applyAlpha(argb: Int, alpha: Int): Int {
		val srcAlpha = argb shr 24 and 0xFF
		val dstAlpha = (srcAlpha * (alpha / 255.0) + 0.5f).toInt()
		return 0xFFFFFF and argb or (dstAlpha shl 24)
	}

	private fun getMultipliedAlpha(argb: Int, alpha: Int): Int {
		return (Color.alpha(argb) * (alpha / 255.0) + 0.5).toInt()
	}

	private fun drawSelector(canvas: Canvas, index: Int, selectorPath: Path?, alphaMod: Float) {
		val alpha = (mAlpha[index % 2].value * alphaMod + 0.5f).toInt()
		val color = applyAlpha(mSelectorColor, alpha)

		// Calculate the current radius at which to place the selection circle.
		val selRadius = mSelectorRadius
		val selLength = mCircleRadius - mTextInset[index]
		val selAngleRad = Math.toRadians(mSelectionDegrees[index % 2].toDouble())
		val selCenterX = mXCenter + selLength * Math.sin(selAngleRad).toFloat()
		val selCenterY = mYCenter - selLength * Math.cos(selAngleRad).toFloat()

		// Draw the selection circle.
		val paint = mPaintSelector[index % 2][SELECTOR_CIRCLE]
		paint.color = color
		canvas.drawCircle(selCenterX, selCenterY, selRadius.toFloat(), paint)

		// If needed, set up the clip path for later.
		if (selectorPath != null) {
			selectorPath.reset()
			selectorPath.addCircle(selCenterX, selCenterY, selRadius.toFloat(), Path.Direction.CCW)
		}

		// Draw the dot if we're between two items.
		val shouldDrawDot = mSelectionDegrees[index % 2] % 30 != 0
		if (shouldDrawDot) {
			val dotPaint = mPaintSelector[index % 2][SELECTOR_DOT]
			dotPaint.color = mSelectorDotColor
			canvas.drawCircle(selCenterX, selCenterY, mSelectorDotRadius.toFloat(), dotPaint)
		}

		// Shorten the line to only go from the edge of the center dot to the
		// edge of the selection circle.
		val sin = Math.sin(selAngleRad)
		val cos = Math.cos(selAngleRad)
		val lineLength = selLength - selRadius
		val centerX = mXCenter + (mCenterDotRadius * sin).toInt()
		val centerY = mYCenter - (mCenterDotRadius * cos).toInt()
		val linePointX = (centerX + (lineLength * sin).toInt()).toFloat()
		val linePointY = (centerY - (lineLength * cos).toInt()).toFloat()

		// Draw the line.
		val linePaint = mPaintSelector[index % 2][SELECTOR_LINE]
		linePaint.color = color
		linePaint.strokeWidth = mSelectorStroke.toFloat()
		canvas.drawLine(mXCenter.toFloat(), mYCenter.toFloat(), linePointX, linePointY, linePaint)
	}

	private fun calculatePositionsHours() {
		// Calculate the text positions
		val numbersRadius = (mCircleRadius - mTextInset[HOURS]).toFloat()

		// Calculate the positions for the 12 numbers in the main circle.
		calculatePositions(mPaint[HOURS], numbersRadius, mXCenter.toFloat(), mYCenter.toFloat(),
				mTextSize[HOURS].toFloat(), mOuterTextX[HOURS], mOuterTextY[HOURS])

		// If we have an inner circle, calculate those positions too.
		if (mIs24HourMode) {
			val innerNumbersRadius = mCircleRadius - mTextInset[HOURS_INNER]
			calculatePositions(mPaint[HOURS], innerNumbersRadius.toFloat(), mXCenter.toFloat(), mYCenter.toFloat(),
					mTextSize[HOURS_INNER].toFloat(), mInnerTextX, mInnerTextY)
		}
	}

	private fun calculatePositionsMinutes() {
		// Calculate the text positions
		val numbersRadius = (mCircleRadius - mTextInset[MINUTES]).toFloat()

		// Calculate the positions for the 12 numbers in the main circle.
		calculatePositions(mPaint[MINUTES], numbersRadius, mXCenter.toFloat(), mYCenter.toFloat(),
				mTextSize[MINUTES].toFloat(), mOuterTextX[MINUTES], mOuterTextY[MINUTES])
	}

	/**
	 * Draw the 12 text values at the positions specified by the textGrid parameters.
	 */
	private fun drawTextElements(canvas: Canvas, textSize: Float, typeface: Typeface?,
	                             textColor: ColorStateList, texts: Array<String>?, textX: FloatArray, textY: FloatArray, paint: Paint,
	                             alpha: Int, showActivated: Boolean, activatedDegrees: Int, activatedOnly: Boolean) {
		paint.textSize = textSize
		paint.typeface = typeface

		// The activated index can touch a range of elements.
		val activatedIndex = activatedDegrees / (360.0f / NUM_POSITIONS)
		val activatedFloor = activatedIndex.toInt()
		val activatedCeil = Math.ceil(activatedIndex.toDouble()).toInt() % NUM_POSITIONS

		for (i in 0..11) {
			val activated = activatedFloor == i || activatedCeil == i
			if (activatedOnly && !activated) {
				continue
			}

			val stateMask = SUtils.STATE_ENABLED or if (showActivated && activated) SUtils.STATE_ACTIVATED else 0
			val color = textColor.getColorForState(SUtils.resolveStateSet(stateMask), 0)
			paint.color = color
			paint.alpha = getMultipliedAlpha(color, alpha)

			canvas.drawText(texts!![i], textX[i], textY[i], paint)
		}
	}

	private inner class InvalidateUpdateListener : ValueAnimator.AnimatorUpdateListener {
		override fun onAnimationUpdate(animation: ValueAnimator) {
			this@RadialTimePickerView.invalidate()
		}
	}

	private fun startHoursToMinutesAnimation() {
		if (mHoursToMinutesAnims.size == 0) {
			mHoursToMinutesAnims.add(getFadeOutAnimator(mAlpha[HOURS],
					ALPHA_OPAQUE, ALPHA_TRANSPARENT, mInvalidateUpdateListener))
			mHoursToMinutesAnims.add(getFadeInAnimator(mAlpha[MINUTES],
					ALPHA_TRANSPARENT, ALPHA_OPAQUE, mInvalidateUpdateListener))
		}

		if (mTransition != null && mTransition!!.isRunning) {
			mTransition!!.end()
		}
		mTransition = AnimatorSet()
		mTransition!!.playTogether(mHoursToMinutesAnims)
		mTransition!!.start()
	}

	private fun startMinutesToHoursAnimation() {
		if (mMinuteToHoursAnims.size == 0) {
			mMinuteToHoursAnims.add(getFadeOutAnimator(mAlpha[MINUTES],
					ALPHA_OPAQUE, ALPHA_TRANSPARENT, mInvalidateUpdateListener))
			mMinuteToHoursAnims.add(getFadeInAnimator(mAlpha[HOURS],
					ALPHA_TRANSPARENT, ALPHA_OPAQUE, mInvalidateUpdateListener))
		}

		if (mTransition != null && mTransition!!.isRunning) {
			mTransition!!.end()
		}
		mTransition = AnimatorSet()
		mTransition!!.playTogether(mMinuteToHoursAnims)
		mTransition!!.start()
	}

	private fun getDegreesFromXY(x: Float, y: Float, constrainOutside: Boolean): Int {
		// Ensure the point is inside the touchable area.
		val innerBound: Int
		val outerBound: Int
		if (mIs24HourMode && mShowHours) {
			innerBound = mMinDistForInnerNumber
			outerBound = mMaxDistForOuterNumber
		} else {
			val index = if (mShowHours) HOURS else MINUTES
			val center = mCircleRadius - mTextInset[index]
			innerBound = center - mSelectorRadius
			outerBound = center + mSelectorRadius
		}

		val dX = (x - mXCenter).toDouble()
		val dY = (y - mYCenter).toDouble()
		val distFromCenter = Math.sqrt(dX * dX + dY * dY)
		if (distFromCenter < innerBound || constrainOutside && distFromCenter > outerBound) {
			return -1
		}

		// Convert to degrees.
		val degrees = (Math.toDegrees(Math.atan2(dY, dX) + Math.PI / 2) + 0.5).toInt()
		return if (degrees < 0) {
			degrees + 360
		} else {
			degrees
		}
	}

	private fun getInnerCircleFromXY(x: Float, y: Float): Boolean {
		if (mIs24HourMode && mShowHours) {
			val dX = (x - mXCenter).toDouble()
			val dY = (y - mYCenter).toDouble()
			val distFromCenter = Math.sqrt(dX * dX + dY * dY)
			return distFromCenter <= mHalfwayDist
		}
		return false
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (!mInputEnabled) {
			return true
		}

		val action = event.actionMasked
		if (action == MotionEvent.ACTION_MOVE
				|| action == MotionEvent.ACTION_UP
				|| action == MotionEvent.ACTION_DOWN) {
			var forceSelection = false
			var autoAdvance = false

			if (action == MotionEvent.ACTION_DOWN) {
				// This is a new event stream, reset whether the value changed.
				mChangedDuringTouch = false
			} else if (action == MotionEvent.ACTION_UP) {
				autoAdvance = true

				// If we saw a down/up pair without the value changing, assume
				// this is a single-tap selection and force a change.
				if (!mChangedDuringTouch) {
					forceSelection = true
				}
			}

			mChangedDuringTouch = mChangedDuringTouch or handleTouchInput(
					event.x, event.y, forceSelection, autoAdvance)
		}

		return true
	}

	private fun handleTouchInput(
			x: Float, y: Float, forceSelection: Boolean, autoAdvance: Boolean): Boolean {
		val isOnInnerCircle = getInnerCircleFromXY(x, y)
		val degrees = getDegreesFromXY(x, y, false)
		if (degrees == -1) {
			return false
		}

		val type: Int
		val newValue: Int
		val valueChanged: Boolean

		if (mShowHours) {
			val snapDegrees = snapOnly30s(degrees, 0) % 360
			valueChanged = mIsOnInnerCircle != isOnInnerCircle || mSelectionDegrees[HOURS] != snapDegrees
			mIsOnInnerCircle = isOnInnerCircle
			mSelectionDegrees[HOURS] = snapDegrees
			type = HOURS
			newValue = currentHour
		} else {
			val snapDegrees = snapPrefer30s(degrees) % 360
			valueChanged = mSelectionDegrees[MINUTES] != snapDegrees
			mSelectionDegrees[MINUTES] = snapDegrees
			type = MINUTES
			newValue = currentMinute
		}

		if (valueChanged || forceSelection || autoAdvance) {
			// Fire the listener even if we just need to auto-advance.
			if (mListener != null) {
				mListener!!.onValueSelected(type, newValue, autoAdvance)
			}

			// Only provide feedback if the value actually changed.
			if (valueChanged || forceSelection) {
				SUtils.vibrateForTimePicker(this)
				invalidate()
			}
			return true
		}

		return false
	}

	public override fun dispatchHoverEvent(event: MotionEvent): Boolean {
		// First right-of-refusal goes the touch exploration helper.
		return mTouchHelper!!.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
	}

	fun setInputEnabled(inputEnabled: Boolean) {
		mInputEnabled = inputEnabled
		invalidate()
	}

	private inner class RadialPickerTouchHelper : androidx.customview.widget.ExploreByTouchHelper(this@RadialTimePickerView) {
		private val mTempRect = Rect()

		private val TYPE_HOUR = 1
		private val TYPE_MINUTE = 2

		private val SHIFT_TYPE = 0
		private val MASK_TYPE = 0xF

		private val SHIFT_VALUE = 8
		private val MASK_VALUE = 0xFF

		/**
		 * Increment in which virtual views are exposed for minutes.
		 */
		private val MINUTE_INCREMENT = 5

		override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
			if (mShowHours) {
				val min = if (mIs24HourMode) 0 else 1
				val max = if (mIs24HourMode) 23 else 12
				for (i in min..max) {
					virtualViewIds.add(makeId(TYPE_HOUR, i))
				}
			} else {
				val current = currentMinute
				var i = 0
				while (i < MINUTES_IN_CIRCLE) {
					virtualViewIds.add(makeId(TYPE_MINUTE, i))

					// If the current minute falls between two increments,
					// insert an extra node for it.
					if (current > i && current < i + MINUTE_INCREMENT) {
						virtualViewIds.add(makeId(TYPE_MINUTE, current))
					}
					i += MINUTE_INCREMENT
				}
			}
		}

		override fun onPopulateEventForVirtualView(virtualViewId: Int, event: AccessibilityEvent) {
			event.className = javaClass.getName()

			val type = getTypeFromId(virtualViewId)
			val value = getValueFromId(virtualViewId)
			val description = getVirtualViewDescription(type, value)
			event.contentDescription = description
		}

		override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
			node.className = javaClass.getName()
			node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)

			val type = getTypeFromId(virtualViewId)
			val value = getValueFromId(virtualViewId)
			val description = getVirtualViewDescription(type, value)
			node.contentDescription = description

			getBoundsForVirtualView(virtualViewId, mTempRect)
			node.setBoundsInParent(mTempRect)

			val selected = isVirtualViewSelected(type, value)
			node.isSelected = selected

			val nextId = getVirtualViewIdAfter(type, value)
			if (nextId != androidx.customview.widget.ExploreByTouchHelper.INVALID_ID) {
				node.setTraversalBefore(this@RadialTimePickerView, nextId)
			}
		}

		override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
			if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
				val type = getTypeFromId(virtualViewId)
				val value = getValueFromId(virtualViewId)
				if (type == TYPE_HOUR) {
					val hour = if (mIs24HourMode) value else hour12To24(value, mAmOrPm)
					currentHour = hour
					return true
				} else if (type == TYPE_MINUTE) {
					currentMinute = value
					return true
				}
			}
			return false
		}

		override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
			super.onInitializeAccessibilityNodeInfo(host, info)

			info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
			info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)
		}

		override fun performAccessibilityAction(host: View, action: Int, arguments: Bundle): Boolean {
			if (super.performAccessibilityAction(host, action, arguments)) {
				return true
			}

			when (action) {
				AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> {
					adjustPicker(1)
					return true
				}
				AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> {
					adjustPicker(-1)
					return true
				}
			}

			return false
		}

		private fun adjustPicker(step: Int) {
			val stepSize: Int
			val initialStep: Int
			val maxValue: Int
			val minValue: Int
			if (mShowHours) {
				stepSize = 1

				val currentHour24 = currentHour
				if (mIs24HourMode) {
					initialStep = currentHour24
					minValue = 0
					maxValue = 23
				} else {
					initialStep = hour24To12(currentHour24)
					minValue = 1
					maxValue = 12
				}
			} else {
				stepSize = 5
				initialStep = currentMinute / stepSize
				minValue = 0
				maxValue = 55
			}

			val nextValue = (initialStep + step) * stepSize
			val clampedValue = SUtils.constrain(nextValue, minValue, maxValue)
			if (mShowHours) {
				currentHour = clampedValue
			} else {
				currentMinute = clampedValue
			}
		}

		override fun getVirtualViewAt(x: Float, y: Float): Int {
			val id: Int
			val degrees = getDegreesFromXY(x, y, true)
			if (degrees != -1) {
				val snapDegrees = snapOnly30s(degrees, 0) % 360
				if (mShowHours) {
					val isOnInnerCircle = getInnerCircleFromXY(x, y)
					val hour24 = getHourForDegrees(snapDegrees, isOnInnerCircle)
					val hour = if (mIs24HourMode) hour24 else hour24To12(hour24)
					id = makeId(TYPE_HOUR, hour)
				} else {
					val current = currentMinute
					val touched = getMinuteForDegrees(degrees)
					val snapped = getMinuteForDegrees(snapDegrees)

					// If the touched minute is closer to the current minute
					// than it is to the snapped minute, return current.
					val currentOffset = getCircularDiff(current, touched, MINUTES_IN_CIRCLE)
					val snappedOffset = getCircularDiff(snapped, touched, MINUTES_IN_CIRCLE)
					val minute: Int
					if (currentOffset < snappedOffset) {
						minute = current
					} else {
						minute = snapped
					}
					id = makeId(TYPE_MINUTE, minute)
				}
			} else {
				id = androidx.customview.widget.ExploreByTouchHelper.INVALID_ID
			}

			return id
		}

		/**
		 * Returns the difference in degrees between two values along a circle.
		 *
		 * @param first  value in the range [0,max]
		 * @param second value in the range [0,max]
		 * @param max    the maximum value along the circle
		 * @return the difference in between the two values
		 */
		private fun getCircularDiff(first: Int, second: Int, max: Int): Int {
			val diff = Math.abs(first - second)
			val midpoint = max / 2
			return if (diff > midpoint) max - diff else diff
		}

		private fun getVirtualViewIdAfter(type: Int, value: Int): Int {
			if (type == TYPE_HOUR) {
				val nextValue = value + 1
				val max = if (mIs24HourMode) 23 else 12
				if (nextValue <= max) {
					return makeId(type, nextValue)
				}
			} else if (type == TYPE_MINUTE) {
				val current = currentMinute
				val snapValue = value - value % MINUTE_INCREMENT
				val nextValue = snapValue + MINUTE_INCREMENT
				if (value < current && nextValue > current) {
					// The current value is between two snap values.
					return makeId(type, current)
				} else if (nextValue < MINUTES_IN_CIRCLE) {
					return makeId(type, nextValue)
				}
			}
			return androidx.customview.widget.ExploreByTouchHelper.INVALID_ID
		}

		private fun hour12To24(hour12: Int, amOrPm: Int): Int {
			var hour24 = hour12
			if (hour12 == 12) {
				if (amOrPm == AM) {
					hour24 = 0
				}
			} else if (amOrPm == PM) {
				hour24 += 12
			}
			return hour24
		}

		private fun hour24To12(hour24: Int): Int {
			return if (hour24 == 0) {
				12
			} else if (hour24 > 12) {
				hour24 - 12
			} else {
				hour24
			}
		}

		private fun getBoundsForVirtualView(virtualViewId: Int, bounds: Rect) {
			val radius: Float
			val type = getTypeFromId(virtualViewId)
			val value = getValueFromId(virtualViewId)
			val centerRadius: Float
			val degrees: Float
			if (type == TYPE_HOUR) {
				val innerCircle = getInnerCircleForHour(value)
				if (innerCircle) {
					centerRadius = (mCircleRadius - mTextInset[HOURS_INNER]).toFloat()
					radius = mSelectorRadius.toFloat()
				} else {
					centerRadius = (mCircleRadius - mTextInset[HOURS]).toFloat()
					radius = mSelectorRadius.toFloat()
				}

				degrees = getDegreesForHour(value).toFloat()
			} else if (type == TYPE_MINUTE) {
				centerRadius = (mCircleRadius - mTextInset[MINUTES]).toFloat()
				degrees = getDegreesForMinute(value).toFloat()
				radius = mSelectorRadius.toFloat()
			} else {
				// This should never happen.
				centerRadius = 0f
				degrees = 0f
				radius = 0f
			}

			val radians = Math.toRadians(degrees.toDouble())
			val xCenter = mXCenter + centerRadius * Math.sin(radians).toFloat()
			val yCenter = mYCenter - centerRadius * Math.cos(radians).toFloat()

			bounds.set((xCenter - radius).toInt(), (yCenter - radius).toInt(),
					(xCenter + radius).toInt(), (yCenter + radius).toInt())
		}

		private fun getVirtualViewDescription(type: Int, value: Int): CharSequence? {
			val description: CharSequence?
			if (type == TYPE_HOUR || type == TYPE_MINUTE) {
				description = Integer.toString(value)
			} else {
				description = null
			}
			return description
		}

		private fun isVirtualViewSelected(type: Int, value: Int): Boolean {
			return if (type == TYPE_HOUR)
				currentHour == value
			else
				type == TYPE_MINUTE && currentMinute == value
		}

		private fun makeId(type: Int, value: Int): Int {

			return type shl SHIFT_TYPE or (value shl SHIFT_VALUE)
		}

		private fun getTypeFromId(id: Int): Int {

			return id.ushr(SHIFT_TYPE) and MASK_TYPE
		}

		private fun getValueFromId(id: Int): Int {
			return id.ushr(SHIFT_VALUE) and MASK_VALUE
		}
	}

	private class IntHolder(var value: Int)

	companion object {
		private val TAG = RadialTimePickerView::class.java.getSimpleName()

		private val HOURS = 0
		private val MINUTES = 1
		private val HOURS_INNER = 2

		private val SELECTOR_CIRCLE = 0
		private val SELECTOR_DOT = 1
		private val SELECTOR_LINE = 2

		private val AM = 0
		private val PM = 1

		// Opaque alpha level
		private val ALPHA_OPAQUE = 255

		// Transparent alpha level
		private val ALPHA_TRANSPARENT = 0

		private val HOURS_IN_CIRCLE = 12
		private val MINUTES_IN_CIRCLE = 60
		private val DEGREES_FOR_ONE_HOUR = 360 / HOURS_IN_CIRCLE
		private val DEGREES_FOR_ONE_MINUTE = 360 / MINUTES_IN_CIRCLE

		private val HOURS_NUMBERS = intArrayOf(12, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
		private val HOURS_NUMBERS_24 = intArrayOf(0, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)
		private val MINUTES_NUMBERS = intArrayOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55)

		private val FADE_OUT_DURATION = 500
		private val FADE_IN_DURATION = 500

		private val SNAP_PREFER_30S_MAP = IntArray(361)

		private val NUM_POSITIONS = 12
		private val COS_30 = FloatArray(NUM_POSITIONS)
		private val SIN_30 = FloatArray(NUM_POSITIONS)

		init {
			// Prepare mapping to snap touchable degrees to selectable degrees.
			preparePrefer30sMap()

			val increment = 2.0 * Math.PI / NUM_POSITIONS
			var angle = Math.PI / 2.0
			for (i in 0 until NUM_POSITIONS) {
				COS_30[i] = Math.cos(angle).toFloat()
				SIN_30[i] = Math.sin(angle).toFloat()
				angle += increment
			}
		}

		/**
		 * Split up the 360 degrees of the circle among the 60 selectable values. Assigns a larger
		 * selectable area to each of the 12 visible values, such that the ratio of space apportioned
		 * to a visible value : space apportioned to a non-visible value will be 14 : 4.
		 * E.g. the output of 30 degrees should have a higher range of input associated with it than
		 * the output of 24 degrees, because 30 degrees corresponds to a visible number on the clock
		 * circle (5 on the minutes, 1 or 13 on the hours).
		 */
		private fun preparePrefer30sMap() {
			// We'll split up the visible output and the non-visible output such that each visible
			// output will correspond to a range of 14 associated input degrees, and each non-visible
			// output will correspond to a range of 4 associate input degrees, so visible numbers
			// are more than 3 times easier to get than non-visible numbers:
			// {354-359,0-7}:0, {8-11}:6, {12-15}:12, {16-19}:18, {20-23}:24, {24-37}:30, etc.
			//
			// If an output of 30 degrees should correspond to a range of 14 associated degrees, then
			// we'll need any input between 24 - 37 to snap to 30. Working out from there, 20-23 should
			// snap to 24, while 38-41 should snap to 36. This is somewhat counter-intuitive, that you
			// can be touching 36 degrees but have the selection snapped to 30 degrees; however, this
			// inconsistency isn't noticeable at such fine-grained degrees, and it affords us the
			// ability to aggressively prefer the visible values by a factor of more than 3:1, which
			// greatly contributes to the selectability of these values.

			// The first output is 0, and each following output will increment by 6 {0, 6, 12, ...}.
			var snappedOutputDegrees = 0
			// Count of how many inputs we've designated to the specified output.
			var count = 1
			// How many input we expect for a specified output. This will be 14 for output divisible
			// by 30, and 4 for the remaining output. We'll special case the outputs of 0 and 360, so
			// the caller can decide which they need.
			var expectedCount = 8
			// Iterate through the input.
			for (degrees in 0..360) {
				// Save the input-output mapping.
				SNAP_PREFER_30S_MAP[degrees] = snappedOutputDegrees
				// If this is the last input for the specified output, calculate the next output and
				// the next expected count.
				if (count == expectedCount) {
					snappedOutputDegrees += 6
					if (snappedOutputDegrees == 360) {
						expectedCount = 7
					} else if (snappedOutputDegrees % 30 == 0) {
						expectedCount = 14
					} else {
						expectedCount = 4
					}
					count = 1
				} else {
					count++
				}
			}
		}

		/**
		 * Returns mapping of any input degrees (0 to 360) to one of 60 selectable output degrees,
		 * where the degrees corresponding to visible numbers (i.e. those divisible by 30) will be
		 * weighted heavier than the degrees corresponding to non-visible numbers.
		 * See [.preparePrefer30sMap] documentation for the rationale and generation of the
		 * mapping.
		 */
		private fun snapPrefer30s(degrees: Int): Int {
			return if (SNAP_PREFER_30S_MAP == null) {
				-1
			} else SNAP_PREFER_30S_MAP[degrees]
		}

		/**
		 * Returns mapping of any input degrees (0 to 360) to one of 12 visible output degrees (all
		 * multiples of 30), where the input will be "snapped" to the closest visible degrees.
		 *
		 * @param degrees            The input degrees
		 * @param forceHigherOrLower The output may be forced to either the higher or lower step, or may
		 * be allowed to snap to whichever is closer. Use 1 to force strictly higher, -1 to force
		 * strictly lower, and 0 to snap to the closer one.
		 * @return output degrees, will be a multiple of 30
		 */
		private fun snapOnly30s(degrees: Int, forceHigherOrLower: Int): Int {
			var degrees = degrees
			val stepSize = DEGREES_FOR_ONE_HOUR
			var floor = degrees / stepSize * stepSize
			val ceiling = floor + stepSize
			if (forceHigherOrLower == 1) {
				degrees = ceiling
			} else if (forceHigherOrLower == -1) {
				if (degrees == floor) {
					floor -= stepSize
				}
				degrees = floor
			} else {
				if (degrees - floor < ceiling - degrees) {
					degrees = floor
				} else {
					degrees = ceiling
				}
			}
			return degrees
		}

		/**
		 * Using the trigonometric Unit Circle, calculate the positions that the text will need to be
		 * drawn at based on the specified circle radius. Place the values in the textGridHeights and
		 * textGridWidths parameters.
		 */
		private fun calculatePositions(paint: Paint, radius: Float, xCenter: Float, yCenter: Float,
		                               textSize: Float, x: FloatArray, y: FloatArray) {
			var yCenter = yCenter
			// Adjust yCenter to account for the text's baseline.
			paint.textSize = textSize
			yCenter -= (paint.descent() + paint.ascent()) / 2

			for (i in 0 until NUM_POSITIONS) {
				x[i] = xCenter - radius * COS_30[i]
				y[i] = yCenter - radius * SIN_30[i]
			}
		}

		private fun getFadeOutAnimator(target: IntHolder, startAlpha: Int, endAlpha: Int,
		                               updateListener: InvalidateUpdateListener): ObjectAnimator {
			val animator = ObjectAnimator.ofInt(target, "value", startAlpha, endAlpha)
			animator.duration = FADE_OUT_DURATION.toLong()
			animator.addUpdateListener(updateListener)
			return animator
		}

		private fun getFadeInAnimator(target: IntHolder, startAlpha: Int, endAlpha: Int,
		                              updateListener: InvalidateUpdateListener): ObjectAnimator {
			val delayMultiplier = 0.25f
			val transitionDurationMultiplier = 1f
			val totalDurationMultiplier = transitionDurationMultiplier + delayMultiplier
			val totalDuration = (FADE_IN_DURATION * totalDurationMultiplier).toInt()
			val delayPoint = delayMultiplier * FADE_IN_DURATION / totalDuration

			val kf0: Keyframe
			val kf1: Keyframe
			val kf2: Keyframe
			kf0 = Keyframe.ofInt(0f, startAlpha)
			kf1 = Keyframe.ofInt(delayPoint, startAlpha)
			kf2 = Keyframe.ofInt(1f, endAlpha)
			val fadeIn = PropertyValuesHolder.ofKeyframe("value", kf0, kf1, kf2)

			val animator = ObjectAnimator.ofPropertyValuesHolder(target, fadeIn)
			animator.duration = totalDuration.toLong()
			animator.addUpdateListener(updateListener)
			return animator
		}
	}
}

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

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView

import com.appeaser.sublimepickerlibrary.R

/**
 * Indicator used for selected year in YearPickerView
 * Needs fixing.
 */
internal class CircularIndicatorTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
                                                                   defStyleAttr: Int = 0, defStyleRes: Int = 0) : AppCompatTextView(context, attrs) {

	private val mCirclePaint = Paint()

	private val mItemIsSelectedText: String
	private var mCircleColor: Int = 0
	private var mDrawIndicator: Boolean = false

	init {
		val res = context.resources
		mItemIsSelectedText = res.getString(R.string.item_is_selected)
		init()
	}

	private fun init() {
		mCirclePaint.typeface = Typeface.create(mCirclePaint.typeface, Typeface.BOLD)
		mCirclePaint.isAntiAlias = true
		mCirclePaint.textAlign = Paint.Align.CENTER
		mCirclePaint.style = Paint.Style.FILL
	}

	fun setCircleColor(color: Int) {
		if (color != mCircleColor) {
			mCircleColor = color
			mCirclePaint.color = mCircleColor
			mCirclePaint.alpha = SELECTED_CIRCLE_ALPHA
			requestLayout()
		}
	}

	fun setDrawIndicator(drawIndicator: Boolean) {
		mDrawIndicator = drawIndicator
	}

	public override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		if (mDrawIndicator) {
			val width = width
			val height = height
			val radius = Math.min(width, height) / 2
			canvas.drawCircle((width / 2).toFloat(), (height / 2).toFloat(), radius.toFloat(), mCirclePaint)
		}
	}

	override fun getContentDescription(): CharSequence {
		val itemText = text
		return if (mDrawIndicator) {
			String.format(mItemIsSelectedText, itemText)
		} else {
			itemText
		}
	}

	companion object {
		private val SELECTED_CIRCLE_ALPHA = 60
	}
}

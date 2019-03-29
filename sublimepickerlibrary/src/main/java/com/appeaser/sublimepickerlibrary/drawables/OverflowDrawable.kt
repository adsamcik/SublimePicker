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

package com.appeaser.sublimepickerlibrary.drawables

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.drawable.Drawable

import com.appeaser.sublimepickerlibrary.R

/**
 * Material style overflow icon
 */
class OverflowDrawable(context: Context, color: Int) : Drawable() {

	internal var mPaintCircle: Paint
	internal var center1: PointF
	internal var center2: PointF
	internal var center3: PointF
	internal var mRadius: Float = 0.toFloat()
	internal var mWidthHeight: Int = 0

	init {
		val res = context.resources
		mWidthHeight = res.getDimensionPixelSize(R.dimen.options_size)
		val density = res.displayMetrics.densityDpi / 160f

		mRadius = 2 * density

		val centerXY = mWidthHeight / 2f

		center1 = PointF(centerXY, centerXY - 6 * density/* 6dp */)
		center2 = PointF(centerXY, centerXY)
		center3 = PointF(centerXY, centerXY + 6 * density/* 6dp */)

		mPaintCircle = Paint()
		mPaintCircle.color = color
		mPaintCircle.isAntiAlias = true
	}

	override fun draw(canvas: Canvas) {
		canvas.drawCircle(center1.x, center1.y, mRadius, mPaintCircle)
		canvas.drawCircle(center2.x, center2.y, mRadius, mPaintCircle)
		canvas.drawCircle(center3.x, center3.y, mRadius, mPaintCircle)
	}

	override fun getMinimumHeight(): Int {
		return mWidthHeight
	}

	override fun getMinimumWidth(): Int {
		return mWidthHeight
	}

	override fun getIntrinsicHeight(): Int {
		return mWidthHeight
	}

	override fun getIntrinsicWidth(): Int {
		return mWidthHeight
	}

	override fun setAlpha(alpha: Int) {
		mPaintCircle.alpha = alpha
		invalidateSelf()
	}

	override fun setColorFilter(cf: ColorFilter?) {
		mPaintCircle.colorFilter = cf
		invalidateSelf()
	}

	override fun getOpacity(): Int {
		return PixelFormat.OPAQUE
	}
}

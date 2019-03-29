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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.animation.AnticipateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * Provides animated transition between 'on' and 'off' state.
 * Used as background for 'WeekButton'.
 */
class CheckableDrawable(color: Int, private var mChecked: Boolean, private val mExpandedWidthHeight: Int) : Drawable() {

	private val ANIMATION_DURATION_EXPAND = 500
	private val ANIMATION_DURATION_COLLAPSE = 400
	private val mMinAlpha: Int
	private val mMaxAlpha: Int
	private val mPaint: Paint

	private var asTransition: AnimatorSet? = null
	private val mExpandInterpolator = OvershootInterpolator()
	private val mCollapseInterpolator = AnticipateInterpolator()
	private val mRectEvaluator = CRectFEvaluator()

	private var mRectToDraw: RectF? = null
	private var mCollapsedRect: RectF? = null
	private var mExpandedRect: RectF? = null
	private var mReady: Boolean = false

	init {

		mMaxAlpha = Color.alpha(color)
		// Todo: Provide an option to change this value
		mMinAlpha = 0

		mRectToDraw = RectF()
		mExpandedRect = RectF()
		mCollapsedRect = RectF()
		mPaint = Paint()
		mPaint.color = color
		mPaint.alpha = mMaxAlpha
		mPaint.isAntiAlias = true
		mPaint.style = Paint.Style.FILL
	}

	// initialize dimensions
	private fun setDimens(width: Int, height: Int) {
		mReady = true

		val expandedLeft = (width - mExpandedWidthHeight) / 2f
		val expandedTop = (height - mExpandedWidthHeight) / 2f
		val expandedRight = (width + mExpandedWidthHeight) / 2f
		val expandedBottom = (height + mExpandedWidthHeight) / 2f

		val collapsedLeft = width / 2f
		val collapsedTop = height / 2f
		val collapsedRight = width / 2f
		val collapsedBottom = height / 2f

		mCollapsedRect = RectF(collapsedLeft, collapsedTop,
				collapsedRight, collapsedBottom)
		mExpandedRect = RectF(expandedLeft, expandedTop,
				expandedRight, expandedBottom)

		reset()
	}

	// Called when 'WeekButton' checked state changes
	fun setCheckedOnClick(checked: Boolean, callback: OnAnimationDone) {
		mChecked = checked
		if (!mReady) {
			invalidateSelf()
			return
		}
		reset()
		onClick(callback)
	}

	private fun onClick(callback: OnAnimationDone) {
		animate(mChecked, callback)
	}

	private fun cancelAnimationInTracks() {
		if (asTransition != null && asTransition!!.isRunning) {
			asTransition!!.cancel()
		}
	}

	// Set state without animation
	fun setChecked(checked: Boolean) {
		if (mChecked == checked)
			return

		mChecked = checked
		reset()
	}

	private fun reset() {
		cancelAnimationInTracks()

		if (mChecked) {
			mRectToDraw!!.set(mExpandedRect)
		} else {
			mRectToDraw!!.set(mCollapsedRect)
		}

		invalidateSelf()
	}

	// Animate between 'on' & 'off' state
	private fun animate(expand: Boolean, callback: OnAnimationDone?) {
		val from = if (expand) mCollapsedRect else mExpandedRect
		val to = if (expand) mExpandedRect else mCollapsedRect

		mRectToDraw!!.set(from)

		val oaTransition = ObjectAnimator.ofObject(this,
				"newRectBounds",
				mRectEvaluator, from, to)

		val duration = if (expand)
			ANIMATION_DURATION_EXPAND
		else
			ANIMATION_DURATION_COLLAPSE

		oaTransition.duration = duration.toLong()
		oaTransition.interpolator = if (expand)
			mExpandInterpolator
		else
			mCollapseInterpolator

		val oaAlpha = ObjectAnimator.ofInt(this,
				"alpha",
				if (expand) mMinAlpha else mMaxAlpha,
				if (expand) mMaxAlpha else mMinAlpha)
		oaAlpha.duration = duration.toLong()

		asTransition = AnimatorSet()
		asTransition!!.playTogether(oaTransition, oaAlpha)

		asTransition!!.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				super.onAnimationEnd(animation)

				callback?.animationIsDone()
			}

			override fun onAnimationCancel(animation: Animator) {
				super.onAnimationCancel(animation)

				callback?.animationHasBeenCancelled()
			}
		})

		asTransition!!.start()
	}

	override fun draw(canvas: Canvas) {
		if (!mReady) {
			setDimens(bounds.width(), bounds.height())
			return
		}

		canvas.drawOval(mRectToDraw!!, mPaint)
	}

	override fun setAlpha(alpha: Int) {
		mPaint.alpha = alpha
	}

	override fun setColorFilter(cf: ColorFilter?) {

	}

	override fun getOpacity(): Int {
		return PixelFormat.TRANSLUCENT
	}

	// ObjectAnimator property
	fun setNewRectBounds(newRectBounds: RectF) {
		mRectToDraw = newRectBounds
		invalidateSelf()
	}

	// Callback
	interface OnAnimationDone {
		fun animationIsDone()

		fun animationHasBeenCancelled()
	}
}

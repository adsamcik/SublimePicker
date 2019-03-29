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

package com.appeaser.sublimepickerlibrary.drawables

import android.animation.TypeEvaluator
import android.graphics.RectF

/**
 * This evaluator can be used to perform type interpolation between RectF values.
 * It is a modified version of 'RectEvaluator'
 */
class CRectFEvaluator : TypeEvaluator<RectF> {

	/**
	 * When null, a new Rect is returned on every evaluate call. When non-null,
	 * mRect will be modified and returned on every evaluate.
	 */
	private val mRectF: RectF?

	/**
	 * Construct a RectEvaluator that returns a new Rect on every evaluate call.
	 * To avoid creating an object for each evaluate call,
	 * [CRectFEvaluator.CRectFEvaluator] should be used
	 * whenever possible.
	 */
	constructor() {}

	/**
	 * Constructs a RectEvaluator that modifies and returns `reuseRect`
	 * in #evaluate(float, android.graphics.RectF, android.graphics.Rect) calls.
	 * The value returned from
	 * #evaluate(float, android.graphics.RectF, android.graphics.Rect) should
	 * not be cached because it will change over time as the object is reused on each
	 * call.
	 *
	 * @param reuseRect A Rect to be modified and returned by evaluate.
	 */
	constructor(reuseRect: RectF) {
		mRectF = reuseRect
	}

	/**
	 * This function returns the result of linearly interpolating the start and
	 * end Rect values, with `fraction` representing the proportion
	 * between the start and end values. The calculation is a simple parametric
	 * calculation on each of the separate components in the Rect objects
	 * (left, top, right, and bottom).
	 *
	 * If #CRectFEvaluator(android.graphics.Rect) was used to construct
	 * this RectEvaluator, the object returned will be the `reuseRect`
	 * passed into the constructor.
	 *
	 * @param fraction   The fraction from the starting to the ending values
	 * @param startValue The start Rect
	 * @param endValue   The end Rect
	 * @return A linear interpolation between the start and end values, given the
	 * `fraction` parameter.
	 */
	override fun evaluate(fraction: Float, startValue: RectF, endValue: RectF): RectF {
		val left = startValue.left + (endValue.left - startValue.left) * fraction
		val top = startValue.top + (endValue.top - startValue.top) * fraction
		val right = startValue.right + (endValue.right - startValue.right) * fraction
		val bottom = startValue.bottom + (endValue.bottom - startValue.bottom) * fraction
		if (mRectF == null) {
			return RectF(left, top, right, bottom)
		} else {
			mRectF.set(left, top, right, bottom)
			return mRectF
		}
	}
}

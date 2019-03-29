package com.appeaser.sublimepickerlibrary.utilities

import android.content.res.ColorStateList

/**
 * Created by Admin on 13/02/2016.
 */
object TextColorHelper {

	fun resolveMaterialHeaderTextColor(): ColorStateList {
		val states = arrayOf(intArrayOf(android.R.attr.state_activated), intArrayOf())

		val colors = intArrayOf(SUtils.COLOR_TEXT_PRIMARY, SUtils.COLOR_TEXT_SECONDARY)

		return ColorStateList(states, colors)
	}
}

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

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView

import com.appeaser.sublimepickerlibrary.R
import com.appeaser.sublimepickerlibrary.utilities.SUtils

import java.util.Calendar

/**
 * Displays a selectable list of years.
 */
class YearPickerView : ListView {
	private var mAdapter: YearAdapter? = null
	private var mViewSize: Int = 0
	private var mChildSize: Int = 0

	private var mOnYearSelectedListener: OnYearSelectedListener? = null

	val firstPositionOffset: Int
		get() {
			val firstChild = getChildAt(0) ?: return 0
			return firstChild.top
		}

	@JvmOverloads
	constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int = android.R.attr.listViewStyle) : super(context, attrs, defStyleAttr) {
		init()
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
		init()
	}

	private fun init() {
		val frame = AbsListView.LayoutParams(
				AbsListView.LayoutParams.MATCH_PARENT, AbsListView.LayoutParams.WRAP_CONTENT)
		layoutParams = frame

		val res = context.resources
		mViewSize = res.getDimensionPixelOffset(R.dimen.datepicker_view_animator_height)
		mChildSize = res.getDimensionPixelOffset(R.dimen.datepicker_year_label_height)

		onItemClickListener = OnItemClickListener { parent, view, position, id ->
			val year = mAdapter!!.getYearForPosition(position)
			mAdapter!!.setSelection(year)

			if (mOnYearSelectedListener != null) {
				mOnYearSelectedListener!!.onYearChanged(this@YearPickerView, year)
			}
		}

		mAdapter = YearAdapter(context)
		adapter = mAdapter
	}

	fun setOnYearSelectedListener(listener: OnYearSelectedListener) {
		mOnYearSelectedListener = listener
	}

	/**
	 * Sets the currently selected year. Jumps immediately to the new year.
	 *
	 * @param year the target year
	 */
	fun setYear(year: Int) {
		mAdapter!!.setSelection(year)

		post {
			val position = mAdapter!!.getPositionForYear(year)
			if (position >= 0 && position < count) {
				setSelectionCentered(position)
			}
		}
	}

	private fun setSelectionCentered(position: Int) {
		val offset = mViewSize / 2 - mChildSize / 2
		setSelectionFromTop(position, offset)
	}

	fun setRange(min: Calendar, max: Calendar) {
		mAdapter!!.setRange(min, max)
	}

	private class YearAdapter(private val mContext: Context) : BaseAdapter() {
		private val mInflater: LayoutInflater

		private var mActivatedYear: Int = 0
		private var mMinYear: Int = 0
		private var mCount: Int = 0

		init {
			mInflater = LayoutInflater.from(mContext)
		}

		fun setRange(minDate: Calendar, maxDate: Calendar) {
			val minYear = minDate.get(Calendar.YEAR)
			val count = maxDate.get(Calendar.YEAR) - minYear + 1

			if (mMinYear != minYear || mCount != count) {
				mMinYear = minYear
				mCount = count
				notifyDataSetInvalidated()
			}
		}

		fun setSelection(year: Int): Boolean {
			if (mActivatedYear != year) {
				mActivatedYear = year
				notifyDataSetChanged()
				return true
			}
			return false
		}

		override fun getCount(): Int {
			return mCount
		}

		override fun getItem(position: Int): Int? {
			return getYearForPosition(position)
		}

		override fun getItemId(position: Int): Long {
			return getYearForPosition(position).toLong()
		}

		fun getPositionForYear(year: Int): Int {
			return year - mMinYear
		}

		fun getYearForPosition(position: Int): Int {
			return mMinYear + position
		}

		override fun hasStableIds(): Boolean {
			return true
		}

		@SuppressLint("SetTextI18n")
		override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
			val v: TextView
			val hasNewView = convertView == null
			if (hasNewView) {
				v = mInflater.inflate(ITEM_LAYOUT, parent, false) as TextView
			} else {
				v = convertView as TextView?
			}

			val year = getYearForPosition(position)
			val activated = mActivatedYear == year

			if (hasNewView || v.isActivated != activated) {
				val textAppearanceResId: Int
				if (activated && ITEM_TEXT_ACTIVATED_APPEARANCE != 0) {
					textAppearanceResId = ITEM_TEXT_ACTIVATED_APPEARANCE
				} else {
					textAppearanceResId = ITEM_TEXT_APPEARANCE
				}

				if (SUtils.isApi_23_OrHigher) {
					v.setTextAppearance(textAppearanceResId)
				} else {
					v.setTextAppearance(mContext, textAppearanceResId)
				}

				v.isActivated = activated
			}

			v.text = Integer.toString(year)
			return v
		}

		override fun getItemViewType(position: Int): Int {
			return 0
		}

		override fun getViewTypeCount(): Int {
			return 1
		}

		override fun isEmpty(): Boolean {
			return false
		}

		override fun areAllItemsEnabled(): Boolean {
			return true
		}

		override fun isEnabled(position: Int): Boolean {
			return true
		}

		companion object {

			private val ITEM_LAYOUT = R.layout.year_label_text_view
			private val ITEM_TEXT_APPEARANCE = R.style.SPYearLabelTextAppearance
			private val ITEM_TEXT_ACTIVATED_APPEARANCE = R.style.SPYearLabelActivatedTextAppearance
		}
	}

	// TODO: Might have to build the chain all the way
	// TODO: up to View#onInitializeAccessibilityEventInternal(AccessibilityEvent event)
	/*@Override
    public void onInitializeAccessibilityEventInternal(AccessibilityEvent event) {
        super.onInitializeAccessibilityEventInternal(event);

        // There are a bunch of years, so don't bother.
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            event.setFromIndex(0);
            event.setToIndex(0);
        }
    }*/

	/**
	 * The callback used to indicate the user changed the year.
	 */
	interface OnYearSelectedListener {
		/**
		 * Called upon a year change.
		 *
		 * @param view The view associated with this listener.
		 * @param year The year that was set.
		 */
		fun onYearChanged(view: YearPickerView, year: Int)
	}
}

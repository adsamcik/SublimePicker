/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

import com.appeaser.sublimepickerlibrary.R
import com.appeaser.sublimepickerlibrary.utilities.Config
import com.appeaser.sublimepickerlibrary.utilities.SUtils

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.ArrayList

/**
 * This displays a list of months in a calendar format with selectable days.
 */
internal class DayPickerViewPager @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewPager(context, attrs) {

	private val MONTH_SCROLL_THRESHOLD: Int
	private val TOUCH_SLOP_SQUARED: Int

	private val mMatchParentChildren = ArrayList<View>(1)

	private var mPopulateMethod: Method? = null
	private var mAlreadyTriedAccessingMethod: Boolean = false

	private var mCanPickRange: Boolean = false
	private var mDayPickerPagerAdapter: DayPickerPagerAdapter? = null

	private var mInitialDownX: Float = 0.toFloat()
	private var mInitialDownY: Float = 0.toFloat()
	private var mIsLongPressed = false

	private var mCheckForLongPress: CheckForLongPress? = null
	private var mTempSelectedDate: SelectedDate? = null
	private var mScrollerRunnable: ScrollerRunnable? = null
	private var mScrollingDirection = NOT_SCROLLING

	init {
		TOUCH_SLOP_SQUARED = ViewConfiguration.get(context).scaledTouchSlop * ViewConfiguration.get(context).scaledTouchSlop
		MONTH_SCROLL_THRESHOLD = context.resources
				.getDimensionPixelSize(R.dimen.sp_month_scroll_threshold)
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		//populate();
		// Use reflection
		callPopulate()

		// Everything below is mostly copied from FrameLayout.
		var count = childCount

		val measureMatchParentChildren = View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.EXACTLY || View.MeasureSpec.getMode(heightMeasureSpec) != View.MeasureSpec.EXACTLY

		var maxHeight = 0
		var maxWidth = 0
		var childState = 0

		for (i in 0 until count) {
			val child = getChildAt(i)
			if (child.visibility != View.GONE) {
				measureChild(child, widthMeasureSpec, heightMeasureSpec)
				val lp = child.layoutParams as ViewPager.LayoutParams
				maxWidth = Math.max(maxWidth, child.measuredWidth)
				maxHeight = Math.max(maxHeight, child.measuredHeight)
				childState = View.combineMeasuredStates(childState, child.measuredState)
				if (measureMatchParentChildren) {
					if (lp.width == ViewPager.LayoutParams.MATCH_PARENT || lp.height == ViewPager.LayoutParams.MATCH_PARENT) {
						mMatchParentChildren.add(child)
					}
				}
			}
		}

		// Account for padding too
		maxWidth += paddingLeft + paddingRight
		maxHeight += paddingTop + paddingBottom

		// Check against our minimum height and width
		maxHeight = Math.max(maxHeight, suggestedMinimumHeight)
		maxWidth = Math.max(maxWidth, suggestedMinimumWidth)

		// Check against our foreground's minimum height and width
		if (SUtils.isApi_23_OrHigher) {
			val drawable = foreground
			if (drawable != null) {
				maxHeight = Math.max(maxHeight, drawable.minimumHeight)
				maxWidth = Math.max(maxWidth, drawable.minimumWidth)
			}
		}

		setMeasuredDimension(View.resolveSizeAndState(maxWidth, widthMeasureSpec, childState),
				View.resolveSizeAndState(maxHeight, heightMeasureSpec,
						childState shl View.MEASURED_HEIGHT_STATE_SHIFT))

		count = mMatchParentChildren.size
		if (count > 1) {
			for (i in 0 until count) {
				val child = mMatchParentChildren[i]

				val lp = child.layoutParams as ViewPager.LayoutParams
				val childWidthMeasureSpec: Int
				val childHeightMeasureSpec: Int

				if (lp.width == ViewPager.LayoutParams.MATCH_PARENT) {
					childWidthMeasureSpec = View.MeasureSpec.makeMeasureSpec(
							measuredWidth - paddingLeft - paddingRight,
							View.MeasureSpec.EXACTLY)
				} else {
					childWidthMeasureSpec = ViewGroup.getChildMeasureSpec(widthMeasureSpec,
							paddingLeft + paddingRight,
							lp.width)
				}

				if (lp.height == ViewPager.LayoutParams.MATCH_PARENT) {
					childHeightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
							measuredHeight - paddingTop - paddingBottom,
							View.MeasureSpec.EXACTLY)
				} else {
					childHeightMeasureSpec = ViewGroup.getChildMeasureSpec(heightMeasureSpec,
							paddingTop + paddingBottom,
							lp.height)
				}

				child.measure(childWidthMeasureSpec, childHeightMeasureSpec)
			}
		}

		mMatchParentChildren.clear()
	}

	private fun initializePopulateMethod() {
		try {
			mPopulateMethod = ViewPager::class.java!!.getDeclaredMethod("populate", *null as Array<Class<*>>?)
			mPopulateMethod!!.isAccessible = true
		} catch (nsme: NoSuchMethodException) {
			nsme.printStackTrace()
		}

		mAlreadyTriedAccessingMethod = true
	}

	private fun callPopulate() {
		if (!mAlreadyTriedAccessingMethod) {
			initializePopulateMethod()
		}

		if (mPopulateMethod != null) {
			// Multi-catch block cannot be used before API 19

			try {
				mPopulateMethod!!.invoke(this)
			} catch (e: IllegalAccessException) {
				e.printStackTrace()
			} catch (e: InvocationTargetException) {
				e.printStackTrace()
			}

		} else {
			Log.e(TAG, "Could not call `ViewPager.populate()`")
		}
	}

	fun setCanPickRange(canPickRange: Boolean) {
		mCanPickRange = canPickRange
	}

	override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
		if (!mCanPickRange) {
			return super.onInterceptTouchEvent(ev)
		}

		if (ev.action == MotionEvent.ACTION_DOWN) {
			if (Config.DEBUG) {
				Log.i(TAG, "OITE: DOWN")
			}

			mInitialDownX = ev.x
			mInitialDownY = ev.y

			if (mCheckForLongPress == null) {
				mCheckForLongPress = CheckForLongPress()
			}

			postDelayed(mCheckForLongPress, ViewConfiguration.getLongPressTimeout().toLong())
		} else if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
			if (Config.DEBUG) {
				Log.i(TAG, "OITE: (UP || CANCEL)")
			}

			if (mCheckForLongPress != null) {
				removeCallbacks(mCheckForLongPress)
			}

			mIsLongPressed = false
			mInitialDownX = -1f
			mInitialDownY = -1f
		} else if (ev.action == MotionEvent.ACTION_MOVE) {
			if (Config.DEBUG) {
				Log.i(TAG, "OITE: MOVE")
			}

			if (!isStillALongPress(ev.x.toInt(), ev.y.toInt())) {
				if (Config.DEBUG) {
					Log.i(TAG, "OITE: MOVED TOO MUCH, CANCELLING CheckForLongPress Runnable")
				}

				if (mCheckForLongPress != null) {
					removeCallbacks(mCheckForLongPress)
				}
			}
		}

		return mIsLongPressed || super.onInterceptTouchEvent(ev)
	}

	private fun isStillALongPress(x: Int, y: Int): Boolean {
		return (x - mInitialDownX) * (x - mInitialDownX) + (y - mInitialDownY) * (y - mInitialDownY) <= TOUCH_SLOP_SQUARED
	}

	private inner class CheckForLongPress : Runnable {
		override fun run() {
			if (mDayPickerPagerAdapter != null) {

				mTempSelectedDate = mDayPickerPagerAdapter!!.resolveStartDateForRange(mInitialDownX.toInt(),
						mInitialDownY.toInt(), currentItem)

				if (mTempSelectedDate != null) {
					if (Config.DEBUG) {
						Log.i(TAG, "CheckForLongPress Runnable Fired")
					}

					mIsLongPressed = true
					mDayPickerPagerAdapter!!.onDateRangeSelectionStarted(mTempSelectedDate)
				}
			}
		}
	}

	override fun onTouchEvent(ev: MotionEvent): Boolean {
		if (!mCanPickRange) {
			return super.onTouchEvent(ev)
		}

		// looks like the ViewPager wants to step in
		if (mCheckForLongPress != null) {
			removeCallbacks(mCheckForLongPress)
		}

		if (mIsLongPressed && ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_CANCEL) {
			if (Config.DEBUG) {
				Log.i(TAG, "OTE: LONGPRESS && (UP || CANCEL)")
			}

			if (ev.action == MotionEvent.ACTION_UP) {
				if (mDayPickerPagerAdapter != null) {
					mTempSelectedDate = mDayPickerPagerAdapter!!.resolveEndDateForRange(ev.x.toInt(),
							ev.y.toInt(), currentItem, false)
					mDayPickerPagerAdapter!!.onDateRangeSelectionEnded(mTempSelectedDate)
				}
			}

			mIsLongPressed = false
			mInitialDownX = -1f
			mInitialDownY = -1f
			mScrollingDirection = NOT_SCROLLING

			if (mScrollerRunnable != null) {
				removeCallbacks(mScrollerRunnable)
			}
			//return true;
		} else if (mIsLongPressed && ev.action == MotionEvent.ACTION_DOWN) {
			if (Config.DEBUG) {
				Log.i(TAG, "OTE: LONGPRESS && DOWN")
			}

			mScrollingDirection = NOT_SCROLLING
		} else if (mIsLongPressed && ev.action == MotionEvent.ACTION_MOVE) {
			if (Config.DEBUG) {
				Log.i(TAG, "OTE: LONGPRESS && MOVE")
			}

			val direction = resolveDirectionForScroll(ev.x)
			val directionChanged = mScrollingDirection != direction

			if (directionChanged) {
				if (mScrollerRunnable != null) {
					removeCallbacks(mScrollerRunnable)
				}
			}

			if (mScrollerRunnable == null) {
				mScrollerRunnable = ScrollerRunnable()
			}

			mScrollingDirection = direction

			if (mScrollingDirection == NOT_SCROLLING) {
				if (mDayPickerPagerAdapter != null) {
					mTempSelectedDate = mDayPickerPagerAdapter!!.resolveEndDateForRange(ev.x.toInt(),
							ev.y.toInt(), currentItem, true)

					if (mTempSelectedDate != null) {
						mDayPickerPagerAdapter!!.onDateRangeSelectionUpdated(mTempSelectedDate)
					}
				}
			} else if (directionChanged) { // SCROLLING_LEFT || SCROLLING_RIGHT
				post(mScrollerRunnable)
			}
		}

		return mIsLongPressed || super.onTouchEvent(ev)
	}

	private fun resolveDirectionForScroll(x: Float): Int {
		if (x - left < MONTH_SCROLL_THRESHOLD) {
			return SCROLLING_LEFT
		} else if (right - x < MONTH_SCROLL_THRESHOLD) {
			return SCROLLING_RIGHT
		}

		return NOT_SCROLLING
	}

	private inner class ScrollerRunnable : Runnable {
		override fun run() {
			if (mScrollingDirection == NOT_SCROLLING) {
				return
			}

			val direction = mScrollingDirection

			// Animation is expensive for accessibility services since it sends
			// lots of scroll and content change events.
			val animate = true //!mAccessibilityManager.isEnabled()

			// ViewPager clamps input values, so we don't need to worry
			// about passing invalid indices.
			setCurrentItem(currentItem + direction, animate)

			// Four times the default anim duration
			postDelayed(this, 1000L)
		}
	}

	// May need to refer to this later
	/*@Override
    public boolean onTouchEvent(MotionEvent ev) {
        // looks like the ViewPager wants to step in
        if (mCheckForLongPress != null) {
            removeCallbacks(mCheckForLongPress);
        }

        if (mIsLongPressed && ev.getAction() == MotionEvent.ACTION_UP
                || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            if (Config.DEBUG) {
                Log.i(TAG, "OTE: LONGPRESS && (UP || CANCEL)");
            }

            if (ev.getAction() == MotionEvent.ACTION_UP) {
                if (mDayPickerPagerAdapter != null) {
                    mTempSelectedDate = mDayPickerPagerAdapter.resolveEndDateForRange((int)ev.getX(),
                            (int)ev.getY(), getCurrentItem(), false);
                    mDayPickerPagerAdapter.onDateRangeSelectionEnded(mTempSelectedDate);
                }
            }

            mIsLongPressed = false;
            mInitialDownX = -1;
            mInitialDownY = -1;

            if (mScrollerRunnable != null) {
                removeCallbacks(mScrollerRunnable);
            }
            //return true;
        } else if (mIsLongPressed &&  ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (Config.DEBUG) {
                Log.i(TAG, "OTE: LONGPRESS && DOWN");
            }
        } else if (mIsLongPressed &&  ev.getAction() == MotionEvent.ACTION_MOVE) {
            if (Config.DEBUG) {
                Log.i(TAG, "OTE: LONGPRESS && MOVE");
            }

            int direction = resolveDirectionForScroll(ev.getX(), ev.getY());

            if (direction == 0) {
                mScrollingLeft = false;
                mScrollingRight = false;

                if (mScrollerRunnable != null) {
                    removeCallbacks(mScrollerRunnable);
                }

                if (mDayPickerPagerAdapter != null) {
                    mTempSelectedDate = mDayPickerPagerAdapter.resolveEndDateForRange((int)ev.getX(),
                            (int)ev.getY(), getCurrentItem(), true);

                    if (mTempSelectedDate != null) {
                        mDayPickerPagerAdapter.onDateRangeSelectionUpdated(mTempSelectedDate);
                    }
                }
            } else if (direction == -1) {
                if (mScrollingLeft) {
                    // nothing
                } else if (mScrollingRight) {
                    mScrollingRight = false;
                    mScrollingLeft = true;

                    if (mScrollerRunnable != null) {
                        removeCallbacks(mScrollerRunnable);
                    }

                    if (mScrollerRunnable == null) {
                        mScrollerRunnable = new ScrollerRunnable();
                    }

                    post(mScrollerRunnable);
                } else {
                    mScrollingLeft = true;

                    if (mScrollerRunnable == null) {
                        mScrollerRunnable = new ScrollerRunnable();
                    }

                    post(mScrollerRunnable);
                }
            } else if (direction == 1) {
                if (mScrollingRight) {
                    // nothing
                } else if (mScrollingLeft) {
                    mScrollingLeft = false;
                    mScrollingRight = true;

                    if (mScrollerRunnable != null) {
                        removeCallbacks(mScrollerRunnable);
                    }

                    if (mScrollerRunnable == null) {
                        mScrollerRunnable = new ScrollerRunnable();
                    }

                    post(mScrollerRunnable);
                } else {
                    mScrollingRight = true;

                    if (mScrollerRunnable == null) {
                        mScrollerRunnable = new ScrollerRunnable();
                    }

                    post(mScrollerRunnable);
                }
            }
        }

        return mIsLongPressed || super.onTouchEvent(ev);
    }

    private int resolveDirectionForScroll(float x, float y) {
        if (x - getLeft() < MONTH_SCROLL_THRESHOLD) {
            return -1;
        } else if (getRight() - x < MONTH_SCROLL_THRESHOLD) {
            return 1;
        }

        return 0;
    }

    public class ScrollerRunnable implements Runnable {
        @Override
        public void run() {
            final int direction;
            if (mScrollingLeft) {
                direction = -1;
            } else if (mScrollingRight) {
                direction = 1;
            } else {
                return;
            }

            // Animation is expensive for accessibility services since it sends
            // lots of scroll and content change events.
            final boolean animate = true; //!mAccessibilityManager.isEnabled()

            // ViewPager clamps input values, so we don't need to worry
            // about passing invalid indices.
            setCurrentItem(getCurrentItem() + direction, animate);

            // Four times the default anim duration
            postDelayed(this, 1000L);
        }
    }*/

	override fun setAdapter(adapter: PagerAdapter?) {
		super.setAdapter(adapter)

		if (adapter is DayPickerPagerAdapter) {
			mDayPickerPagerAdapter = adapter
		}
	}

	override fun onRestoreInstanceState(state: Parcelable) {
		// A 'null' hack may be required to keep the ViewPager
		// from saving its own state.

		// Since we were using two
		// ViewPagers with the same ID, state restoration was
		// having issues ==> wrong 'current' item. The approach
		// I am currently using is to define two different layout
		// files, which contain ViewPagers with different IDs.
		// If this approach does not pan out, we will need to
		// employ a 'null' hack where the ViewPager does not
		// get to see 'state' in 'onRestoreInstanceState(Parecelable)'.
		// super.onRestoreInstanceState(null);
		super.onRestoreInstanceState(state)
	}

	companion object {

		private val TAG = DayPickerViewPager::class.java!!.getSimpleName()

		// Scrolling support
		private val SCROLLING_LEFT = -1
		private val NOT_SCROLLING = 0
		private val SCROLLING_RIGHT = 1
	}
}

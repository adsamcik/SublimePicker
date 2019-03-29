package com.appeaser.sublimepickerlibrary.datepicker

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.TypedArray
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.text.format.DateUtils
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout

import com.appeaser.sublimepickerlibrary.R
import com.appeaser.sublimepickerlibrary.common.DecisionButtonLayout
import com.appeaser.sublimepickerlibrary.utilities.AccessibilityUtils
import com.appeaser.sublimepickerlibrary.utilities.Config
import com.appeaser.sublimepickerlibrary.utilities.SUtils

import java.util.Calendar
import java.util.Locale

/**
 * Created by Admin on 11/03/2016.
 */
class RecurrenceEndDatePicker : FrameLayout {

	private var mContext: Context? = null

	// Top-level container.
	private var mContainer: ViewGroup? = null

	// Picker view.
	private var mDayPickerView: DayPickerView? = null

	private var mOnDateSetListener: RecurrenceEndDatePicker.OnDateSetListener? = null

	private var mCurrentDate: SelectedDate? = null
	private var mTempDate: Calendar? = null
	/**
	 * Gets the minimal date supported by this [SublimeDatePicker] in
	 * milliseconds since January 1, 1970 00:00:00 in
	 * [java.util.TimeZone.getDefault] time zone.
	 * Note: The default minimal date is 01/01/1900.
	 *
	 * @return The minimal supported date.
	 */
	var minDate: Calendar? = null
		private set
	/**
	 * Gets the maximal date supported by this [SublimeDatePicker] in
	 * milliseconds since January 1, 1970 00:00:00 in
	 * [java.util.TimeZone.getDefault] time zone.
	 * Note: The default maximal date is 12/31/2100.
	 *
	 * @return The maximal supported date.
	 */
	var maxDate: Calendar? = null
		private set

	var firstDayOfWeek: Int = 0
		set(firstDayOfWeek) {
			var firstDayOfWeek = firstDayOfWeek
			if (firstDayOfWeek < Calendar.SUNDAY || firstDayOfWeek > Calendar.SATURDAY) {
				if (Config.DEBUG) {
					Log.e(TAG, "Provided `firstDayOfWeek` is invalid - it must be between 1 and 7. " +
							"Given value: " + firstDayOfWeek + " Picker will use the default value for the given locale.")
				}

				firstDayOfWeek = mCurrentDate!!.firstDate!!.firstDayOfWeek
			}

			field = firstDayOfWeek

			mDayPickerView!!.firstDayOfWeek = firstDayOfWeek
		}

	private var mCurrentLocale: Locale? = null

	private var mValidationCallback: DatePickerValidationCallback? = null

	private var mDecisionButtonLayout: DecisionButtonLayout? = null

	private val mDecisionButtonLayoutCallback = object : DecisionButtonLayout.Callback {
		override fun onOkay() {
			if (mOnDateSetListener != null) {
				mOnDateSetListener!!.onDateSet(this@RecurrenceEndDatePicker,
						mCurrentDate!!.startDate.get(Calendar.YEAR),
						mCurrentDate!!.startDate.get(Calendar.MONTH),
						mCurrentDate!!.startDate.get(Calendar.DAY_OF_MONTH))
			}
		}

		override fun onCancel() {
			if (mOnDateSetListener != null) {
				mOnDateSetListener!!.onDateOnlyPickerCancelled(this@RecurrenceEndDatePicker)
			}
		}
	}

	/**
	 * Listener called when the user selects a day in the day picker view.
	 */
	private val mProxyDaySelectionEventListener = object : DayPickerView.ProxyDaySelectionEventListener {
		override fun onDaySelected(view: DayPickerView, day: Calendar?) {
			mCurrentDate = SelectedDate(day)
			onDateChanged(true, true)
		}

		override fun onDateRangeSelectionStarted(selectedDate: SelectedDate) {
			mCurrentDate = SelectedDate(selectedDate)
			onDateChanged(false, false)
		}

		override fun onDateRangeSelectionEnded(selectedDate: SelectedDate?) {
			if (selectedDate != null) {
				mCurrentDate = SelectedDate(selectedDate)
				onDateChanged(false, false)
			}
		}

		override fun onDateRangeSelectionUpdated(selectedDate: SelectedDate) {
			if (Config.DEBUG) {
				Log.i(TAG, "onDateRangeSelectionUpdated: $selectedDate")
			}

			mCurrentDate = SelectedDate(selectedDate)
			onDateChanged(false, false)
		}
	}

	val selectedDate: SelectedDate
		get() = SelectedDate(mCurrentDate)

	val selectedDateInMillis: Long
		get() = mCurrentDate!!.startDate.timeInMillis

	@JvmOverloads
	constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = R.attr.spDatePickerStyle) : super(context, attrs, defStyleAttr) {
		initializeLayout(attrs, defStyleAttr, R.style.SublimeDatePickerStyle)
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(context: Context, attrs: AttributeSet,
	            defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
		initializeLayout(attrs, defStyleAttr, defStyleRes)
	}

	private fun initializeLayout(attrs: AttributeSet?,
	                             defStyleAttr: Int, defStyleRes: Int) {
		mContext = context

		setCurrentLocale(Locale.getDefault())
		mCurrentDate = SelectedDate(Calendar.getInstance(mCurrentLocale))
		mTempDate = Calendar.getInstance(mCurrentLocale)
		minDate = Calendar.getInstance(mCurrentLocale)
		maxDate = Calendar.getInstance(mCurrentLocale)

		minDate!!.set(DEFAULT_START_YEAR, Calendar.JANUARY, 1)
		maxDate!!.set(DEFAULT_END_YEAR, Calendar.DECEMBER, 31)

		val res = resources
		val a = mContext!!.obtainStyledAttributes(attrs,
				R.styleable.SublimeDatePicker, defStyleAttr, defStyleRes)
		val inflater = mContext!!.getSystemService(
				Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
		val layoutResourceId = R.layout.recurrence_end_date_picker

		try {
			// Set up and attach container.
			mContainer = inflater.inflate(layoutResourceId, this, false) as ViewGroup
		} catch (e: Exception) {
			e.printStackTrace()
		}

		addView(mContainer)

		var firstDayOfWeek = a.getInt(R.styleable.SublimeDatePicker_spFirstDayOfWeek,
				mCurrentDate!!.firstDate!!.firstDayOfWeek)

		val minDate = a.getString(R.styleable.SublimeDatePicker_spMinDate)
		val maxDate = a.getString(R.styleable.SublimeDatePicker_spMaxDate)

		// Set up min and max dates.
		val tempDate = Calendar.getInstance()

		if (!SUtils.parseDate(minDate, tempDate)) {
			tempDate.set(DEFAULT_START_YEAR, Calendar.JANUARY, 1)
		}

		val minDateMillis = tempDate.timeInMillis

		if (!SUtils.parseDate(maxDate, tempDate)) {
			tempDate.set(DEFAULT_END_YEAR, Calendar.DECEMBER, 31)
		}

		val maxDateMillis = tempDate.timeInMillis

		if (maxDateMillis < minDateMillis) {
			throw IllegalArgumentException("maxDate must be >= minDate")
		}

		val setDateMillis = SUtils.constrain(
				System.currentTimeMillis(), minDateMillis, maxDateMillis)

		this.minDate!!.timeInMillis = minDateMillis
		this.maxDate!!.timeInMillis = maxDateMillis
		mCurrentDate!!.setTimeInMillis(setDateMillis)

		a.recycle()

		mDecisionButtonLayout = mContainer!!.findViewById<View>(R.id.redp_decision_button_layout)
		mDecisionButtonLayout!!.applyOptions(mDecisionButtonLayoutCallback)

		// Set up day picker view.
		mDayPickerView = mContainer!!.findViewById<View>(R.id.redp_day_picker)
		firstDayOfWeek = firstDayOfWeek
		mDayPickerView!!.minDate = this.minDate!!.timeInMillis
		mDayPickerView!!.maxDate = this.maxDate!!.timeInMillis
		mDayPickerView!!.date = mCurrentDate
		mDayPickerView!!.setProxyDaySelectionEventListener(mProxyDaySelectionEventListener)
		mDayPickerView!!.setCanPickRange(false)

		// Set up content descriptions.
		val selectDay = res.getString(R.string.select_day)

		// Initialize for current locale. This also initializes the date, so no
		// need to call onDateChanged.
		onLocaleChanged(mCurrentLocale)
		AccessibilityUtils.makeAnnouncement(mDayPickerView, selectDay)
	}

	private fun onLocaleChanged(locale: Locale?) {
		val dayPickerView = mDayPickerView
				?: // Abort, we haven't initialized yet. This method will get called
				// again later after everything has been set up.
				return

		// Update the header text.
		onCurrentDateChanged(false)
	}

	private fun onCurrentDateChanged(announce: Boolean) {
		if (mDayPickerView == null) {
			// Abort, we haven't initialized yet. This method will get called
			// again later after everything has been set up.
			return
		}

		// TODO: This should use live regions.
		if (announce) {
			val millis = mCurrentDate!!.startDate.timeInMillis
			val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR
			val fullDateText = DateUtils.formatDateTime(mContext, millis, flags)
			AccessibilityUtils.makeAnnouncement(mDayPickerView, fullDateText)
		}
	}

	/**
	 * Initialize the state. If the provided values designate an inconsistent
	 * date the values are normalized before updating the spinners.
	 *
	 * @param year        The initial year.
	 * @param monthOfYear The initial month **starting from zero**.
	 * @param dayOfMonth  The initial day of the month.
	 * @param callback    How user is notified date is changed by
	 * user, can be null.
	 */
	fun init(year: Int, monthOfYear: Int, dayOfMonth: Int,
	         callback: RecurrenceEndDatePicker.OnDateSetListener) {
		mCurrentDate!!.set(Calendar.YEAR, year)
		mCurrentDate!!.set(Calendar.MONTH, monthOfYear)
		mCurrentDate!!.set(Calendar.DAY_OF_MONTH, dayOfMonth)

		mOnDateSetListener = callback

		onDateChanged(false, true)
	}

	/**
	 * Update the current date.
	 *
	 * @param year       The year.
	 * @param month      The month which is **starting from zero**.
	 * @param dayOfMonth The day of the month.
	 */
	fun updateDate(year: Int, month: Int, dayOfMonth: Int) {
		mCurrentDate!!.set(Calendar.YEAR, year)
		mCurrentDate!!.set(Calendar.MONTH, month)
		mCurrentDate!!.set(Calendar.DAY_OF_MONTH, dayOfMonth)

		onDateChanged(false, true)
	}

	// callbackToClient is useless for now & gives us an unnecessary round-trip
	// by calling init(...)
	private fun onDateChanged(fromUser: Boolean, goToPosition: Boolean) {
		mDayPickerView!!.setDate(SelectedDate(mCurrentDate), false, goToPosition)

		onCurrentDateChanged(fromUser)

		if (fromUser) {
			SUtils.vibrateForDatePicker(this@RecurrenceEndDatePicker)
		}
	}

	/**
	 * Sets the minimal date supported by this [SublimeDatePicker] in
	 * milliseconds since January 1, 1970 00:00:00 in
	 * [java.util.TimeZone.getDefault] time zone.
	 *
	 * @param minDate The minimal supported date.
	 */
	fun setMinDate(minDate: Long) {
		mTempDate!!.timeInMillis = minDate
		if (mTempDate!!.get(Calendar.YEAR) == this.minDate!!.get(Calendar.YEAR) && mTempDate!!.get(Calendar.DAY_OF_YEAR) != this.minDate!!.get(Calendar.DAY_OF_YEAR)) {
			return
		}
		if (mCurrentDate!!.startDate.before(mTempDate)) {
			mCurrentDate!!.startDate.timeInMillis = minDate
			onDateChanged(false, true)
		}
		this.minDate!!.timeInMillis = minDate
		mDayPickerView!!.minDate = minDate
	}

	/**
	 * Sets the maximal date supported by this [SublimeDatePicker] in
	 * milliseconds since January 1, 1970 00:00:00 in
	 * [java.util.TimeZone.getDefault] time zone.
	 *
	 * @param maxDate The maximal supported date.
	 */
	fun setMaxDate(maxDate: Long) {
		mTempDate!!.timeInMillis = maxDate
		if (mTempDate!!.get(Calendar.YEAR) == this.maxDate!!.get(Calendar.YEAR) && mTempDate!!.get(Calendar.DAY_OF_YEAR) != this.maxDate!!.get(Calendar.DAY_OF_YEAR)) {
			return
		}
		if (mCurrentDate!!.endDate.after(mTempDate)) {
			mCurrentDate!!.endDate.timeInMillis = maxDate
			onDateChanged(false, true)
		}
		this.maxDate!!.timeInMillis = maxDate
		mDayPickerView!!.maxDate = maxDate
	}

	override fun setEnabled(enabled: Boolean) {
		if (isEnabled == enabled) {
			return
		}

		mContainer!!.isEnabled = enabled
		mDayPickerView!!.isEnabled = enabled
	}

	override fun isEnabled(): Boolean {
		return mContainer!!.isEnabled
	}

	public override fun onConfigurationChanged(newConfig: Configuration) {
		setCurrentLocale(newConfig.locale)
	}

	private fun setCurrentLocale(locale: Locale) {
		if (locale != mCurrentLocale) {
			mCurrentLocale = locale
			onLocaleChanged(locale)
		}
	}

	public override fun onSaveInstanceState(): Parcelable? {
		val superState = super.onSaveInstanceState()

		val listPosition = mDayPickerView!!.mostVisiblePosition

		return SavedState(superState, mCurrentDate!!, minDate!!.timeInMillis,
				maxDate!!.timeInMillis, listPosition)
	}

	@SuppressLint("NewApi")
	public override fun onRestoreInstanceState(state: Parcelable) {
		val bss = state as View.BaseSavedState
		super.onRestoreInstanceState(bss.getSuperState())
		val ss = bss as SavedState

		val date = Calendar.getInstance(mCurrentLocale)
		date.set(ss.selectedYear, ss.selectedMonth, ss.selectedDay)

		mCurrentDate!!.setDate(date)

		minDate!!.timeInMillis = ss.minDate
		maxDate!!.timeInMillis = ss.maxDate

		onCurrentDateChanged(false)

		val listPosition = ss.listPosition
		if (listPosition != -1) {
			mDayPickerView!!.setPosition(listPosition)
		}
	}

	override fun dispatchPopulateAccessibilityEvent(event: AccessibilityEvent): Boolean {
		onPopulateAccessibilityEvent(event)
		return true
	}

	override fun onPopulateAccessibilityEvent(event: AccessibilityEvent) {
		super.onPopulateAccessibilityEvent(event)
		event.text.add(mCurrentDate!!.startDate.time.toString())
	}

	override fun getAccessibilityClassName(): CharSequence {
		return SublimeDatePicker::class.java.getName()
	}

	fun setValidationCallback(callback: DatePickerValidationCallback) {
		mValidationCallback = callback
	}

	protected fun onValidationChanged(valid: Boolean) {
		if (mValidationCallback != null) {
			mValidationCallback!!.onDatePickerValidationChanged(valid)
		}

		mDecisionButtonLayout!!.updateValidity(valid)
	}

	/**
	 * A callback interface for updating input validity when the date picker
	 * when included into a dialog.
	 */
	interface DatePickerValidationCallback {
		fun onDatePickerValidationChanged(valid: Boolean)
	}

	/**
	 * Class for managing state storing/restoring.
	 */
	private class SavedState : View.BaseSavedState {

		val selectedYear: Int
		val selectedMonth: Int
		val selectedDay: Int
		val minDate: Long
		val maxDate: Long
		val listPosition: Int

		/**
		 * Constructor called from [SublimeDatePicker.onSaveInstanceState]
		 */
		private constructor(superState: Parcelable, selectedDate: SelectedDate,
		                    minDate: Long, maxDate: Long, listPosition: Int) : super(superState) {
			selectedYear = selectedDate.startDate.get(Calendar.YEAR)
			selectedMonth = selectedDate.startDate.get(Calendar.MONTH)
			selectedDay = selectedDate.startDate.get(Calendar.DAY_OF_MONTH)
			this.minDate = minDate
			this.maxDate = maxDate
			this.listPosition = listPosition
		}

		/**
		 * Constructor called from [.CREATOR]
		 */
		private constructor(`in`: Parcel) : super(`in`) {
			selectedYear = `in`.readInt()
			selectedMonth = `in`.readInt()
			selectedDay = `in`.readInt()
			minDate = `in`.readLong()
			maxDate = `in`.readLong()
			listPosition = `in`.readInt()
		}

		override fun writeToParcel(dest: Parcel, flags: Int) {
			super.writeToParcel(dest, flags)
			dest.writeInt(selectedYear)
			dest.writeInt(selectedMonth)
			dest.writeInt(selectedDay)
			dest.writeLong(minDate)
			dest.writeLong(maxDate)
			dest.writeInt(listPosition)
		}

		companion object {

			// suppress unused and hiding
			val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {

				override fun createFromParcel(`in`: Parcel): SavedState {
					return SavedState(`in`)
				}

				override fun newArray(size: Int): Array<SavedState> {
					return arrayOfNulls(size)
				}
			}
		}
	}

	/**
	 * The callback used to indicate the user changed the date.
	 */
	interface OnDateChangedListener {

		/**
		 * Called upon a date change.
		 *
		 * @param view         The view associated with this listener.
		 * @param selectedDate The date that was set.
		 */
		fun onDateChanged(view: RecurrenceEndDatePicker, selectedDate: SelectedDate)
	}

	/**
	 * The callback used to indicate the user is done filling in the date.
	 */
	interface OnDateSetListener {

		/**
		 * @param view        The view associated with this listener.
		 * @param year        The year that was set.
		 * @param monthOfYear The month that was set (0-11) for compatibility
		 * with [Calendar].
		 * @param dayOfMonth  The day of the month that was set.
		 */
		fun onDateSet(view: RecurrenceEndDatePicker, year: Int, monthOfYear: Int, dayOfMonth: Int)

		fun onDateOnlyPickerCancelled(view: RecurrenceEndDatePicker)
	}

	companion object {
		private val TAG = RecurrenceEndDatePicker::class.java.getSimpleName()

		private val DEFAULT_START_YEAR = 1900
		private val DEFAULT_END_YEAR = 2100
	}
}

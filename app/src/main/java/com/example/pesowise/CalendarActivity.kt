package com.example.pesowise

import android.Manifest
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pesowise.data.PesoWiseRepository
import com.example.pesowise.data.ReminderDto
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

class CalendarFragment : Fragment() {
    private var selectedDate = LocalDate.now()
    private var displayedMonth = YearMonth.now()
    private lateinit var ui: FinanceUi
    private lateinit var screen: FinanceScreen
    private lateinit var calendarBody: LinearLayout
    private lateinit var reminderContainer: LinearLayout
    private val repository by lazy { PesoWiseRepository() }
    private val preferences by lazy { requireContext().getSharedPreferences("PesoWiseReminders", MODE_PRIVATE) }
    private val reminders = mutableListOf<Reminder>()
    private val dateFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH)

    data class Reminder(val id: String, val title: String, val dueDate: LocalDate, val daysBefore: Int)

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (!isGranted) {
                Toast.makeText(
                    requireContext(),
                    "Notifications are disabled. " +
                            "Enable them in Settings to receive alerts.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private fun requestNotificationPermission() {

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }
    

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        selectedDate = savedInstanceState?.getString("selected_date")?.let(LocalDate::parse) ?: selectedDate
        displayedMonth = savedInstanceState?.getString("displayed_month")?.let(YearMonth::parse) ?: YearMonth.from(selectedDate)
        ui = FinanceUi(requireContext())
        screen = ui.screen("Calendar", displayedMonth, {
            ui.pickMonth(displayedMonth) { changeMonth(it) }
        }, "Add reminder", ::showReminderDialog)
        calendarBody = ui.card()
        reminderContainer = ui.column()
        ui.add(screen.content, calendarBody)
        ui.add(screen.content, reminderContainer)
        loadReminders()
        displayReminders()
        return screen.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requestNotificationPermission()
        reminders.forEach {
            ReminderScheduler.schedule(requireContext(), it.id, it.title, it.dueDate, it.daysBefore)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_date", selectedDate.toString())
        outState.putString("displayed_month", displayedMonth.toString())
        super.onSaveInstanceState(outState)
    }

    private fun changeMonth(month: YearMonth) {
        displayedMonth = month
        selectedDate = month.atDay(minOf(selectedDate.dayOfMonth, month.lengthOfMonth()))
        displayReminders()
    }

    private fun renderCalendar() {
        screen.monthButton.text = displayedMonth.format(FinanceUi.MONTH_FORMAT)
        screen.monthButton.contentDescription = "Select month, ${screen.monthButton.text}"
        calendarBody.removeAllViews()
        val header = ui.row()
        header.addView(ui.iconButton(R.drawable.ic_ui_left, "Previous month") { changeMonth(displayedMonth.minusMonths(1)) }.apply { strokeWidth = 0 })
        ui.add(header, ui.text(displayedMonth.format(FinanceUi.MONTH_FORMAT), 16f, true).apply { gravity = Gravity.CENTER }, width = 0, weight = 1f)
        header.addView(ui.iconButton(R.drawable.ic_ui_right, "Next month") { changeMonth(displayedMonth.plusMonths(1)) }.apply { strokeWidth = 0 })
        ui.add(calendarBody, header, bottom = 10)
        val weekdays = ui.row().apply {
            background = ui.shape(FinanceColors.lavender, 10)
            setPadding(0, ui.dp(8), 0, ui.dp(8))
        }
        listOf("S", "M", "T", "W", "T", "F", "S").forEach {
            ui.add(weekdays, ui.text(it, 12f, true, FinanceColors.muted).apply { gravity = Gravity.CENTER }, width = 0, weight = 1f)
        }
        ui.add(calendarBody, weekdays, bottom = 8)
        val datesWithReminders = reminders.map { it.dueDate }.toSet()
        calendarCells(displayedMonth).chunked(7).forEach { week ->
            val row = ui.row()
            week.forEach { date ->
                val cell = FrameLayout(requireContext())
                if (date != null) {
                    val selected = date == selectedDate
                    val label = ui.text(date.dayOfMonth.toString(), 14f, selected || date == LocalDate.now(),
                        if (selected) Color.WHITE else if (date == LocalDate.now()) FinanceColors.purple else FinanceColors.ink).apply {
                        gravity = Gravity.CENTER
                        if (selected) background = ui.shape(FinanceColors.purple, 24)
                    }
                    cell.addView(label, FrameLayout.LayoutParams(ui.dp(36), ui.dp(36), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
                    if (date in datesWithReminders) cell.addView(View(requireContext()).apply {
                        background = ui.shape(FinanceColors.purple, 4)
                    }, FrameLayout.LayoutParams(ui.dp(4), ui.dp(4), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = ui.dp(4) })
                    cell.isClickable = true
                    cell.isFocusable = true
                    cell.isSelected = selected
                    cell.contentDescription = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)) +
                        (if (selected) ", selected" else "") + (if (date in datesWithReminders) ", has reminders" else "")
                    cell.setOnClickListener { selectedDate = date; displayReminders() }
                    label.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                ui.add(row, cell, width = 0, height = ui.dp(48), weight = 1f)
            }
            ui.add(calendarBody, row)
        }
    }

    private fun showReminderDialog() {

        val dueDate = selectedDate

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL

            val padding =
                (20 * resources.displayMetrics.density).toInt()

            setPadding(padding, 8, padding, 0)
        }

        val titleInput = EditText(requireContext()).apply {
            hint = "Reminder title (e.g. Bills)"
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT
        }

        val dateText = TextView(requireContext()).apply {
            text = "Due Date: ${dueDate.format(dateFormatter)}"
            textSize = 16f
            setPadding(0, 20, 0, 12)
        }

        val reminderOptions = listOf(
            "On the day",
            "1 day before",
            "2 days before",
            "3 days before",
            "7 days before"
        )

        val reminderSpinner = Spinner(requireContext())

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            reminderOptions
        ).apply {
            setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item
            )
        }

        reminderSpinner.adapter = adapter

        layout.addView(titleInput)
        layout.addView(dateText)
        layout.addView(reminderSpinner)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add Reminder")
            .setView(layout)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val title = titleInput.text
                        .toString()
                        .trim()

                    if (title.isEmpty()) {
                        titleInput.error =
                            "Enter a reminder title"
                        return@setOnClickListener
                    }

                    val daysBefore = when (
                        reminderSpinner.selectedItemPosition
                    ) {
                        1 -> 1
                        2 -> 2
                        3 -> 3
                        4 -> 7
                        else -> 0
                    }

                    // Create new reminder
                    val newReminder = Reminder(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        dueDate = dueDate,
                        daysBefore = daysBefore
                    )

                    // Add reminder to list
                    reminders.add(newReminder)

                    // Save locally
                    saveReminders()

                    // Sync to Supabase DB asynchronously
                    lifecycleScope.launch {
                        repository.insertReminder(
                            ReminderDto(
                                title = newReminder.title,
                                dueDate = newReminder.dueDate.toString(),
                                daysBefore = newReminder.daysBefore
                            )
                        )
                    }

                    // Schedule notification
                    val isScheduled =
                        ReminderScheduler.schedule(
                            context = requireContext(),
                            id = newReminder.id,
                            title = newReminder.title,
                            dueDate = newReminder.dueDate,
                            daysBefore = newReminder.daysBefore
                        )

                    // Update reminder list
                    displayReminders()

                    // Show result
                    if (isScheduled) {
                        Toast.makeText(
                            requireContext(),
                            "Reminder saved and notification scheduled!",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Reminder saved, but notification date has passed.",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    dialog.dismiss()
                }
        }

        dialog.show()
    }

    // -----------------------------------------
    // SAVE REMINDERS
    // -----------------------------------------

    private fun saveReminders() {

        val jsonArray = JSONArray()

        for (reminder in reminders) {

            val jsonObject = JSONObject().apply {
                put("id", reminder.id)
                put("title", reminder.title)
                put("dueDate", reminder.dueDate.toString())
                put("daysBefore", reminder.daysBefore)
            }

            jsonArray.put(jsonObject)
        }

        preferences.edit()
            .putString(
                "reminder_list",
                jsonArray.toString()
            )
            .apply()
    }

    // -----------------------------------------
    // LOAD SAVED REMINDERS
    // -----------------------------------------

    private fun loadReminders() {

        reminders.clear()

        val savedData = preferences.getString(
            "reminder_list",
            "[]"
        ) ?: "[]"

        try {
            val jsonArray = JSONArray(savedData)

            for (i in 0 until jsonArray.length()) {

                val jsonObject = jsonArray.getJSONObject(i)

                val reminder = Reminder(
                    id = jsonObject.getString("id"),
                    title = jsonObject.getString("title"),
                    dueDate = LocalDate.parse(
                        jsonObject.getString("dueDate")
                    ),
                    daysBefore = jsonObject.getInt("daysBefore")
                )

                reminders.add(reminder)
            }

        } catch (e: JSONException) {
            Toast.makeText(
                requireContext(),
                "Unable to load saved reminders.",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: java.time.format.DateTimeParseException) {
            Toast.makeText(
                requireContext(),
                "Invalid date in saved reminders.",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    private fun displayReminders() {
        if (!::reminderContainer.isInitialized) return
        renderCalendar()
        reminderContainer.removeAllViews()
        val monthReminders = reminders.filter { YearMonth.from(it.dueDate) == displayedMonth }.sortedBy { it.dueDate }
        val upcoming = monthReminders.filter { !it.dueDate.isBefore(LocalDate.now()) }
        val earlier = monthReminders.filter { it.dueDate.isBefore(LocalDate.now()) }
        ui.heading(reminderContainer, "Upcoming reminders", ui.badge(upcoming.size.toString()))
        if (upcoming.isEmpty()) ui.empty(reminderContainer, "Nothing coming up", "Choose a date and add a reminder for this month.", R.drawable.ic_ui_bell)
        fun addReminder(reminder: Reminder) {
            val card = ui.card(padding = 12)
            val row = ui.row()
            row.addView(ui.icon(categoryIcon(reminder.title)))
            ui.gap(row, 12, true)
            val labels = ui.column()
            ui.add(labels, ui.text(reminder.title, 15f, true))
            ui.add(labels, ui.text(reminder.dueDate.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)),
                12f, color = FinanceColors.muted), top = 4)
            val notificationDate = reminder.dueDate.minusDays(reminder.daysBefore.toLong())
            ui.add(labels, ui.text("Remind " + notificationDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)),
                11f, color = FinanceColors.muted), top = 2)
            ui.add(row, labels, width = 0, weight = 1f)
            ui.gap(row, 6, true)
            val days = ChronoUnit.DAYS.between(LocalDate.now(), reminder.dueDate)
            val timing = when {
                days < 0 -> "Past due"
                days == 0L -> "Today"
                days == 1L -> "Tomorrow"
                else -> "In $days days"
            }
            row.addView(ui.badge(timing, if (days < 0) FinanceColors.coral else FinanceColors.purple,
                if (days < 0) FinanceColors.coralTint else FinanceColors.lavender))
            ui.add(card, row)
            ui.add(reminderContainer, card, bottom = 10)
        }
        upcoming.forEach(::addReminder)
        if (earlier.isNotEmpty()) {
            ui.heading(reminderContainer, "Earlier this month")
            earlier.forEach(::addReminder)
        }
    }
}

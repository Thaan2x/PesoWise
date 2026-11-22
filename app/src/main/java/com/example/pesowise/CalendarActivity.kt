package com.example.pesowise

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CalendarView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

class CalendarActivity : AppCompatActivity() {

    // UI Components
    private lateinit var calendarView: CalendarView
    private lateinit var txtSelectedDate: TextView
    private lateinit var reminderContainer: LinearLayout

    // Selected calendar date
    private var selectedDate: LocalDate = LocalDate.now()

    // Date formatting
    private val dateFormatter =
        DateTimeFormatter.ofPattern(
            "MMMM dd, yyyy",
            Locale.ENGLISH
        )

    // Local storage
    private val preferences by lazy {
        getSharedPreferences(
            "PesoWiseReminders",
            MODE_PRIVATE
        )
    }

    // Reminder collection
    private val reminders = mutableListOf<Reminder>()

    // Reminder model
    data class Reminder(
        val id: String,
        val title: String,
        val dueDate: LocalDate,
        val daysBefore: Int
    )

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->

            if (!isGranted) {
                Toast.makeText(
                    this,
                    "Notifications are disabled. " +
                            "Enable them in Settings to receive alerts.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private fun requestNotificationPermission() {

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        // Request notification permission
        requestNotificationPermission()

        // Initialize UI
        calendarView = findViewById(R.id.calendarView)
        txtSelectedDate = findViewById(R.id.txtSelectedDate)
        reminderContainer = findViewById(R.id.reminderContainer)

        // Restore selected date
        selectedDate = savedInstanceState
            ?.getString("selected_date")
            ?.let {
                LocalDate.parse(it)
            }
            ?: LocalDate.now()

        // Set calendar to selected date
        calendarView.date = selectedDate
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        updateSelectedDate()

        // Load previously saved reminders
        loadReminders()
        displayReminders()

        // Schedule previously saved upcoming reminders
        for (reminder in reminders) {
            ReminderScheduler.schedule(
                context = this,
                id = reminder.id,
                title = reminder.title,
                dueDate = reminder.dueDate,
                daysBefore = reminder.daysBefore
            )
        }

        // Calendar date selection
        calendarView.setOnDateChangeListener {
                _, year, month, dayOfMonth ->

            selectedDate = LocalDate.of(
                year,
                month + 1,
                dayOfMonth
            )

            updateSelectedDate()
        }

        // Add Reminder button
        findViewById<Button>(R.id.btnAddReminder)
            .setOnClickListener {
                showReminderDialog()
            }

        // Back to Dashboard button
        findViewById<Button>(R.id.btnBackCalendar)
            .setOnClickListener {
                finish()
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            "selected_date",
            selectedDate.toString()
        )
        super.onSaveInstanceState(outState)
    }

    // -----------------------------------------
    // UPDATE SELECTED DATE
    // -----------------------------------------

    private fun updateSelectedDate() {
        txtSelectedDate.text =
            "Selected Date: ${selectedDate.format(dateFormatter)}"
    }

    // -----------------------------------------
    // ADD REMINDER DIALOG
    // -----------------------------------------

    private fun showReminderDialog() {

        val dueDate = selectedDate

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            val padding =
                (20 * resources.displayMetrics.density).toInt()

            setPadding(padding, 8, padding, 0)
        }

        val titleInput = EditText(this).apply {
            hint = "Reminder title (e.g. Bills)"
            inputType =
                android.text.InputType.TYPE_CLASS_TEXT
        }

        val dateText = TextView(this).apply {
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

        val reminderSpinner = Spinner(this)

        val adapter = ArrayAdapter(
            this,
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

        val dialog = AlertDialog.Builder(this)
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

                    // Schedule notification
                    val isScheduled =
                        ReminderScheduler.schedule(
                            context = this,
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
                            this,
                            "Reminder saved and notification scheduled!",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
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
                this,
                "Unable to load saved reminders.",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: java.time.format.DateTimeParseException) {
            Toast.makeText(
                this,
                "Invalid date in saved reminders.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // -----------------------------------------
    // DISPLAY REMINDERS
    // -----------------------------------------

    private fun displayReminders() {

        // Remove previously displayed views
        reminderContainer.removeAllViews()

        if (reminders.isEmpty()) {

            val emptyText = TextView(this).apply {
                text = "No reminders added yet."
                textSize = 16f
                setPadding(12, 12, 12, 12)
            }

            reminderContainer.addView(emptyText)
            return
        }

        // Sort by earliest due date
        val sortedReminders =
            reminders.sortedBy { it.dueDate }

        for (reminder in sortedReminders) {

            val notificationDate =
                reminder.dueDate.minusDays(
                    reminder.daysBefore.toLong()
                )

            val reminderText = TextView(this).apply {

                text = """
                    ${reminder.title}
                    Due: ${reminder.dueDate.format(dateFormatter)}
                    Remind: ${notificationDate.format(dateFormatter)}
                """.trimIndent()

                textSize = 16f

                val padding =
                    (16 * resources.displayMetrics.density).toInt()

                setPadding(
                    padding,
                    padding,
                    padding,
                    padding
                )
            }

            reminderContainer.addView(reminderText)
        }
    }
}
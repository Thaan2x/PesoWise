package com.example.pesowise

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class TransactionListActivity : AppCompatActivity() {
    private val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    private val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private var startDate: LocalDate? = null
    private var endDate: LocalDate? = null
    private lateinit var results: LinearLayout
    private lateinit var dateLabel: TextView
    private val prefs by lazy { getSharedPreferences("pesowise_transactions", MODE_PRIVATE) }
    private val purple = Color.rgb(103, 80, 164)
    private val ink = Color.rgb(42, 36, 65)
    private val light = Color.rgb(245, 241, 250)

    private data class Entry(val date: LocalDate, val time: String, val name: String, val amount: Double, val type: String, val order: Int) {
        val sortTime: String get() = if (time.matches(Regex("\\d{2}:\\d{2}"))) time else "00:00"
    }

    private fun button(label: String, compact: Boolean = true) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = if (compact) 12f else 14f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = 40.dp().toFloat()
            setColor(purple)
        }
        minHeight = 0
        minimumHeight = 0
        setPadding(16.dp(), 0, 16.dp(), 0)
    }

    private fun textLabel(value: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(ink)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(255, 251, 255))
        }
        root.addView(textLabel("Transaction History", 23f, true).apply {
            gravity = Gravity.CENTER
            setPadding(8.dp(), 22.dp(), 8.dp(), 16.dp())
        })
        val addTransactionButton = button("+ Add Transaction", false)
        root.addView(addTransactionButton, LinearLayout.LayoutParams(-1, 48.dp()).apply {
            leftMargin = 20.dp()
            rightMargin = 20.dp()
            bottomMargin = 8.dp()
        })
        addTransactionButton.setOnClickListener { showAddTransaction() }
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(18.dp(), 8.dp(), 18.dp(), 12.dp())
        }
        dateLabel = textLabel("", 14f, true)
        controls.addView(dateLabel, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(button("Date Range"), LinearLayout.LayoutParams(112.dp(), 36.dp()).apply {
            leftMargin = 8.dp()
        })
        controls.getChildAt(1).setOnClickListener { showDateRangeDialog() }
        root.addView(controls)
        root.addView(android.view.View(this).apply { setBackgroundColor(Color.rgb(229, 222, 237)) }, LinearLayout.LayoutParams(-1, 1.dp()))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(results)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 8.dp(), 20.dp(), 12.dp())
        }
        actions.addView(button("Back to Dashboard", false), LinearLayout.LayoutParams(-1, 48.dp()))
        actions.getChildAt(0).setOnClickListener { finish() }
        root.addView(actions)
        setContentView(root)
        renderEntries()
    }

    private fun showDateRangeDialog() {
        var selectedStart = startDate
        var selectedEnd = endDate
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 12.dp(), 20.dp(), 4.dp())
        }
        val from = button(selectedStart?.let { "Start: ${it.format(formatter)}" } ?: "Select Start Date")
        val to = button(selectedEnd?.let { "End: ${it.format(formatter)}" } ?: "Select End Date")
        from.setOnClickListener { pickDate(selectedStart) { selectedStart = it; from.text = "Start: ${it.format(formatter)}" } }
        to.setOnClickListener { pickDate(selectedEnd) { selectedEnd = it; to.text = "End: ${it.format(formatter)}" } }
        layout.addView(from, LinearLayout.LayoutParams(-1, 42.dp()).apply { bottomMargin = 10.dp() })
        layout.addView(to, LinearLayout.LayoutParams(-1, 42.dp()))
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Select Date Range")
            .setView(layout)
            .setPositiveButton("Search", null)
            .setNeutralButton("Show All", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val first = selectedStart
                val last = selectedEnd
                if (first == null || last == null) Toast.makeText(this, "Select both dates", Toast.LENGTH_SHORT).show()
                else if (first.isAfter(last)) Toast.makeText(this, "Start date must not be after end date", Toast.LENGTH_LONG).show()
                else { startDate = first; endDate = last; renderEntries(); dialog.dismiss() }
            }
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                startDate = null; endDate = null; renderEntries(); dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun pickDate(current: LocalDate?, onSelected: (LocalDate) -> Unit) {
        val date = current ?: LocalDate.now()
        DatePickerDialog(this, { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
            date.year, date.monthValue - 1, date.dayOfMonth).show()
    }

    private fun showAddTransaction() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp(), 8.dp(), 22.dp(), 4.dp())
        }
        val name = EditText(this).apply { hint = "Description (e.g. Groceries)" }
        val amount = EditText(this).apply {
            hint = "Amount in PHP"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val types = arrayOf("Expense", "Income")
        val categories = mutableListOf("Unassigned")
        val budgetPrefs = getSharedPreferences("PesoWiseBudget", MODE_PRIVATE)
        val planned = try {
            JSONArray(budgetPrefs.getString("${java.time.YearMonth.now()}_expenses", "[]"))
        } catch (_: Exception) { JSONArray() }
        for (i in 0 until planned.length()) {
            val category = planned.optJSONObject(i)?.optString("name", "") ?: ""
            if (category.isNotBlank() && category !in categories) categories.add(category)
        }
        val categorySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@TransactionListActivity, android.R.layout.simple_spinner_dropdown_item, categories)
        }
        val type = Spinner(this).apply {
            adapter = ArrayAdapter(this@TransactionListActivity, android.R.layout.simple_spinner_dropdown_item, types)
        }
        var date = LocalDate.now()
        val dateButton = button("Date: ${date.format(formatter)}")
        dateButton.setOnClickListener { pickDate(date) { chosen -> date = chosen; dateButton.text = "Date: ${date.format(formatter)}" } }
        layout.addView(name)
        layout.addView(amount)
        layout.addView(type)
        layout.addView(TextView(this).apply { text = "Budget category (for expenses)" })
        layout.addView(categorySpinner)
        layout.addView(dateButton, LinearLayout.LayoutParams(-1, 42.dp()).apply { topMargin = 8.dp() })
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add Transaction").setView(layout)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = amount.text.toString().toDoubleOrNull()
                if (name.text.isBlank() || value == null || !value.isFinite() || value <= 0.0) {
                    Toast.makeText(this, "Enter a description and valid positive amount", Toast.LENGTH_LONG).show()
                } else {
                    val entries = readEntries()
                    entries.put(JSONObject()
                        .put("name", name.text.toString().trim())
                        .put("amount", value)
                        .put("date", date.toString())
                        .put("time", java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
                        .put("type", if (type.selectedItemPosition == 1) "income" else "expense")
                        .put("category", if (type.selectedItemPosition == 1) "" else categories[categorySpinner.selectedItemPosition]))
                    prefs.edit().putString("entries", entries.toString()).apply()
                    renderEntries()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun readEntries(): JSONArray = try {
        JSONArray(prefs.getString("entries", "[]"))
    } catch (_: Exception) { JSONArray() }

    private fun renderEntries() {
        results.removeAllViews()
        dateLabel.text = if (startDate != null && endDate != null)
            "${startDate!!.format(formatter)} – ${endDate!!.format(formatter)}"
        else "As of ${LocalDate.now().format(formatter)}"
        val entries = readEntries()
        val matches = mutableListOf<Entry>()
        for (i in 0 until entries.length()) {
            try {
                val obj = entries.getJSONObject(i)
                val date = LocalDate.parse(obj.getString("date"))
                if ((startDate == null || !date.isBefore(startDate)) &&
                    (endDate == null || !date.isAfter(endDate))) {
                    matches.add(Entry(date, obj.optString("time", ""), obj.getString("name"),
                        obj.getDouble("amount"), obj.optString("type", "expense"), i))
                }
            } catch (_: Exception) { /* Keep other valid saved transactions. */ }
        }
        matches.sortWith(compareByDescending<Entry> { it.date }
            .thenByDescending { it.sortTime }.thenByDescending { it.order })
        if (matches.isEmpty()) {
            results.addView(textLabel("No transactions found for this date range.", 14f).apply {
                setPadding(20.dp(), 28.dp(), 20.dp(), 16.dp())
            })
            return
        }
        var previous: LocalDate? = null
        for (entry in matches) {
            if (entry.date != previous) {
                val heading = when (entry.date) {
                    LocalDate.now() -> "Today"
                    LocalDate.now().minusDays(1) -> "Yesterday"
                    else -> entry.date.format(formatter)
                }
                results.addView(textLabel(heading, 17f, true).apply {
                    setBackgroundColor(light)
                    setPadding(20.dp(), 11.dp(), 20.dp(), 11.dp())
                })
                previous = entry.date
            }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(20.dp(), 13.dp(), 20.dp(), 15.dp())
            }
            val time = try {
                java.time.LocalTime.parse(entry.time, java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    .format(timeFormatter)
            } catch (_: Exception) { "Time not recorded" }
            item.addView(textLabel(time, 12f).apply { setTextColor(Color.rgb(110, 103, 125)) })
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            line.addView(textLabel(entry.name, 16f, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(0, -2, 1f))
            val isIncome = entry.type.equals("income", ignoreCase = true)
            val formatted = String.format(Locale.US, "%,.2f", entry.amount)
            line.addView(textLabel("${if (isIncome) "+" else "−"}₱$formatted", 16f, true).apply {
                setTextColor(if (isIncome) Color.rgb(39, 132, 91) else Color.rgb(182, 67, 79))
                setPadding(8.dp(), 0, 0, 0)
            })
            item.addView(line)
            results.addView(item)
            results.addView(android.view.View(this).apply { setBackgroundColor(Color.rgb(235, 230, 239)) },
                LinearLayout.LayoutParams(-1, 1.dp()))
        }
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
}

package com.example.pesowise

import android.app.DatePickerDialog
import android.content.Context.MODE_PRIVATE
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pesowise.data.PesoWiseRepository
import com.example.pesowise.data.TransactionDto
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class TransactionsFragment : Fragment() {
    private var selectedMonth = YearMonth.now()
    private var startDate: LocalDate? = null
    private var endDate: LocalDate? = null
    private var allDates = false
    private var typeFilter = 0
    private lateinit var ui: FinanceUi
    private lateinit var screen: FinanceScreen
    private lateinit var summaries: LinearLayout
    private lateinit var filters: LinearLayout
    private lateinit var results: LinearLayout
    private lateinit var dateLabel: TextView
    private val prefs by lazy { requireContext().getSharedPreferences("pesowise_transactions", MODE_PRIVATE) }
    private val repository by lazy { PesoWiseRepository() }
    private val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        selectedMonth = savedInstanceState?.getString("month")?.let(YearMonth::parse) ?: selectedMonth
        startDate = savedInstanceState?.getString("start_date")?.let(LocalDate::parse)
        endDate = savedInstanceState?.getString("end_date")?.let(LocalDate::parse)
        allDates = savedInstanceState?.getBoolean("all_dates") ?: false
        typeFilter = savedInstanceState?.getInt("type_filter") ?: 0
        ui = FinanceUi(requireContext())
        screen = ui.screen("Transactions", selectedMonth, {
            ui.pickMonth(selectedMonth) {
                selectedMonth = it
                startDate = null
                endDate = null
                allDates = false
                renderEntries()
            }
        }, "Add transaction", ::showAddTransaction)
        summaries = ui.row()
        filters = ui.row()
        dateLabel = ui.text("", 12f, color = FinanceColors.muted)
        results = ui.column()
        ui.add(screen.content, summaries)
        ui.add(screen.content, filters, top = 20, bottom = 12)
        ui.add(screen.content, dateLabel, bottom = 10)
        ui.add(screen.content, results)
        renderEntries()
        return screen.root
    }

    override fun onResume() {
        super.onResume()
        renderEntries()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("month", selectedMonth.toString())
        outState.putString("start_date", startDate?.toString())
        outState.putString("end_date", endDate?.toString())
        outState.putBoolean("all_dates", allDates)
        outState.putInt("type_filter", typeFilter)
        super.onSaveInstanceState(outState)
    }

    private fun renderEntries() {
        if (!::results.isInitialized) return
        val records = readLocalTransactions(requireContext()).filter { record ->
            when {
                startDate != null && endDate != null -> !record.date.isBefore(startDate) && !record.date.isAfter(endDate)
                allDates -> true
                else -> YearMonth.from(record.date) == selectedMonth
            }
        }
        val totals = financeTotals(records)
        summaries.removeAllViews()
        ui.add(summaries, ui.summary("Income", totals.income, true), width = 0, weight = 1f)
        ui.gap(summaries, 12, true)
        ui.add(summaries, ui.summary("Expenses", totals.expenses, false), width = 0, weight = 1f)

        screen.monthButton.text = when {
            startDate != null -> "Custom dates"
            allDates -> "All dates"
            else -> selectedMonth.format(FinanceUi.MONTH_FORMAT)
        }
        screen.monthButton.contentDescription = "Select month, ${screen.monthButton.text}"
        filters.removeAllViews()
        listOf("All", "Income", "Expenses").forEachIndexed { index, label ->
            val chip = ui.button(label) { typeFilter = index; renderEntries() }.apply {
                backgroundTintList = ColorStateList.valueOf(if (index == typeFilter) FinanceColors.lavender else android.graphics.Color.TRANSPARENT)
                strokeWidth = if (index == typeFilter) 0 else ui.dp(1)
                setPadding(ui.dp(8), 0, ui.dp(8), 0)
                textSize = 12f
                isSelected = index == typeFilter
                contentDescription = label + if (isSelected) ", selected" else ""
            }
            if (index > 0) ui.gap(filters, 6, true)
            ui.add(filters, chip, width = 0, weight = 1f)
        }
        ui.gap(filters, 8, true)
        filters.addView(ui.iconButton(R.drawable.ic_nav_calendar, "Filter by date range", ::showDateRangeDialog))
        dateLabel.visibility = if (startDate != null) View.VISIBLE else View.GONE
        dateLabel.text = if (startDate != null && endDate != null)
            "${startDate!!.format(formatter)} – ${endDate!!.format(formatter)}" else ""

        val matches = records.filter { typeFilter == 0 || (typeFilter == 1 && it.isIncome) || (typeFilter == 2 && !it.isIncome) }
        results.removeAllViews()
        if (matches.isEmpty()) {
            ui.empty(results, "No transactions here yet", "Add a transaction or choose another date range.", R.drawable.ic_ui_receipt)
            return
        }
        val card = ui.card(padding = 12)
        var previous: LocalDate? = null
        matches.forEach { entry ->
            if (entry.date != previous) {
                val dateText = when (entry.date) {
                    LocalDate.now() -> "Today, " + entry.date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
                    LocalDate.now().minusDays(1) -> "Yesterday"
                    else -> entry.date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))
                }
                ui.add(card, ui.text(dateText, 13f, true).apply {
                    background = ui.shape(FinanceColors.lavender, 10)
                    setPadding(ui.dp(10), ui.dp(9), ui.dp(10), ui.dp(9))
                }, top = if (previous == null) 0 else 12, bottom = 4)
                previous = entry.date
            } else ui.divider(card, 4, 4)
            val row = ui.row().apply { setPadding(0, ui.dp(12), 0, ui.dp(12)) }
            row.addView(ui.icon(if (entry.isIncome) R.drawable.ic_ui_income else categoryIcon(entry.category),
                if (entry.isIncome) FinanceColors.green else FinanceColors.purple,
                if (entry.isIncome) FinanceColors.greenTint else FinanceColors.lavender))
            ui.gap(row, 12, true)
            val names = ui.column()
            ui.add(names, ui.text(entry.name, 15f, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            val detail = (if (entry.isIncome) "Income" else entry.category) + " · " +
                entry.time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
            ui.add(names, ui.text(detail, 11f, color = FinanceColors.muted).apply { maxLines = 2 }, top = 3)
            ui.add(row, names, width = 0, weight = 1f)
            ui.gap(row, 6, true)
            row.addView(ui.text((if (entry.isIncome) "+" else "−") + peso(entry.cents, true),
                14f, true, if (entry.isIncome) FinanceColors.green else FinanceColors.ink))
            ui.add(card, row)
        }
        ui.add(results, card)
    }

    private fun pickDate(date: LocalDate, onSelected: (LocalDate) -> Unit) {
        DatePickerDialog(requireContext(), { _, year, month, day ->
            onSelected(LocalDate.of(year, month + 1, day))
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }

    private fun showDateRangeDialog() {
        var first = startDate ?: selectedMonth.atDay(1)
        var last = endDate ?: selectedMonth.atEndOfMonth()
        val body = ui.column(20)
        val from = ui.button("From: ${first.format(formatter)}")
        val to = ui.button("To: ${last.format(formatter)}")
        from.setOnClickListener { pickDate(first) { first = it; from.text = "From: ${it.format(formatter)}" } }
        to.setOnClickListener { pickDate(last) { last = it; to.text = "To: ${it.format(formatter)}" } }
        ui.add(body, from, bottom = 12)
        ui.add(body, to)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle("Date range").setView(body)
            .setPositiveButton("Apply", null).setNeutralButton("All dates", null)
            .setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (first.isAfter(last)) {
                    to.error = "End date must be on or after start date"
                } else {
                    startDate = first; endDate = last; allDates = false
                    renderEntries(); dialog.dismiss()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                startDate = null; endDate = null; allDates = true
                renderEntries(); dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showAddTransaction() {
        val body = ui.column(20)
        val name = EditText(requireContext()).apply { hint = "Description"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
        val amount = EditText(requireContext()).apply { hint = "Amount in PHP"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val type = Spinner(requireContext()).apply {
            adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, listOf("Expense", "Income"))
        }
        var date = if (selectedMonth == YearMonth.now()) LocalDate.now() else selectedMonth.atDay(1)
        val categories = mutableListOf<String>()
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
        val category = Spinner(requireContext()).apply { adapter = categoryAdapter }
        fun loadCategories() {
            val previous = category.selectedItem?.toString()
            val options = listOf("Unassigned") + ExpenseCategoryStore.names(requireContext())
            categoryAdapter.setNotifyOnChange(false)
            categoryAdapter.clear()
            categoryAdapter.addAll(options)
            categoryAdapter.notifyDataSetChanged()
            val index = options.indexOfFirst { it.equals(previous, ignoreCase = true) }
            category.setSelection(if (index >= 0) index else 0)
        }
        loadCategories()
        val dateButton = ui.button("Date: ${date.format(formatter)}")
        dateButton.setOnClickListener {
            pickDate(date) { date = it; dateButton.text = "Date: ${it.format(formatter)}"; loadCategories() }
        }
        ui.add(body, name)
        ui.add(body, amount)
        ui.add(body, type, top = 8)
        ui.add(body, ui.text("Budget category for expenses", 12f, color = FinanceColors.muted), top = 12)
        ui.add(body, category)
        ui.add(body, dateButton, top = 12)
        val scroll = android.widget.ScrollView(requireContext()).apply { addView(body) }
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle("Add transaction").setView(scroll)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cents = runCatching { BigDecimal(amount.text.toString().trim()).movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact() }.getOrNull()
                when {
                    name.text.isBlank() -> name.error = "Enter a description"
                    cents == null || cents <= 0L -> amount.error = "Enter a positive amount with up to 2 decimals"
                    else -> {
                        val desc = name.text.toString().trim()
                        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                        val kind = if (type.selectedItemPosition == 1) "income" else "expense"
                        val cat = if (kind == "income") "" else category.selectedItem?.toString().orEmpty()
                        val value = cents / 100.0
                        val array = runCatching { JSONArray(prefs.getString("entries", "[]")) }.getOrDefault(JSONArray())
                        array.put(JSONObject().put("name", desc).put("amount", value).put("date", date.toString())
                            .put("time", time).put("type", kind).put("category", cat))
                        prefs.edit().putString("entries", array.toString()).apply()
                        lifecycleScope.launch {
                            repository.insertTransaction(TransactionDto(name = desc, amount = value, type = kind,
                                category = cat, date = date.toString(), time = time))
                        }
                        selectedMonth = YearMonth.from(date)
                        startDate = null; endDate = null; allDates = false; typeFilter = 0
                        renderEntries()
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }
}

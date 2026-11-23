package com.example.pesowise

import android.content.Context.MODE_PRIVATE
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pesowise.data.MonthlyBudgetDto
import com.example.pesowise.data.PesoWiseRepository
import com.example.pesowise.data.PlannedExpenseDto
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth
import java.util.Locale

class BudgetFragment : Fragment() {
    private var selectedMonth = YearMonth.now()
    private val month: String get() = selectedMonth.toString()
    private val prefs by lazy { requireContext().getSharedPreferences("PesoWiseBudget", MODE_PRIVATE) }
    private val repository by lazy { PesoWiseRepository() }
    private lateinit var ui: FinanceUi
    private lateinit var screen: FinanceScreen
    private lateinit var body: LinearLayout
    private var removeButton: MaterialButton? = null
    private val selectedExpenseIndices = mutableSetOf<Int>()
    private var deleteSelectionMode = false

    private data class Expense(
        val name: String,
        val cents: Long
    )

    private fun key(suffix: String): String {
        return "${month}_$suffix"
    }

    private fun savedAmount(suffix: String): Long {
        return prefs.getLong(key(suffix), 0L)
    }

    private fun parseAmount(raw: String): Long? {
        return try {
            val decimal = BigDecimal(
                raw.trim().replace(",", "")
            ).setScale(
                2,
                RoundingMode.UNNECESSARY
            )

            if (decimal.signum() < 0) {
                null
            } else {
                decimal.movePointRight(2).longValueExact()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun expenses(): MutableList<Expense> {

        val result = mutableListOf<Expense>()

        val array = try {
            JSONArray(
                prefs.getString(key("expenses"), "[]")
            )
        } catch (_: Exception) {
            JSONArray()
        }

        for (i in 0 until array.length()) {

            val item = array.optJSONObject(i) ?: continue

            val name = item.optString("name")
            val cents = item.optLong("cents", -1)

            if (name.isNotBlank() && cents >= 0) {
                result.add(
                    Expense(name, cents)
                )
            }
        }

        return result
    }

    private fun saveExpenses(items: List<Expense>) {

        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("name", item.name)
                    .put("cents", item.cents)
            )
        }

        prefs.edit()
            .putString(key("expenses"), array.toString())
            .apply()
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        selectedMonth = savedInstanceState?.getString("month")?.let(YearMonth::parse) ?: selectedMonth
        deleteSelectionMode = savedInstanceState?.getBoolean("selecting") ?: false
        selectedExpenseIndices.clear()
        selectedExpenseIndices.addAll(savedInstanceState?.getIntArray("selected")?.toList().orEmpty())
        ui = FinanceUi(requireContext())
        screen = ui.screen("Budget", selectedMonth, {
            ui.pickMonth(selectedMonth) {
                selectedMonth = it
                deleteSelectionMode = false
                selectedExpenseIndices.clear()
                refresh()
            }
        })
        body = ui.column()
        ui.add(screen.content, body)
        refresh()
        return screen.root
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("month", month)
        outState.putBoolean("selecting", deleteSelectionMode)
        outState.putIntArray("selected", selectedExpenseIndices.toIntArray())
        super.onSaveInstanceState(outState)
    }

    private fun editAmount(
        field: String,
        title: String
    ) {

        val input = EditText(requireContext()).apply {

            inputType =
                InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_FLAG_DECIMAL

            hint = "PHP 0.00"

            setText(
                BigDecimal.valueOf(
                    savedAmount(field),
                    2
                ).toPlainString()
            )

            selectAll()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val cents = parseAmount(
                    input.text.toString()
                )

                if (cents == null) {

                    input.error =
                        "Enter a valid non-negative amount (up to 2 decimals)"

                } else {

                    prefs.edit()
                        .putLong(key(field), cents)
                        .apply()

                    // Sync budget / money available with Supabase
                    lifecycleScope.launch {
                        val money = savedAmount("money")
                        val budget = savedAmount("budget")
                        repository.upsertMonthlyBudget(
                            MonthlyBudgetDto(
                                month = month,
                                availableMoneyCents = money,
                                plannedBudgetCents = budget
                            )
                        )
                    }

                    dialog.dismiss()
                    refresh()
                }
            }
        }

        dialog.show()
    }

    private fun addExpense() {

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }

        val nameInput = EditText(requireContext()).apply {

            hint = "Expense (e.g. Rent, Food)"

            inputType =
                InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }

        val valueInput = EditText(requireContext()).apply {

            hint = "Planned amount in PHP"

            inputType =
                InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        container.addView(nameInput)
        container.addView(valueInput)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add planned expense")
            .setView(container)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val cents = parseAmount(
                    valueInput.text.toString()
                )

                when {

                    nameInput.text.toString().isBlank() -> {
                        nameInput.error =
                            "Enter an expense name"
                    }

                    cents == null || cents == 0L -> {
                        valueInput.error =
                            "Enter an amount greater than zero"
                    }

                    else -> {

                        val expName = nameInput.text.toString().trim()
                        val items = expenses()

                        items.add(
                            Expense(
                                expName,
                                cents
                            )
                        )

                        saveExpenses(items)
                        // Keep this name reusable even when the monthly plan changes.
                        ExpenseCategoryStore.add(requireContext(), expName)

                        // Sync planned expense with Supabase
                        lifecycleScope.launch {
                            repository.insertPlannedExpense(
                                PlannedExpenseDto(
                                    month = month,
                                    name = expName,
                                    cents = cents
                                )
                            )
                        }

                        dialog.dismiss()
                        refresh()
                    }
                }
            }
        }

        dialog.show()
    }


    private fun updateDeleteControls() {
        removeButton?.text = "Remove (${selectedExpenseIndices.size})"
        removeButton?.isEnabled = selectedExpenseIndices.isNotEmpty()
    }

    private fun removeExpenses(indices: Set<Int>) {
        if (indices.isEmpty()) return
        MaterialAlertDialogBuilder(requireContext()).setTitle("Remove planned expenses?")
            .setMessage("Remove ${indices.size} planned expense(s)? Recorded transactions are kept.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                val items = expenses()
                indices.sortedDescending().forEach { if (it in items.indices) items.removeAt(it) }
                saveExpenses(items)
                deleteSelectionMode = false
                selectedExpenseIndices.clear()
                refresh()
            }.show()
    }

    private fun refresh() {
        if (!::body.isInitialized) return
        screen.monthButton.text = selectedMonth.format(FinanceUi.MONTH_FORMAT)
        screen.monthButton.contentDescription = "Select month, ${screen.monthButton.text}"
        body.removeAllViews()
        removeButton = null
        val records = readLocalTransactions(requireContext()).filter { YearMonth.from(it.date) == selectedMonth }
        val actual = categoryTotals(records)
        val spent = financeTotals(records).expenses
        val money = savedAmount("money")
        val budget = savedAmount("budget")
        val items = expenses()
        selectedExpenseIndices.retainAll(items.indices.toSet())
        val planned = items.sumOf { it.cents }
        val projected = maxOf(planned, spent)

        val hero = ui.card(FinanceColors.lavender)
        val heroRow = ui.row()
        val heroValues = ui.column()
        ui.add(heroValues, ui.text("Money remaining", 14f, color = FinanceColors.muted))
        ui.add(heroValues, ui.amount(money - spent, 32, if (spent > money) FinanceColors.coral else FinanceColors.ink), top = 5)
        ui.add(heroValues, ui.text("From ${peso(money)} starting money", 12f, color = FinanceColors.muted), top = 3)
        ui.add(heroRow, heroValues, width = 0, weight = 1f)
        heroRow.addView(ui.iconButton(R.drawable.ic_ui_edit, "Edit starting money") {
            editAmount("money", "Starting money for ${selectedMonth.format(FinanceUi.MONTH_FORMAT)}")
        })
        hero.addView(heroRow)
        ui.add(body, hero)

        val budgetCard = ui.card()
        val budgetHeader = ui.row()
        ui.add(budgetHeader, ui.text("Monthly budget", 18f, true), width = 0, weight = 1f)
        budgetHeader.addView(ui.button("Edit") { editAmount("budget", "Monthly budget") })
        ui.add(budgetCard, budgetHeader)
        ui.add(budgetCard, ui.amount(spent, 26), top = 4)
        ui.add(budgetCard, ui.text("of ${peso(budget)} spent", 12f, color = FinanceColors.muted), top = 2)
        ui.progress(budgetCard, spent, budget, if (spent > budget && budget > 0L) FinanceColors.coral else FinanceColors.purple)
        val footer = ui.row()
        val remaining = budget - spent
        ui.add(footer, ui.text(if (remaining >= 0L) "${peso(remaining)} left" else "${peso(-remaining)} over",
            13f, true, if (remaining < 0L) FinanceColors.coral else FinanceColors.muted), width = 0, weight = 1f)
        val status = when {
            money == 0L -> "Add money"
            budget == 0L -> "Set budget"
            projected > money -> "Short on funds"
            projected > budget -> "Over budget"
            else -> "On track"
        }
        val healthy = money > 0L && budget > 0L && projected <= minOf(money, budget)
        footer.addView(ui.badge(status, if (healthy) FinanceColors.green else FinanceColors.purple,
            if (healthy) FinanceColors.greenTint else FinanceColors.lavender))
        ui.add(budgetCard, footer)
        if (money > 0L && projected > money) {
            ui.add(budgetCard, ui.text("Planned or actual spending exceeds your starting money by ${peso(projected - money)}.",
                12f, color = FinanceColors.coral), top = 10)
        } else if (budget > 0L && planned > budget) {
            ui.add(budgetCard, ui.text("Your planned expenses exceed the budget by ${peso(planned - budget)}.",
                12f, color = FinanceColors.coral), top = 10)
        }
        ui.add(body, budgetCard, top = 16)

        val add = ui.button("Add", icon = R.drawable.ic_ui_plus, onClick = ::addExpense)
        ui.heading(body, "Planned expenses", if (deleteSelectionMode)
            ui.button("Cancel") { deleteSelectionMode = false; selectedExpenseIndices.clear(); refresh() } else add)
        if (deleteSelectionMode) {
            removeButton = ui.button("Remove (0)", true) { removeExpenses(selectedExpenseIndices.toSet()) }
            ui.add(body, removeButton!!, bottom = 12)
            updateDeleteControls()
        }
        items.forEachIndexed { index, item ->
            val value = actual[item.name] ?: 0L
            val card = ui.card(padding = 12)
            val row = ui.row()
            if (deleteSelectionMode) {
                row.addView(CheckBox(requireContext()).apply {
                    contentDescription = "Select ${item.name}"
                    buttonTintList = ColorStateList.valueOf(FinanceColors.purple)
                    isChecked = index in selectedExpenseIndices
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) selectedExpenseIndices.add(index) else selectedExpenseIndices.remove(index)
                        updateDeleteControls()
                    }
                })
            } else row.addView(ui.icon(categoryIcon(item.name)))
            ui.gap(row, 12, true)
            val labels = ui.column()
            ui.add(labels, ui.text(item.name, 15f, true))
            ui.add(labels, ui.text("${peso(value)} / ${peso(item.cents)}", 12f, color = FinanceColors.muted), top = 3)
            ui.add(row, labels, width = 0, weight = 1f)
            if (!deleteSelectionMode) {
                lateinit var more: MaterialButton
                more = ui.iconButton(R.drawable.ic_ui_more, "Options for ${item.name}") {
                    PopupMenu(requireContext(), more).apply {
                        menu.add("Remove expense")
                        menu.add("Select multiple")
                        setOnMenuItemClickListener {
                            if (it.title == "Remove expense") removeExpenses(setOf(index))
                            else {
                                deleteSelectionMode = true
                                selectedExpenseIndices.add(index)
                                refresh()
                            }
                            true
                        }
                    }.show()
                }
                more.strokeWidth = 0
                row.addView(more)
            }
            ui.add(card, row)
            ui.progress(card, value, item.cents, if (value > item.cents) FinanceColors.coral else FinanceColors.purple)
            ui.add(card, ui.text(if (value <= item.cents) "${peso(item.cents - value)} remaining" else "${peso(value - item.cents)} over plan",
                11f, color = if (value > item.cents) FinanceColors.coral else FinanceColors.muted))
            ui.add(body, card, bottom = 8)
        }
        val assigned = items.map { it.name }.toSet()
        actual.filterKeys { it !in assigned }.forEach { (category, value) ->
            val card = ui.card(padding = 12)
            val row = ui.row()
            row.addView(ui.icon(categoryIcon(category)))
            ui.gap(row, 12, true)
            val labels = ui.column()
            ui.add(labels, ui.text(category, 15f, true))
            ui.add(labels, ui.text("${peso(value)} spent · No plan set", 12f, color = FinanceColors.muted), top = 3)
            ui.add(row, labels, width = 0, weight = 1f)
            ui.add(card, row)
            ui.add(body, card, bottom = 8)
        }
        if (items.isEmpty() && actual.isEmpty()) {
            ui.empty(body, "Give your money a plan", "Add a planned expense to start tracking each category.", R.drawable.ic_ui_bag)
        }
        ui.add(body, ui.text("Category spending updates automatically when you add an expense transaction.",
            12f, color = FinanceColors.muted), top = 12)
    }
}

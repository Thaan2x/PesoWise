
package com.example.pesowise

import android.graphics.Color
import android.os.Bundle
import android.content.Context.MODE_PRIVATE
import android.view.View
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pesowise.data.MonthlyBudgetDto
import com.example.pesowise.data.PesoWiseRepository
import com.example.pesowise.data.PlannedExpenseDto
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.YearMonth
import java.util.Locale

class BudgetFragment : Fragment(R.layout.activity_budget) {

    private val prefs by lazy {
        requireContext().getSharedPreferences("PesoWiseBudget", MODE_PRIVATE)
    }

    private val repository by lazy {
        PesoWiseRepository()
    }

    private val month: String
        get() = YearMonth.now().toString()

    private val moneyFormat =
        NumberFormat.getCurrencyInstance(
            Locale.forLanguageTag("en-PH")
        )

    private lateinit var balanceView: TextView
    private lateinit var budgetView: TextView
    private lateinit var plannedView: TextView
    private lateinit var leftView: TextView
    private lateinit var statusView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var expenseList: LinearLayout
    private val selectedExpenseIndices = mutableSetOf<Int>()
    private var deleteSelectionMode = false

    private fun updateDeleteControls() {
        val trash = requireView().findViewById<ImageButton>(R.id.btnDeleteSelected)
        val remove = requireView().findViewById<Button>(R.id.btnRemoveSelected)
        val hasSelection = deleteSelectionMode && selectedExpenseIndices.isNotEmpty()
        trash.visibility = if (hasSelection) View.GONE else View.VISIBLE
        remove.visibility = if (hasSelection) View.VISIBLE else View.GONE
        remove.text = "Remove (${selectedExpenseIndices.size})"
        trash.contentDescription = if (deleteSelectionMode)
            "Cancel expense selection" else "Select expenses to remove"
    }

    private data class Expense(
        val name: String,
        val cents: Long
    )

    private fun key(suffix: String): String {
        return "${month}_$suffix"
    }

    private fun amount(cents: Long): String {
        return moneyFormat.format(
            BigDecimal.valueOf(cents, 2)
        )
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        balanceView = requireView().findViewById(R.id.txtMoney)
        budgetView = requireView().findViewById(R.id.txtBudget)
        plannedView = requireView().findViewById(R.id.txtSpent)
        leftView = requireView().findViewById(R.id.txtRemaining)
        statusView = requireView().findViewById(R.id.txtStatus)
        progress = requireView().findViewById(R.id.budgetProgress)
        expenseList = requireView().findViewById(R.id.expenseList)
        requireView().findViewById<ImageButton>(R.id.btnDeleteSelected).setOnClickListener {
            deleteSelectionMode = !deleteSelectionMode
            selectedExpenseIndices.clear()
            refresh()
        }
        requireView().findViewById<Button>(R.id.btnRemoveSelected).setOnClickListener {
            if (selectedExpenseIndices.isNotEmpty()) {
                val count = selectedExpenseIndices.size
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete selected expenses?")
                    .setMessage("Remove $count planned expense(s)? Recorded transactions will not be deleted.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        val updated = expenses()
                        selectedExpenseIndices.sortedDescending().forEach { index ->
                            if (index in updated.indices) updated.removeAt(index)
                        }
                        saveExpenses(updated)
                        selectedExpenseIndices.clear()
                        deleteSelectionMode = false
                        refresh()
                    }
                    .show()
            }
        }

        requireView().findViewById<Button>(R.id.btnSetMoney)
            .setOnClickListener {
                editAmount(
                    "money",
                    "Enter money available this month"
                )
            }

        requireView().findViewById<Button>(R.id.btnSetBudget)
            .setOnClickListener {
                editAmount(
                    "budget",
                    "Create monthly budget"
                )
            }

        requireView().findViewById<Button>(R.id.btnAddExpense)
            .setOnClickListener {
                addExpense()
            }

        refresh()
    }

    override fun onResume() {
        super.onResume()

        if (::balanceView.isInitialized) {
            refresh()
        }
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

        val dialog = AlertDialog.Builder(requireContext())
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

        val dialog = AlertDialog.Builder(requireContext())
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

    private fun actualExpenses(): Pair<Map<String, Long>, Long> {
        val totals = mutableMapOf<String, Long>()
        var total = 0L
        val transactionPrefs = requireContext().getSharedPreferences("pesowise_transactions", MODE_PRIVATE)
        val transactions = try {
            JSONArray(transactionPrefs.getString("entries", "[]"))
        } catch (_: Exception) { JSONArray() }
        for (i in 0 until transactions.length()) {
            val transaction = transactions.optJSONObject(i) ?: continue
            if (transaction.optString("type", "expense") != "expense") continue
            val date = transaction.optString("date", "")
            if (!date.startsWith("$month-")) continue
            val value = try {
                BigDecimal(transaction.getString("amount"))
                    .movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
            } catch (_: Exception) { continue }
            if (value <= 0) continue
            val category = transaction.optString("category", "Unassigned").ifBlank { "Unassigned" }
            totals[category] = (totals[category] ?: 0L) + value
            total += value
        }
        return totals to total
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    private fun refresh() {
        val money = savedAmount("money")
        val budget = savedAmount("budget")
        val items = expenses()
        val planned = items.fold(0L) { total, item -> total + item.cents }
        val (actualByCategory, spent) = actualExpenses()
        val remainingMoney = money - spent
        val remainingBudget = budget - spent
        balanceView.text = amount(money)
        requireView().findViewById<TextView>(R.id.txtCashLeft).apply {
            text = amount(remainingMoney)
            setTextColor(if (remainingMoney < 0L) Color.rgb(183, 28, 28) else Color.rgb(46, 125, 50))
        }
        requireView().findViewById<TextView>(R.id.txtTotalSpent).text = amount(spent)
        budgetView.text = amount(budget)
        plannedView.text = "Planned: ${amount(planned)}  |  Spent: ${amount(spent)}"
        leftView.text = "Money left: ${amount(remainingMoney)}  |  Budget left: ${amount(remainingBudget)}"
        val ratio = if (budget > 0L) ((spent.toDouble() / budget) * 100).toInt().coerceIn(0, 100) else 0
        progress.progress = ratio
        requireView().findViewById<TextView>(R.id.txtPercentage).text = "$ratio% of budget spent"
        val projected = maxOf(planned, spent)
        val status = when {
            money == 0L -> "Enter your available money to check affordability."
            budget == 0L -> "Create a monthly budget to get started."
            spent > money -> "SHORT ON FUNDS: Actual spending exceeds your money by ${amount(spent - money)}."
            projected > money -> "SHORT ON FUNDS: Planned spending exceeds your money by ${amount(projected - money)}."
            spent > budget -> "OVER BUDGET: Spending exceeds your budget by ${amount(spent - budget)}."
            projected > budget -> "OVER BUDGET: Planned expenses exceed your budget by ${amount(projected - budget)}."
            projected == money -> "JUST ENOUGH: Your money exactly covers your expenses."
            else -> "MORE THAN ENOUGH: ${amount(remainingMoney)} cash left; ${amount(money - projected)} projected after planned expenses."
        }
        statusView.text = status
        statusView.setTextColor(if (spent > money || projected > money || spent > budget || projected > budget)
            Color.rgb(183, 28, 28) else Color.rgb(46, 125, 50))
        expenseList.removeAllViews()
        updateDeleteControls()
        val purple = Color.rgb(103, 80, 164)
        fun cell(value: String, width: Int, bold: Boolean = false): TextView = TextView(requireContext()).apply {
            text = value
            textSize = if (bold) 14f else 15f
            setTextColor(if (bold) purple else Color.DKGRAY)
            setPadding(10.dp(), 16.dp(), 10.dp(), 16.dp())
            layoutParams = LinearLayout.LayoutParams(width.dp(), LinearLayout.LayoutParams.WRAP_CONTENT)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
        }
        fun tableRow(category: String, plan: String, actual: String, left: String, header: Boolean = false): LinearLayout =
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                if (header) setBackgroundColor(Color.rgb(240, 235, 248))
                addView(cell(category, 135, header))
                addView(cell(plan, 115, header))
                addView(cell(actual, 115, header))
                addView(cell(left, 115, header))
            }
        expenseList.addView(tableRow("Category", "Planned", "Spent", "Left", true))
        // A checkbox selects a planned expense for bulk deletion; it does not record spending.
        items.forEachIndexed { index, item ->
            val actual = actualByCategory[item.name] ?: 0L
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val checkbox = CheckBox(requireContext()).apply {
                text = item.name
                textSize = 15f
                setTextColor(Color.DKGRAY)
                buttonTintList = android.content.res.ColorStateList.valueOf(purple)
                isChecked = index in selectedExpenseIndices
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedExpenseIndices.add(index)
                    else selectedExpenseIndices.remove(index)
                    updateDeleteControls()
                }
            }
            if (deleteSelectionMode) {
                row.addView(checkbox, LinearLayout.LayoutParams(135.dp(), 60.dp()))
            } else {
                row.addView(cell(item.name, 135))
            }
            row.addView(cell(amount(item.cents), 115))
            row.addView(cell(amount(actual), 115))
            row.addView(cell(amount(item.cents - actual), 115))
            expenseList.addView(row)
            expenseList.addView(View(requireContext()).apply {
                setBackgroundColor(Color.rgb(233, 228, 239))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1.dp()
                )
            })
        }
        val assigned = items.map { it.name }.toSet()
        actualByCategory.filterKeys { it !in assigned }.toSortedMap().forEach { (category, value) ->
            expenseList.addView(tableRow(category, "—", amount(value), "—"))
        }
        if (items.isEmpty() && actualByCategory.isEmpty()) {
            expenseList.addView(TextView(requireContext()).apply {
                text = "No expenses yet. Add a planned expense or record a purchase."
                setPadding(8, 20, 8, 20)
            })
        }

    }
}

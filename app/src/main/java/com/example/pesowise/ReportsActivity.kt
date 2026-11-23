package com.example.pesowise

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pesowise.data.PesoWiseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class ReportsFragment : Fragment() {
    private var selectedMonth = YearMonth.now()
    private lateinit var ui: FinanceUi
    private lateinit var screen: FinanceScreen
    private lateinit var body: LinearLayout
    private val repository by lazy { PesoWiseRepository() }
    private var reportJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        selectedMonth = savedInstanceState?.getString("month")?.let(YearMonth::parse) ?: selectedMonth
        ui = FinanceUi(requireContext())
        screen = ui.screen("Reports", selectedMonth, {
            ui.pickMonth(selectedMonth) { selectedMonth = it; updateReport() }
        })
        body = ui.column()
        ui.add(screen.content, body)
        return screen.root
    }

    override fun onResume() {
        super.onResume()
        updateReport()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("month", selectedMonth.toString())
        super.onSaveInstanceState(outState)
    }

    private fun updateReport() {
        reportJob?.cancel()
        val month = selectedMonth
        screen.monthButton.text = month.format(FinanceUi.MONTH_FORMAT)
        screen.monthButton.contentDescription = "Select month, ${screen.monthButton.text}"
        val local = readLocalTransactions(requireContext()).filter { YearMonth.from(it.date) == month }
        render(local)
        // Local entries are saved immediately. A late remote response must not replace them with older totals.
        if (local.isEmpty()) reportJob = viewLifecycleOwner.lifecycleScope.launch {
            val remote = repository.getTransactions().getOrNull().orEmpty().mapNotNull { dto ->
                runCatching {
                    require(dto.amount.isFinite() && dto.amount > 0)
                    FinanceRecord(LocalDate.parse(dto.date),
                        runCatching { LocalTime.parse(dto.time) }.getOrDefault(LocalTime.MIN),
                        dto.name, BigDecimal.valueOf(dto.amount).movePointRight(2)
                            .setScale(0, RoundingMode.HALF_UP).longValueExact(),
                        dto.type, dto.category.ifBlank { "Unassigned" })
                }.getOrNull()
            }.filter { YearMonth.from(it.date) == month }
            if (selectedMonth == month && remote.isNotEmpty() &&
                readLocalTransactions(requireContext()).none { YearMonth.from(it.date) == month }) render(remote)
        }
    }

    private fun render(records: List<FinanceRecord>) {
        body.removeAllViews()
        val totals = financeTotals(records)
        val hero = ui.card(FinanceColors.lavender)
        ui.add(hero, ui.text("Monthly balance", 17f, true))
        ui.add(hero, ui.amount(totals.balance, 32, if (totals.balance < 0L) FinanceColors.coral else FinanceColors.ink), top = 6)
        ui.add(hero, ui.text("Income minus expenses", 12f, color = FinanceColors.muted), top = 3)
        ui.divider(hero, 16, 14)
        val summaryRow = ui.row()
        val income = ui.summary("Income", totals.income, true).apply {
            background = null
            setPadding(0, 0, 0, 0)
        }
        val expenses = ui.summary("Expenses", totals.expenses, false).apply {
            background = null
            setPadding(0, 0, 0, 0)
        }
        ui.add(summaryRow, income, width = 0, weight = 1f)
        ui.gap(summaryRow, 12, true)
        ui.add(summaryRow, expenses, width = 0, weight = 1f)
        ui.add(hero, summaryRow)
        ui.add(body, hero)

        val comparison = ui.card()
        ui.add(comparison, ui.text("Income vs expenses", 18f, true), bottom = 16)
        val largest = maxOf(totals.income, totals.expenses)
        listOf(Triple("Income", totals.income, FinanceColors.green),
            Triple("Expenses", totals.expenses, FinanceColors.purple)).forEachIndexed { index, (label, amount, color) ->
            val row = ui.row()
            ui.add(row, ui.text(label), width = 0, weight = 1f)
            row.addView(ui.text(peso(amount), 14f, true))
            ui.add(comparison, row, top = if (index > 0) 12 else 0)
            ui.progress(comparison, amount, largest, color)
        }
        ui.add(body, comparison, top = 16)

        val categories = categoryTotals(records)
        val breakdown = ui.card()
        ui.add(breakdown, ui.text("Spending by category", 18f, true), bottom = 16)
        val chartRow = if (resources.configuration.screenWidthDp >= 400) ui.row() else ui.column()
        val donut = FinanceDonutView(requireContext(), categories)
        ui.add(chartRow, donut, width = if (chartRow.orientation == LinearLayout.HORIZONTAL) ui.dp(152) else -1,
            height = ui.dp(184))
        val legend = ui.column()
        if (categories.isEmpty()) {
            ui.add(legend, ui.text("No expenses yet", 14f, true))
            ui.add(legend, ui.text("Recorded expenses will appear here.", 12f, color = FinanceColors.muted), top = 5)
        }
        categories.entries.forEachIndexed { index, entry ->
            val row = ui.row()
            row.addView(View(requireContext()).apply {
                background = ui.shape(FinanceColors.chart[index % FinanceColors.chart.size], 3)
            }, LinearLayout.LayoutParams(ui.dp(10), ui.dp(10)))
            ui.gap(row, 8, true)
            val labels = ui.column()
            ui.add(labels, ui.text(entry.key, 12f, color = FinanceColors.muted))
            ui.add(labels, ui.text(peso(entry.value), 13f, true), top = 2)
            ui.add(row, labels, width = 0, weight = 1f)
            ui.add(legend, row, bottom = 12)
        }
        if (chartRow.orientation == LinearLayout.HORIZONTAL) {
            ui.gap(chartRow, 12, true)
            ui.add(chartRow, legend, width = 0, weight = 1f)
        } else ui.add(chartRow, legend, top = 8)
        ui.add(breakdown, chartRow)
        ui.add(body, breakdown, top = 16)
    }
}

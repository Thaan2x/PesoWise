package com.example.pesowise

import android.content.Context
import org.json.JSONArray
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale

internal data class FinanceRecord(
    val date: LocalDate,
    val time: LocalTime,
    val name: String,
    val cents: Long,
    val type: String,
    val category: String,
    val order: Int = 0,
) {
    val isIncome: Boolean get() = type.equals("income", true)
}

internal data class FinanceTotals(val income: Long, val expenses: Long) {
    val balance: Long get() = income - expenses
}

internal fun financeTotals(records: List<FinanceRecord>) = FinanceTotals(
    records.filter { it.isIncome }.sumOf { it.cents },
    records.filterNot { it.isIncome }.sumOf { it.cents },
)

internal fun categoryTotals(records: List<FinanceRecord>): Map<String, Long> =
    records.filterNot { it.isIncome }.groupBy { it.category.ifBlank { "Unassigned" } }
        .mapValues { (_, rows) -> rows.sumOf { it.cents } }
        .toList().sortedByDescending { it.second }.toMap()

internal fun calendarCells(month: YearMonth): List<LocalDate?> {
    val offset = month.atDay(1).dayOfWeek.value % 7
    val count = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    return List(count) { index ->
        val day = index - offset + 1
        if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
    }
}

internal fun peso(cents: Long, exact: Boolean = false): String {
    val value = BigDecimal.valueOf(cents, 2)
    val format = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = if (exact || cents % 100L != 0L) 2 else 0
        maximumFractionDigits = 2
    }
    return (if (cents < 0L) "−" else "") + "₱" + format.format(value.abs())
}

internal fun readLocalTransactions(context: Context): List<FinanceRecord> {
    val prefs = context.getSharedPreferences("pesowise_transactions", Context.MODE_PRIVATE)
    val array = try { JSONArray(prefs.getString("entries", "[]")) }
        catch (_: Exception) { JSONArray() }
    return buildList {
        for (index in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(index)
                val cents = BigDecimal(obj.getString("amount")).movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP).longValueExact()
                if (cents <= 0L) continue
                add(FinanceRecord(
                    date = LocalDate.parse(obj.getString("date")),
                    time = runCatching { LocalTime.parse(obj.optString("time")) }.getOrDefault(LocalTime.MIN),
                    name = obj.getString("name"),
                    cents = cents,
                    type = obj.optString("type", "expense"),
                    category = obj.optString("category", "").ifBlank { "Unassigned" },
                    order = index,
                ))
            } catch (_: Exception) {
                // One invalid stored item must not hide the remaining transactions.
            }
        }
    }.sortedWith(compareByDescending<FinanceRecord> { it.date }
        .thenByDescending { it.time }.thenByDescending { it.order })
}

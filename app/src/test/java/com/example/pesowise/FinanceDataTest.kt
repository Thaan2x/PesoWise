package com.example.pesowise

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class FinanceDataTest {
    private fun record(cents: Long, type: String, category: String = "") =
        FinanceRecord(LocalDate.of(2026, 11, 23), LocalTime.NOON, "Entry", cents, type, category)

    @Test fun totalsKeepIncomeAndExpensesSeparateAndPreserveCents() {
        val result = financeTotals(listOf(record(3000000, "income"), record(1245055, "expense"), record(45, "INCOME")))
        assertEquals(3000045L, result.income)
        assertEquals(1245055L, result.expenses)
        assertEquals(1754990L, result.balance)
        assertEquals(FinanceTotals(0, 0), financeTotals(emptyList()))
    }

    @Test fun categoriesExcludeIncomeAndKeepUnassignedExpenses() {
        val totals = categoryTotals(listOf(record(400, "expense", "Food"), record(250, "expense", "Food"),
            record(1000, "income", "Salary"), record(125, "expense")))
        assertEquals(listOf("Food", "Unassigned"), totals.keys.toList())
        assertEquals(650L, totals["Food"])
        assertEquals(125L, totals["Unassigned"])
        assertFalse(totals.containsKey("Salary"))
    }

    @Test fun calendarUsesSundayColumnsAndIncludesEveryDateOnce() {
        val november = calendarCells(YearMonth.of(2026, 11))
        assertEquals(LocalDate.of(2026, 11, 1), november.first())
        assertEquals(LocalDate.of(2026, 11, 23), november[22])
        assertEquals(30, november.filterNotNull().distinct().size)
        assertEquals(0, november.size % 7)
        val leap = calendarCells(YearMonth.of(2028, 2))
        assertNull(leap.first())
        assertEquals(29, leap.filterNotNull().size)
        assertTrue(leap.contains(LocalDate.of(2028, 2, 29)))
    }

    @Test fun moneyDoesNotLoseFractionalPesosOrNegativeSign() {
        assertEquals("₱30,000", peso(3000000))
        assertEquals("₱12,450.55", peso(1245055))
        assertEquals("−₱150.00", peso(-15000, true))
        assertEquals("₱0", peso(0))
    }
}

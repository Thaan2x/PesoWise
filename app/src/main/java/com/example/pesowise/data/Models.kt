package com.example.pesowise.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionDto(
    val id: String? = null,
    val name: String,
    val amount: Double,
    val type: String = "expense",
    val category: String = "",
    val date: String,
    val time: String = "",
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class MonthlyBudgetDto(
    val id: String? = null,
    val month: String,
    @SerialName("available_money_cents")
    val availableMoneyCents: Long,
    @SerialName("planned_budget_cents")
    val plannedBudgetCents: Long,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class PlannedExpenseDto(
    val id: String? = null,
    val month: String,
    val name: String,
    val cents: Long,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class ReminderDto(
    val id: String? = null,
    val title: String,
    @SerialName("due_date")
    val dueDate: String,
    @SerialName("days_before")
    val daysBefore: Int = 0,
    @SerialName("created_at")
    val createdAt: String? = null
)

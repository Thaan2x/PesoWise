package com.example.pesowise.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PesoWiseRepository {
    private val client get() = SupabaseProvider.client

    // ==========================================
    // TRANSACTIONS CRUD
    // ==========================================

    suspend fun getTransactions(): Result<List<TransactionDto>> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["transactions"]
                .select()
                .decodeList<TransactionDto>()
        }
    }

    suspend fun insertTransaction(transaction: TransactionDto): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["transactions"].insert(transaction)
            Unit
        }
    }

    suspend fun updateTransaction(id: String, transaction: TransactionDto): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["transactions"].update(transaction) {
                filter {
                    eq("id", id)
                }
            }
            Unit
        }
    }

    suspend fun deleteTransaction(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["transactions"].delete {
                filter {
                    eq("id", id)
                }
            }
            Unit
        }
    }

    suspend fun deleteTransactionByNameAndDate(name: String, date: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["transactions"].delete {
                filter {
                    eq("name", name)
                    eq("date", date)
                }
            }
            Unit
        }
    }

    // ==========================================
    // MONTHLY BUDGET & PLANNED EXPENSES CRUD
    // ==========================================

    suspend fun getMonthlyBudget(month: String): Result<MonthlyBudgetDto?> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["monthly_budgets"]
                .select {
                    filter {
                        eq("month", month)
                    }
                }
                .decodeSingleOrNull<MonthlyBudgetDto>()
        }
    }

    suspend fun upsertMonthlyBudget(budget: MonthlyBudgetDto): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["monthly_budgets"].upsert(budget)
            Unit
        }
    }

    suspend fun getPlannedExpenses(month: String): Result<List<PlannedExpenseDto>> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["planned_expenses"]
                .select {
                    filter {
                        eq("month", month)
                    }
                }
                .decodeList<PlannedExpenseDto>()
        }
    }

    suspend fun insertPlannedExpense(expense: PlannedExpenseDto): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["planned_expenses"].insert(expense)
            Unit
        }
    }

    suspend fun deletePlannedExpense(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["planned_expenses"].delete {
                filter {
                    eq("id", id)
                }
            }
            Unit
        }
    }

    suspend fun deletePlannedExpenseByName(month: String, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["planned_expenses"].delete {
                filter {
                    eq("month", month)
                    eq("name", name)
                }
            }
            Unit
        }
    }

    // ==========================================
    // REMINDERS CRUD
    // ==========================================

    suspend fun getReminders(): Result<List<ReminderDto>> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["reminders"]
                .select()
                .decodeList<ReminderDto>()
        }
    }

    suspend fun insertReminder(reminder: ReminderDto): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["reminders"].insert(reminder)
            Unit
        }
    }

    suspend fun deleteReminder(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["reminders"].delete {
                filter {
                    eq("id", id)
                }
            }
            Unit
        }
    }

    suspend fun deleteReminderByTitleAndDueDate(title: String, dueDate: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            client.postgrest["reminders"].delete {
                filter {
                    eq("title", title)
                    eq("due_date", dueDate)
                }
            }
            Unit
        }
    }
}

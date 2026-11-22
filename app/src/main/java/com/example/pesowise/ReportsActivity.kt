package com.example.pesowise

import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class ReportsActivity : AppCompatActivity() {

    private var demoMode = false

    private lateinit var txtIncome: TextView
    private lateinit var txtExpenses: TextView
    private lateinit var txtNetBalance: TextView
    private lateinit var txtReportStatus: TextView

    private lateinit var progressIncome: ProgressBar
    private lateinit var progressExpenses: ProgressBar
    private lateinit var btnDemoData: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        txtIncome = findViewById(R.id.txtIncome)
        txtExpenses = findViewById(R.id.txtExpenses)
        txtNetBalance = findViewById(R.id.txtNetBalance)
        txtReportStatus = findViewById(R.id.txtReportStatus)

        progressIncome = findViewById(R.id.progressIncome)
        progressExpenses = findViewById(R.id.progressExpenses)
        btnDemoData = findViewById(R.id.btnDemoData)

        val currentMonth = LocalDate.now()
            .format(
                DateTimeFormatter.ofPattern(
                    "MMMM yyyy",
                    Locale.ENGLISH
                )
            )

        findViewById<TextView>(R.id.txtReportMonth)
            .text = currentMonth

        demoMode = savedInstanceState
            ?.getBoolean("demo_mode") ?: false

        btnDemoData.setOnClickListener {
            demoMode = !demoMode
            updateReport()
        }

        findViewById<Button>(R.id.btnBackReports)
            .setOnClickListener {
                finish()
            }

        updateReport()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("demo_mode", demoMode)
        super.onSaveInstanceState(outState)
    }

    private fun updateReport() {

        // Temporary demonstration figures.
        // Replace with Supabase transaction totals later.
        val income = if (demoMode) 15000.0 else 0.0
        val expenses = if (demoMode) 3500.0 else 0.0

        val balance = income - expenses

        txtIncome.text = formatPeso(income)
        txtExpenses.text = formatPeso(expenses)
        txtNetBalance.text = formatPeso(balance)

        // Scale both bars relative to the larger value.
        val largest = maxOf(income, expenses)

        if (largest > 0.0) {
            progressIncome.progress =
                ((income / largest) * 100)
                    .roundToInt().coerceIn(0, 100)

            progressExpenses.progress =
                ((expenses / largest) * 100)
                    .roundToInt().coerceIn(0, 100)
        } else {
            progressIncome.progress = 0
            progressExpenses.progress = 0
        }

        txtReportStatus.text = if (demoMode) {
            "DEMO DATA ONLY - Not actual transactions"
        } else {
            "No transaction data connected yet."
        }

        btnDemoData.text = if (demoMode) {
            "Clear Demo Data"
        } else {
            "Show Demo Data"
        }
    }

    private fun formatPeso(amount: Double): String {
        return String.format(
            Locale.US,
            "₱%,.2f",
            amount
        )
    }
}
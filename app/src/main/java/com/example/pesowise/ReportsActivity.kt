package com.example.pesowise

import android.os.Bundle
import android.content.Context.MODE_PRIVATE
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pesowise.data.PesoWiseRepository
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class ReportsFragment : Fragment(R.layout.activity_reports) {

    private var demoMode = false
    private val repository by lazy { PesoWiseRepository() }

    private lateinit var txtIncome: TextView
    private lateinit var txtExpenses: TextView
    private lateinit var txtNetBalance: TextView
    private lateinit var txtReportStatus: TextView

    private lateinit var progressIncome: ProgressBar
    private lateinit var progressExpenses: ProgressBar
    private lateinit var btnDemoData: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        txtIncome = requireView().findViewById(R.id.txtIncome)
        txtExpenses = requireView().findViewById(R.id.txtExpenses)
        txtNetBalance = requireView().findViewById(R.id.txtNetBalance)
        txtReportStatus = requireView().findViewById(R.id.txtReportStatus)

        progressIncome = requireView().findViewById(R.id.progressIncome)
        progressExpenses = requireView().findViewById(R.id.progressExpenses)
        btnDemoData = requireView().findViewById(R.id.btnDemoData)

        val currentMonth = LocalDate.now()
            .format(
                DateTimeFormatter.ofPattern(
                    "MMMM yyyy",
                    Locale.ENGLISH
                )
            )

        requireView().findViewById<TextView>(R.id.txtReportMonth)
            .text = currentMonth

        demoMode = savedInstanceState
            ?.getBoolean("demo_mode") ?: false

        btnDemoData.setOnClickListener {
            demoMode = !demoMode
            updateReport()
        }

    }

    override fun onResume() {
        super.onResume()
        updateReport()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("demo_mode", demoMode)
        super.onSaveInstanceState(outState)
    }

    private fun updateReport() {
        if (demoMode) {
            val income = 15000.0
            val expenses = 3500.0
            renderMetrics(income, expenses, "DEMO DATA ONLY - Not actual transactions")
            btnDemoData.text = "Clear Demo Data"
            return
        }

        btnDemoData.text = "Show Demo Data"

        // Load local transactions & attempt Supabase fetch
        val prefs = requireContext().getSharedPreferences("pesowise_transactions", MODE_PRIVATE)
        val jsonArray = try {
            JSONArray(prefs.getString("entries", "[]"))
        } catch (_: Exception) { JSONArray() }

        var localIncome = 0.0
        var localExpenses = 0.0

        val currentMonthPrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM", Locale.ENGLISH))

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.optJSONObject(i) ?: continue
            val date = obj.optString("date", "")
            if (!date.startsWith(currentMonthPrefix)) continue
            val amt = obj.optDouble("amount", 0.0)
            val type = obj.optString("type", "expense")
            if (type.equals("income", ignoreCase = true)) {
                localIncome += amt
            } else {
                localExpenses += amt
            }
        }

        renderMetrics(localIncome, localExpenses, "Connected to PesoWise Local & Supabase Storage")

        viewLifecycleOwner.lifecycleScope.launch {
            val result = repository.getTransactions()
            result.getOrNull()?.let { dtos ->
                var remoteIncome = 0.0
                var remoteExpenses = 0.0
                for (dto in dtos) {
                    if (dto.date.startsWith(currentMonthPrefix)) {
                        if (dto.type.equals("income", ignoreCase = true)) {
                            remoteIncome += dto.amount
                        } else {
                            remoteExpenses += dto.amount
                        }
                    }
                }
                if (dtos.isNotEmpty() && !demoMode) {
                    renderMetrics(remoteIncome, remoteExpenses, "Live Supabase Sync Active")
                }
            }
        }
    }

    private fun renderMetrics(income: Double, expenses: Double, statusText: String) {
        val balance = income - expenses

        txtIncome.text = formatPeso(income)
        txtExpenses.text = formatPeso(expenses)
        txtNetBalance.text = formatPeso(balance)

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

        txtReportStatus.text = statusText
    }

    private fun formatPeso(amount: Double): String {
        return String.format(
            Locale.US,
            "₱%,.2f",
            amount
        )
    }
}
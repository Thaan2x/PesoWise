
package com.example.pesowise

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnTransactions =
            findViewById<Button>(R.id.btnTransactions)

        val btnBudget =
            findViewById<Button>(R.id.btnBudget)

        val btnReports =
            findViewById<Button>(R.id.btnReports)

        val btnCalendar =
            findViewById<Button>(R.id.btnCalendar)

        btnTransactions.setOnClickListener {
            val intent = Intent(
                this,
                TransactionListActivity::class.java
            )
            startActivity(intent)
        }

        btnBudget.setOnClickListener {
            val intent = Intent(
                this,
                BudgetActivity::class.java
            )
            startActivity(intent)
        }

        btnReports.setOnClickListener {
            val intent = Intent(
                this,
                ReportsActivity::class.java
            )
            startActivity(intent)
        }

        btnCalendar.setOnClickListener {
            val intent = Intent(
                this,
                CalendarActivity::class.java
            )
            startActivity(intent)
        }
    }
}

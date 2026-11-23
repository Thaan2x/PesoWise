package com.example.pesowise

import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private lateinit var bottomNavigation: BottomNavigationView
    private var selectedSection = R.id.nav_transactions

    private val sectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            bottomNavigation.selectedItemId = R.id.nav_transactions
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applyTopSystemInsets()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        bottomNavigation = findViewById(R.id.bottom_navigation)
        onBackPressedDispatcher.addCallback(this, sectionBackCallback)
        bottomNavigation.setOnItemSelectedListener { showSection(it.itemId) }
        bottomNavigation.setOnItemReselectedListener { }

        val initialSection = savedInstanceState?.getInt("selected_section")
            ?.takeIf { bottomNavigation.menu.findItem(it) != null }
            ?: R.id.nav_transactions
        showSection(initialSection)
        bottomNavigation.menu.findItem(initialSection).isChecked = true
    }

    private fun showSection(sectionId: Int): Boolean {
        if (supportFragmentManager.isStateSaved) return false

        val tag = "section_$sectionId"
        val section = supportFragmentManager.findFragmentByTag(tag) ?: when (sectionId) {
            R.id.nav_transactions -> TransactionsFragment()
            R.id.nav_budget -> BudgetFragment()
            R.id.nav_reports -> ReportsFragment()
            R.id.nav_calendar -> CalendarFragment()
            else -> return false
        }

        val transaction = supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
        supportFragmentManager.fragments.filter { it != section }.forEach {
            transaction.hide(it).setMaxLifecycle(it, Lifecycle.State.STARTED)
        }
        if (section.isAdded) {
            transaction.show(section)
        } else {
            transaction.add(R.id.section_container, section, tag)
        }
        transaction.setMaxLifecycle(section, Lifecycle.State.RESUMED)
            .setPrimaryNavigationFragment(section)
            .commitNow()

        selectedSection = sectionId
        sectionBackCallback.isEnabled = sectionId != R.id.nav_transactions
        section.view?.let { updateStatusBarAppearance(it) }
        return true
    }

    override fun onResume() {
        super.onResume()
        supportFragmentManager.primaryNavigationFragment?.view?.let {
            updateStatusBarAppearance(it)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected_section", selectedSection)
        super.onSaveInstanceState(outState)
    }
}

package com.example.pesowise

import android.Manifest
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationRedesignTest {
    @Test fun allTabsWorkAndSelectedTabSurvivesRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(allOf(withId(R.id.finance_page_title), isDisplayed())).check(matches(withText("Transactions")))
            onView(withId(R.id.nav_budget)).perform(click())
            onView(allOf(withId(R.id.finance_page_title), isDisplayed())).check(matches(withText("Budget")))
            onView(withId(R.id.nav_reports)).perform(click())
            onView(allOf(withId(R.id.finance_page_title), isDisplayed())).check(matches(withText("Reports")))
            scenario.recreate()
            onView(allOf(withId(R.id.finance_page_title), isDisplayed())).check(matches(withText("Reports")))
            onView(withId(R.id.nav_calendar)).perform(click())
            onView(allOf(withId(R.id.finance_page_title), isDisplayed())).check(matches(withText("Calendar")))
            pressBack()
            onView(allOf(withId(R.id.finance_page_title), isDisplayed())).check(matches(withText("Transactions")))
        }
    }
}

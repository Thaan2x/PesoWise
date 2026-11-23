package com.example.pesowise

import android.content.Context
import org.json.JSONArray

/** Reusable category names, independent of monthly planned amounts. */
object ExpenseCategoryStore {
    private const val PREFS = "PesoWiseBudget"
    private const val CATEGORY_KEY = "expense_category_names"

    fun names(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val result = linkedSetOf<String>()
        fun collect(raw: String?) {
            val array = runCatching { JSONArray(raw ?: "[]") }.getOrDefault(JSONArray())
            for (i in 0 until array.length()) {
                val item = array.opt(i)
                val name = when (item) {
                    is String -> item
                    is org.json.JSONObject -> item.optString("name")
                    else -> ""
                }.trim()
                if (name.isNotEmpty() && !name.equals("Unassigned", ignoreCase = true) &&
                    result.none { it.equals(name, ignoreCase = true) }) result.add(name)
            }
        }
        collect(prefs.getString(CATEGORY_KEY, "[]"))
        // Migrate legacy monthly planned expense names without losing old data.
        prefs.all.forEach { (key, value) ->
            if (key.matches(Regex("\\d{4}-\\d{2}_expenses")) && value is String) collect(value)
        }
        persist(context, result.toList())
        return result.toList()
    }

    fun add(context: Context, name: String) {
        val clean = name.trim()
        if (clean.isBlank() || clean.equals("Unassigned", ignoreCase = true)) return
        val current = names(context).toMutableList()
        if (current.none { it.equals(clean, ignoreCase = true) }) {
            current.add(clean)
            persist(context, current)
        }
    }

    private fun persist(context: Context, names: List<String>) {
        val array = JSONArray()
        names.forEach { array.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(CATEGORY_KEY, array.toString()).apply()
    }
}

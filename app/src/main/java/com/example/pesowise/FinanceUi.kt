package com.example.pesowise

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.widget.TextViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object FinanceColors {
    val background = Color.rgb(250, 249, 252)
    val ink = Color.rgb(41, 36, 53)
    val muted = Color.rgb(124, 116, 138)
    val purple = Color.rgb(103, 80, 164)
    val lavender = Color.rgb(237, 231, 247)
    val border = Color.rgb(231, 225, 239)
    val green = Color.rgb(39, 132, 91)
    val greenTint = Color.rgb(229, 246, 236)
    val coral = Color.rgb(194, 91, 89)
    val coralTint = Color.rgb(253, 237, 233)
    val teal = Color.rgb(59, 142, 146)
    val chart = listOf(purple, Color.rgb(185, 157, 220), teal, Color.rgb(224, 172, 129), Color.rgb(129, 143, 174))
}

internal data class FinanceScreen(
    val root: FrameLayout,
    val content: LinearLayout,
    val monthButton: MaterialButton,
)

internal class FinanceUi(val context: Context) {
    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun column(padding: Int = 0) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        if (padding > 0) setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }

    fun row() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun text(value: String, size: Float = 14f, bold: Boolean = false, color: Int = FinanceColors.ink) =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    fun amount(value: Long, size: Int = 30, color: Int = FinanceColors.ink, exact: Boolean = false) =
        text(peso(value, exact), size.toFloat(), true, color).apply {
            maxLines = 1
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 14, size, 1,
                android.util.TypedValue.COMPLEX_UNIT_SP)
        }

    fun add(parent: LinearLayout, view: View, top: Int = 0, bottom: Int = 0,
            width: Int = LinearLayout.LayoutParams.MATCH_PARENT,
            height: Int = LinearLayout.LayoutParams.WRAP_CONTENT, weight: Float = 0f) {
        parent.addView(view, LinearLayout.LayoutParams(width, height, weight).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        })
    }

    fun gap(parent: LinearLayout, size: Int, horizontal: Boolean = false) {
        parent.addView(View(context), if (horizontal) LinearLayout.LayoutParams(dp(size), 1)
            else LinearLayout.LayoutParams(1, dp(size)))
    }

    fun shape(color: Int, radius: Int = 20, border: Boolean = false) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (border) setStroke(dp(1), FinanceColors.border)
    }

    fun card(color: Int = Color.WHITE, padding: Int = 16) = column(padding).apply {
        background = shape(color, 20, color == Color.WHITE)
    }

    fun button(label: String, primary: Boolean = false, icon: Int? = null,
               onClick: (() -> Unit)? = null) = MaterialButton(context).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        cornerRadius = dp(14)
        insetTop = 0
        insetBottom = 0
        minWidth = 0
        minimumWidth = 0
        minHeight = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        backgroundTintList = ColorStateList.valueOf(if (primary) FinanceColors.purple else Color.TRANSPARENT)
        setTextColor(if (primary) Color.WHITE else FinanceColors.purple)
        strokeWidth = if (primary) 0 else dp(1)
        strokeColor = ColorStateList.valueOf(FinanceColors.border)
        elevation = if (primary) dp(2).toFloat() else 0f
        if (icon != null) {
            setIconResource(icon)
            iconSize = dp(20)
            iconPadding = dp(8)
            iconTint = ColorStateList.valueOf(if (primary) Color.WHITE else FinanceColors.purple)
        }
        if (onClick != null) setOnClickListener { onClick() }
    }

    fun iconButton(drawable: Int, description: String, onClick: () -> Unit) =
        button("", icon = drawable, onClick = onClick).apply {
            contentDescription = description
            iconPadding = 0
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }

    fun icon(drawable: Int, color: Int = FinanceColors.purple,
             tint: Int = FinanceColors.lavender) = ImageView(context).apply {
        setImageResource(drawable)
        imageTintList = ColorStateList.valueOf(color)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = shape(tint, 12)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
    }

    fun divider(parent: LinearLayout, top: Int = 12, bottom: Int = 12) {
        add(parent, View(context).apply { setBackgroundColor(FinanceColors.border) },
            top, bottom, height = dp(1))
    }

    fun heading(parent: LinearLayout, title: String, action: View? = null) {
        val row = row()
        add(row, text(title, 18f, true), width = 0, weight = 1f)
        if (action != null) row.addView(action)
        add(parent, row, top = 20, bottom = 12)
    }

    fun progress(parent: LinearLayout, value: Long, max: Long, color: Int = FinanceColors.purple) {
        val fraction = if (max > 0L) (value.toDouble() / max).coerceIn(0.0, 1.0) else 0.0
        val bar = LinearProgressIndicator(context).apply {
            isIndeterminate = false
            this.max = 1000
            progress = (fraction * 1000).toInt()
            trackThickness = dp(7)
            trackCornerRadius = dp(4)
            trackColor = FinanceColors.lavender
            setIndicatorColor(color)
            contentDescription = if (max > 0L) "${(value.toDouble() / max * 100).toInt()}% used" else "No limit set"
        }
        add(parent, bar, top = 12, bottom = 8, height = dp(8))
    }

    fun badge(value: String, color: Int = FinanceColors.purple,
              tint: Int = FinanceColors.lavender) = text(value, 12f, true, color).apply {
        background = shape(tint, 10)
        setPadding(dp(10), dp(6), dp(10), dp(6))
    }

    fun empty(parent: LinearLayout, title: String, detail: String, drawable: Int) {
        val card = card()
        card.gravity = Gravity.CENTER
        card.setPadding(dp(20), dp(28), dp(20), dp(28))
        card.addView(icon(drawable))
        add(card, text(title, 17f, true).apply { gravity = Gravity.CENTER }, top = 12)
        add(card, text(detail, 14f, color = FinanceColors.muted).apply { gravity = Gravity.CENTER }, top = 6)
        add(parent, card, top = 8)
    }

    fun summary(title: String, cents: Long, income: Boolean): LinearLayout {
        val card = card(padding = 12)
        val row = row()
        row.addView(icon(if (income) R.drawable.ic_ui_income else R.drawable.ic_ui_expense,
            if (income) FinanceColors.green else FinanceColors.coral,
            if (income) FinanceColors.greenTint else FinanceColors.coralTint))
        gap(row, 10, true)
        val values = column()
        add(values, text(title, 12f, color = FinanceColors.muted))
        add(values, amount(cents, 22), top = 3)
        add(row, values, width = 0, weight = 1f)
        card.addView(row)
        return card
    }

    fun screen(title: String, month: YearMonth, chooseMonth: () -> Unit,
               actionLabel: String? = null, action: (() -> Unit)? = null): FinanceScreen {
        val root = FrameLayout(context).apply { setBackgroundColor(FinanceColors.background) }
        val content = column()
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(24), dp(18), dp(24), dp(if (action != null) 96 else 24))
            addView(content)
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        add(content, text("PESOWISE", 12f, true, FinanceColors.purple).apply { letterSpacing = 0.09f })
        add(content, text(title, 28f, true).apply {
            id = R.id.finance_page_title
            androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
        }, top = 5)
        val monthButton = button(month.format(MONTH_FORMAT), icon = R.drawable.ic_ui_down, onClick = chooseMonth).apply {
            id = R.id.finance_month_picker
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
            setTextColor(FinanceColors.ink)
            strokeColor = ColorStateList.valueOf(FinanceColors.purple)
            contentDescription = "Select month, ${month.format(MONTH_FORMAT)}"
        }
        add(content, monthButton, top = 16, bottom = 20, width = -2)
        if (actionLabel != null && action != null) {
            val fab = button(actionLabel, true, R.drawable.ic_ui_plus, action).apply {
                cornerRadius = dp(18)
                minHeight = dp(54)
                minimumHeight = dp(54)
                setPadding(dp(20), dp(12), dp(20), dp(12))
            }
            root.addView(fab, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(dp(24), 0, dp(24), dp(16))
            })
        }
        return FinanceScreen(root, content, monthButton)
    }

    fun pickMonth(current: YearMonth, onSelected: (YearMonth) -> Unit) {
        val row = row()
        val month = NumberPicker(context).apply {
            minValue = 1
            maxValue = 12
            displayedValues = (1..12).map { YearMonth.of(2026, it).format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)) }.toTypedArray()
            value = current.monthValue
            wrapSelectorWheel = true
            contentDescription = "Month"
        }
        val year = NumberPicker(context).apply {
            minValue = 1900
            maxValue = 2200
            value = current.year.coerceIn(minValue, maxValue)
            wrapSelectorWheel = false
            contentDescription = "Year"
        }
        add(row, month, width = 0, weight = 1f)
        add(row, year, width = 0, weight = 1f)
        MaterialAlertDialogBuilder(context).setTitle("Select month").setView(row)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply") { _, _ -> onSelected(YearMonth.of(year.value, month.value)) }
            .show()
    }

    companion object {
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    }
}

internal fun categoryIcon(name: String): Int = when {
    Regex("food|grocer|meal|shop", RegexOption.IGNORE_CASE).containsMatchIn(name) -> R.drawable.ic_ui_bag
    Regex("transport|fare|bus|fuel", RegexOption.IGNORE_CASE).containsMatchIn(name) -> R.drawable.ic_ui_transport
    Regex("rent|home|house", RegexOption.IGNORE_CASE).containsMatchIn(name) -> R.drawable.ic_ui_home
    else -> R.drawable.ic_ui_receipt
}

internal class FinanceDonutView(context: Context, private val categories: Map<String, Long>) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val total = categories.values.sum()
    init {
        contentDescription = if (total == 0L) "No expenses recorded" else
            "Total spending ${peso(total)}. " + categories.entries.joinToString { "${it.key}: ${peso(it.value)}" }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val stroke = 22 * density
        val radius = minOf(width.toFloat(), height.toFloat()) / 2 - stroke / 2 - 8 * density
        val cx = width / 2f
        val cy = height / 2f
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.color = FinanceColors.lavender
        canvas.drawOval(bounds, paint)
        var start = -90f
        if (total > 0L) categories.entries.forEachIndexed { index, entry ->
            val sweep = (entry.value.toDouble() / total * 360).toFloat()
            paint.color = FinanceColors.chart[index % FinanceColors.chart.size]
            canvas.drawArc(bounds, start, sweep, false, paint)
            start += sweep
        }
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.color = FinanceColors.ink
        val label = if (total == 0L) "No spending" else peso(total)
        paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 22f, resources.displayMetrics)
        while (paint.measureText(label) > radius * 1.45f && paint.textSize > 10 * density) paint.textSize -= density
        canvas.drawText(label, cx, cy, paint)
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        paint.color = FinanceColors.muted
        canvas.drawText(if (total == 0L) "This month" else "Total spent", cx, cy + 23 * density, paint)
    }
}

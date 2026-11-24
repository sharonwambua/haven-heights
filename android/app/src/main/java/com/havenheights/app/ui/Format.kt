package com.havenheights.app.ui

import androidx.compose.ui.graphics.Color
import com.havenheights.app.ui.theme.HavenColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun kes(amount: Double): String =
    "KES " + NumberFormat.getIntegerInstance(Locale.US).format(Math.round(amount))

/** Meter values: up to 3 decimals, no trailing zeros. */
fun units(value: Double): String =
    BigDecimal(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

fun friendlyDate(iso: String): String = try {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(dateFmt)
} catch (_: Exception) {
    try { LocalDate.parse(iso.take(10)).format(dateFmt) } catch (_: Exception) { iso }
}

fun displayDate(date: LocalDate): String = date.format(dateFmt)

fun urgencyLabel(v: String) = when (v) { "HIGH" -> "High"; "MEDIUM" -> "Medium"; else -> "Low" }
fun statusLabel(v: String) = when (v) { "OPEN" -> "Open"; "IN_PROGRESS" -> "In progress"; else -> "Resolved" }

val categories = listOf("WATER", "MAINTENANCE", "GARBAGE", "GENERAL")
fun categoryLabel(v: String) = when (v) {
    "WATER" -> "Water"; "MAINTENANCE" -> "Maintenance"; "GARBAGE" -> "Garbage collection"; else -> "General"
}

/** (container, content) colours for a notice category. */
fun categoryColors(v: String): Pair<Color, Color> = when (v) {
    "WATER" -> HavenColors.SageMist to HavenColors.SageDeep
    "MAINTENANCE" -> HavenColors.Nude to HavenColors.Brown
    "GARBAGE" -> HavenColors.Sage to HavenColors.Cocoa
    else -> HavenColors.BurgundyMist to HavenColors.Burgundy
}

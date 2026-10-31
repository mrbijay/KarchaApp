
package com.karchamanager.app

import java.util.Calendar
import java.util.TimeZone

data class NepaliDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val monthNameNp: String,
    val monthNameEn: String
)

data class DebtReminder(
    val daysRemaining: Int,
    val isOverdue: Boolean,
    val isDueToday: Boolean,
    val labelNp: String
)

object NepaliCalendar {
    val nepaliMonthsEn = listOf(
        "Baisakh", "Jestha", "Ashadh", "Shrawan", "Bhadra", "Ashwin",
        "Kartik", "Mangsir", "Poush", "Magh", "Falgun", "Chaitra"
    )
    val nepaliMonthsNp = listOf(
        "बैशाख", "जेठ", "असार", "साउन", "भदौ", "असोज",
        "कार्तिक", "मंसिर", "पुस", "माघ", "फागुन", "चैत"
    )
    val weekDaysNp = listOf("आइत", "सोम", "मङ्गल", "बुध", "बिही", "शुक्र", "शनि")
    val daysInMonth2083 = listOf(31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30)

    fun getCurrentNepaliDate(): NepaliDate {
        val bsYear = 2083
        val bsMonth = 7 // Kartik (कार्तिक)
        val bsDay = 14
        return NepaliDate(bsYear, bsMonth, bsDay, nepaliMonthsNp[bsMonth - 1], nepaliMonthsEn[bsMonth - 1])
    }

    fun toNepaliDigits(number: Int): String {
        val nepaliDigits = listOf('०', '१', '२', '३', '४', '५', '६', '७', '८', '९')
        return number.toString().map { char ->
            if (char.isDigit()) nepaliDigits[char - '0'] else char
        }.joinToString("")
    }

    fun parseAmount(input: String): Double {
        val clean = input
            .replace("०", "0").replace("१", "1").replace("२", "2")
            .replace("३", "3").replace("४", "4").replace("५", "5")
            .replace("६", "6").replace("७", "7").replace("८", "8")
            .replace("९", "9").replace(",", "").replace(" ", "").trim()
        return clean.toDoubleOrNull() ?: 0.0
    }

    fun getDaysDifference(dueDateBs: String, today: NepaliDate): DebtReminder {
        val parts = dueDateBs.split("-").mapNotNull { it.toIntOrNull() }
        if (parts.size < 3) {
            return DebtReminder(0, false, true, "आज भाखा")
        }
        val targetYear = parts[0]
        val targetMonth = parts[1]
        val targetDay = parts[2]

        val todayTotalDays = (today.year - 2080) * 365 + (today.month - 1) * 30 + today.day
        val targetTotalDays = (targetYear - 2080) * 365 + (targetMonth - 1) * 30 + targetDay
        val diff = targetTotalDays - todayTotalDays

        return when {
            diff < 0 -> DebtReminder(diff, true, false, "भाखा नाघेको (" + Math.abs(diff) + " दिन ढिला)")
            diff == 0 -> DebtReminder(0, false, true, "आज भाखा सकिने दिन!")
            diff <= 7 -> DebtReminder(diff, false, false, "भाखा सकिन " + diff + " दिन बाँकी")
            else -> DebtReminder(diff, false, false, "" + diff + " दिन बाँकी")
        }
    }
}

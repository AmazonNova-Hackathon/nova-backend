package com.mediagent.app.util

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object FhirDateFormatter {
    private val fhirFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val displayEn = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val displayHi = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("hi"))

    fun toDisplay(fhirDate: String, locale: String = "en"): String = runCatching {
        // Try plain date first (yyyy-MM-dd)
        val date = runCatching { LocalDate.parse(fhirDate, fhirFormat) }.getOrElse {
            // Try ISO datetime with offset (e.g. 2026-03-15T16:21:48.251140+00:00)
            OffsetDateTime.parse(fhirDate).toLocalDate()
        }
        if (locale == "hi") date.format(displayHi) else date.format(displayEn)
    }.getOrDefault(fhirDate)
}

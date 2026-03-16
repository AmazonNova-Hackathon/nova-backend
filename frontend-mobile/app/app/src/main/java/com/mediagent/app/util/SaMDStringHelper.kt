package com.mediagent.app.util

object SaMDStringHelper {
    fun interpretationLabel(code: String): String = when (code) {
        "N" -> "Within range"
        "H" -> "Above reference range"
        "L" -> "Below reference range"
        "HH" -> "Critically elevated"
        "LL" -> "Critically low"
        else -> "See report"
    }

    fun severityLabel(severity: String): String = when (severity) {
        "urgent" -> "Requires prompt review"
        "attention" -> "Worth monitoring"
        "informational" -> "For your awareness"
        else -> severity
    }

    const val disclaimerFull = "Chetana does not provide medical advice. All information is for informational purposes only. Always consult a licensed healthcare provider."
    const val disclaimerShort = "For informational purposes only. Consult your doctor."
    const val disclaimerInline = "This is not medical advice."
    const val extractionDisclaimer = "This is an AI-assisted summary of your lab report. Always verify with your original report and consult your doctor."
}

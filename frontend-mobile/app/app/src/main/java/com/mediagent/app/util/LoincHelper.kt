package com.mediagent.app.util

object LoincHelper {
    data class LoincEntry(
        val code: String,
        val displayName: String,
        val category: String,
        val unit: String,
        val normalLow: Double,
        val normalHigh: Double,
    )

    val map = mapOf(
        "2339-0" to LoincEntry("2339-0", "Glucose (Fasting)", "Blood Sugar", "mg/dL", 70.0, 100.0),
        "4548-4" to LoincEntry("4548-4", "HbA1c", "Blood Sugar", "%", 4.0, 5.6),
        "2093-3" to LoincEntry("2093-3", "Total Cholesterol", "Lipid Panel", "mg/dL", 0.0, 200.0),
        "18262-6" to LoincEntry("18262-6", "LDL Cholesterol", "Lipid Panel", "mg/dL", 0.0, 100.0),
        "2085-9" to LoincEntry("2085-9", "HDL Cholesterol", "Lipid Panel", "mg/dL", 40.0, 60.0),
        "2571-8" to LoincEntry("2571-8", "Triglycerides", "Lipid Panel", "mg/dL", 0.0, 150.0),
        "3016-3" to LoincEntry("3016-3", "TSH", "Thyroid", "uIU/mL", 0.4, 4.0),
        "1989-3" to LoincEntry("1989-3", "Vitamin D (25-OH)", "Vitamins", "ng/mL", 30.0, 100.0),
        "718-7" to LoincEntry("718-7", "Hemoglobin", "CBC", "g/dL", 12.0, 16.0),
        "2276-4" to LoincEntry("2276-4", "Ferritin", "Vitamins", "ng/mL", 13.0, 150.0),
        "2160-0" to LoincEntry("2160-0", "Creatinine", "Kidney", "mg/dL", 0.7, 1.3),
    )

    fun category(code: String): String = map[code]?.category ?: "Other"
}

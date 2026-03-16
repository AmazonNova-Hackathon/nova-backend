package com.mediagent.app.util

object ChatResponseValidator {
    private val blockedPhrases = listOf(
        "you have diabetes",
        "you are diabetic",
        "you have hypertension",
        "you have anaemia",
        "you are anaemic",
        "i recommend taking",
        "you should take",
        "prescribe",
        "diagnosed with",
        "suffering from",
        "you are sick",
    )

    fun validate(response: String): ValidationResult {
        val lower = response.lowercase()
        val hasBlockedPhrase = blockedPhrases.any { lower.contains(it) }
        return if (hasBlockedPhrase) {
            ValidationResult.Blocked(
                "I'm not able to answer that directly. Please consult your doctor for a diagnosis or treatment recommendation.\n\n${SaMDStringHelper.disclaimerShort}"
            )
        } else {
            ValidationResult.Safe(response)
        }
    }

    sealed class ValidationResult {
        data class Safe(val text: String) : ValidationResult()
        data class Blocked(val fallback: String) : ValidationResult()
    }
}

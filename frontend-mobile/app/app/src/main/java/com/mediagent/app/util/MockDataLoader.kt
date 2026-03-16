package com.mediagent.app.util

import android.content.Context
import kotlinx.serialization.json.Json

class MockDataLoader(
    val context: Context,
    val json: Json,
) {
    inline fun <reified T> load(fileName: String): T {
        val value = context.assets.open(fileName).bufferedReader().use { it.readText() }
        return json.decodeFromString(value)
    }
}

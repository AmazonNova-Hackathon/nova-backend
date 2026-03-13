package com.chetana.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Placeholder: Following Chetana Mobile PRD
            Greeting("Chetana")
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Welcome to $name - Awaken to your health.")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    Greeting("Chetana")
}

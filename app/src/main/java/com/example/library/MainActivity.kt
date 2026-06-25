package com.example.library

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.library.ui.theme.LibraryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ativa o suporte de ponta a ponta (edge-to-edge)
        enableEdgeToEdge()
        
        setContent {
            LibraryTheme {
                MainScreen()
            }
        }
    }
}
package com.example.ketchup

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.example.ketchup.navigation.KetchupNavGraph
import com.example.ketchup.ui.theme.KetchupTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val app = application as KetchupApplication

        setContent {
            KetchupTheme(theme = app.prefsManager.theme) {
                KetchupNavGraph(app = app, activity = this)
            }
        }
    }
}

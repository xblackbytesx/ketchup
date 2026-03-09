package com.example.ketchup.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.ketchup.data.PreferencesManager

abstract class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = PreferencesManager(this)
        ThemeHelper.applyTheme(this, prefs)
        super.onCreate(savedInstanceState)
    }
}

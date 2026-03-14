package com.example.ketchup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.ketchup.data.SecureStorage
import com.example.ketchup.data.db.AppDatabase
import com.example.ketchup.ui.feed.FeedActivity
import com.example.ketchup.ui.lock.LockActivity
import com.example.ketchup.ui.setup.SetupActivity
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = SecureStorage(this)
        val app = application as KetchupApplication

        val feedCount = runBlocking { AppDatabase.getInstance(this@MainActivity).feedDao().getCount() }

        when {
            storage.isPinConfigured() && !app.isAuthenticated -> {
                startActivity(Intent(this, LockActivity::class.java))
            }
            feedCount == 0 -> {
                startActivity(Intent(this, SetupActivity::class.java))
            }
            else -> {
                startActivity(Intent(this, FeedActivity::class.java))
            }
        }
        finish()
    }
}

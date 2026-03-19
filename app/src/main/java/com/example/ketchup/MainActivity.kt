package com.example.ketchup

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ketchup.data.SecureStorage
import com.example.ketchup.data.db.AppDatabase
import com.example.ketchup.ui.feed.FeedActivity
import com.example.ketchup.ui.lock.LockActivity
import com.example.ketchup.ui.setup.SetupActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val storage = SecureStorage(this)
        val app = application as KetchupApplication

        lifecycleScope.launch {
            val feedCount = try {
                withContext(Dispatchers.IO) {
                    AppDatabase.getInstance(this@MainActivity).feedDao().getCount()
                }
            } catch (e: Exception) {
                0
            }
            val target = when {
                storage.isPinConfigured() && !app.isAuthenticated -> LockActivity::class.java
                feedCount == 0 -> SetupActivity::class.java
                else -> FeedActivity::class.java
            }
            startActivity(Intent(this@MainActivity, target))
            finish()
        }
    }
}

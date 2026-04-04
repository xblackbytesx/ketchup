package com.example.ketchup.navigation

import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ketchup.KetchupApplication
import com.example.ketchup.data.db.AppDatabase
import com.example.ketchup.ui.feed.FeedScreen
import com.example.ketchup.ui.lock.LockScreen
import com.example.ketchup.ui.reader.ArticleReaderScreen
import com.example.ketchup.ui.settings.SettingsScreen
import com.example.ketchup.ui.setup.SetupPinScreen
import com.example.ketchup.ui.setup.SetupScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun KetchupNavGraph(app: KetchupApplication, activity: FragmentActivity) {
    val navController = rememberNavController()

    // Determine start destination from app state
    var startDestination: Any? by remember { mutableStateOf(null) }
    LaunchedEffect(Unit) {
        val feedCount = try {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(activity).feedDao().getCount()
            }
        } catch (_: Exception) { 0 }

        startDestination = when {
            app.secureStorage.isPinConfigured() && !app.isAuthenticated -> LockRoute
            feedCount == 0 -> SetupRoute
            else -> FeedRoute
        }
    }

    val start = startDestination ?: return

    NavHost(navController = navController, startDestination = start) {
        composable<SetupRoute> {
            SetupScreen(
                app = app,
                onFeedAdded = {
                    if (app.secureStorage.isPinConfigured()) {
                        navController.navigate(LockRoute) {
                            popUpTo<SetupRoute> { inclusive = true }
                        }
                    } else {
                        navController.navigate(SetupPinRoute) {
                            popUpTo<SetupRoute> { inclusive = true }
                        }
                    }
                },
            )
        }

        composable<SetupPinRoute> {
            SetupPinScreen(
                app = app,
                onComplete = {
                    navController.navigate(FeedRoute) {
                        popUpTo<SetupPinRoute> { inclusive = true }
                    }
                },
            )
        }

        composable<LockRoute> {
            LockScreen(
                app = app,
                activity = activity,
                onUnlocked = {
                    navController.navigate(FeedRoute) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable<FeedRoute> {
            FeedScreen(
                app = app,
                onOpenArticle = { articleId, filterId ->
                    navController.navigate(ArticleReaderRoute(articleId, filterId))
                },
                onOpenSettings = {
                    navController.navigate(SettingsRoute)
                },
            )
        }

        composable<ArticleReaderRoute>(
            enterTransition = { fadeIn(tween(200)) },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { fadeOut(tween(200)) },
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<ArticleReaderRoute>()
            ArticleReaderScreen(
                app = app,
                articleId = route.articleId,
                filterId = route.filterId,
                onBack = { navController.popBackStack() },
            )
        }

        composable<SettingsRoute> {
            SettingsScreen(
                app = app,
                onBack = { navController.popBackStack() },
                onResetApp = {
                    navController.navigate(SetupRoute) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onThemeChanged = {
                    // Restart activity to pick up new theme
                    activity.recreate()
                },
            )
        }
    }
}

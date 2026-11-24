package com.davide.salvacibo.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.davide.salvacibo.ui.detail.DetailScreen
import com.davide.salvacibo.ui.edit.EditScreen
import com.davide.salvacibo.ui.home.HomeScreen
import com.davide.salvacibo.ui.scan.ScanScreen
import com.davide.salvacibo.ui.settings.SettingsScreen
import com.davide.salvacibo.ui.support.SupportScreen

private object Routes {
    const val HOME = "home"
    const val SCAN = "scan"
    const val EDIT = "edit"
    const val DETAIL = "detail/{id}"
    const val SETTINGS = "settings"
    const val SUPPORT = "support"
    fun detail(id: Long) = "detail/$id"
}

@Composable
fun AppNavHost(openProductId: Long?) {
    val nav = rememberNavController()
    val vm: AppViewModel = viewModel()

    // aperta toccando una notifica → vai subito al prodotto
    LaunchedEffect(openProductId) {
        if (openProductId != null && openProductId > 0) nav.navigate(Routes.detail(openProductId))
    }

    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) + fadeIn(tween(300))
        },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) + fadeOut(tween(300))
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                vm = vm,
                onScan = {
                    vm.startNewDraft()
                    nav.navigate(Routes.SCAN)
                },
                onAddManual = {
                    vm.startNewDraft()
                    nav.navigate(Routes.EDIT)
                },
                onOpenProduct = { id -> nav.navigate(Routes.detail(id)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onSupport = { nav.navigate(Routes.SUPPORT) },
            )
        }
        composable(Routes.SCAN) {
            ScanScreen(
                vm = vm,
                onClose = { nav.popBackStack() },
                onDone = {
                    nav.navigate(Routes.EDIT) { popUpTo(Routes.SCAN) { inclusive = true } }
                },
                onManual = {
                    nav.navigate(Routes.EDIT) { popUpTo(Routes.SCAN) { inclusive = true } }
                },
            )
        }
        composable(Routes.EDIT) {
            EditScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onSaved = { id ->
                    // torna alla lista e apri il dettaglio del prodotto salvato
                    nav.popBackStack(Routes.HOME, inclusive = false)
                    nav.navigate(Routes.detail(id))
                },
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            DetailScreen(
                vm = vm,
                productId = id,
                onBack = { nav.popBackStack() },
                onEdit = { product ->
                    vm.loadDraftFrom(product)
                    nav.navigate(Routes.EDIT)
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onSupport = { nav.navigate(Routes.SUPPORT) },
            )
        }
        composable(Routes.SUPPORT) {
            SupportScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}

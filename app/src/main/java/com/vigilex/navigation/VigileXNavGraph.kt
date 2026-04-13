package com.vigilex.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.vigilex.core.model.Role
import com.vigilex.feature.auth.LoginScreen
import com.vigilex.feature.auth.SplashScreen
import com.vigilex.feature.driver.DriverHomeScreen
import com.vigilex.feature.owner.dashboard.OwnerDashboardScreen
import com.vigilex.feature.owner.driverdetail.DriverDetailScreen
import com.vigilex.feature.owner.drivers.DriversManagementScreen
import com.vigilex.feature.owner.settings.OwnerSettingsScreen
import com.vigilex.feature.owner.trips.TripDetailScreen
import com.vigilex.feature.owner.trips.TripHistoryScreen
import com.vigilex.feature.superadmin.AddCompanyScreen
import com.vigilex.feature.superadmin.CompanyDetailScreen
import com.vigilex.feature.superadmin.SuperAdminDashboardScreen

@Composable
fun VigileXNavGraph(navController: NavHostController) {

    // ── Sign-out helper: clears Firebase session then returns to splash
    // (splash sees NoSession → immediately goes to login)
    val signOut: () -> Unit = {
        // Stop the monitoring foreground service before signing out
        val ctx = navController.context.applicationContext
        ctx.stopService(android.content.Intent(ctx, com.vigilex.feature.driver.service.MonitoringForegroundService::class.java))
        FirebaseAuth.getInstance().signOut()
        navController.navigate(Routes.SPLASH) {
            popUpTo(0) { inclusive = true }
        }
    }

    // ── Role → destination helper
    fun roleDestination(role: Role) = when (role) {
        Role.DRIVER      -> Routes.DRIVER_HOME
        Role.OWNER       -> Routes.OWNER_DASHBOARD
        Role.SUPER_ADMIN -> Routes.SUPER_ADMIN_DASHBOARD
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH
    ) {

        // ── Splash ───────────────────────────────────────────────────────────
        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateTo = { role ->
                    navController.navigate(roleDestination(role)) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        // ── Login ────────────────────────────────────────────────────────────
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { role ->
                    navController.navigate(roleDestination(role)) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        // ── Driver ───────────────────────────────────────────────────────────
        composable(Routes.DRIVER_HOME) {
            DriverHomeScreen(onSignOut = signOut)
        }

        // ── Owner ────────────────────────────────────────────────────────────
        composable(Routes.OWNER_DASHBOARD) {
            OwnerDashboardScreen(
                onDriverClick        = { driverId, tripId -> navController.navigate(Routes.driverDetail(driverId, tripId)) },
                onNavigateToHistory  = { navController.navigate(Routes.TRIP_HISTORY) },
                onNavigateToDrivers  = { navController.navigate(Routes.DRIVERS_MANAGEMENT) },
                onNavigateToSettings = { navController.navigate(Routes.OWNER_SETTINGS) }
            )
        }

        composable(
            route = Routes.DRIVER_DETAIL,
            arguments = listOf(
                navArgument("driverId") { type = NavType.StringType },
                navArgument("tripId")   { type = NavType.StringType }
            )
        ) { backStack ->
            DriverDetailScreen(
                driverId = backStack.arguments?.getString("driverId") ?: "",
                tripId   = backStack.arguments?.getString("tripId")   ?: "",
                onBack   = { navController.popBackStack() }
            )
        }

        composable(Routes.TRIP_HISTORY) {
            TripHistoryScreen(
                onTripClick = { tripId -> navController.navigate(Routes.tripDetail(tripId)) },
                onBack      = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.TRIP_DETAIL,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) { backStack ->
            TripDetailScreen(
                tripId = backStack.arguments?.getString("tripId") ?: "",
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DRIVERS_MANAGEMENT) {
            DriversManagementScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.OWNER_SETTINGS) {
            OwnerSettingsScreen(
                onBack    = { navController.popBackStack() },
                onSignOut = signOut
            )
        }

        // ── Super Admin ──────────────────────────────────────────────────────
        composable(Routes.SUPER_ADMIN_DASHBOARD) {
            SuperAdminDashboardScreen(
                onCompanyClick = { companyId -> navController.navigate(Routes.companyDetail(companyId)) },
                onAddCompany   = { navController.navigate(Routes.ADD_COMPANY) },
                onSignOut      = signOut
            )
        }

        composable(Routes.ADD_COMPANY) {
            AddCompanyScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.COMPANY_DETAIL,
            arguments = listOf(navArgument("companyId") { type = NavType.StringType })
        ) { backStack ->
            CompanyDetailScreen(
                companyId = backStack.arguments?.getString("companyId") ?: "",
                onBack    = { navController.popBackStack() }
            )
        }
    }
}

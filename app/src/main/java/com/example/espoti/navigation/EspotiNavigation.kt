package com.example.espoti.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.espoti.ui.screens.HomeScreen
import com.example.espoti.ui.screens.LoginScreen
import com.example.espoti.ui.screens.RegisterScreen
import com.example.espoti.ui.screens.WelcomeScreen

// ============================================================================
// SCREEN ROUTES
// ----------------------------------------------------------------------------
// Every screen has a unique "route" (just a String id). This is what you use
// to tell the app to navigate somewhere, e.g. navController.navigate(Screen.Login.route).
// Add a new entry here (and a matching `composable(...)` block below) any
// time you add a new screen to the prototype.
// ============================================================================
sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")   // Bienvenida
    object Login : Screen("login")       // Login
    object Register : Screen("register") // Registro
    object Home : Screen("home")         // Inicio
}

/**
 * Hosts all 4 screens and wires up the navigation between them.
 * This is called once from MainActivity - you normally don't need to touch
 * this file unless you're adding/removing an entire screen.
 */
@Composable
fun EspotiNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.Welcome.route
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onLoginClick = { navController.navigate(Screen.Login.route) },
                onRegisterClick = { navController.navigate(Screen.Register.route) }
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    // Clear Login/Welcome from the back stack so the phone
                    // back button doesn't return to them after logging in.
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate(Screen.Register.route) }
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.navigate(Screen.Login.route) }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onLogoutClick = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) // clear the entire back stack
                    }
                }
            )
        }
    }
}

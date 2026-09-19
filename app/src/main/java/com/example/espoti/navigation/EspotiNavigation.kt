package com.example.espoti.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.espoti.ui.model.Meeting
import com.example.espoti.ui.screens.HomeScreen
import com.example.espoti.ui.screens.LoginScreen
import com.example.espoti.ui.screens.MeetingDetailScreen
import com.example.espoti.ui.screens.MeetingsScreen
import com.example.espoti.ui.screens.RegisterScreen
import com.example.espoti.ui.screens.WelcomeScreen
import com.example.espoti.ui.model.sampleMeetings
import com.example.espoti.ui.screens.CreateMeetingScreen1
import com.example.espoti.ui.screens.CreateMeetingScreen2

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
    object Meetings : Screen("meetings")
    object CreateMeeting1 : Screen("create_meeting1")
    object CreateMeeting2 : Screen("create_meeting2")
    object MeetingDetail : Screen("meeting_detail/{meetingId}") {
        fun createRoute(meetingId: Int): String {
            return "meeting_detail/$meetingId"
        }

    }
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
                onLoginClick = {
                    navController.navigate(Screen.Login.route) { launchSingleTop = true }
                },
                onRegisterClick = {
                    navController.navigate(Screen.Register.route) { launchSingleTop = true }
                }
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
                onNavigateToRegister = {
                    // Replace Login with Register instead of stacking on top
                    // of it, so bouncing between the two auth screens doesn't
                    // grow the back stack or the RAM ._.
                    navController.navigate(Screen.Register.route) {
                        popUpTo(Screen.Welcome.route)
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Register.route) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Welcome.route)
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onLogoutClick = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) // clear the entire back stack
                    }
                },
                onMeetingsClick = {
                    navController.navigate(Screen.Meetings.route){
                        launchSingleTop = true
                    }
                },
                onCreateMeetingClick = {
                    navController.navigate(Screen.CreateMeeting1.route){
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.CreateMeeting1.route) {
            CreateMeetingScreen1(
                onScheduleClick = {
                    navController.navigate(Screen.CreateMeeting2.route){
                        launchSingleTop = true
                    }
                }, onHomeClick = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
        composable(Screen.CreateMeeting2.route) {
            CreateMeetingScreen2(
                onVoteClick = {
                    navController.navigate(Screen.Home.route){
                        launchSingleTop = true
                    }
                },
                onHomeClick = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
        composable(Screen.Meetings.route) {
            MeetingsScreen(
                onHomeClick = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
            onDetailClick = {meeting ->
                navController.navigate(Screen.MeetingDetail.createRoute(meeting.id))

                }
            )
        }
        composable(Screen.MeetingDetail.route) { backStackEntry ->
            val meetingId =
                backStackEntry.arguments?.getString("meetingId")?.toIntOrNull()
            val meeting = sampleMeetings.find {
                it.id==meetingId
            }

            if (meeting != null) {
                MeetingDetailScreen(
                    meeting = meeting,
                    onHomeClick = {
                        navController.popBackStack(
                            Screen.Home.route,
                            inclusive = false
                        )
                    },
                    onBackClick = {if (navController.currentDestination?.route == Screen.MeetingDetail.route){
                        navController.popBackStack()
                    }
                    }
                )
            }


        }
    }
}

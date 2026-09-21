package com.example.espoti.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.espoti.ui.components.BottomNavItem
import com.example.espoti.ui.components.EspotiBottomNav
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
sealed class Screen(
    val route: String,
    // Which bottom-bar tab is highlighted on this screen. Leave it null for
    // screens that must NOT show the bottom bar (welcome/login/register).
    // A new screen only needs this one argument to get the shared bar.
    val bottomNavItem: BottomNavItem? = null
) {
    object Welcome : Screen("welcome")   // Bienvenida
    object Login : Screen("login")       // Login
    object Register : Screen("register") // Registro
    object Home : Screen("home", BottomNavItem.HOME) // Inicio
    object Meetings : Screen("meetings", BottomNavItem.MEETINGS)
    object CreateMeeting1 : Screen("create_meeting1", BottomNavItem.HOME)
    object CreateMeeting2 : Screen("create_meeting2", BottomNavItem.HOME)
    object MeetingDetail : Screen("meeting_detail/{meetingId}", BottomNavItem.MEETINGS) {
        fun createRoute(meetingId: Int): String {
            return "meeting_detail/$meetingId"
        }

    }

    companion object {
        private val all by lazy {
            listOf(Welcome, Login, Register, Home, Meetings, CreateMeeting1, CreateMeeting2, MeetingDetail)
        }

        /** Looks up a screen by the route pattern reported by the NavController. */
        fun fromRoute(route: String?): Screen? = all.firstOrNull { it.route == route }
    }
}

/**
 * Hosts all 4 screens and wires up the navigation between them.
 * This is called once from MainActivity - you normally don't need to touch
 * this file unless you're adding/removing an entire screen.
 */
@Composable
fun EspotiNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedTab = Screen.fromRoute(backStackEntry?.destination?.route)?.bottomNavItem

    // Single bottom bar for the whole app. It is only shown on screens that
    // declare a `bottomNavItem`, and every tab behaves the same everywhere.
    Scaffold(
        // The screens' own Scaffolds already handle the status-bar inset, so
        // this one only reserves the space taken by the bottom bar.
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (selectedTab != null) {
                EspotiBottomNav(
                    selectedItem = selectedTab,
                    onHomeClick = { navController.navigateToTab(Screen.Home) },
                    onMeetingsClick = { navController.navigateToTab(Screen.Meetings) },
                    // No Friends screen yet: wire it here once it exists.
                    onFriendsClick = {},
                    // Placeholder: Profile acts as logout until a Profile screen exists.
                    onProfileClick = {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(0) // clear the entire back stack
                        }
                    },
                    onAddClick = { navController.navigateToTab(Screen.CreateMeeting1) }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Welcome.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
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
                    onCreateMeetingClick = {
                        navController.navigate(Screen.CreateMeeting1.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.CreateMeeting1.route) {
                CreateMeetingScreen1(
                    onScheduleClick = {
                        navController.navigate(Screen.CreateMeeting2.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.CreateMeeting2.route) {
                CreateMeetingScreen2(
                    onVoteClick = {
                        navController.navigate(Screen.Home.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Meetings.route) {
                MeetingsScreen(
                    onDetailClick = { meeting ->
                        navController.navigate(Screen.MeetingDetail.createRoute(meeting.id))
                    }
                )
            }
            composable(Screen.MeetingDetail.route) { backStackEntry ->
                val meetingId =
                    backStackEntry.arguments?.getString("meetingId")?.toIntOrNull()
                val meeting = sampleMeetings.find {
                    it.id == meetingId
                }

                if (meeting != null) {
                    MeetingDetailScreen(
                        meeting = meeting,
                        onBackClick = {
                            if (navController.currentDestination?.route == Screen.MeetingDetail.route) {
                                navController.popBackStack()
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Navigates to a screen from the bottom bar. Every tab pops back to Home first,
 * so the stack is always Home -> tab and tapping tabs never piles up screens.
 * `launchSingleTop` makes tapping the tab you're already on a no-op.
 */
private fun NavHostController.navigateToTab(screen: Screen) {
    navigate(screen.route) {
        popUpTo(Screen.Home.route) { inclusive = screen == Screen.Home }
        launchSingleTop = true
    }
}

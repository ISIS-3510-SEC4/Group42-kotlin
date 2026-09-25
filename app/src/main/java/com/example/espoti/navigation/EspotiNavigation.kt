package com.example.espoti.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.espoti.viewmodel.CreateMeetingViewModel
import com.example.espoti.viewmodel.MeetingDetailViewModel
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
/** What the phone's back button does on a screen. */
enum class BackBehavior {
    /** Always go to Home, no matter how many screens were opened on the way. */
    GO_HOME,

    /** Normal behavior: return to the screen we came from (or leave the app if there is none). */
    PREVIOUS
}

sealed class Screen(
    val route: String,
    // Which bottom-bar tab is highlighted on this screen. Leave it null for
    // screens that must NOT show the bottom bar (welcome/login/register).
    // A new screen only needs this one argument to get the shared bar.
    val bottomNavItem: BottomNavItem? = null,
    // Phone back button. The default sends the user to Home, so any new "main"
    // screen behaves correctly without extra work. Only screens that follow a
    // flow (auth, create meeting) or are the root (Welcome, Home) use PREVIOUS.
    val backBehavior: BackBehavior = BackBehavior.GO_HOME
) {
    // Root of the app: back leaves the app.
    object Welcome : Screen("welcome", backBehavior = BackBehavior.PREVIOUS)   // Bienvenida
    // Login/Register sit right above Welcome, so "previous" is always Welcome.
    object Login : Screen("login", backBehavior = BackBehavior.PREVIOUS)       // Login
    object Register : Screen("register", backBehavior = BackBehavior.PREVIOUS) // Registro
    // Home is the base of the stack after login: back leaves the app.
    object Home : Screen("home", BottomNavItem.HOME, BackBehavior.PREVIOUS) // Inicio
    object Meetings : Screen("meetings", BottomNavItem.MEETINGS)
    // Create meeting is opened from any screen and returns to wherever it was opened.
    object CreateMeeting1 : Screen("create_meeting1", BottomNavItem.HOME, BackBehavior.PREVIOUS)
    object CreateMeeting2 : Screen("create_meeting2", BottomNavItem.HOME, BackBehavior.PREVIOUS)
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EspotiNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentScreen = Screen.fromRoute(backStackEntry?.destination?.route)
    val selectedTab = currentScreen?.bottomNavItem
    // imeAnimationTarget is the keyboard's FINAL size, known as soon as it starts
    // opening. Using it (instead of the animated `ime` inset) makes the content
    // jump straight to its final position instead of sliding along with the keyboard.
    val keyboardInsets = WindowInsets.imeAnimationTarget
    val keyboardVisible = keyboardInsets.getBottom(LocalDensity.current) > 0

    // Phone back button: screens marked GO_HOME jump straight to Home, dropping
    // everything opened in between (e.g. Meetings -> Detail -> back = Home).
    BackHandler(enabled = currentScreen?.backBehavior == BackBehavior.GO_HOME) {
        navController.popBackStack(Screen.Home.route, inclusive = false)
    }

    // Single bottom bar for the whole app. It is only shown on screens that
    // declare a `bottomNavItem`, and every tab behaves the same everywhere.
    Scaffold(
        // The screens' own Scaffolds already handle the status-bar inset, so
        // this one only reserves the space taken by the bottom bar.
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            // Hidden while the keyboard is open so it doesn't float over it.
            if (selectedTab != null && !keyboardVisible) {
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
                    onAddClick = { navController.navigateToCreateMeeting() }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Welcome.route,
            // Lifts every screen above the keyboard (the window doesn't resize on
            // its own because the app is edge-to-edge). Scrollable screens then
            // keep the focused field in view.
            modifier = Modifier
                .padding(bottom = innerPadding.calculateBottomPadding())
                .windowInsetsPadding(keyboardInsets)
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
                    onCreateMeetingClick = { navController.navigateToCreateMeeting() }
                )
            }
            composable(Screen.CreateMeeting1.route) { backStackEntry ->
                // Scoped to this entry so CreateMeeting2 reuses the same state
                // (see CreateMeeting2's composable below).
                val createMeetingViewModel: CreateMeetingViewModel = viewModel(backStackEntry)
                CreateMeetingScreen1(
                    viewModel = createMeetingViewModel,
                    onScheduleClick = {
                        navController.navigate(Screen.CreateMeeting2.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.CreateMeeting2.route) {
                // Share the ViewModel owned by CreateMeeting1 (it is always
                // right below Create 2 in the back stack).
                val parentEntry = remember(it) {
                    navController.getBackStackEntry(Screen.CreateMeeting1.route)
                }
                val createMeetingViewModel: CreateMeetingViewModel = viewModel(parentEntry)
                CreateMeetingScreen2(
                    viewModel = createMeetingViewModel,
                    onVoteClick = {
                        // Flow finished: drop both create screens (and whatever
                        // was opened before them) and land on Home.
                        navController.popBackStack(Screen.Home.route, inclusive = false)
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
            composable(Screen.MeetingDetail.route) {
                // The ViewModel reads the "meetingId" route argument itself.
                val detailViewModel: MeetingDetailViewModel = viewModel()
                detailViewModel.meeting?.let { meeting ->
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

/**
 * Opens the create-meeting flow on top of the CURRENT screen without touching
 * the stack below it, so phone back returns to wherever it was launched from
 * (Home, Meetings, ...). If the flow is already open, Create 1 is not reset
 * when tapped again; from Create 2 it goes back to a fresh Create 1.
 */
private fun NavHostController.navigateToCreateMeeting() {
    if (currentDestination?.route == Screen.CreateMeeting1.route) return
    navigate(Screen.CreateMeeting1.route) {
        popUpTo(Screen.CreateMeeting1.route) { inclusive = true }
        launchSingleTop = true
    }
}

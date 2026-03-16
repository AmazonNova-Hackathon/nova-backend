package com.mediagent.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mediagent.app.presentation.viewmodel.*
import com.mediagent.app.ui.auth.LoginScreen
import com.mediagent.app.ui.auth.OtpScreen
import com.mediagent.app.ui.auth.SplashScreen
import com.mediagent.app.ui.chat.ChatScreen
import com.mediagent.app.ui.family.FamilyDashboard
import com.mediagent.app.ui.family.MemberDetailScreen
import com.mediagent.app.ui.insights.InsightsFeedScreen
import com.mediagent.app.ui.report.ReportDetailScreen
import com.mediagent.app.ui.onboarding.OnboardingScreen
import com.mediagent.app.ui.settings.SettingsScreen
import com.mediagent.app.ui.upload.CaptureScreen
import com.mediagent.app.ui.upload.ProcessingScreen
import com.mediagent.app.ui.upload.ResultScreen
import com.mediagent.app.ui.theme.BrandPurple
import com.mediagent.app.ui.theme.TextPrimary
import com.mediagent.app.ui.theme.TextTertiary

object ChetanaRoutes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val OTP = "otp"
    const val MAIN = "main"
    const val FAMILY = "family"
    const val MEMBER = "member/{memberId}"
    const val REPORT = "report/{reportId}"
    const val CAPTURE = "capture"
    const val PROCESSING = "processing/{reportId}/{memberId}"
    const val RESULT = "result/{reportId}/{memberId}"
    const val CHAT = "chat?reportId={reportId}"
    const val CHAT_BASE = "chat"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"

    fun member(id: String) = "member/$id"
    fun report(id: String) = "report/$id"
    fun processing(reportId: String, memberId: String) = "processing/$reportId/$memberId"
    fun chat(reportId: String? = null) = if (reportId != null) "chat?reportId=$reportId" else "chat"
    fun result(reportId: String, memberId: String) = "result/$reportId/$memberId"
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(ChetanaRoutes.FAMILY, "Family", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(ChetanaRoutes.CAPTURE, "Upload", Icons.Filled.CameraAlt, Icons.Filled.CameraAlt),
    BottomNavItem(ChetanaRoutes.CHAT_BASE, "Chat", Icons.Filled.ChatBubbleOutline, Icons.Outlined.ChatBubbleOutline),
    BottomNavItem(ChetanaRoutes.INSIGHTS, "Insights", Icons.Filled.Lightbulb, Icons.Outlined.Lightbulb),
)

@Composable
fun ChetanaApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        ChetanaRoutes.FAMILY,
        ChetanaRoutes.CAPTURE,
        ChetanaRoutes.CHAT,
        ChetanaRoutes.INSIGHTS,
    ) || currentRoute?.startsWith("chat") == true

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                ChetanaBottomBar(navController = navController)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ChetanaRoutes.SPLASH,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            composable(ChetanaRoutes.SPLASH) {
                SplashScreen(
                    onNavigateToLogin = {
                        navController.navigate(ChetanaRoutes.LOGIN) {
                            popUpTo(ChetanaRoutes.SPLASH) { inclusive = true }
                        }
                    },
                    onNavigateToMain = {
                        navController.navigate(ChetanaRoutes.FAMILY) {
                            popUpTo(ChetanaRoutes.SPLASH) { inclusive = true }
                        }
                    },
                    onNavigateToOnboarding = {
                        navController.navigate(ChetanaRoutes.ONBOARDING) {
                            popUpTo(ChetanaRoutes.SPLASH) { inclusive = true }
                        }
                    },
                )
            }

            composable(ChetanaRoutes.ONBOARDING) {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                OnboardingScreen(
                    onComplete = {
                        settingsViewModel.completeOnboarding()
                        navController.navigate(ChetanaRoutes.LOGIN) {
                            popUpTo(ChetanaRoutes.ONBOARDING) { inclusive = true }
                        }
                    },
                )
            }

            composable(ChetanaRoutes.LOGIN) {
                LoginScreen(
                    onNavigateToOtp = { navController.navigate(ChetanaRoutes.OTP) },
                )
            }

            composable(ChetanaRoutes.OTP) {
                OtpScreen(
                    onVerified = {
                        navController.navigate(ChetanaRoutes.FAMILY) {
                            popUpTo(ChetanaRoutes.SPLASH) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(ChetanaRoutes.FAMILY) {
                FamilyDashboard(
                    onNavigateToMember = { memberId ->
                        navController.navigate(ChetanaRoutes.member(memberId))
                    },
                    onNavigateToCapture = {
                        navController.navigate(ChetanaRoutes.CAPTURE)
                    },
                    onNavigateToSettings = {
                        navController.navigate(ChetanaRoutes.SETTINGS)
                    },
                )
            }

            composable(
                route = ChetanaRoutes.MEMBER,
                arguments = listOf(navArgument("memberId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val memberId = backStackEntry.arguments?.getString("memberId") ?: return@composable
                // Share the FamilyViewModel from the FAMILY screen so data is already loaded
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(ChetanaRoutes.FAMILY)
                }
                MemberDetailScreen(
                    memberId = memberId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToReport = { reportId ->
                        navController.navigate(ChetanaRoutes.report(reportId))
                    },
                    familyViewModel = hiltViewModel(parentEntry),
                )
            }

            composable(
                route = ChetanaRoutes.REPORT,
                arguments = listOf(navArgument("reportId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
                ReportDetailScreen(
                    reportId = reportId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToChat = {
                        navController.navigate(ChetanaRoutes.chat(reportId))
                    },
                    onDelete = { navController.popBackStack() },
                )
            }

            composable(ChetanaRoutes.CAPTURE) {
                CaptureScreen(
                    onDismiss = { navController.popBackStack() },
                    onCaptured = { reportId, memberId ->
                        navController.navigate(ChetanaRoutes.processing(reportId, memberId))
                    },
                )
            }

            composable(
                route = ChetanaRoutes.PROCESSING,
                arguments = listOf(
                    navArgument("reportId") { type = NavType.StringType },
                    navArgument("memberId") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
                val memberId = backStackEntry.arguments?.getString("memberId") ?: return@composable
                ProcessingScreen(
                    reportId = reportId,
                    memberId = memberId,
                    onComplete = { id ->
                        navController.navigate(ChetanaRoutes.result(id, memberId)) {
                            popUpTo(ChetanaRoutes.CAPTURE) { inclusive = true }
                        }
                    },
                    onDismiss = {
                        navController.navigate(ChetanaRoutes.FAMILY) {
                            popUpTo(ChetanaRoutes.CAPTURE) { inclusive = true }
                        }
                    },
                )
            }

            composable(
                route = ChetanaRoutes.RESULT,
                arguments = listOf(
                    navArgument("reportId") { type = NavType.StringType },
                    navArgument("memberId") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val reportId = backStackEntry.arguments?.getString("reportId") ?: return@composable
                val resultMemberId = backStackEntry.arguments?.getString("memberId") ?: return@composable
                ResultScreen(
                    reportId = reportId,
                    memberId = resultMemberId,
                    onNavigateToChat = { navController.navigate(ChetanaRoutes.chat(reportId)) },
                    onDelete = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = ChetanaRoutes.CHAT,
                arguments = listOf(
                    navArgument("reportId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                val chatReportId = backStackEntry.arguments?.getString("reportId")
                val chatViewModel: ChatViewModel = hiltViewModel()
                LaunchedEffect(chatReportId) {
                    chatViewModel.setReportContext(chatReportId)
                }
                ChatScreen(
                    onNavigateBack = { navController.popBackStack() },
                    chatViewModel = chatViewModel,
                )
            }

            composable(ChetanaRoutes.INSIGHTS) {
                InsightsFeedScreen()
            }

            composable(ChetanaRoutes.SETTINGS) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onLogout = {
                        navController.navigate(ChetanaRoutes.SPLASH) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun ChetanaBottomBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                    )
                },
                label = { Text(item.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandPurple,
                    selectedTextColor = BrandPurple,
                    unselectedIconColor = TextTertiary,
                    unselectedTextColor = TextTertiary,
                    indicatorColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    }
}

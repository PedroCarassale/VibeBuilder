package com.vibebuilder.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vibebuilder.app.ui.screens.createproject.CreateProjectScreen
import com.vibebuilder.app.ui.screens.projectdetail.ProjectDetailScreen
import com.vibebuilder.app.ui.screens.projectlist.ProjectListScreen

@Composable
fun VibeNavGraph() {
    val navController = rememberNavController()
    var deletionEventId by remember { mutableLongStateOf(0L) }
    fun navigateHome() {
        val popped = navController.popBackStack()
        if (!popped) {
            navController.navigate(Screen.ProjectList.route) {
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.ProjectList.route
    ) {
        composable(Screen.ProjectList.route) {
            ProjectListScreen(
                onCreateProject = { navController.navigate(Screen.CreateProject.route) },
                onProjectClick = { projectId ->
                    navController.navigate(Screen.ProjectDetail.routeFor(projectId))
                },
                deletionEventId = deletionEventId,
                onDeletionConfirmationShown = { deletionEventId = 0L }
            )
        }

        composable(Screen.CreateProject.route) {
            CreateProjectScreen(
                onBack = ::navigateHome,
                onProjectCreated = { projectId ->
                    navController.popBackStack()
                    navController.navigate(Screen.ProjectDetail.routeFor(projectId))
                }
            )
        }

        composable(
            route = Screen.ProjectDetail.route,
            arguments = listOf(
                navArgument(Screen.ProjectDetail.ARG_PROJECT_ID) { type = NavType.StringType }
            )
        ) { entry ->
            val projectId = entry.arguments
                ?.getString(Screen.ProjectDetail.ARG_PROJECT_ID).orEmpty()
            ProjectDetailScreen(
                projectId = projectId,
                onBack = ::navigateHome,
                onDeleted = {
                    deletionEventId = System.currentTimeMillis()
                    val returnedHome = navController.popBackStack(
                        Screen.ProjectList.route,
                        inclusive = false
                    )
                    if (!returnedHome) {
                        navController.navigate(Screen.ProjectList.route) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    }
}

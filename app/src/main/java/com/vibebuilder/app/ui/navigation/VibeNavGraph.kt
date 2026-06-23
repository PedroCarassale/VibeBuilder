package com.vibebuilder.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.vibebuilder.app.ui.screens.v0settings.V0IntegrationScreen

@Composable
fun VibeNavGraph() {
    val navController = rememberNavController()
    var deletionConfirmed by remember { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = Screen.ProjectList.route
    ) {
        composable(Screen.ProjectList.route) {
            ProjectListScreen(
                onCreateProject = { navController.navigate(Screen.CreateProject.route) },
                onOpenV0Settings = { navController.navigate(Screen.SettingsV0.route) },
                onProjectClick = { projectId ->
                    navController.navigate(Screen.ProjectDetail.routeFor(projectId))
                },
                deletionConfirmed = deletionConfirmed,
                onDeletionConfirmationShown = { deletionConfirmed = false }
            )
        }

        composable(Screen.CreateProject.route) {
            CreateProjectScreen(
                onBack = { navController.popBackStack() },
                onProjectCreated = { projectId ->
                    navController.popBackStack()
                    navController.navigate(Screen.ProjectDetail.routeFor(projectId))
                }
            )
        }

        composable(Screen.SettingsV0.route) {
            V0IntegrationScreen(onBack = { navController.popBackStack() })
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
                onBack = { navController.popBackStack() },
                onDeleted = {
                    deletionConfirmed = true
                    navController.popBackStack(Screen.ProjectList.route, inclusive = false)
                }
            )
        }
    }
}

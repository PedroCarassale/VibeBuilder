package com.vibebuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vibebuilder.data.MockProjectRepository
import com.vibebuilder.ui.screens.CreateProjectScreen
import com.vibebuilder.ui.screens.PromptScreen
import com.vibebuilder.ui.screens.ProjectListScreen
import com.vibebuilder.ui.screens.VersionHistoryScreen
import com.vibebuilder.ui.screens.WebPreviewScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VibeBuilderApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VibeBuilderApp() {
    val navController = rememberNavController()
    val repository = remember { MockProjectRepository() }

    Scaffold(topBar = { TopAppBar(title = { Text("VibeBuilder") }) }) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "project_list",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("project_list") {
                ProjectListScreen(
                    projects = repository.getProjects(),
                    onCreateProject = { navController.navigate("create_project") },
                    onOpenProject = { navController.navigate("prompt/$it") }
                )
            }
            composable("create_project") {
                CreateProjectScreen(
                    onCancel = { navController.popBackStack() },
                    onCreate = { title, description ->
                        val project = repository.createProject(title, description)
                        navController.navigate("prompt/${project.id}")
                    }
                )
            }
            composable(
                route = "prompt/{projectId}",
                arguments = listOf(navArgument("projectId") { type = NavType.StringType })
            ) { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
                PromptScreen(
                    messages = repository.getMessages(projectId),
                    onBack = { navController.popBackStack() },
                    onSendPrompt = { repository.sendPrompt(projectId, it) },
                    onOpenPreview = { navController.navigate("preview/$projectId") },
                    onOpenHistory = { navController.navigate("history/$projectId") }
                )
            }
            composable("preview/{projectId}") {
                WebPreviewScreen(onBack = { navController.popBackStack() })
            }
            composable("history/{projectId}") { backStackEntry ->
                val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
                VersionHistoryScreen(
                    versions = repository.getVersions(projectId),
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

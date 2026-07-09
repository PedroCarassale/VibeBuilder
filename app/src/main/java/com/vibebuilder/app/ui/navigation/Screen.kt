package com.vibebuilder.app.ui.navigation

/**
 * Single source of truth for navigation routes.
 *
 * Routes are kept simple strings to avoid pulling in extra navigation-compose-typed deps
 * in Delivery 1. Helper builders ensure call sites can't pass malformed routes.
 */
sealed class Screen(val route: String) {
    data object ProjectList : Screen("projects")
    data object CreateProject : Screen("projects/create")
    data object Account : Screen("account")
    data object ProjectDetail : Screen("projects/{projectId}") {
        const val ARG_PROJECT_ID: String = "projectId"
        fun routeFor(projectId: String): String = "projects/$projectId"
    }
}

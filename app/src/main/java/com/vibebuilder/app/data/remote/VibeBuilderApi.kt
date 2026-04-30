package com.vibebuilder.app.data.remote

/**
 * Placeholder contract for the future remote API described in AGENTS.md.
 *
 * Implementations will use Retrofit/Ktor against endpoints such as:
 *  - POST   /projects
 *  - GET    /projects
 *  - GET    /projects/{id}
 *  - POST   /projects/{id}/prompts
 *  - GET    /projects/{id}/versions
 *  - GET    /projects/{id}/preview
 *
 * Kept as an empty interface for now so the data layer compiles without networking deps,
 * but the boundary is already in place.
 */
interface VibeBuilderApi

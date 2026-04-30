# AGENTS.md

## Project Overview

This project is a mobile Android application that allows users to create web applications using AI prompts.

The Android app is the client used by the user. The generated projects are web apps, not APKs. The goal is to provide a mobile-first experience similar to tools like v0.dev, where users can describe an idea in natural language, generate an initial web app, preview it, and continue iterating through prompts.

## Product Vision

The app should help users capture an idea anywhere, describe it from their phone, and quickly transform it into a usable web app prototype.

The long-term vision is an end-to-end AI builder that can:
- generate an initial web app from a prompt,
- let users iterate through additional prompts,
- preview changes,
- keep version history,
- run basic validations,
- and allow users to fork public projects from other users.

## Core Concept

The user does not manually code inside the mobile app. Instead, the user interacts with an AI builder through prompts.

The generated output should be a web app project because web apps are easier to preview, deploy, share, and modify than native mobile apps.

## Main User Flow

1. User opens the Android app.
2. User creates a new project.
3. User writes a prompt describing the web app they want.
4. The backend/AI service generates a web app structure.
5. The user can preview the generated app.
6. The user can send follow-up prompts to modify the app.
7. The app keeps a history of iterations.
8. The user can save, export, share, or eventually publish the project.

## Academic Delivery Plan

### Delivery 1

Build the first functional version of the Android app.

The user should be able to:
- create a project,
- write an initial prompt,
- generate a first version of a web app,
- preview the result,
- send follow-up prompts to iterate on the generated app,
- see a basic history of changes.

This delivery should prove the core value of the product: creating and modifying a web app from a mobile device using AI.

### Delivery 2

Improve project control and generation quality.

The app should include:
- better project structure,
- improved preview experience,
- version history,
- project metadata,
- ability to rename and organize projects,
- better error handling,
- loading states,
- regeneration flow when something fails,
- basic validation of generated output.

This delivery should make the app feel like a real builder, not just a prompt input screen.

### Delivery 3

Add a community library and fork system.

The app should include:
- a public or shared project library,
- ability to browse apps created by other users,
- ability to open another user's project,
- ability to fork that project,
- ability to modify the fork with new prompts,
- ownership of the forked version,
- basic attribution to the original project.

This should work similarly to a simplified GitHub fork flow, but adapted to non-technical users and AI-generated web apps.

## Suggested Architecture

Use a mobile Android frontend and a backend service.

### Android App

Responsible for:
- authentication if needed,
- project list,
- prompt input,
- project detail screen,
- preview screen,
- version history UI,
- community library UI,
- fork actions.

Recommended stack:
- Kotlin or Java for native Android,
- or React Native if the team prefers JavaScript/TypeScript.

### Backend

Responsible for:
- storing users,
- storing projects,
- storing generated files,
- storing prompt history,
- managing versions,
- calling the AI model,
- preparing generated web app previews,
- managing forks.

Recommended stack:
- Node.js with TypeScript,
- NestJS or Express,
- PostgreSQL or MySQL,
- object storage for generated files if needed.

### Generated Web Apps

Generated apps should initially be simple web projects.

Recommended output:
- React + Vite + TypeScript,
- component-based structure,
- simple CSS or Tailwind,
- JSON metadata describing project pages and components.

Avoid generating native APKs in the first version.

## Important Product Decisions

Do not build a full native app generator at the beginning.

The generated app should be a web app because:
- it is easier to preview inside the Android app,
- it is easier to deploy,
- it avoids app store friction,
- it is easier for AI to generate,
- it is easier to fork and remix.

Do not focus on advanced code editing in the first version.

The main interaction should be prompt-based:
- "Create a landing page for a gym"
- "Add login"
- "Change the color palette"
- "Add a dashboard"
- "Make the app look more professional"
- "Add a task list screen"

## Data Model Ideas

Use these entities as a starting point:

### User

Represents a person using the app.

Possible fields:
- name
- email
- avatar
- created projects
- forked projects

### Project

Represents a generated web app.

Possible fields:
- title
- description
- owner
- visibility
- current version
- created date
- updated date
- original project if it is a fork

### ProjectVersion

Represents one generated state of a project.

Possible fields:
- project
- version number
- prompt used
- generated files
- preview URL
- created date
- status

### PromptMessage

Represents a user instruction or AI response in the project conversation.

Possible fields:
- project
- role
- content
- created date

### Fork

Represents the relationship between an original project and a copied project.

Possible fields:
- original project
- forked project
- original author
- new owner
- created date

## UX Principles

The app should feel simple and mobile-first.

Prioritize:
- large prompt input,
- clear project cards,
- fast preview access,
- visible iteration history,
- simple actions,
- minimal technical jargon.

Avoid overwhelming the user with file trees or raw code in the first version.

Advanced code views can be added later, but the main user should be able to create apps without knowing how to code.

## Screens

Initial suggested screens:

1. Home / Project List
2. Create Project
3. Project Detail
4. Prompt Chat / Iteration Screen
5. Web App Preview
6. Version History
7. Community Library
8. Forked Project Detail
9. Settings / Profile

## Coding Guidelines

Prefer simple, readable code over over-engineered abstractions.

When implementing features:
- keep components small,
- use clear naming,
- separate API logic from UI logic,
- handle loading, error, and empty states,
- validate user input,
- avoid hardcoded mock data once backend integration exists.

## Backend API Ideas

Possible endpoints:

POST /projects
GET /projects
GET /projects/:id
POST /projects/:id/prompts
GET /projects/:id/versions
GET /projects/:id/preview
POST /projects/:id/fork
GET /library/projects
PATCH /projects/:id
DELETE /projects/:id

## AI Generation Rules

When generating a web app:

- produce a small but complete working project,
- prefer React + Vite + TypeScript,
- keep dependencies minimal,
- generate clean component structure,
- avoid unnecessary complexity,
- include a clear home page,
- include placeholder data when needed,
- avoid fake backend unless explicitly requested.

When iterating:

- preserve existing project intent,
- modify only what the user requested,
- do not rewrite the whole project unless necessary,
- create a new version after each successful change.
- Definition of Done

A feature is complete when:

- it works from the mobile UI,
- it handles loading and error states,
- it stores the required data,
- it is connected to the backend or clearly mocked,
- it can be demonstrated in a realistic user flow.

## Current Priority

Start with Delivery 1.

Focus on:

- creating projects,
- sending prompts,
- generating a simple web app,
- previewing it,
- iterating with follow-up prompts,
- saving prompt/version history.

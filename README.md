# VibeBuilder — Android (Delivery 1)

App Android nativa que permite crear web apps describiéndolas en lenguaje natural desde el celular. Esta es la primera entrega académica: foco en demostrar el flujo end-to-end (crear proyecto → prompt → preview → iterar → historial) usando datos mockeados pero con una arquitectura lista para conectar el backend real.

Ver el documento de producto en [`AGENTS.md`](./AGENTS.md).

## Backend y deploy en Vercel

El API Node.js vive en [`backend/`](./backend/). Para publicarlo en Vercel y apuntar la app Android (APK) a esa URL, seguí la guía **[`docs/deploy-vercel.md`](./docs/deploy-vercel.md)**.

Resumen rápido:

1. En Vercel, crear proyecto con **Root Directory** = `backend`.
2. Configurar `V0_API_KEY`, `V0_KEYSTORE_SECRET` (y opcionalmente `V0_API_URL`).
3. `vercel deploy --prod` desde `backend/`.
4. En `gradle.properties`: `API_BASE_URL=https://tu-proyecto.vercel.app`
5. Generar APK release y probar en dispositivo.

## Stack

- **Kotlin 2.0.21** + **Jetpack Compose** (Material 3)
- **Navigation Compose**
- **ViewModel + StateFlow + Coroutines**
- **kotlinx-datetime**
- Min SDK 24 · Target/Compile SDK 35 · JDK 17

## Cómo correr

Requisitos: Android Studio Koala/Ladybug+ (AGP 8.5+), JDK 17, Android SDK 35.

1. Abrir la carpeta `VibeBuilder/` desde Android Studio (File → Open).
2. Esperar a que Gradle sincronice.
3. Seleccionar un emulador (API 24+) o dispositivo físico.
4. Ejecutar la configuración `app`.

Al primer arranque la app trae un proyecto demo (“Landing de gimnasio”) precargado para mostrar el flujo sin tener que llenar el formulario.

> El proyecto aún no incluye el wrapper de Gradle (`gradlew`/`gradlew.bat`) ni el `gradle-wrapper.jar`. Android Studio los regenera automáticamente al abrir el proyecto. Si querés generarlos por línea de comandos: `gradle wrapper --gradle-version 8.9` (necesitás Gradle instalado globalmente para ese comando inicial).

## Arquitectura

Capas separadas para que el día que el backend exista solo cambie la capa `data`:

```
app/src/main/java/com/vibebuilder/app/
├── MainActivity.kt
├── VibeBuilderApp.kt              # Application
├── di/
│   └── ServiceLocator.kt          # DI manual (mock por defecto)
├── domain/
│   ├── model/                     # Project, ProjectVersion, PromptMessage
│   └── repository/                # ProjectRepository (interfaz)
├── data/
│   ├── remote/                    # VibeBuilderApi (placeholder)
│   └── repository/
│       ├── MockProjectRepository.kt   # Implementación in-memory + datos demo
│       └── RemoteProjectRepository.kt # Skeleton para el backend real
└── ui/
    ├── theme/                     # Color, Type, Theme (Material 3)
    ├── components/                # Loading/Error/Empty reutilizables
    ├── navigation/                # Screen + VibeNavGraph
    └── screens/
        ├── projectlist/           # Lista de proyectos (Home)
        ├── createproject/         # Formulario de creación + prompt inicial
        └── projectdetail/         # Tabs Prompt / Preview / Historial
```

### ¿Cómo conectar al backend cuando exista?

1. Implementar `VibeBuilderApi` con Retrofit/Ktor contra los endpoints listados en `AGENTS.md` (`POST /projects`, `GET /projects`, `POST /projects/:id/prompts`, etc.).
2. Completar `RemoteProjectRepository` reemplazando los `notImplemented()` por llamadas reales.
3. En `ServiceLocator`, cambiar:
   ```kotlin
   val projectRepository: ProjectRepository by lazy { RemoteProjectRepository(api) }
   ```
   Ningún archivo de UI tiene que tocarse: las pantallas dependen únicamente de la interfaz `ProjectRepository`.

## Pantallas (Delivery 1)

| Pantalla            | Estado                                                                 |
|---------------------|------------------------------------------------------------------------|
| Project List (Home) | Lista los proyectos del usuario, FAB para crear, estados loading/empty |
| Create Project      | Form con nombre, descripción opcional y prompt inicial (con validación)|
| Project Detail      | Tabs: **Prompt** (chat de iteraciones), **Preview** (placeholder del HTML generado), **Historial** (versiones) |

## Datos mock

`MockProjectRepository` mantiene proyectos, versiones y mensajes en `MutableStateFlow`s, simula latencia (~600 ms) y crea una nueva versión por cada prompt enviado. Los flows son reactivos: cualquier prompt nuevo aparece automáticamente en las pestañas de Prompt e Historial sin recargar.

## Limitaciones conocidas (a resolver en Deliveries 2/3)

- El preview es un placeholder textual; no renderiza HTML real (eso requerirá un `WebView` apuntando a la URL devuelta por el backend).
- Sin persistencia en disco: los datos se reinician al cerrar la app.
- Sin autenticación, sin librería pública, sin fork.
- Sin manejo avanzado de errores ni reintentos.

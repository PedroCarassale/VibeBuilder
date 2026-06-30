# Desplegar el backend en Vercel y conectar la app Android

Guía para publicar `backend/` en Vercel y generar un APK que apunte a esa URL.

## Requisitos

- Cuenta en [Vercel](https://vercel.com)
- [Vercel CLI](https://vercel.com/docs/cli): `npm i -g vercel`
- Node.js 20+ (el runtime de Vercel usa Node compatible con `node:sqlite`)

## 1. Variables de entorno en Vercel

En el dashboard del proyecto (**Settings → Environment Variables**) o con CLI:

| Variable | Obligatoria | Descripción |
|----------|-------------|-------------|
| `V0_API_KEY` | Recomendada | API key de [v0](https://v0.app/chat/settings/keys). Sin ella el backend usa el proveedor mock. |
| `V0_KEYSTORE_SECRET` | Recomendada | Secreto ≥ 16 caracteres para cifrar keys v0 por sesión (`PUT /integrations/v0`). |
| `V0_API_URL` | No | URL base del API v0 (default `https://api.v0.dev/v1`). |
| `TURSO_DATABASE_URL` | **Sí en producción** | La integración Turso en Vercel la crea al enlazar Storage. |
| `TURSO_AUTH_TOKEN` | **Sí en producción** | Token de la base Turso (también la inyecta la integración). |
| `BLOB_READ_WRITE_TOKEN` | **Sí en producción** | Token de Vercel Blob para persistir artefactos generados (archivos del proyecto web). |
| `DB_PATH` | No | Solo SQLite local; en Vercel sin Turso usa `/tmp` (datos **no persistentes**). |
| `ARTIFACT_STORAGE_PATH` | No | Solo local; carpeta para artefactos en desarrollo (default `./data/artifacts`). |

### Turso (integración Vercel Storage)

El backend **ya incluye** `@libsql/client` y se conecta solo si existen `TURSO_DATABASE_URL` y `TURSO_AUTH_TOKEN`. **No hace falta** copiar el ejemplo de Next.js (`export const POST = ...`) de la guía de Vercel: eso es para App Router; este proyecto usa `backend/src/database-connection.js`.

Tras enlazar Turso en vercel.com:

```bash
cd backend
npm install
vercel env pull .env.development.local
```

Para desarrollo local, `bootstrap.js` carga `backend/.env` y luego `backend/.env.development.local` (variables de Turso).

En producción, redeploy y revisá logs: debe aparecer `[database] Using Turso (libsql) for persistent storage.`

Ejemplo con CLI (desde `backend/`):

```bash
cd backend
vercel env add V0_API_KEY production
vercel env add V0_KEYSTORE_SECRET production
```

Copia las mismas claves que usarías en `backend/.env` local (ver `backend/.env.example`).

## 2. Crear y desplegar el proyecto

El **Root Directory** del proyecto en Vercel debe ser `backend` (no la raíz del monorepo). Si no lo configurás, Vercel puede intentar desplegar archivos incorrectos (por ejemplo `src/app.js`) y fallar con *Invalid export*.

En el dashboard: **Project Settings → General → Root Directory** → `backend`.

```bash
cd backend
vercel login
vercel link
# Elegir equipo, nombre del proyecto, y confirmar que el directorio es backend/

vercel deploy          # preview
vercel deploy --prod   # producción
```

Anota la URL de producción, por ejemplo: `https://vibebuilder-api.vercel.app`

### Probar el API desplegado

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "X-Session-Id: 8d6d3a2c-8d6a-4bf2-a0cf-f77a45ef27ab" \
  https://TU-PROYECTO.vercel.app/projects
```

Deberías recibir `200` (lista vacía) o un JSON de proyectos.

## 3. Configurar la app Android (APK)

La URL del backend se inyecta en compilación con la propiedad Gradle `API_BASE_URL`.

### Opción A — `gradle.properties` (recomendado para release)

En la raíz del repo (`VibeBuilder/gradle.properties`), añade:

```properties
API_BASE_URL=https://TU-PROYECTO.vercel.app
```

### Opción B — `local.properties` (solo tu máquina)

```properties
API_BASE_URL=https://TU-PROYECTO.vercel.app
```

`local.properties` está en `.gitignore`.

### Desarrollo local

| Entorno | `API_BASE_URL` típica |
|---------|------------------------|
| Emulador Android + backend en PC | `http://10.0.2.2:3000` usando `-PAPI_BASE_URL=http://10.0.2.2:3000` |
| Dispositivo físico + backend en PC | `http://IP_DE_TU_PC:3000` |
| APK / release contra Vercel | `https://TU-PROYECTO.vercel.app` (sin barra final; default del proyecto: `https://vibe-builder-backend.vercel.app`) |

Generar APK de release en Android Studio: **Build → Generate Signed Bundle / APK → APK**.

O por línea de comandos (con wrapper Gradle):

```bash
./gradlew :app:assembleRelease
```

El APK queda en `app/build/outputs/apk/release/`.

## 4. Integración v0 desde el celular

Con backend en Vercel y `V0_KEYSTORE_SECRET` configurado:

1. Abre la app → pantalla de integración v0.
2. Guarda tu API key (se envía cifrada al backend por `X-Session-Id`).
3. Crea un proyecto y envía un prompt.

Si solo usas `V0_API_KEY` en el servidor (sin keystore por sesión), la generación usará la key del entorno de Vercel.

## Limitaciones sin Turso (SQLite en `/tmp`)

- Sin `TURSO_DATABASE_URL`, cada instancia serverless tiene su propia DB vacía: `GET /projects` devuelve `[]` y los prompts fallan con *project not found*.
- Con la integración Turso enlazada y el código actual desplegado, los datos persisten en la nube.
- Las llamadas a v0 pueden tardar varios minutos; `vercel.json` fija `maxDuration: 300` (requiere plan que lo permita).

## 5. Resolución de problemas

| Síntoma | Qué revisar |
|---------|-------------|
| App no conecta | `API_BASE_URL` en el APK, HTTPS en release, permiso `INTERNET` |
| `401 SESSION_REQUIRED` | La app debe enviar `X-Session-Id` (ya lo hace `HttpVibeBuilderApi`) |
| Generación mock | Falta `V0_API_KEY` o key de sesión en integración v0 |
| `503` en `/integrations/v0` | Falta `V0_KEYSTORE_SECRET` en Vercel |
| Timeout en prompts | Plan Vercel / `maxDuration`; revisar logs: `vercel logs` |

## Cómo funciona en Vercel

Vercel detecta automáticamente `src/server.js` porque llama a `server.listen()`. Ese archivo enruta todas las peticiones (`/projects`, `/integrations/v0`, etc.). No hace falta carpeta `api/`.

```
backend/
  vercel.json         # maxDuration para src/server.js
  src/server.js       # entrada detectada por Vercel (listen)
  src/http-app.js     # router HTTP (antes app.js; renombrado para evitar conflicto con convenciones de Vercel)
  src/bootstrap.js    # init compartido
```

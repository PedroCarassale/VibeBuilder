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
| `DB_PATH` | No | En Vercel, si no se define, se usa `/tmp/vibebuilder.db` (datos **no persistentes** entre cold starts). |

Ejemplo con CLI (desde `backend/`):

```bash
cd backend
vercel env add V0_API_KEY production
vercel env add V0_KEYSTORE_SECRET production
```

Copia las mismas claves que usarías en `backend/.env` local (ver `backend/.env.example`).

## 2. Crear y desplegar el proyecto

El **Root Directory** del proyecto en Vercel debe ser `backend` (no la raíz del monorepo).

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
| Emulador Android + backend en PC | `http://10.0.2.2:3000` (default si no defines nada) |
| Dispositivo físico + backend en PC | `http://IP_DE_TU_PC:3000` |
| APK / release contra Vercel | `https://TU-PROYECTO.vercel.app` (sin barra final) |

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

## Limitaciones importantes (SQLite en serverless)

- En Vercel, SQLite vive en `/tmp` y **puede perderse** cuando la función se recicla (cold start).
- Para un uso real en producción conviene migrar a **Turso**, **Neon** o **Vercel Postgres** (Delivery 2).
- Las llamadas a v0 pueden tardar varios minutos; `vercel.json` fija `maxDuration: 300` (requiere plan que lo permita).

## 5. Resolución de problemas

| Síntoma | Qué revisar |
|---------|-------------|
| App no conecta | `API_BASE_URL` en el APK, HTTPS en release, permiso `INTERNET` |
| `401 SESSION_REQUIRED` | La app debe enviar `X-Session-Id` (ya lo hace `HttpVibeBuilderApi`) |
| Generación mock | Falta `V0_API_KEY` o key de sesión en integración v0 |
| `503` en `/integrations/v0` | Falta `V0_KEYSTORE_SECRET` en Vercel |
| Timeout en prompts | Plan Vercel / `maxDuration`; revisar logs: `vercel logs` |

## Estructura añadida para Vercel

```
backend/
  api/index.js      # handler serverless
  vercel.json       # rewrites + maxDuration
  src/bootstrap.js  # init compartido local + Vercel
```

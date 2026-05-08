# Notas técnicas iniciales - VibeBuilder

## Propósito

Este documento define criterios técnicos para Delivery 1, alineados con la visión del producto y el estado actual del proyecto. El foco es evitar decisiones contradictorias mientras se evoluciona desde una base mockeada hacia un flujo real de generación e iteración de web apps desde Android.

## Arquitectura actual vs arquitectura objetivo

| Dimensión | Arquitectura actual (hoy) | Arquitectura objetivo (Delivery 1 cerrada) |
|---|---|---|
| Frontend Android | UI preliminar con lista de proyectos y chat simulados | UI conectada a backend real, con estados `loading/error/empty/success` consistentes |
| Datos | Hardcoded, sin persistencia real | Persistencia en backend para `Project`, `PromptMessage`, `ProjectVersion` |
| IA / generación | Respuestas fijas simuladas | Orquestación real vía backend hacia proveedor de generación (Vercel v0 SDK) |
| Versionado | No existe versionado funcional | Versionado incremental por proyecto (`v1`, `v2`, ...), con estado por versión |
| Preview | No existe preview funcional | Preview por `previewUrl` en mobile (WebView o URL embebida) |
| Trazabilidad | Sin historial real | Historial de prompts y versiones recuperable entre sesiones |

## Principios de diseño para esta fase

- Priorizar flujo vertical completo sobre cobertura amplia de features.
- Encapsular la dependencia de IA en backend para reducir acoplamiento en Android.
- Diseñar modelos mínimos estables y extenderlos después (no sobre-ingeniería temprana).
- Hacer explícitos estados de carga y error para evitar incertidumbre de usuario.
- Asegurar trazabilidad desde el día 1: cada intento de generación crea una versión registrable.

## Decisiones técnicas iniciales (ADR-lite)

| ID | Decisión | Alternativas consideradas | Por qué se eligió | Impacto |
|---|---|---|---|---|
| ADR-01 | Android consume un backend propio; no llama directamente a proveedores de IA | 1) Android -> v0 directo 2) Android -> backend -> v0 | Seguridad (no exponer claves), control de costos, observabilidad e independencia de proveedor | Se agrega complejidad de backend, pero se gana control técnico y capacidad de evolución |
| ADR-02 | Modelo de datos inicial mínimo con `Project`, `PromptMessage`, `ProjectVersion` | 1) Modelo amplio desde inicio (incluyendo forks/comunidad) 2) Solo `Project` + texto libre | Permite cubrir Delivery 1 completo sin bloquear futuras extensiones | Acelera implementación inicial; requerirá migraciones para Delivery 2/3 |
| ADR-03 | Versionado por proyecto con numeración incremental y estado (`success/failed`) | 1) Sobrescribir estado actual sin historial 2) Versionado solo para éxitos | Preserva trazabilidad y habilita reintentos sin perder contexto | Mayor volumen de datos, pero mejora depuración y experiencia de iteración |
| ADR-04 | Preview basado en `previewUrl` entregada por backend | 1) Render local de archivos 2) Preview remoto por URL | Simplifica app Android y desacopla ejecución de app generada | Dependencia de disponibilidad de URL y compatibilidad WebView |
| ADR-05 | State management orientado a estados explícitos por operación (`idle/loading/success/failed/empty`) | 1) Estados implícitos en UI 2) Un único estado global de pantalla | Reduce errores de UX y facilita pruebas del flujo asíncrono | Requiere disciplina en capa de presentación y contratos claros |
| ADR-06 | Integración con Vercel v0 SDK detrás de adaptador interno de generación | 1) Acoplar servicios al SDK en múltiples módulos 2) Endpoint genérico sin adaptador | Limita impacto de cambios del SDK y habilita multi-proveedor futuro | Costo inicial de abstracción, ahorro alto en mantenimiento futuro |

## Modelo de datos inicial (base Delivery 1)

| Entidad | Campos mínimos | Relación clave | Notas de evolución |
|---|---|---|---|
| `Project` | `id`, `title`, `description`, `ownerId/sessionId`, `currentVersion`, `createdAt`, `updatedAt` | 1 proyecto -> N versiones / N mensajes | Extender en Delivery 2 con metadata y organización |
| `ProjectVersion` | `id`, `projectId`, `versionNumber`, `promptSnapshot`, `status`, `previewUrl`, `providerMeta`, `createdAt` | Cada versión pertenece a un proyecto | En Delivery 2: validaciones de salida, métricas de calidad |
| `PromptMessage` | `id`, `projectId`, `versionId` (nullable), `role`, `content`, `createdAt` | Mensajes cronológicos por proyecto | Mantener acople flexible para mensajes sin versión asociada |

Criterio: modelo pequeño, consistente y persistente antes de agregar entidades de comunidad/forks.

## Estrategia de backend (fase inicial)

- Backend en Node.js + TypeScript como capa de orquestación y persistencia.
- API mínima para Delivery 1: creación/listado de proyectos, envío de prompts, listado de versiones, obtención de preview.
- Contratos de respuesta tipados con errores accionables para Android.
- Idempotencia en operaciones sensibles a reintentos de red (al menos en creación y generación).
- Observabilidad mínima: eventos de crear proyecto, generar versión, fallo de generación, abrir preview, reintento.

## Cierre de ambiguedades tecnicas D1 (normativo)

Esta sección define reglas obligatorias para Delivery 1. Si otro documento contradice estas reglas, prevalece esta sección.

### Politica de `currentVersion` (invariante unico)

- `Project.currentVersionId` puede ser `null` y referencia a `ProjectVersion.id`.
- `currentVersionId` solo puede apuntar a una version `success` del mismo proyecto.
- Las versiones `failed` no actualizan `currentVersionId`.
- Al crear una version `success`, `currentVersionId` se actualiza en la misma transaccion.
- `versionNumber` es incremental y unico por proyecto (`UNIQUE(projectId, versionNumber)`).

Regla operativa: `currentVersion` significa "ultima version usable", no "ultimo intento".

### Contrato canonico de preview

Fuente de verdad:
- La unica fuente valida de preview es `ProjectVersion.previewUrl` para versiones `success`.

Endpoint canonico:
- `GET /projects/:projectId/preview?target=current|version&versionNumber=<int opcional>`

Errores canonicos minimos:
- `404 PROJECT_NOT_FOUND`
- `404 VERSION_NOT_FOUND`
- `409 PREVIEW_NOT_READY`
- `410 PREVIEW_EXPIRED`
- `424 PREVIEW_UNAVAILABLE`
- `400 INVALID_PREVIEW_QUERY`

### Identidad/sesion minima D1

- Todas las requests app -> backend incluyen `X-Session-Id` (UUIDv4).
- `X-Session-Id` se genera en primera apertura y se persiste localmente.
- Sin `X-Session-Id` valido, backend responde `401 SESSION_REQUIRED`.
- No hay login de usuario en D1; identidad anonima por dispositivo.

### Idempotencia y reintentos minimos

Operaciones mutantes con idempotencia obligatoria:
- `POST /projects`
- `POST /projects/:projectId/prompts`

Reglas:
- App envia `X-Idempotency-Key` (UUIDv4) por accion de usuario.
- Reintentos tecnicos reutilizan la misma key.
- Misma key + mismo payload => misma respuesta (replay deterministico).
- Misma key + payload distinto => `409 IDEMPOTENCY_KEY_REUSED`.
- Reintentar solo en timeout o `408/429/5xx` con backoff corto.

### Glosario canonico minimo

- **Vercel v0**: proveedor externo de generacion, consumido solo por backend.
- **Proyecto (`Project`)**: contenedor principal de iteraciones.
- **Version (`ProjectVersion`)**: resultado de un intento de generacion.
- **Version actual (`currentVersion`)**: ultima version `success` utilizable.
- **Iteracion**: prompt nuevo sobre proyecto existente que crea `N+1`.
- **Preview**: representacion accesible por `previewUrl` asociada a version `success`.
- **Sesion D1**: identidad anonima minima por dispositivo (`X-Session-Id`).
- **Idempotencia**: garantia de no duplicar efectos ante reintentos.

## Integración futura con Vercel v0 SDK (plan de evolución)

### Fase A - Sustitución de mocks por proveedor real

- Implementar adaptador `GenerationProvider` en backend con implementación `V0Provider`.
- Guardar en `providerMeta` información mínima no sensible para trazabilidad.
- Convertir respuesta del proveedor al contrato interno de `ProjectVersion`.

### Fase B - Robustez operativa

- Incorporar timeouts y política de retries controlada en backend.
- Clasificar errores (transitorio, validación, proveedor) para mensajes claros en app.
- Registrar métricas de latencia y tasa de `failed`.

### Fase C - Desacoplamiento para futuro multi-proveedor

- Mantener a Android sin conocimiento de v0.
- Agregar selector de proveedor interno por configuración (no expuesto al usuario final en Delivery 1).
- Preparar pruebas de contrato del adaptador para evitar regresiones ante cambios del SDK.

## Manejo de estados en UI (loading/error/empty)

| Escenario | Estado requerido | Criterio de UX |
|---|---|---|
| Cargar proyectos | `loading` -> `success/empty/error` | Mostrar feedback inmediato y acción de reintento en error |
| Crear proyecto | `loading` transitorio + validaciones previas | Evitar doble envío y duplicados |
| Enviar prompt/generar | `loading` bloqueante del submit | Mantener contexto visible del chat durante espera |
| Historial sin versiones | `empty` explícito | Indicar próximo paso recomendado (enviar primer prompt) |
| Preview fallido | `error` con fallback | Permitir reintentar sin perder navegación |

Decisión de criterio: no ocultar fallos técnicos; convertirlos en estados accionables.

## Versionado básico de proyectos

- Cada prompt de iteración genera una nueva `ProjectVersion` (`N+1`) en el mismo proyecto.
- Una versión puede finalizar en `success` o `failed`; ambos resultados se guardan.
- `currentVersion` del proyecto apunta siempre a la última versión exitosa (`success`).
- El historial debe listar al menos número de versión, fecha, estado y prompt asociado.
- Reintentar una generación fallida crea una nueva versión; no sobrescribe intentos previos.

## Deuda técnica aceptada (fase Delivery 1)

| Deuda aceptada | Justificación | Límite para no escalar |
|---|---|---|
| Estrategia de identidad/sesión básica | Prioridad en validar flujo core antes de auth robusta | No avanzar a comunidad/forks sin reforzar autenticación |
| Observabilidad mínima (no full tracing) | Reducir tiempo de salida a flujo E2E funcional | Debe existir logging suficiente para depurar fallos críticos |
| Preview con dependencia fuerte de URL externa | Simplifica implementación inicial | Definir fallback y monitorear tasa de errores de carga |
| Modelo de datos sin entidades sociales | Comunidad y forks están fuera de Delivery 1 | Diseñar migraciones antes de abrir Delivery 3 |
| Manejo inicial de concurrencia con reglas simples | Evitar complejidad prematura | Revisar si aparecen duplicados o orden inconsistente |

## Riesgos técnicos y mitigaciones

| Riesgo | Probabilidad | Impacto | Mitigación |
|---|---|---|---|
| Cambios o inestabilidad en v0 SDK | Media | Alta | Encapsular SDK en adaptador y pruebas de contrato |
| Latencia alta en generación | Alta | Alta | Estados de espera claros, timeouts y reintentos controlados |
| Duplicados por reintentos de red | Media | Media/Alta | Idempotencia en backend + bloqueo de doble submit en app |
| Preview no usable en WebView | Media | Alta | Criterios mínimos de compatibilidad y fallback por URL |
| Inconsistencias de numeración de versiones | Media | Alta | Reglas transaccionales en backend para `versionNumber` |
| Crecimiento de deuda por acelerar entregas | Alta | Media | Revisiones quincenales de deuda y backlog técnico explícito |

## Guardrails para iteraciones futuras

- No introducir features de Delivery 2/3 si rompe foco de flujo core de Delivery 1.
- Cualquier cambio de arquitectura debe indicar qué requisito P0/P1 mejora.
- Si una decisión aumenta acoplamiento con proveedor, debe incluir plan de reversibilidad.
- Ninguna iteración se considera cerrada sin validar persistencia + historial + manejo de error.

## Criterio de coherencia técnica (salida de este documento)

Este documento se considera efectivo si permite que cualquier miembro del equipo implemente nuevas iteraciones manteniendo:

1. Contratos consistentes entre Android, backend y proveedor de generación.
2. Versionado trazable sin pérdida de contexto de prompts.
3. Manejo explícito de estados asíncronos en UX.
4. Evolución incremental de mocks a integración real sin contradicciones de arquitectura.

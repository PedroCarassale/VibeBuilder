# T22 - QA de regresion + RC D1.5 (gate final Delivery 1)

## Alcance
- Ejecutar evidencia automatizada obligatoria del gate final D1.
- Consolidar estado de `REQ-001` a `REQ-011`.
- Determinar si el RC queda aprobado con demo E2E repetible.

## Evidencia automatizada ejecutada
- Backend:
  - Comando: `npm test`
  - Resultado: `PASS (55/55)`
- Android:
  - Comando: `./gradlew.bat :app:lint :app:testDebugUnitTest`
  - Resultado: `PASS (BUILD SUCCESSFUL)`
  - Nota: warning no bloqueante de compatibilidad AGP `8.5.2` con `compileSdk 35`.

## Matriz de cumplimiento REQ (gate D1)

| REQ | Estado | Evidencia |
|---|---|---|
| REQ-001 | PASS | `backend/test/projects.post.test.js` (`POST /projects crea proyecto`) + `backend/test/projects.get.test.js` (`GET /projects devuelve proyectos de la sesion actual`). |
| REQ-002 | PASS | `backend/test/projects.get.test.js` (`GET /projects` lista real, orden y no mezcla sesiones). |
| REQ-003 | PASS | `backend/test/prompts.post.test.js` (`crea PromptMessage y ProjectVersion en success/failed`). |
| REQ-004 | PASS | `backend/test/prompts.post.v0.test.js` + `backend/test/v0-provider.test.js` (integracion proveedor, metadatos y manejo de errores). |
| REQ-005 | FAIL | Hay cobertura backend de preview (`backend/test/preview.get.test.js`), pero falta evidencia manual Android/WebView repetible en esta corrida de RC. |
| REQ-006 | PASS | `backend/test/prompts.post.test.js` (`prompts de seguimiento crea N+1`). |
| REQ-007 | PASS | `backend/test/project-detail.get.test.js` (`versions payload final` y `limita a ultimas 20`). |
| REQ-008 | FAIL | Hay evidencia parcial (`GET /messages` cronologico y tests de repositorio), pero falta prueba de persistencia post-reinicio real de app en esta corrida. |
| REQ-009 | PASS | `app/src/test/java/com/vibebuilder/app/ui/screens/projectdetail/ProjectDetailViewModelTest.kt` (loading/failed/retry y bloqueo doble envio) + `backend/test/prompts.post.test.js`. |
| REQ-010 | PASS | `backend/test/prompts.post.test.js` (`reintento visible crea version N+1 sin duplicar proyecto`). |
| REQ-011 | PASS | `backend/test/projects.post.test.js` (`title invalido -> 400`) + validaciones de envio en `ProjectDetailViewModelTest`. |

### Resultado de aceptacion 1 (`REQ-001` a `REQ-011` en verde)
- Estado: **FAIL**
- Causa: `REQ-005` y `REQ-008` sin evidencia E2E manual Android repetible en esta corrida.

## Estado de demo E2E repetible (aceptacion 2)
- Estado: **FAIL**
- Causa concreta:
  - En este gate se ejecuto solo evidencia automatizada (backend + lint/tests unitarios Android).
  - No se ejecuto ni registro la demo manual completa en emulador/dispositivo real para el flujo:
    `Home -> Crear -> Prompt inicial -> Resultado -> Preview -> Iterar -> Historial -> Reinicio app`.

## Checklist RC consolidado (aceptacion 3)

### Bloque A - Suite automatizada obligatoria
- `npm test`: **PASS**
- `./gradlew.bat :app:lint :app:testDebugUnitTest`: **PASS**
- Resultado bloque A: **PASS**

### Bloque B - Flujo E2E manual repetible D1
- Caso core completo (crear/generar/preview/iterar/historial): **FAIL (no ejecutado en esta corrida)**
- Caso persistencia tras reinicio de app: **FAIL (no ejecutado en esta corrida)**
- Caso fallback de preview/reintento visible: **FAIL (no ejecutado en esta corrida)**
- Resultado bloque B: **FAIL**

### Bloque C - Cierre RC
- Sin bloqueadores P0/P1 en flujo core con evidencia de ejecucion: **FAIL**
- Checklist RC completo aprobado: **FAIL**
- Resultado bloque C: **FAIL**

## Bloqueadores detectados y propuesta

### BQ-22-01 (bloqueador de salida RC)
- Descripcion: Falta evidencia manual repetible Android para `REQ-005` y `REQ-008`, por lo que el gate de salida no puede cerrarse en verde.
- Impacto: Delivery 1 no alcanza criterio de RC aprobado en esta corrida.
- Propuesta concreta (proximo paso):
  1. Ejecutar checklist manual completo en emulador y 1 dispositivo real.
  2. Registrar para cada caso: fecha/hora, entorno, dispositivo, PASS/FAIL y evidencia (capturas/logs).
  3. Reabrir este T22 y actualizar estados de Bloque B/C a PASS solo con evidencia trazable.

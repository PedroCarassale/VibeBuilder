# Epic 5 - Hardening minimo D1 (D1.5)

## T18 - Sesion minima D1 (`X-Session-Id`)
**Descripcion:** identidad anonima por dispositivo.  
**Aceptacion:**
- header obligatorio en requests
- persistido localmente
- backend rechaza requests sin sesion valida

## T19 - Idempotencia (`X-Idempotency-Key`)
**Descripcion:** evitar duplicados por reintentos/red inestable.  
**Aceptacion:**
- obligatorio en `POST /projects` y `POST /projects/:projectId/prompts`
- replay deterministico con misma key+payload
- `409` si misma key con payload distinto

## T20 - Politica de `currentVersionId`
**Descripcion:** fijar invariante canonico.  
**Aceptacion:**
- apunta siempre a ultima version `success`
- version `failed` no actualiza `currentVersionId`
- actualizacion transaccional al cerrar `success`

## T21 - Telemetria minima D1
**Descripcion:** eventos para KPIs y diagnosticos.  
**Aceptacion:**
- eventos: crear, generar, fallar, preview, iterar
- logs con correlacion por `projectId/versionId`
- dashboard o reporte basico para seguimiento

## T22 - QA de regresion + RC
**Descripcion:** gate final de Delivery 1.  
**Aceptacion:**
- REQ-001 a REQ-011 en verde
- demo E2E repetible sin bloqueadores
- checklist RC completo aprobado

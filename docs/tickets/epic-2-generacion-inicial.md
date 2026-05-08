# Epic 2 - Prompt inicial y generacion real (D1.2)

## T6 - Backend: Enviar prompt (`POST /projects/:projectId/prompts`)
**Descripcion:** recibe prompt y crea intento de generacion.  
**Aceptacion:**
- crea `PromptMessage`
- crea `ProjectVersion` con estado final `success/failed`
- valida existencia de proyecto

## T7 - Backend: Adaptador proveedor IA (v0)
**Descripcion:** encapsular llamada al proveedor en servicio interno.  
**Aceptacion:**
- Android no conoce proveedor
- guarda `providerMeta` no sensible
- maneja timeout/errores controlados

## T8 - Android: Chat con envio real
**Descripcion:** conectar envio de prompt al backend.  
**Aceptacion:**
- estados `idle/loading/success/failed`
- bloqueo de doble tap durante `loading`
- error visible con accion de reintento

## T9 - Persistencia de mensajes por proyecto
**Descripcion:** guardar y recuperar historial de chat real.  
**Aceptacion:**
- mensajes en orden cronologico
- no duplicados al reintentar
- persisten al reabrir app

## T10 - QA E2E D1.2
**Descripcion:** validar primer ciclo de generacion real.  
**Aceptacion:**
- prompt inicial crea v1
- exito y fallo cubiertos
- sin duplicados por doble envio

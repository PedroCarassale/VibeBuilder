# Epic 4 - Iteracion e historial (D1.4)

## T14 - Backend: Versionado incremental `N+1`
**Descripcion:** cada prompt de seguimiento crea nueva version.  
**Aceptacion:**
- `UNIQUE(projectId, versionNumber)`
- no sobrescribe versiones previas
- mantiene vinculo prompt <-> version

## T15 - Backend: Historial de versiones (`GET /projects/:projectId/versions`)
**Descripcion:** listar historial minimo por proyecto.  
**Aceptacion:**
- incluye `versionNumber`, `status`, `createdAt`, `promptSnapshot`
- soporta recuperacion estable de ultimas 20
- orden consistente

## T16 - Android: UI de historial
**Descripcion:** pantalla/listado de versiones y estado.  
**Aceptacion:**
- muestra versiones del proyecto
- permite identificar ultima `success`
- estados vacios/error manejados

## T17 - QA E2E D1.4
**Descripcion:** validar iteracion completa.  
**Aceptacion:**
- prompt de seguimiento crea `N+1`
- historial refleja cambios
- persistencia tras reinicio de app

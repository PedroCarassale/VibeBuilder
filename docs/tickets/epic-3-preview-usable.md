# Epic 3 - Preview usable (D1.3)

## T11 - Backend: Resolver preview canonico
**Descripcion:** `GET /projects/:projectId/preview?target=current|version&versionNumber=N`.  
**Aceptacion:**
- usa `ProjectVersion.previewUrl` como fuente
- errores canonicos: `PREVIEW_NOT_READY`, `PREVIEW_EXPIRED`, `PREVIEW_UNAVAILABLE`
- respuesta consistente para app

## T12 - Android: Pantalla de preview
**Descripcion:** renderizar preview en WebView/embebido.  
**Aceptacion:**
- abre preview para version `success`
- fallback claro si falla carga
- navegacion Preview -> Chat sin perder contexto

## T13 - QA E2E D1.3
**Descripcion:** validar flujo de preview en dispositivo real.  
**Aceptacion:**
- preview usable en caso feliz
- fallback funcional en URL invalida
- smoke test en al menos 1 dispositivo Android

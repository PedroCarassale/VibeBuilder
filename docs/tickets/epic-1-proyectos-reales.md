# Epic 1 - Proyectos reales (D1.1)

## T1 - Backend: Crear proyecto (`POST /projects`)
**Descripcion:** endpoint para crear proyecto con `title` obligatorio y `description` opcional.  
**Aceptacion:**
- retorna `201` con `projectId`
- valida `title` no vacio
- guarda en DB asociado a sesion (`X-Session-Id`)

## T2 - Backend: Listar proyectos (`GET /projects`)
**Descripcion:** endpoint para listar proyectos de la sesion actual.  
**Aceptacion:**
- retorna solo proyectos de esa sesion
- orden por `updatedAt` desc
- maneja vacio con lista vacia (no error)

## T3 - Android: Home consume listado real
**Descripcion:** reemplazar mocks por `GET /projects`.  
**Aceptacion:**
- estados `loading/empty/error/success`
- se ve nuevo proyecto al volver desde crear
- no quedan datos hardcodeados en Home

## T4 - Android: Crear proyecto desde UI
**Descripcion:** formulario con validacion minima.  
**Aceptacion:**
- bloquea submit con titulo vacio
- muestra error de validacion en UI
- crea proyecto y navega a detalle/chat

## T5 - QA E2E D1.1
**Descripcion:** prueba de punta a punta de proyectos.  
**Aceptacion:**
- crear proyecto funciona
- cerrar/reabrir app mantiene datos
- error de red muestra accion de reintento

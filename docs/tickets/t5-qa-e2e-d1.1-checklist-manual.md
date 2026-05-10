# T5 - QA E2E D1.1 (checklist manual ejecutable)

## Precondiciones
- Backend local arriba en `http://10.0.2.2:3000`.
- App Android instalada en emulador/dispositivo.
- Sesion limpia opcional: desinstalar/reinstalar app para iniciar sin cache visual.

## Caso 1 - Crear proyecto funciona
1. Abrir Home.
2. Tocar `Crear proyecto`.
3. Completar `Nombre del proyecto` con texto valido.
4. Tocar `Crear proyecto`.
5. Volver a Home.
6. Verificar que el proyecto creado aparece en la lista.

Resultado esperado:
- Se crea sin errores y se ve en Home.

## Caso 2 - Cerrar/reabrir app mantiene datos
1. Con al menos un proyecto visible en Home, cerrar app completamente (swipe away/force stop).
2. Reabrir app.
3. Verificar que Home vuelve a listar el/los mismos proyectos.

Resultado esperado:
- No se pierden proyectos ya persistidos.

## Caso 3 - Error de red muestra accion de reintento
1. Desconectar backend o cortar red del emulador.
2. Abrir/reabrir Home para forzar carga.
3. Verificar que aparece estado de error con boton `Reintentar`.
4. Restablecer backend/red.
5. Tocar `Reintentar`.
6. Verificar que Home carga proyectos correctamente.

Resultado esperado:
- Error visible con accion `Reintentar`.
- El reintento recupera la lista cuando vuelve la conectividad.

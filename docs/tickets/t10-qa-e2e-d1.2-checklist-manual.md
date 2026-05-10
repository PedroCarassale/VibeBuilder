# T10 - QA E2E D1.2 (checklist manual ejecutable)

## Alcance
- Validar el primer ciclo real de generacion en Android para Epic 2 (D1.2).
- Complementar pruebas automaticas con verificacion manual de UX visible.

## Precondiciones
- Backend local arriba en `http://10.0.2.2:3000`.
- App Android instalada en emulador/dispositivo.
- Tener una sesion limpia opcional (reiniciar app) para iniciar sin ruido.

## Caso 1 - Prompt inicial crea v1
1. Abrir Home y crear un proyecto nuevo.
2. Entrar al detalle del proyecto.
3. Escribir un prompt inicial valido.
4. Tocar enviar una sola vez.
5. Esperar estado final (`success` o `failed`).
6. Ir a historial/chat y verificar que existe una version `1` asociada al prompt enviado.

Resultado esperado:
- El primer envio crea exactamente `v1`.
- El prompt queda visible en historial del proyecto.

## Caso 2 - Camino de exito y camino de fallo
1. Con backend operativo, enviar un prompt valido.
2. Verificar estado de exito en UI (sin error visible).
3. Forzar fallo (apagar backend o desconectar red).
4. Enviar otro prompt valido.
5. Verificar que la UI muestra error entendible y accion de reintento.

Resultado esperado:
- Se observa flujo de exito con envio completado.
- Se observa flujo de fallo con mensaje accionable.

## Caso 3 - Sin duplicados por doble envio/reintento visible
1. Escribir un prompt valido.
2. Tocar enviar repetidamente de forma rapida mientras aparece loading.
3. Verificar que el boton de enviar queda bloqueado durante loading.
4. Si hubo fallo, tocar `Reintentar` una sola vez.
5. Verificar que no aparecen mensajes/versiones duplicadas por una misma accion visible.

Resultado esperado:
- No hay doble envio mientras loading esta activo.
- Un reintento visible produce un unico nuevo intento.
- No se crean duplicados en chat/historial por taps repetidos del usuario.

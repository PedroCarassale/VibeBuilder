# T13 - QA E2E D1.3 (checklist manual ejecutable)

## Alcance
- Validar preview usable del Epic 3 en Android.
- Cubrir caso feliz, fallback por URL inválida/falla y smoke test en dispositivo real.

## Precondiciones
- Backend local arriba en `http://10.0.2.2:3000`.
- App Android instalada en emulador y en al menos 1 dispositivo real.
- Existe un proyecto con al menos una versión `success` y `previewUrl` válida.

## Caso 1 - Preview usable (caso feliz)
1. Abrir la app y entrar al detalle del proyecto.
2. Ir a la pestaña `Preview`.
3. Verificar que la versión actual `success` carga en WebView.
4. Volver a la pestaña `Prompt`.
5. Confirmar que el contexto del proyecto/mensajes se mantiene.

Resultado esperado (PASS):
- Se renderiza contenido utilizable en mobile.
- Navegar `Preview -> Prompt` no pierde estado visible del proyecto.

## Caso 2 - Fallback funcional (URL inválida/falla)
1. Usar una versión `success` con `previewUrl` inválida **o** cortar red para forzar error de carga.
2. Abrir pestaña `Preview`.
3. Verificar que aparece estado de error o no disponible (fallback visible).
4. Si aparece botón `Reintentar`, tocarlo tras restaurar red/URL válida.
5. Confirmar que la vista intenta cargar nuevamente.

Resultado esperado (PASS):
- La UI no queda en blanco/crash; muestra fallback accionable.
- `Reintentar` ejecuta un nuevo intento de carga cuando aplica.

## Caso 3 - Smoke test en dispositivo real
1. Instalar build debug en dispositivo Android físico.
2. Repetir `Caso 1` completo.
3. Repetir `Caso 2` completo (forzando red offline/online).
4. Registrar modelo del dispositivo, versión Android y resultado.

Resultado esperado (PASS):
- El flujo de preview funciona también en dispositivo real.
- No hay bloqueadores críticos en navegación o carga de preview.

## Registro sugerido
- Fecha/hora:
- Entorno: (Emulador / Dispositivo real)
- Dispositivo + Android:
- Caso 1: PASS/FAIL
- Caso 2: PASS/FAIL
- Caso 3: PASS/FAIL
- Notas / evidencia (captura breve):

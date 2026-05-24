# T17 - QA E2E D1.4 (checklist manual ejecutable)

## Alcance
- Validar el flujo completo de iteracion e historial del Epic 4 (D1.4).
- Cubrir evidencia automatica disponible y verificacion manual para persistencia tras reinicio de app.

## Evidencia automatica ejecutada
- Backend (suite completa):
  - Comando: `npm test`
  - Resultado: `PASS (43/43)`
- Backend (foco D1.4):
  - Comando: `node --test ./test/prompts.post.test.js ./test/project-detail.get.test.js`
  - Resultado: `PASS (10/10)`
  - Cubre explicitamente:
    - `POST /projects/:projectId/prompts de seguimiento crea N+1 y mantiene vinculo prompt-version`
    - `GET /projects/:projectId/versions devuelve payload final y orden consistente`
    - `GET /projects/:projectId/versions limita a ultimas 20 versiones`
    - `GET /projects/:projectId/messages devuelve mensajes cronologicos`

## Limitaciones del entorno local (Android)
- `gradlew`/`gradlew.bat`: no presentes en el repo actual.
- `gradle` global: no instalado en este entorno (`CommandNotFoundException`).
- Impacto: no se pudieron ejecutar `lint`/tests de Android desde CLI en esta corrida.

## Precondiciones para QA manual Android
- Backend local arriba en `http://10.0.2.2:3000`.
- App Android instalada en emulador o dispositivo.
- Contar con un proyecto existente con al menos una version previa (`N`).

## Caso 1 - Prompt de seguimiento crea `N+1`
1. Abrir la app y entrar al detalle de un proyecto con version existente.
2. Ir a la pestaña de prompt/chat.
3. Enviar un prompt de seguimiento valido.
4. Esperar estado final del envio (`success` o `failed`).
5. Ir a `History`.
6. Verificar que aparece una nueva version con numero incremental (`N+1`) y asociada al nuevo prompt.

Resultado esperado (PASS):
- Se crea exactamente una nueva version incremental por envio visible.
- El prompt enviado queda vinculado a la nueva version.

## Caso 2 - Historial refleja cambios
1. Con el mismo proyecto, enviar al menos 2 prompts adicionales.
2. Abrir `History`.
3. Confirmar orden consistente (mas reciente primero o criterio definido por UI, pero estable).
4. Verificar presencia de datos minimos por item: numero de version, estado y fecha.
5. Validar que el contenido/resumen del prompt asociado cambia segun cada iteracion.

Resultado esperado (PASS):
- El historial refleja cada nueva iteracion sin duplicados.
- Los estados y metadatos son coherentes con lo ejecutado.

## Caso 3 - Persistencia tras reinicio de app
1. Tras completar los casos 1 y 2, cerrar completamente la app (swipe away/force stop).
2. Abrir nuevamente la app.
3. Volver al mismo proyecto.
4. Revisar `History` y el chat/mensajes del proyecto.
5. Confirmar que siguen visibles las versiones y mensajes previos, en orden consistente.

Resultado esperado (PASS):
- No se pierden mensajes/versiones tras reinicio.
- Se conserva el estado persistido del proyecto y su historial.

## Registro sugerido de ejecucion
- Fecha/hora:
- Entorno: (Emulador / Dispositivo real)
- Dispositivo + Android:
- Caso 1: PASS/FAIL
- Caso 2: PASS/FAIL
- Caso 3: PASS/FAIL
- Evidencia (capturas / logs):

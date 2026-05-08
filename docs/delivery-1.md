# Delivery 1 - Plan ejecutable por incrementos

## Objetivo de Delivery 1 (1 frase)

Entregar un flujo funcional end-to-end en Android para **crear proyecto -> enviar prompt inicial -> generar web app real -> previsualizar -> iterar -> ver historial basico persistido**.

## Alcance operativo de esta entrega

- **Incluye**: REQ-001 a REQ-011 como base funcional y de confiabilidad minima.
- **No incluye**: comunidad, forks, publicacion, edicion avanzada de codigo, colaboracion en tiempo real.
- **Regla de foco**: si una tarea no mejora directamente el flujo core, pasa a backlog de Delivery 2+.

## Cadencia sugerida

- Incrementos semanales, verticales y demostrables.
- Cada incremento cierra con: demo interna + checklist de aceptacion + pruebas minimas en verde.
- No abrir siguiente incremento con bloqueadores criticos del anterior.

## Trazabilidad y medicion D1 (REQ -> Incremento -> Prueba -> KPI)

### Regla de cobertura

- Esta matriz usa unicamente REQs existentes (`REQ-001` a `REQ-012`) definidos en `docs/product-requirements.md`.
- El cierre de Delivery 1 se evalua con `REQ-001` a `REQ-011`.
- `REQ-012` queda deferred y no bloquea salida de D1.

### KPIs de referencia D1

- `KPI-D1-01`: Activacion de valor (`>= 60%`).
- `KPI-D1-02`: Tiempo mediano a primer preview (`<= 10 min`).
- `KPI-D1-03`: Iteracion efectiva (`>= 35%`).
- `KPI-D1-04`: Confiabilidad tecnica de generacion (`>= 90%`).
- `KPI-D1-05`: Retencion de 7 dias (`>= 25%`).
- `KPI-D1-06`: Integridad de persistencia (`>= 99%`).
- `KPI-D1-07`: Duplicacion por reintento (`= 0%`).

| REQ | Incremento D1.x | Prueba de aceptacion | KPI asociado |
|---|---|---|---|
| REQ-001 | D1.1 | Crear proyecto con titulo valido; persiste con ID y aparece en Home tras recarga/reapertura. | KPI-D1-01, KPI-D1-06 |
| REQ-002 | D1.1 | Home lista proyectos desde backend y refleja altas sin mocks. | KPI-D1-01, KPI-D1-06 |
| REQ-003 | D1.2 | Prompt inicial crea v1 con estado final `success/failed`. | KPI-D1-01, KPI-D1-04 |
| REQ-004 | D1.2 | Backend invoca proveedor real y guarda metadatos/artefactos. | KPI-D1-04 |
| REQ-005 | D1.3 | Preview renderiza contenido usable; error de carga tiene fallback accionable. | KPI-D1-02, KPI-D1-04 |
| REQ-006 | D1.4 | Prompt de seguimiento crea version `N+1` en el mismo proyecto. | KPI-D1-03, KPI-D1-04 |
| REQ-007 | D1.4 | Historial muestra numero/fecha/estado/prompt; recupera al menos 20 versiones. | KPI-D1-03, KPI-D1-06 |
| REQ-008 | D1.4 | Mensajes persisten en orden y sin duplicados tras reabrir app. | KPI-D1-05, KPI-D1-06 |
| REQ-009 | D1.2 + D1.5 | Estados correctos, doble envio bloqueado, error + reintento visible. | KPI-D1-04, KPI-D1-02 |
| REQ-010 | D1.5 | Reintento tras `failed` crea nueva version sin duplicar proyecto. | KPI-D1-04, KPI-D1-07 |
| REQ-011 | D1.1 + D1.5 | Validaciones bloquean envio invalido sin llamada backend. | KPI-D1-04, KPI-D1-07 |
| REQ-012 | Deferred (D2) | Edicion de metadata post-creacion (fuera de gate D1). | No aplica a cierre D1 |

### Criterio de cumplimiento de D1

Delivery 1 se considera cumplido cuando:
1. Todas las pruebas de aceptacion de `REQ-001` a `REQ-011` estan en verde.
2. No hay bloqueadores abiertos en el flujo core (crear, generar, preview, iterar, historial).
3. Se cumplen simultaneamente las metas KPI-D1-01 a KPI-D1-07 en la ventana acordada.

## Contrato transversal D1 (sesion + idempotencia)

Aplica a todos los incrementos D1.1-D1.5.

### Identidad minima

- Toda request app -> backend incluye `X-Session-Id` (UUIDv4 persistido localmente).
- Sin `X-Session-Id` valido, backend responde `401 SESSION_REQUIRED`.

### Idempotencia minima

- Operaciones mutantes (`POST /projects`, `POST /projects/:id/prompts`) requieren `X-Idempotency-Key`.
- Reintentos tecnicos deben reutilizar la misma key.
- Reuso de key con payload distinto debe fallar con `409 IDEMPOTENCY_KEY_REUSED`.

### Criterio de aceptacion transversal

- [ ] No hay duplicados de proyecto/version por reconexion o doble tap.
- [ ] Reintentos tecnicos preservan respuesta deterministica.

---

## D1.1 - Fundacion real de proyectos (Semana 1)

### Funcionalidades

- Reemplazar datos hardcodeados por listado real de proyectos desde backend.
- Crear proyecto con `titulo` obligatorio y `descripcion` opcional.
- Validaciones minimas en app (`titulo` no vacio, limites de longitud definidos).
- Estados de UI para lista/creacion: `loading`, `empty`, `error`.

### Criterios de aceptacion

- [ ] El usuario crea un proyecto y recibe ID unico persistido.
- [ ] Al volver a Home, el proyecto aparece en la lista sin mocks.
- [ ] Cerrar/reabrir app no pierde proyectos ya persistidos.
- [ ] Si `titulo` es invalido, no se llama al backend y se muestra validacion.
- [ ] UI maneja carga, vacio y error de forma visible.

### Pruebas minimas requeridas

- [ ] Test unitario de validacion de formulario de creacion.
- [ ] Test de API/repository para crear y listar proyectos.
- [ ] Test E2E corto: Home -> Crear proyecto -> volver a Home y ver proyecto.
- [ ] Caso de error de red al listar proyectos (mensaje + accion de reintento).

### Riesgos

- Inconsistencia entre modelo Android y contrato backend.
- Duplicados por reintentos sin idempotencia.
- Persistencia parcial (crea en backend pero no refleja en UI).

### Dependencias

- Endpoint `POST /projects`.
- Endpoint `GET /projects`.
- Modelo minimo `Project` en backend y DB.
- Estrategia basica de sesion/usuario para asociar proyectos.

---

## D1.2 - Generacion inicial real (Semana 2)

### Funcionalidades

- Enviar prompt inicial desde chat del proyecto.
- Integrar backend con proveedor real de generacion (v0 SDK) y crear `ProjectVersion` v1.
- Persistir mensajes de chat (`PromptMessage`) asociados al proyecto.
- Exponer estados de generacion: `idle`, `loading`, `success`, `failed`.

### Criterios de aceptacion

- [ ] Proyecto sin versiones acepta prompt inicial valido.
- [ ] El prompt queda persistido en historial de mensajes.
- [ ] Se crea version 1 con estado final `success` o `failed`.
- [ ] UI bloquea doble envio mientras hay `loading`.
- [ ] Si falla generacion, el usuario ve error entendible y accion de reintento.

### Pruebas minimas requeridas

- [ ] Test unitario del state machine de envio (`idle/loading/success/failed`).
- [ ] Test de integracion backend: prompt inicial crea version y metadatos.
- [ ] Test E2E: crear proyecto -> enviar prompt -> recibir estado final.
- [ ] Test de no-duplicacion ante doble tap rapido en boton enviar.

### Riesgos

- Latencia alta del proveedor de IA.
- Cambios de API/proveedor o timeout.
- Costos por reintentos descontrolados.

### Dependencias

- Endpoint `POST /projects/:id/prompts`.
- Servicio backend de orquestacion IA (encapsulado).
- Modelo `ProjectVersion` y `PromptMessage`.
- Politica de timeout/retry controlada en backend.

---

## D1.3 - Preview utilizable en mobile (Semana 3)

### Funcionalidades

- Pantalla de preview para versiones `success`.
- Render de `previewUrl` en WebView (o URL embebida definida).
- Manejo de errores de carga con fallback de reintento.
- Acceso rapido a preview desde chat/detalle de proyecto.

### Criterios de aceptacion

- [ ] Version `success` con `previewUrl` abre contenido util en mobile.
- [ ] Si la URL falla, UI muestra estado de error accionable.
- [ ] Se puede volver al chat sin perder contexto del proyecto.
- [ ] El tiempo de apertura de preview es aceptable para demo interna.

### Pruebas minimas requeridas

- [ ] Test de navegacion: Chat -> Preview -> Chat.
- [ ] Test de render basico de WebView con URL valida.
- [ ] Test de fallback en URL invalida/no disponible.
- [ ] Smoke test manual en al menos 1 dispositivo real Android.

### Riesgos

- Preview no compatible con WebView.
- Problemas de CORS/certificados/redireccion.
- Experiencia lenta que afecta la percepcion de valor.

### Dependencias

- Endpoint canonico: `GET /projects/:id/preview?target=current|version&versionNumber=N`.
- Fuente de preview: `ProjectVersion.previewUrl` de versiones `success`.
- Errores esperados y manejados por UI: `PREVIEW_NOT_READY`, `PREVIEW_EXPIRED`, `PREVIEW_UNAVAILABLE`.
- Politica de seguridad de carga web en Android (HTTPS).

---

## D1.4 - Iteracion y historial basico (Semana 4)

### Funcionalidades

- Enviar prompts de seguimiento sobre proyecto existente.
- Crear nuevas versiones numeradas (`N+1`) por cada iteracion.
- Vista de historial basico de versiones (numero, fecha, estado, prompt).
- Persistencia y recuperacion de mensajes en orden cronologico sin duplicados.

### Criterios de aceptacion

- [ ] Desde una version previa, el usuario envia prompt de seguimiento.
- [ ] Se crea nueva version `N+1` asociada al mismo proyecto.
- [ ] Historial lista versiones con campos minimos requeridos.
- [ ] Al reabrir app, mensajes e historial permanecen consistentes.
- [ ] Se soporta recuperacion estable de al menos 20 versiones.

### Pruebas minimas requeridas

- [ ] Test de integracion: prompt de seguimiento crea version consecutiva.
- [ ] Test de orden cronologico e integridad de mensajes.
- [ ] Test E2E completo: crear -> generar -> preview -> iterar -> historial.
- [ ] Test de regresion: iterar no rompe preview de la ultima version exitosa.

### Riesgos

- Inconsistencias de numeracion de versiones.
- Duplicados/desorden de mensajes por concurrencia.
- Historial incompleto bajo fallas de red.

### Dependencias

- Endpoint `GET /projects/:id/versions`.
- Contrato claro entre `Project`, `ProjectVersion` y `PromptMessage`.
- Estrategia de ordenamiento e idempotencia en backend.

---

## D1.5 - Hardening y cierre a release candidate (Semana 5)

### Funcionalidades

- Reintento de generacion fallida sin crear proyectos duplicados.
- Validaciones de entrada completas en crear proyecto y prompts.
- Mejora de mensajes de error y estados de carga en todos los pasos core.
- Instrumentacion minima de eventos: crear, generar, fallar, previsualizar, iterar.
- Limpieza de deuda tecnica critica para estabilidad de demo repetible.

### Criterios de aceptacion

- [ ] Reintentar una version `failed` crea nueva version en el mismo proyecto.
- [ ] No hay bloqueadores P0/P1 abiertos en el flujo core.
- [ ] Casos de error frecuentes tienen mensaje accionable y salida clara.
- [ ] Demo E2E pasa de forma repetible en entorno objetivo.
- [ ] Se registra telemetria minima para diagnosticar fallos.

### Pruebas minimas requeridas

- [ ] Regression suite del flujo core (happy path + fallas comunes).
- [ ] Test E2E de reintento despues de `failed`.
- [ ] Smoke test de persistencia tras cerrar/reabrir app.
- [ ] Prueba manual guiada con checklist de demo final completa.

### Riesgos

- Quedar en "funciona en demo puntual" pero no en repeticion.
- Bugs de borde por reintentos y red intermitente.
- Acumulacion de deuda tecnica antes del corte.

### Dependencias

- Definicion de ambiente estable para QA/demo.
- Logs/observabilidad minima habilitados.
- Criterios de severidad para bug triage (bloqueador, mayor, menor).

---

## Checklist de demo final de Delivery 1

### Flujo funcional obligatorio

- [ ] Abrir app y ver Home con proyectos reales (sin hardcode).
- [ ] Crear proyecto con datos validos.
- [ ] Entrar a chat del proyecto y enviar prompt inicial.
- [ ] Esperar resultado de generacion real (`success` o `failed`).
- [ ] Si `success`, abrir preview y validar contenido util.
- [ ] Enviar prompt de seguimiento para iterar.
- [ ] Confirmar creacion de nueva version `N+1`.
- [ ] Abrir historial y verificar versiones con prompt/estado/fecha.
- [ ] Cerrar y reabrir app; validar persistencia de proyecto, mensajes y versiones.

### Manejo de fallos obligatorio

- [ ] Simular fallo de generacion y validar mensaje + reintento.
- [ ] Confirmar que el reintento no crea proyecto duplicado.
- [ ] Simular falla de carga de preview y validar fallback.
- [ ] Simular error de red temporal y validar recuperacion visible.

---

## Definicion de Release Candidate (RC) - Delivery 1

Se considera **Release Candidate de Delivery 1** cuando se cumple todo lo siguiente:

- [ ] Flujo core completo operativo de punta a punta en Android con backend real.
- [ ] Requisitos P0 cerrados y P1 criticos del flujo principal cerrados.
- [ ] Sin bugs bloqueadores abiertos en crear, generar, preview, iterar, historial.
- [ ] Pruebas minimas de cada incremento ejecutadas y en verde.
- [ ] Demo final ejecutable por cualquier miembro del equipo siguiendo checklist.
- [ ] Telemetria/logs minimos disponibles para investigar fallos.
- [ ] Riesgos residuales documentados con plan de mitigacion para Delivery 2.

---

## Gobernanza semanal (operativa)

### Ritual de inicio de semana

- [ ] Confirmar objetivo del incremento y limites de alcance.
- [ ] Revisar dependencias externas y bloqueos potenciales.
- [ ] Acordar criterios de aceptacion y pruebas de salida de la semana.

### Ritual de cierre de semana

- [ ] Ejecutar demo interna del incremento completo.
- [ ] Registrar brechas contra criterios de aceptacion.
- [ ] Actualizar backlog: mover no-critico a Delivery 2+.
- [ ] Confirmar readiness para abrir siguiente incremento.

### Regla de decision rapida

- [ ] Si una tarea no mejora el flujo core de Delivery 1, no entra al sprint actual.


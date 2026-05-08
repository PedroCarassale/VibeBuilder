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

- Endpoint `GET /projects/:id/preview` (o entrega equivalente de `previewUrl`).
- Version `success` con URL disponible y estable.
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


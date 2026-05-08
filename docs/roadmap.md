# Roadmap de producto - VibeBuilder

## Resumen ejecutivo

VibeBuilder debe avanzar en iteraciones cortas que demuestren valor real al usuario desde mobile: pasar de una experiencia mockeada a un flujo funcional de **crear -> generar -> previsualizar -> iterar -> guardar historial**.  
La prioridad del roadmap se define por dos criterios combinados:

1. **Valor al usuario**: primero lo que permite validar el resultado desde el celular sin saber programar.
2. **Dependencia técnica**: primero lo que habilita capacidades futuras (backend, persistencia, contrato de generación, versionado).

Este roadmap organiza la ejecución en cuatro fases progresivas, con hitos de demo/checkpoint y criterios explícitos de salida para decidir el avance entre fases sin perder foco.

## Tabla principal de fases

| Fase | Horizonte tentativo | Objetivo | Entregables demostrables | Riesgos principales | Criterio de salida |
|---|---|---|---|---|---|
| **Fase 0 - Estado actual** | **Corto plazo (base inmediata)** | Consolidar diagnóstico real del producto y alinear al equipo sobre alcance Delivery 1. | - Flujo demo actual documentado (Home mock -> Crear proyecto -> Chat simulado).<br>- Brechas priorizadas entre estado actual y valor objetivo.<br>- Contrato funcional mínimo del flujo E2E definido para ejecución. | - Falsa sensación de progreso por UI sin backend real.<br>- Scope creep hacia features de Delivery 2/3 antes de cerrar núcleo. | Existe acuerdo explícito de equipo sobre: alcance inmediato, fuera de alcance, dependencias críticas y definición de éxito de Delivery 1. |
| **Fase 1 - Delivery 1 funcional** | **Corto plazo (iteraciones de validación núcleo)** | Validar el valor central: crear y modificar una web app desde Android mediante prompts con datos reales. | - Creación/listado de proyectos persistidos.<br>- Envío de prompt inicial y de seguimiento con generación real vía backend.<br>- Preview funcional de versiones exitosas en mobile.<br>- Historial básico de mensajes y versiones persistido.<br>- Estados de carga/error + reintento en fallos. | - Latencia/fallos del proveedor de generación.<br>- Preview no utilizable en WebView.<br>- Inconsistencias de persistencia en mensajes/versiones. | Un usuario puede completar de punta a punta: crear proyecto, generar versión, ver preview, iterar con nuevo prompt y consultar historial real sin datos hardcodeados. |
| **Fase 2 - Mejora de control y calidad** | **Medio plazo** | Reducir fricción operativa y elevar confiabilidad para que el producto se sienta estable y controlable. | - Estructura de proyecto más clara y metadata editable (renombrado/organización básica).<br>- Flujo de regeneración robusto y validaciones de salida.<br>- Mejoras de preview (fallbacks, resiliencia, UX de errores).<br>- Observabilidad operativa mínima (eventos y errores clave). | - Crecer complejidad sin mejorar activación/retención.<br>- Coste técnico de hardening sin métricas accionables.<br>- Deuda técnica por contratos débiles entre app/backend. | KPIs de confiabilidad y experiencia alcanzan umbrales internos definidos (éxito de generación, tiempo a preview, iteración efectiva) y las demos muestran estabilidad repetible. |
| **Fase 3 - Comunidad y forks** | **Largo plazo** | Expandir valor de creación individual a colaboración asíncrona y reutilización de proyectos. | - Biblioteca pública/compartida de proyectos.<br>- Apertura de proyectos de terceros.<br>- Flujo de fork con ownership y atribución básica.<br>- Iteración sobre proyectos forkeados manteniendo historial y origen. | - Moderación y calidad del contenido compartido.<br>- Complejidad de permisos/visibilidad/atribución.<br>- Incremento de costos de almacenamiento y consulta. | Un usuario puede descubrir un proyecto público, forkearlo, iterarlo con prompts y conservar trazabilidad del origen sin romper su experiencia individual de creación. |

## Vista temporal tentativa (sin fechas rígidas)

### Corto plazo

- Completar Fase 0 y Fase 1 en ciclos breves orientados a demos de flujo completo.
- Prioridad máxima: cerrar el loop de valor central con backend y persistencia real.
- Regla de priorización: no incorporar funcionalidades de comunidad/forks hasta cerrar criterio de salida de Fase 1.

### Medio plazo

- Ejecutar Fase 2 para transformar un flujo funcional en una experiencia robusta y repetible.
- Prioridad: confiabilidad, control de proyecto, validación de salidas y mejor gestión de errores.
- Regla de priorización: cada mejora debe demostrar impacto en activación, tiempo a resultado o tasa de iteración.

### Largo plazo

- Ejecutar Fase 3 para habilitar crecimiento por red: descubrimiento, reutilización y forks.
- Prioridad: diseño correcto de ownership, atribución y experiencia de exploración.
- Regla de priorización: solo avanzar cuando la experiencia individual (Fase 1/2) esté estable.

## Hitos de validación de producto (demos/checkpoints)

| Hito | Tipo | Qué se valida | Evidencia esperada |
|---|---|---|---|
| **Hito A - Demo baseline** | Demo interna | Que todo el equipo comparte diagnóstico de estado actual y brechas. | Walkthrough del flujo mock actual + backlog priorizado de brechas P0. |
| **Hito B - Primer E2E real** | Checkpoint técnico-producto | Creación de proyecto + prompt inicial + versión generada con persistencia real. | Demo en dispositivo Android con datos reales y registro en backend. |
| **Hito C - Valor visible al usuario** | Demo de producto | Preview utilizable + primera iteración por prompt de seguimiento. | Sesión completa crear -> generar -> preview -> iterar -> nueva versión. |
| **Hito D - Confiabilidad mínima** | Checkpoint de calidad | Manejo correcto de loading/error/reintento y trazabilidad de historial. | Casos de éxito y falla demostrados sin romper flujo ni duplicar datos. |
| **Hito E - Ready para escalamiento** | Gate de fase | Estabilidad operativa para iniciar mejoras de Delivery 2. | KPIs base y checklist de salida de Fase 1 cumplidos de forma repetible. |
| **Hito F - Validación social** | Demo de expansión | Descubrimiento y fork funcional de proyecto público. | Flujo browse -> open -> fork -> iterar con atribución visible. |

## Priorización para próximos sprints

### Principios de decisión

1. **Primero valor núcleo**: todo sprint debe fortalecer el flujo crear/generar/preview/iterar/historial.
2. **Primero dependencias habilitantes**: backend, persistencia y contrato de generación antes que capas de UX avanzadas.
3. **Primero demostrable**: se priorizan entregables que puedan mostrarse de punta a punta en demo.
4. **Primero confiabilidad sobre amplitud**: ante conflicto, estabilizar flujo actual antes de abrir nuevas áreas.

### Orden sugerido de ejecución

1. **Persistencia y APIs mínimas** (habilitador técnico crítico).
2. **Generación real + versionado** (valor central del producto).
3. **Preview usable en mobile** (validación directa para usuario final).
4. **Iteración con prompts de seguimiento** (profundiza uso recurrente).
5. **Hardening de errores/reintentos/validaciones** (confiabilidad y retención).
6. **Control de proyecto y mejoras de calidad** (Delivery 2).
7. **Comunidad y forks** (Delivery 3, tras estabilidad).

## Criterio de uso de este roadmap

Este documento guía priorización de sprint cuando haya dudas de alcance:

- Si una iniciativa no mejora valor central ni habilita dependencias críticas, pasa a backlog diferido.
- Si una iniciativa agrega complejidad sin mejorar demostración E2E, no entra al sprint actual.
- Si una iniciativa compromete foco de Delivery 1, se reevalúa en la transición a Fase 2/3.
# Roadmap de Producto - VibeBuilder

## Resumen ejecutivo

Este roadmap prioriza iteraciones cortas con entregables demostrables para validar, en orden, el valor central de VibeBuilder: crear y evolucionar una web app desde Android mediante prompts, sin programar manualmente.

La estrategia avanza por cuatro fases:
- **Fase 0** consolida la base actual (hoy mayormente mockeada) para habilitar decisiones con evidencia.
- **Fase 1** entrega el flujo funcional end-to-end de Delivery 1.
- **Fase 2** fortalece control, calidad y confiabilidad operativa.
- **Fase 3** abre crecimiento orgánico con comunidad y forks.

La priorizacion sigue dos criterios: **valor directo al usuario** primero y **dependencias tecnicas** como secuencia obligatoria para minimizar retrabajo.

> Nota de contexto: no se encontro `docs/scope.md` en el repositorio al momento de redactar este documento. El alcance se deduce de `AGENTS.md`, `docs/current-state.md` y `docs/product-requirements.md`.

## Tabla principal de fases

| Fase | Horizonte tentativo | Objetivo | Entregables demostrables | Riesgos clave | Criterio de salida |
|---|---|---|---|---|---|
| **Fase 0 - Estado actual** | **Corto plazo (inmediato)** | Pasar de demo visual a base medible para construir Delivery 1 sin deuda critica invisible. | 1) Inventario de gaps entre UI mock y flujo real. 2) Contrato minimo app-backend para proyectos, prompts, versiones y preview. 3) Instrumentacion basica de eventos (crear proyecto, generar, fallar, previsualizar). 4) Demo interna reproducible del flujo actual + limitaciones explicitadas. | Descubrir tarde dependencias de integracion; subestimar deuda tecnica del MVP; falta de trazabilidad de fallos. | Equipo alinea alcance P0/P1 de Delivery 1, contratos minimos aprobados y checklist tecnico listo para ejecucion por sprint. |
| **Fase 1 - Delivery 1 funcional** | **Corto plazo** | Validar el valor central: crear proyecto, generar primera version real, previsualizar e iterar con historial basico persistente. | 1) Crear proyecto persistido (sin hardcode). 2) Prompt inicial con generacion real via backend/proveedor. 3) Preview util en mobile (WebView o URL embebida). 4) Prompt de seguimiento que crea version N+1. 5) Historial basico de versiones y mensajes persistidos. 6) Demo E2E: crear -> generar -> preview -> iterar -> revisar historial. | Calidad inconsistente de generacion; latencia alta; preview no usable en mobile; errores de persistencia en versiones/mensajes. | Flujo E2E estable en entorno de demo, metricas iniciales instrumentadas y cumplimiento de requerimientos P0 (REQ-001 a REQ-008). |
| **Fase 2 - Control y calidad** | **Medio plazo** | Convertir el flujo funcional en producto confiable: mayor control del proyecto, mejor manejo de errores y mejor calidad percibida. | 1) Mejoras de estructura y metadata del proyecto (editar/renombrar/organizar). 2) Estados robustos de generacion (`idle/loading/success/failed`) y reintento guiado. 3) Validaciones de entrada y salida con mensajes accionables. 4) Regeneracion ante fallo sin duplicar proyectos. 5) Preview mejorado con fallbacks y criterios minimos de render. 6) Demo comparativa de calidad: antes vs despues en tasa de exito y tiempo a valor. | Sobrecargar UX con complejidad operativa; crecimiento de deuda en backend por parches; costos por reintentos mal controlados. | KPIs de confiabilidad y tiempo a primer valor mejoran sostenidamente; incidencias criticas de flujo principal reducidas; cumplimiento de P1 definido para Delivery 1 y extensiones de Delivery 2. |
| **Fase 3 - Comunidad y forks** | **Largo plazo** | Habilitar crecimiento por red: descubrir proyectos de terceros, forkearlos y continuar iteraciones con ownership y atribucion. | 1) Biblioteca publica de proyectos. 2) Vista de detalle de proyecto publico. 3) Accion de fork con trazabilidad del origen. 4) Fork editable con prompts y versiones propias. 5) Atribucion basica al proyecto original. 6) Demo de red: descubrir -> abrir -> fork -> iterar -> guardar como proyecto propio. | Moderacion y calidad de contenido publico; complejidad de permisos/ownership; confusion de usuarios entre original y fork. | Flujo completo de comunidad/fork operativo, reglas de ownership claras y trazabilidad original->fork validada en datos y UI. |

## Vista temporal tentativa (sin fechas rigidas)

- **Corto plazo**
  - Completar Fase 0 y ejecutar Fase 1 por incrementos verticales del flujo E2E.
  - Objetivo de sprint: cada iteracion debe terminar en demo funcional, no solo avance tecnico.
- **Medio plazo**
  - Ejecutar Fase 2 priorizando estabilidad, recuperacion ante fallos y control de proyecto.
  - Objetivo de sprint: reducir friccion operativa y mejorar confiabilidad percibida.
- **Largo plazo**
  - Construir Fase 3 para crecimiento organico y reutilizacion de ideas mediante forks.
  - Objetivo de sprint: validar adopcion de la capa social sin degradar el flujo core.

## Hitos de validacion de producto (demos/checkpoints)

1. **Checkpoint A - Valor base visible**
   - Demo del flujo actual con brechas explicitadas y riesgos priorizados (cierre de Fase 0).
2. **Checkpoint B - Primer valor real**
   - Usuario crea proyecto y obtiene primera web app previsualizable desde Android (mitad de Fase 1).
3. **Checkpoint C - Iteracion real**
   - Usuario aplica prompt de seguimiento y ve version N+1 en preview con historial guardado (cierre de Fase 1).
4. **Checkpoint D - Confiabilidad operativa**
   - Flujo maneja fallos/reintentos con estados claros y menor tasa de abandono (mitad/cierre de Fase 2).
5. **Checkpoint E - Escalamiento por comunidad**
   - Usuario descubre proyecto publico, lo forkea y continua iterando como propio (cierre de Fase 3).

## Priorizacion para proximos sprints

### Regla de priorizacion (valor al usuario x dependencia tecnica)

1. **Primero**: todo lo que reduce distancia entre idea y preview usable (core value loop).
2. **Segundo**: todo lo que evita perdida de trabajo (persistencia, historial, reintentos seguros).
3. **Tercero**: mejoras de control/calidad que aumentan confianza y repeticion de uso.
4. **Cuarto**: funcionalidades de crecimiento (comunidad/forks) una vez estable el core.

### Orden sugerido de ejecucion

- **Sprint tipo 1 (vertical)**: crear proyecto real + listado persistido.
- **Sprint tipo 2 (vertical)**: prompt inicial + generacion real + version 1.
- **Sprint tipo 3 (vertical)**: preview mobile util + manejo de error base.
- **Sprint tipo 4 (vertical)**: prompts de seguimiento + versionado N+1 + historial.
- **Sprint tipo 5+ (hardening)**: reintentos, validaciones, metadata, observabilidad y calidad.

Este orden maximiza evidencia temprana de valor al usuario y minimiza bloqueos tecnicos para fases futuras.

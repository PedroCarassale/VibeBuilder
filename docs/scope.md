# Scope de implementación inmediato - VibeBuilder

## Objetivo de este documento

Definir **qué se construye ahora** y **qué no se construye ahora** para Delivery 1, con límites explícitos para evitar scope creep.

Este alcance está alineado con:
- `AGENTS.md` (prioridad Delivery 1 y producto mobile-first),
- `docs/current-state.md` (estado actual mayormente mockeado),
- `docs/product-requirements.md` (requerimientos REQ-001 a REQ-012).

## Horizonte de alcance

- **Fase objetivo:** Delivery 1 (validación del valor central).
- **Resultado buscado:** crear proyecto -> generar web app -> preview -> iterar -> guardar historial básico.
- **Principio guía:** simplicidad funcional demostrable de punta a punta sobre amplitud de features.
- **Decisión tecnológica central:** la generación de código con IA se implementa mediante **SDK de v0** orquestado desde backend.

---

## In Scope (sí se construye ahora)

> Cada item incluye resultado esperado y definición de done verificable.

| Área | Ítem in scope | Resultado esperado | Definición de done |
|---|---|---|---|
| Proyectos | Crear proyecto con `titulo` obligatorio y `descripcion` opcional | El usuario crea proyectos reales desde Android, sin datos hardcodeados | Proyecto persistido con ID único en backend; aparece en Home tras refresco/reapertura; validación local evita título vacío |
| Proyectos | Listar proyectos desde backend | Home muestra fuente de verdad real del usuario/sesión | Se elimina dependencia de lista mock; carga, vacío y error visibles en UI |
| Chat/iteración | Enviar prompt inicial y prompts de seguimiento | El usuario itera el proyecto por chat sin editar código manual | Cada envío válido genera registro de mensaje y dispara flujo de generación; bloqueo de doble envío durante `loading` |
| Chat/iteración | Persistencia de mensajes de chat por proyecto | El contexto conversacional se conserva entre sesiones | Al cerrar/abrir app, mensajes reaparecen en orden cronológico sin duplicados |
| Generación IA | Integración real backend -> **SDK de v0** para generar web app | Se reemplazan respuestas simuladas por generación real usando el proveedor definido para esta fase | Ante prompt válido, backend invoca v0 SDK, crea versión con estado `success` o `failed` y guarda metadatos de salida |
| Generación IA | Reintento de generación fallida dentro del mismo proyecto | El usuario recupera fallas sin crear proyectos duplicados | Desde una versión `failed`, reintentar crea nueva versión (N+1) y mantiene trazabilidad del intento previo |
| Preview | Preview funcional en app (WebView o URL embebida) | El usuario puede validar la salida generada desde mobile | Para versiones `success` con `previewUrl`, renderiza contenido útil; si falla carga, se muestra error accionable + reintento |
| Historial/versiones | Historial básico de versiones por proyecto | Trazabilidad mínima de la evolución | Lista de versiones con número, fecha, estado y prompt asociado; recuperación estable de al menos últimas 20 |
| Backend/datos | Modelo mínimo persistente: `Project`, `PromptMessage`, `ProjectVersion` | Base de datos soporta flujo end-to-end del Delivery 1 | Operaciones CRUD mínimas para crear/listar proyectos, registrar prompts y listar versiones; relación entre entidades consistente |
| Backend/datos | API mínima para Delivery 1 | Contrato claro entre Android y backend | Endpoints operativos para crear/listar proyecto, enviar prompt, listar versiones y obtener preview; respuestas tipadas con errores controlados |
| Comunidad/forks | Placeholder no funcional (opcional de UI) | El producto no promete una capacidad aún no construida | Si existe acceso en UI, muestra estado "Próximamente" sin lógica de backend ni navegación a flujos incompletos |

---

## Out of Scope (no se construye ahora)

| Área | Ítem out of scope | Motivo de exclusión en esta fase |
|---|---|---|
| Proyectos | Organización avanzada (carpetas, etiquetas, búsqueda compleja) | No aporta validación del núcleo crear-generar-iterar-preview |
| Proyectos | Edición amplia de metadatos y personalización completa de proyecto | Se limita complejidad operativa en Delivery 1 |
| Chat/iteración | Chat multimodal (voz, imágenes, adjuntos complejos) | Aumenta superficie técnica sin impacto directo en validación inicial |
| Generación IA | Edición manual avanzada del código generado dentro de la app | Contradice el enfoque prompt-first de fase inicial |
| Generación IA | Soporte multi-proveedor en runtime con selector para usuario final | Se posterga hasta estabilizar contrato interno de generación |
| Preview | Editor visual de preview o inspección técnica de archivos | No es necesario para demostrar valor central actual |
| Historial/versiones | Diff visual entre versiones y rollback avanzado | Complejidad de producto para fases posteriores |
| Backend/datos | Colaboración en tiempo real multiusuario | Fuera de los objetivos de Delivery 1 |
| Backend/datos | Autenticación completa de producción (SSO, roles complejos) | En esta fase basta estrategia básica de sesión/usuario |
| Comunidad/forks | Biblioteca pública, exploración de proyectos y sistema de forks | Está definido para Delivery 3 |
| Comunidad/forks | Publicación/despliegue final y marketplace | No bloquea validación inicial de propuesta de valor |

---

## Deferred (se posterga con justificación)

| Área | Ítem deferred | Justificación | Señal para re-priorizar |
|---|---|---|---|
| Proyectos | Renombrar/editar metadatos post-creación (REQ-012) | Es útil pero no bloquea validación núcleo | Re-priorizar si usuarios no encuentran proyectos o hay fricción de organización |
| Chat/iteración | Plantillas de prompts y sugerencias inteligentes | Mejora calidad de uso, no necesidad base | Re-priorizar si tasa de éxito de primera generación es baja |
| Generación IA | Reglas automáticas de validación de salida generada | Requiere más inversión de backend/QA | Re-priorizar si aumenta tasa de versiones `failed` o resultados inválidos |
| Preview | Mejoras avanzadas de preview (navegación interna, controles extra) | Primer objetivo es "preview utilizable", no "preview perfecto" | Re-priorizar si el preview actual impide demos o test de usuarios |
| Historial/versiones | Comparación semántica entre versiones y restauración | Valor alto, costo técnico alto para esta etapa | Re-priorizar en transición a Delivery 2 |
| Backend/datos | Observabilidad avanzada y analítica profunda | Se requiere base estable antes de instrumentación extensa | Re-priorizar al iniciar hardening operativo |
| Comunidad/forks | Biblioteca pública + fork con atribución | Definido en roadmap de Delivery 3 | Re-priorizar solo tras cumplir KPIs de Delivery 1/2 |

---

## Dependencias externas y supuestos

| Dependencia | Estado esperado en esta fase | Riesgo principal | Mitigación |
|---|---|---|---|
| SDK de v0 (vía backend) | Integración funcional mínima para crear versiones reales | Cambios de API, latencia, caídas o costo | Encapsular v0 detrás de interfaz interna de backend; no acoplar Android directamente al SDK |
| Infra de persistencia (DB + storage de artefactos/metadatos) | Disponible para proyectos, prompts y versiones | Pérdida de trazabilidad o inconsistencias | Modelo mínimo estable + pruebas E2E de flujo completo |
| Entrega de `previewUrl` utilizable en mobile | URL renderizable desde Android | Preview no usable en WebView | Definir criterio mínimo de render + fallback de error/reintento |
| Red móvil y tiempos de respuesta | Condiciones reales variables | Abandono por esperas largas | Estados de carga visibles, reintentos y mensajes claros |

---

## Riesgos de alcance (scope risks)

| Riesgo | Señal temprana | Impacto | Acción de control |
|---|---|---|---|
| Se agregan features de Delivery 2/3 antes de cerrar núcleo | Historias de comunidad/forks entran al sprint actual | Retraso del objetivo principal | Bloquear ingreso si no mejora KPI de Delivery 1 |
| Sobre-ingeniería técnica temprana | Nuevas capas/abstracciones sin necesidad operativa | Menor velocidad de entrega | Priorizar arquitectura mínima viable y contratos simples |
| Dependencia excesiva de una demo "bonita" pero no funcional | UI avanza más rápido que backend real | Falsa percepción de progreso | Criterio de avance solo por flujo E2E funcionando |
| Expansión de casos edge antes del happy path | Se atienden escenarios raros sin cerrar base | Entrega fragmentada | Regla: primero happy path completo, luego robustez incremental |

---

## Reglas de decisión para cambios de alcance

Aplicar estas reglas antes de aceptar cualquier nueva solicitud:

1. **Regla de objetivo central**  
   Solo entra al alcance si mejora directamente: crear proyecto, generar versión, preview, iterar o guardar historial.

2. **Regla de evidencia**  
   Toda ampliación debe indicar qué requisito/KPI de Delivery 1 mejora y cómo se medirá.

3. **Regla de costo-oportunidad**  
   Si desplaza un item P0 no cerrado, pasa automáticamente a Deferred.

4. **Regla de dependencia**  
   Si depende de infraestructura no disponible esta iteración, no entra a In Scope.

5. **Regla de complejidad incremental**  
   Preferir la solución más simple que funcione end-to-end; optimizaciones y generalizaciones pasan a fases siguientes.

6. **Regla de corte por fase**  
   Comunidad/forks/publicación quedan fuera hasta completar objetivos y métricas mínimas de Delivery 1.

---

## Criterio de salida de esta fase (Definition of Done global Delivery 1)

La fase se considera cumplida cuando un usuario puede, desde Android y con datos reales:

1. Crear un proyecto.
2. Enviar prompt inicial y obtener una versión (`success` o `failed`).
3. Visualizar preview cuando hay `success`.
4. Enviar prompt de seguimiento para crear versión nueva.
5. Ver historial básico de versiones y mensajes persistidos.

Si alguno de estos cinco pasos no funciona de punta a punta, Delivery 1 no está cerrado.


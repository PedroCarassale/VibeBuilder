# Roadmap de producto - VibeBuilder

## Resumen ejecutivo

VibeBuilder avanza en iteraciones cortas orientadas a demostrar valor real desde mobile: **crear -> generar -> previsualizar -> iterar -> guardar historial**.

La prioridad se define por dos criterios:
1. **Valor al usuario**: reducir distancia entre idea y preview usable.
2. **Dependencias técnicas**: cerrar backend, persistencia y contrato de generación antes de ampliar superficie funcional.

Este roadmap organiza la ejecución en cuatro fases con criterios de salida explícitos para evitar scope creep.

## Tabla principal de fases

| Fase | Horizonte tentativo | Objetivo | Entregables demostrables | Riesgos principales | Criterio de salida |
|---|---|---|---|---|---|
| **Fase 0 - Estado actual** | **Corto plazo (inmediato)** | Consolidar diagnóstico real y alinear alcance de Delivery 1. | - Flujo demo actual documentado (Home mock -> Crear proyecto -> Chat simulado).<br>- Brechas priorizadas entre estado actual y valor objetivo.<br>- Contrato funcional mínimo del flujo E2E definido. | - Falsa sensación de progreso por UI sin backend.<br>- Entrada prematura de features de Delivery 2/3. | Acuerdo explícito sobre alcance inmediato, fuera de alcance, dependencias críticas y definición de éxito de Delivery 1. |
| **Fase 1 - Delivery 1 funcional** | **Corto plazo** | Validar el valor central con datos reales y flujo E2E funcional. | - Creación/listado de proyectos persistidos.<br>- Prompt inicial y prompts de seguimiento con generación real vía backend.<br>- Preview funcional en mobile.<br>- Historial básico de mensajes y versiones persistido.<br>- Confiabilidad mínima: estados visibles, reintento básico y validaciones mínimas. | - Latencia/fallos del proveedor de generación.<br>- Preview no utilizable en WebView.<br>- Inconsistencias de persistencia. | Usuario completa de punta a punta: crear proyecto, generar versión, ver preview, iterar y consultar historial sin datos hardcodeados. |
| **Fase 2 - Control y calidad** | **Medio plazo** | Endurecer operación y elevar confiabilidad/gestión del proyecto. | - Mejor organización del proyecto y metadata ampliada.<br>- Regeneración y reintentos robustos con políticas más completas.<br>- Validaciones avanzadas de entrada/salida y errores más accionables.<br>- Mejoras de preview y observabilidad operativa. | - Sobrecarga de complejidad sin impacto en métricas.<br>- Deuda técnica por hardening tardío. | KPIs de confiabilidad y experiencia mejoran de forma sostenida y la operación es repetible sin incidencias críticas. |
| **Fase 3 - Comunidad y forks** | **Largo plazo** | Expandir valor individual hacia reutilización social de proyectos. | - Biblioteca pública/compartida.<br>- Apertura de proyectos de terceros.<br>- Fork con ownership y atribución básica.<br>- Iteración sobre forks con trazabilidad del origen. | - Complejidad de permisos/atribución.<br>- Costos y moderación de contenido. | Usuario descubre un proyecto público, lo forkea, lo itera y mantiene trazabilidad original->fork. |

## Vista temporal tentativa (sin fechas rígidas)

### Corto plazo

- Completar Fase 0 y Fase 1 con incrementos verticales demostrables.
- Prioridad máxima: loop de valor central funcionando E2E.
- Regla: no incorporar comunidad/forks antes de cerrar criterio de salida de Fase 1.

### Medio plazo

- Ejecutar Fase 2 para pasar de funcional a confiable y controlable.
- Regla: cada mejora debe impactar activación, tiempo a resultado o tasa de iteración.

### Largo plazo

- Ejecutar Fase 3 para crecimiento por red (descubrimiento y forks).
- Regla: avanzar solo con experiencia individual estable en Fase 1/2.

## Hitos de validación (demos/checkpoints)

| Hito | Tipo | Qué se valida | Evidencia esperada |
|---|---|---|---|
| **Hito A - Baseline alineado** | Demo interna | Diagnóstico compartido de estado actual y brechas. | Walkthrough del flujo mock + backlog priorizado P0/P1. |
| **Hito B - Primer E2E real** | Checkpoint técnico-producto | Crear proyecto + prompt inicial + versión real persistida. | Demo Android con datos reales y registro backend. |
| **Hito C - Valor visible** | Demo de producto | Preview usable + iteración con prompt de seguimiento. | Flujo crear -> generar -> preview -> iterar -> nueva versión. |
| **Hito D - Confiabilidad mínima D1** | Checkpoint de calidad | Estados, errores y reintento básico sin romper trazabilidad. | Casos de éxito/falla demostrados sin duplicados ni pérdida de historial. |
| **Hito E - Hardening operativo** | Gate de fase | Listo para escalar mejoras de Delivery 2. | KPIs base + estabilidad repetible del flujo core. |
| **Hito F - Expansión social** | Demo de expansión | Descubrimiento y fork funcional. | Flujo browse -> open -> fork -> iterar con atribución visible. |

## Priorización para próximos sprints

### Principios de decisión

1. Primero valor núcleo: crear/generar/preview/iterar/historial.
2. Primero dependencias habilitantes: backend/persistencia/contrato.
3. Primero demostrable: cada sprint termina en demo E2E.
4. Primero confiabilidad sobre amplitud: estabilizar antes de expandir.

### Orden sugerido de ejecución

1. Persistencia y APIs mínimas.
2. Generación real + versionado.
3. Preview usable en mobile.
4. Iteración con prompts de seguimiento.
5. Confiabilidad mínima D1: estados, reintento básico y validaciones mínimas.
6. Hardening y control ampliado (Fase 2).
7. Comunidad y forks (Fase 3).

### Gate de salida Fase 1 (KPIs operativos y de producto)

La Fase 1 solo se cierra cuando se cumple la trazabilidad definida en `docs/delivery-1.md` (REQ->D1.x->Prueba->KPI) y, en paralelo, se alcanzan estos umbrales:

- KPI-D1-01 Activación de valor: >= 60%
- KPI-D1-02 Tiempo a primer preview (mediana): <= 10 min
- KPI-D1-03 Iteración efectiva: >= 35%
- KPI-D1-04 Confiabilidad técnica de generación: >= 90%
- KPI-D1-05 Retención 7 días: >= 25%
- KPI-D1-06 Integridad de persistencia (operativo): >= 99%
- KPI-D1-07 Duplicación por reintento (operativo): = 0%

Nota: `REQ-012` permanece deferred para D2 y no forma parte del gate de salida de Fase 1.

## Criterio de uso del roadmap

- Si una iniciativa no mejora valor central ni habilita dependencias críticas, pasa a backlog diferido.
- Si una iniciativa agrega complejidad sin mejorar demostración E2E, no entra al sprint actual.
- Comunidad/forks/publicación permanecen fuera del alcance operativo hasta cerrar salida de Fase 1.

# Product Requirements - VibeBuilder (Delivery 1)

## 1. Objetivo del producto

Transformar el MVP actual (basado en datos y respuestas simuladas) en una version funcional que permita crear y evolucionar prototipos de aplicaciones web desde Android mediante prompts, con generacion real, preview util y trazabilidad basica de iteraciones.

El objetivo de Delivery 1 es validar el valor central del producto: **crear y modificar una web app desde el celular sin programar manualmente**.

## 2. Problema que resuelve

Hoy, una persona con una idea de producto suele necesitar computadora, conocimiento tecnico y tiempo para convertirla en un prototipo usable.

VibeBuilder resuelve esta friccion permitiendo:
- capturar una idea desde el celular,
- describirla con lenguaje natural,
- obtener una primera version funcional de una web app,
- iterarla con nuevos prompts,
- y conservar historial basico de cambios.

## 3. Usuarios objetivo

- **Creador no tecnico**: persona con una idea de app que quiere un prototipo rapido sin escribir codigo.
- **Emprendedor en etapa temprana**: necesita validar conceptos y mostrar demos funcionales.
- **Estudiante / perfil academico**: requiere convertir ideas en prototipos para entregas o validaciones iniciales.

## 4. Requerimientos funcionales

### Prioridades

- **P0**: imprescindible para cumplir Delivery 1.
- **P1**: importante para operacion robusta de Delivery 1.
- **P2**: deseable si no compromete fechas.

| ID | Prioridad | Descripcion | Valor de negocio | Criterio de aceptacion verificable |
|---|---|---|---|---|
| REQ-001 | P0 | El usuario puede crear un proyecto desde Android con `titulo` (obligatorio) y `descripcion` (opcional). | Permite iniciar el flujo principal de construccion desde una idea. | Dado un usuario en Home, cuando completa titulo valido y confirma, entonces se crea un proyecto persistido con ID unico y se muestra en la lista tras recargar la app. |
| REQ-002 | P0 | La lista de proyectos del usuario se obtiene desde backend y reemplaza datos hardcodeados. | Convierte el MVP visual en producto util con datos reales. | Con backend disponible, la pantalla Home muestra proyectos del usuario autenticado/sesion actual; al crear un nuevo proyecto aparece en la lista sin depender de datos mock. |
| REQ-003 | P0 | El usuario puede enviar un prompt inicial para generar la primera version de la web app del proyecto. | Materializa la propuesta de valor principal del producto. | Dado un proyecto sin versiones, cuando se envia un prompt inicial valido, entonces se registra el prompt y se crea una version 1 con estado final `success` o `failed`. |
| REQ-004 | P0 | El sistema integra un servicio real de generacion (via backend) para producir un resultado de web app y metadatos de salida. | Elimina respuestas simuladas y habilita generacion real. | Ante un prompt valido, el backend invoca el proveedor configurado, guarda artefactos/metadatos generados y responde con referencia de version creada. |
| REQ-005 | P0 | El usuario puede visualizar un preview de la version generada desde la app (WebView o URL embebida). | Permite validar rapidamente el resultado generado. | Dada una version `success` con `previewUrl`, al abrir Preview se renderiza contenido web utilizable; si la URL no carga, se muestra estado de error accionable. |
| REQ-006 | P0 | El usuario puede enviar prompts de seguimiento sobre un proyecto existente para iterar el resultado. | Habilita mejora incremental del prototipo sin rehacer todo. | Dada una version previa `success`, cuando se envia un nuevo prompt, entonces se crea una nueva version numerada (N+1) vinculada al proyecto. |
| REQ-007 | P0 | El sistema conserva historial basico de versiones por proyecto. | Permite trazabilidad de evolucion y comparacion simple de avances. | En la vista de historial, se listan versiones con numero, fecha, estado y prompt asociado; al menos las ultimas 20 versiones deben recuperarse correctamente. |
| REQ-008 | P0 | El chat del proyecto persiste mensajes (usuario/sistema) asociados al proyecto y version cuando corresponda. | Mantiene contexto de iteracion y evita perdida de informacion. | Al cerrar y reabrir la app, los mensajes previos del proyecto se muestran en orden cronologico y sin duplicados. |
| REQ-009 | P1 | El flujo de generacion e iteracion expone estados de proceso (`idle`, `loading`, `success`, `failed`) y mensajes de error claros. | Mejora confianza del usuario y reduce abandonos por incertidumbre. | Durante una solicitud, la UI bloquea doble envio; ante fallo de red/proveedor, muestra mensaje entendible y opcion de reintentar. |
| REQ-010 | P1 | El usuario puede reintentar una generacion fallida sin crear proyectos duplicados. | Reduce friccion operativa y recupera sesiones fallidas. | Dada una version `failed`, al reintentar se crea una nueva version en el mismo proyecto y queda registro del intento previo fallido. |
| REQ-011 | P1 | La aplicacion valida entradas minimas antes de enviar datos al backend (titulo y prompt no vacios, limites de longitud). | Evita errores evitables y costos innecesarios de procesamiento. | Si titulo o prompt estan vacios o fuera de limite, se bloquea envio y se muestra validacion en pantalla; no se llama al backend. |
| REQ-012 | P2 | La app permite editar metadatos basicos del proyecto (titulo/descripcion) luego de creado. | Mejora organizacion temprana sin ampliar alcance a funcionalidades de Delivery 2/3. | Cuando el usuario edita titulo/descripcion y confirma, los cambios persisten y se reflejan en Home y detalle del proyecto. |

## 5. Requerimientos no funcionales

1. **Disponibilidad operativa minima (Delivery 1):**
   - El sistema debe estar disponible para demostraciones internas y pruebas funcionales de punta a punta en horario de trabajo.
2. **Rendimiento percibido:**
   - La navegacion entre Home, Crear Proyecto, Chat e Historial debe responder en menos de 2 segundos en condiciones normales de red.
   - En operaciones largas de generacion, la UI debe informar progreso/estado de forma continua.
3. **Confiabilidad de datos:**
   - No se deben perder proyectos, prompts ni versiones ya confirmados por backend.
   - Las operaciones deben ser idempotentes donde aplique para evitar duplicados por reintentos de red.
4. **Seguridad basica:**
   - Toda comunicacion app-backend debe usar HTTPS.
   - No exponer claves de proveedores de IA en la app Android.
5. **Mantenibilidad:**
   - Separar logica de UI, capa de red y modelos de dominio para facilitar evolucion en Delivery 2.
6. **Observabilidad minima:**
   - Registrar eventos y errores basicos de creacion, generacion, iteracion y preview para diagnostico.

## 6. Restricciones y supuestos

### Restricciones

- Se prioriza **Delivery 1**: crear proyecto, generar primera version, preview, iterar y guardar historial.
- El output generado debe ser una **web app**, no APK nativa.
- No se implementa edicion avanzada de codigo en la app movil.
- No se incluye comunidad, biblioteca publica ni forks en esta version.
- La app Android consume un backend propietario para orquestar la IA y persistencia.

### Supuestos

- Existira un proveedor de generacion de web apps accesible via backend (por ejemplo, integracion prevista con V0 SDK).
- Se dispone de infraestructura minima para persistencia de proyectos, mensajes y versiones.
- El equipo define una estrategia de identidad/sesion suficiente para asociar datos a un usuario (aunque sea basica en Delivery 1).
- El preview generado podra exponerse mediante URL utilizable dentro de la app.

## 7. Riesgos de producto

1. **Calidad inconsistente de generacion**
   - Impacto: baja confianza del usuario.
   - Mitigacion inicial: prompts guiados, manejo claro de errores y reintentos simples.
2. **Tiempos altos de generacion**
   - Impacto: abandono durante el flujo principal.
   - Mitigacion inicial: estados visibles, mensajes de espera y notificacion de resultado.
3. **Preview no utilizable en mobile**
   - Impacto: se rompe la validacion de valor del producto.
   - Mitigacion inicial: definir criterios minimos de render para WebView y fallback por URL externa.
4. **Persistencia incompleta de historial**
   - Impacto: perdida de contexto y frustracion.
   - Mitigacion inicial: modelo de datos simple y pruebas E2E de creacion -> iteracion -> historial.
5. **Dependencia fuerte del proveedor de IA**
   - Impacto: bloqueos por caidas, cambios de API o costo.
   - Mitigacion inicial: encapsular proveedor en backend y estandarizar contrato interno.

## 8. Metricas de exito iniciales (KPIs Delivery 1)

1. **Activacion de valor principal**
   - % de proyectos nuevos que llegan a primera version `success` en la misma sesion.
   - Meta inicial: >= 60%.
2. **Tiempo a primer resultado**
   - Tiempo mediano desde "crear proyecto" hasta visualizar primer preview.
   - Meta inicial: <= 10 minutos (incluyendo espera de generacion).
3. **Iteracion efectiva**
   - % de proyectos con al menos 1 prompt de seguimiento luego de la primera version.
   - Meta inicial: >= 35%.
4. **Confiabilidad del flujo**
   - Tasa de operaciones de generacion completadas sin error tecnico (app/backend).
   - Meta inicial: >= 90%.
5. **Retencion corta de uso**
   - % de usuarios que vuelven a abrir un proyecto en 7 dias.
   - Meta inicial: >= 25%.

## 9. Fuera de alcance (version actual)

### No incluido en Delivery 1

- Comunidad de proyectos.
- Sistema de forks y atribucion.
- Publicacion/despliegue final del proyecto.
- Exportacion avanzada de codigo.
- Colaboracion multiusuario en tiempo real.
- Edicion manual avanzada del codigo generado.

### Futuro (referencia Delivery 2/3)

- Delivery 2: mejor estructura de proyecto, mejor preview, control de metadata ampliado, manejo de errores avanzado, validaciones y flujo de regeneracion mas robusto.
- Delivery 3: biblioteca publica, exploracion de proyectos de terceros, apertura y fork, ownership del fork y atribucion basica.

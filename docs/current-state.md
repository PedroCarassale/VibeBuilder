# Estado actual

VibeBuilder cuenta actualmente con una primera versión muy preliminar de la aplicación Android. Esta versión sirve como base inicial para validar la experiencia principal, pero todavía no incluye generación real de aplicaciones web, integración con APIs externas ni persistencia dinámica.

El usuario puede entrar a la aplicación, revisar una lista de proyectos ya creados y acceder a una vista de chat para simular la interacción con un agente de IA. Sin embargo, la mayoría del comportamiento actual está mockeado o hardcodeado.

## Capacidades actuales

- El usuario puede abrir la aplicación.
- El usuario puede ver una lista inicial de proyectos.
- El usuario puede iniciar el flujo de creación de un proyecto nuevo.
- El usuario puede ingresar un título y una breve descripción para un proyecto.
- El usuario puede entrar a una vista de chat asociada al proyecto.
- El usuario puede enviar mensajes dentro del chat.
- La aplicación muestra respuestas simuladas del supuesto agente de IA.

## Pantallas existentes

### Home / Lista de proyectos

Es la primera pantalla que ve el usuario al entrar a la aplicación. Muestra los proyectos existentes del usuario.

Actualmente, los proyectos mostrados en esta pantalla vienen de datos hardcodeados. No hay conexión con backend, base de datos ni almacenamiento local real.

### Crear proyecto

Existe una pantalla intermedia para crear un nuevo proyecto. En esta pantalla el usuario puede indicar información básica como el título y una breve descripción.

Por ahora, este flujo funciona como parte de la experiencia visual inicial. Todavía no crea un proyecto persistido en un backend ni dispara una generación real.

### Chat con agente de IA

Existe una vista de chat donde el usuario puede conversar con un supuesto agente de IA.

Actualmente, el agente no existe como integración real. El usuario puede mandar mensajes y la aplicación responde con mensajes simulados, por ejemplo respuestas fijas como "mensaje uno" o "mensaje dos".

Esta pantalla representa el lugar donde, en futuras versiones, ocurrirán los idas y vueltas entre el usuario y el agente para crear o modificar la aplicación web generada.

## Datos y persistencia

La lista de proyectos está hardcodeada.

No existe todavía:

- backend conectado,
- base de datos,
- persistencia local real,
- historial real de proyectos,
- historial real de prompts,
- historial real de versiones.

## Generación con IA

La aplicación todavía no genera aplicaciones web reales.

No existe integración actual con:

- API propia,
- modelo de IA,
- Vercel V0,
- SDK de V0,
- servicio de generación de archivos.

La intención futura es conectar la aplicación con Vercel V0 usando su SDK para manejar la lógica de generación e iteración de las aplicaciones web.

## Preview

Actualmente no existe una pantalla de preview del proyecto generado.

El usuario todavía no puede visualizar una aplicación web generada, ni mediante WebView, ni mediante URL, ni mediante una vista mockeada.

## Iteración actual

La iteración existe solamente como interfaz simulada.

El usuario puede escribir mensajes en el chat y recibir respuestas fijas, pero esas respuestas no modifican un proyecto, no generan archivos, no crean versiones y no usan lógica real de IA.

## Fuera del alcance actual

La versión actual todavía no incluye:

- generación real de aplicaciones web,
- preview de la aplicación generada,
- integración con Vercel V0,
- backend,
- autenticación,
- almacenamiento persistente,
- historial real de versiones,
- comunidad de proyectos,
- sistema de forks,
- exportación o despliegue de proyectos.

## Dirección futura

El objetivo de las próximas iteraciones es transformar esta base visual en un flujo funcional donde el usuario pueda:

- crear un proyecto desde un prompt,
- enviar ese prompt a un servicio de generación,
- generar una primera versión de una aplicación web,
- previsualizar el resultado,
- mandar prompts de seguimiento,
- guardar historial de mensajes y versiones,
- seguir iterando sobre el proyecto desde la app.

La integración prevista para la lógica de generación es Vercel V0 mediante su SDK.

## Demo actual

La versión actual puede demostrarse con el siguiente flujo:

1. Abrir la aplicación.
2. Ver la lista de proyectos mockeados.
3. Iniciar la creación de un proyecto nuevo.
4. Completar título y breve descripción.
5. Entrar a la vista de chat.
6. Enviar mensajes.
7. Ver respuestas simuladas del agente.

Esta demo muestra la intención general del producto, pero no representa todavía un flujo de generación real.

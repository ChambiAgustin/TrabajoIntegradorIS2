# Proyecto Ingeniería de Software II: Especificación, Gestión y Planificación

**Integrantes:** Candela Sangroniz, Chambi Agustín, Gillo Celina

## 1. Problema que se quiere resolver
La institución carece de una plataforma unificada, intuitiva y accesible para la gestión académica y la comunicación. Los procesos descentralizados dificultan el seguimiento del rendimiento de los alumnos y docentes, y no existe un canal oficial y directo para la interacción dentro de las comisiones. Se requiere un sistema que centralice la información, mejore la experiencia de usuario (UX) y ofrezca herramientas de evaluación y comunicación segmentadas por roles.

## 2. Usuarios del sistema
* **Alumnos:** Consumen contenido, se inscriben a materias, envían trabajos y revisan su propia evolución.
* **Docentes (Profesores):** Gestionan sus comisiones, asignan horarios, suben notas, registran asistencias y se comunican con los alumnos.
* **Directivos:** Tienen un perfil analítico y de auditoría para evaluar el panorama educativo general (sin acceso a comunicaciones privadas de las clases).
* **Administradores:** Gestionan el soporte técnico y la creación/baja de usuarios y materias base.

## 3. Funcionalidades principales
* **Gestión de Accesos y Roles:** Áreas de sistema y permisos estrictamente diferenciados según el tipo de usuario.
* **Módulo de Inscripción y Planificación:** Inscripción de alumnos a materias y asignación de horarios/comisiones por parte de los docentes.
* **Aula Virtual y Comunicación:** Tablero de comunidad por comisión y mensajería privada entre docente y alumno.
* **Módulo de Seguimiento y Reportes:** Carga de notas, asistencias y entregas (prácticos/teóricos). Tableros de análisis de rendimiento para que los directivos evalúen la evolución educativa.
* **Sistema de Notificaciones / Calendario:** Un panel simple para alertas (ej. "Nueva nota cargada") o fechas de entregas.

## 4. Cambios de alcance ocurridos
El proyecto evoluciona de un simple registro administrativo de profesores y usuarios a un entorno académico integral. Se amplía el alcance para incluir la interacción directa en un "aula virtual", el manejo de inscripciones a comisiones y la generación de métricas de rendimiento para la toma de decisiones directivas.

## 5. Restricciones técnicas
* **Compatibilidad heredada:** El sistema debe mantener retrocompatibilidad con la arquitectura base del año anterior (Java puro con gestor de dependencias Maven).
* **Renderizado del lado del servidor:** La interfaz debe seguir utilizando el motor de plantillas Mustache, limitando el uso de frameworks frontend reactivos complejos para mantener la simpleza del proyecto original.

## 6. Tamaño del equipo
El equipo está conformado por 3 integrantes.

## 7. Tecnologías elegidas y justificación
* **Backend:** Java con Maven (Tipado fuerte y robustez para soportar la nueva estructura de roles).
* **Frontend:** HTML5, CSS3 y Mustache (Curva de aprendizaje baja, interfaces accesibles e inyección dinámica sencilla).
* **Base de Datos:** SQLite (Ideal para el alcance del proyecto y fácil de compartir sin necesidad de servidores externos).
* **Herramientas de desarrollo:** VS Code y GitHub (GitHub Projects para gestión de backlog).

## 8. Plazo estimado
**3 a 4 meses (aprox. 12-14 semanas).** El desarrollo se dividirá en iteraciones cortas aprovechando la asistencia de herramientas de Inteligencia Artificial para agilizar la codificación rutinaria y la generación de casos de prueba, permitiendo enfocar el mayor esfuerzo humano en la arquitectura, reglas de negocio y revisión de calidad.

## 9. Problemas encontrados
* **Deuda técnica inicial:** Al hacer el pull del proyecto anterior, se detectó la necesidad de refactorizar parte del código base para soportar la nueva estructura de roles (directivos, alumnos, etc.) sin romper las funcionalidades existentes (login y gestión básica).
* **Curva de adaptación:** Necesidad de coordinar el estilo de código entre los 3 integrantes nuevos, ya que cada uno tiene metodologías de trabajo distintas provenientes de otros equipos.

## 10. Forma de organización del equipo
El equipo adoptará una estructura colaborativa enfocada en la complementariedad, la coordinación y la comunicación continua. Para evitar cuellos de botella, dividiremos el proyecto por responsabilidades principales, apoyándonos mutuamente:
* **Chambi Agustín (Gestor/Backend):** Responsable de mantener actualizado el GitHub Projects, asegurar el cumplimiento del cronograma y liderar la lógica de negocio en Java.
* **Candela Sangroniz (Frontend/UX):** Lidera el desarrollo de las vistas en HTML/CSS y Mustache, garantizando que la interfaz sea accesible e intuitiva.
* **Celina Gillo (Datos/QA):** Encargada de evolucionar el esquema en SQLite (`scheme.sql`), además de definir y validar los criterios de aceptación (pruebas) antes de fusionar el código.
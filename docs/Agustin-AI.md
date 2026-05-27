# Registro de Asistencia de Inteligencia Artificial (Agustin-AI)

Este documento registra la colaboración entre el alumno Chambi Agustín y el asistente de Inteligencia Artificial durante el desarrollo del Trabajo Integrador de Ingeniería de Software II. Su objetivo es mantener una bitácora auditable de las decisiones técnicas y el uso de la IA en el proyecto.

---

## Interacción 1: Actualización de repositorio y Planificación de Implementación

**Petición (Agustín):** 
Actualizar el entorno local con los últimos cambios del repositorio remoto de los compañeros. Crear este documento de registro de interacciones con la IA para tener feedback de las peticiones y resoluciones. Finalmente, analizar la documentación actualizada para establecer un plan de implementación, arrancando con el trabajo técnico real.

**Resolución (IA):** 
Se ejecutó un `git pull` sincronizando exitosamente el archivo `docs/markdown.md` sin conflictos. Se identificaron las tareas de Backend asignadas a Agustín. Se procedió a crear este archivo `Agustin-AI.md` y se le presentó al usuario el Plan de Acción enfocado en el Hito 1 (Issues #2 y #3: Refactorización de Arquitectura, Login y Alta de Profesores).

---

## Interacción 2: Confirmación de plan y solicitud de detalle técnico

**Petición (Agustín):** 
Confirmación del plan de acción propuesto. Solicitud expresa de documentar y explicar detalladamente cada cambio de alto impacto o muy técnico en esta bitácora, con fines de aprendizaje y claridad en la evolución del código.

**Resolución (IA):** 
Se actualizó el documento `Agustin-AI.md`. Se comenzó con el análisis del archivo central `App.java`. Se detectó que el manejo actual de usuarios no incluye el concepto de roles (`TipoUsuario`). Se determinó que el primer paso técnico real es ajustar el esquema de la base de datos y los modelos para soportar esta característica (Inicio del Issue #2).

---

## Interacción 3: Refactorización Estructural (Base de Datos) y Auditoría de Colaboradores

**Petición (Agustín):**
Aprobación para avanzar con los cambios estructurales en la base de datos. Solicitud de agregar una sección explícita de "Errores de Colaboradores" para detallar y evidenciar cómo se resuelven los problemas arquitectónicos generados por las dependencias previas del equipo.

**Resolución (IA):**
Se reestructuró y corrigió el script de inicialización `scheme.sql`.

**Errores de Colaboradores Detectados y Resueltos:**
1. **Falta de Integridad Relacional:** El diseño de base de datos aportado por el rol de QA/Datos creaba la tabla `users` completamente aislada, sin el campo `tipo_usuario` (rol) y sin una clave foránea que la conecte con la persona real. Esto rompía el patrón del Modelo de Dominio y dejaba el ruteo del backend "ciego".
2. **Problema de Dependencias en Creación:** El script original intentaba crear dependencias en orden incorrecto si se hubiesen aplicado llaves foráneas. Se corrigió el orden a: primero `persons` (entidad fuerte), y luego `users` y `professors` apuntando hacia la primera con restricciones estrictas de borrado en cascada.

---

## Interacción 4: Reconstrucción de Base de Datos y Refactorización del Login

**Petición (Agustín):**
Aprobación para ejecutar la reconstrucción de la base de datos local y continuar con la refactorización de la lógica del backend (Login y Sesiones) en Java.

**Resolución (IA):**
Se reconstruyó con éxito la base de datos `dev.db` usando el nuevo `scheme.sql` a través de la terminal (SQLite). 

Se realizaron los siguientes cambios de alto impacto en el código fuente (Cierre del núcleo del Issue #2):
1. **Modelo `User.java`:** Se implementaron los métodos `getTipoUsuario()` y `setPersonId()` para tipar fuertemente las consultas a la base de datos.
2. **Sesiones HTTP Seguras (`App.java`):** En el método POST de `/login`, tras la verificación criptográfica con `BCrypt`, ahora se recupera el rol (`tipo_usuario`) de la BD y se almacena en `req.session().attribute("tipoUsuario")`.
3. **Pase a Vistas (`App.java`):** La ruta GET de `/dashboard` ahora inyecta el `tipo_usuario` al modelo que recibe la vista Mustache, lo cual permitirá posteriormente mostrar u ocultar opciones administrativas.
4. **Endpoint de Alta:** En la ruta POST de `/user/new`, se configuró para recibir el parámetro `tipo_usuario` o en su defecto, asignar automáticamente el rol "ALUMNO" por seguridad de acceso.

---

## Interacción 5: Ejecución de Pruebas y Limpieza de Deuda Técnica (Issue #3)

**Petición (Agustín):**
Ejecutar los tests unitarios mediante Maven para verificar la integridad del código base. En caso de ser exitoso, continuar con la Issue #3 (Alta de Profesor).

**Resolución (IA):**
Se corrió con éxito `mvn clean test` (BUILD SUCCESS), verificando que la lógica base no se rompió tras la refactorización.
Se inició el Issue #3 eliminando código duplicado en `App.java`. Toda la responsabilidad de profesores ahora recae de forma exclusiva en el controlador `ProfessorController.java`.

---

## Interacción 6: Auto-Generación de Cuentas (Cierre Issue #3)

**Petición (Agustín):**
Aprobación de la estrategia de generar automáticamente una cuenta de usuario (`User`) para el profesor recién registrado, vinculando su identidad.

**Resolución (IA):**
Se refactorizó el bloque `post("/profesor/new")` dentro de `ProfessorController.java`. Al crearse la `Person` en la base de datos, el sistema ahora:
1. Crea automáticamente el `User` y le asigna el rol `"DOCENTE"`.
2. Asigna el DNI de la persona como nombre de usuario.
3. Hashea de manera segura el DNI con `BCrypt` para utilizarlo como contraseña inicial por defecto.
4. Vincula el ID de la Persona (`person_id`) al nuevo usuario.
5. Usa el DNI también como legajo inicial en la tabla `professors` para evitar violaciones de unicidad (ya que originalmente el código harcodeaba un `0`).
6. Se eliminó el uso de consultas SQL puras (`Base.exec`) y se reemplazó por el paradigma orientado a objetos del ORM ActiveJDBC (`nuevoProfesor.saveIt()`).

---

## Interacción 7: Acondicionamiento de Vista para UI/UX (Issue #4)

**Petición (Agustín):**
Dejar el entorno preparado y ordenado para que la colaboradora Candela (UI/UX) pueda realizar su trabajo sin imprevistos o fallos de integración.

**Resolución (IA):**
Se intervino en el archivo `profesor_form.mustache` inyectando los campos opcionales (`teléfono`, `dirección` y `cargo`) manteniendo la misma estructura y clases de TailwindCSS ya presentes. 
De manera simultánea, se actualizó el `ProfessorController.java` para capturar estos nuevos datos y validarlos (si vienen vacíos, asumen valores por defecto). Esto garantiza una integración "Plug & Play" para el equipo de Frontend.

---

## Interacción 8: Creación de Seeders y Datos Ficticios (Issue #5)

**Petición (Agustín):**
Realizar un sprint rápido para cargar automáticamente en la base de datos: 1 administrador, 2 profesores completos y 2 alumnos. Registrar las credenciales ficticias en la bitácora para fines de prueba.

**Resolución (IA):**
Se implementó el patrón de diseño *Database Seeder* creando la clase estática `Seeder.java` dentro del nuevo paquete `com.is1.proyecto.utils`. Se configuró el `App.main()` para que, justo después de instanciar las credenciales de DB, abra una conexión temporal, evalúe si la tabla de usuarios está vacía, y de ser así, inyecte automáticamente la data semilla. Todas las contraseñas se almacenaron hasheadas con `BCrypt`.

### Credenciales de Prueba Inyectadas (Seeders)
Para ingresar al sistema, utilizar los siguientes usuarios (Username / Password):

**Administrador:**
*   Usuario: `admin` | Clave: `admin123`

**Docentes:**
*   Alan Turing -> Usuario: `22222222` | Clave: `22222222` (Legajo/DNI)
*   Ada Lovelace -> Usuario: `33333333` | Clave: `33333333` (Legajo/DNI)

**Alumnos:**
*   Carlos Perez -> Usuario: `44444444` | Clave: `44444444` (DNI)
*   Maria Gomez -> Usuario: `55555555` | Clave: `55555555` (DNI)

package com.is1.proyecto.controllers; // 1. Definir el paquete

// Importaciones necesarias
import org.javalite.activejdbc.Base; // Para manejar la conexión a la base de datos
import com.is1.proyecto.models.Person; // Importa el modelo Person
import com.is1.proyecto.models.Professor; // Importa el modelo Professor
import spark.ModelAndView; // Para renderizar vistas Mustache
import spark.template.mustache.MustacheTemplateEngine; // Motor de plantillas

import java.util.HashMap; // Para crear el modelo de datos para la vista
import java.util.Map; // Interfaz Map

// --- ¡NUEVAS IMPORTACIONES PARA ARREGLAR EL ERROR 400! ---
import java.net.URLEncoder; // Para codificar la URL
import java.nio.charset.StandardCharsets; // Para decirle que use UTF-8

import static spark.Spark.get; // Importa el método estático 'get' de Spark
import static spark.Spark.post; // Importa el método estático 'post' de Spark

public class ProfessorController {

    // Método para registrar/configurar las rutas de este controlador
    public static void registerRoutes() {

        // --- RUTA GET para MOSTRAR el formulario de alta ---
        // Cuando un navegador pide la página '/profesor/new' usando GET...
        get("/profesor/new", (req, res) -> {
            Map<String, Object> model = new HashMap<>(); // Crea un mapa vacío para la plantilla

            // Recupera mensajes de error o éxito si fueron pasados en la URL (ej. después de un POST)
            // Usamos queryParamOrDefault para evitar errores si el parámetro no existe o es raro
            String errorMessage = req.queryParamOrDefault("error", null); // Devuelve null si no existe
            if (errorMessage != null && !errorMessage.isEmpty()) {
                // Solo intentamos usarlo si es una cadena válida
                model.put("errorMessage", errorMessage);
            }
            String successMessage = req.queryParamOrDefault("message", null); // Devuelve null si no existe
            if (successMessage != null && !successMessage.isEmpty()) {
                // Solo intentamos usarlo si es una cadena válida
                model.put("successMessage", successMessage);
            }

            // Devuelve el objeto ModelAndView que le dice a Spark:
            // "Renderiza la plantilla 'profesor_form.mustache' usando los datos del 'model'"
            return new ModelAndView(model, "profesor_form.mustache");
        }, new MustacheTemplateEngine()); // Usa el motor Mustache para renderizar


        // --- RUTA POST para PROCESAR el formulario de alta ---
        // Cuando el formulario HTML envía datos a '/profesor/new' usando POST...
        post("/profesor/new", (req, res) -> {

            // 1. OBTENER DATOS DEL FORMULARIO
            //    req.queryParams("name_del_input") lee el valor enviado desde el HTML
            String nombre = req.queryParams("nombre");
            String apellido = req.queryParams("apellido");
            String correo = req.queryParams("correo");
            String dniStr = req.queryParams("dni"); // DNI viene como texto

            // 2. VALIDACIONES

            // a) Campos obligatorios no vacíos
            if (nombre == null || nombre.trim().isEmpty() ||
                apellido == null || apellido.trim().isEmpty() ||
                correo == null || correo.trim().isEmpty() ||
                dniStr == null || dniStr.trim().isEmpty())
            {
                // Si falta alguno, redirige DE VUELTA al formulario GET, pasando un mensaje de error en la URL
                // ¡CORREGIDO! Usamos URLEncoder.encode para los acentos.
                String mensaje = "Todos los campos son obligatorios.";
                res.redirect("/profesor/new?error=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8));
                return null; // Detiene la ejecución de esta ruta POST
            }

            // b) Formato de correo (validación simple)
            if (!correo.contains("@") || !correo.contains(".")) {
                // ¡CORREGIDO!
                String mensaje = "Formato de correo electrónico inválido.";
                res.redirect("/profesor/new?error=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8));
                return null;
            }

            // c) DNI debe ser numérico (intentamos convertirlo)
            int dni;
            try {
                dni = Integer.parseInt(dniStr.trim());
            } catch (NumberFormatException e) {
                // ¡CORREGIDO!
                String mensaje = "El DNI debe ser un número.";
                res.redirect("/profesor/new?error=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8));
                return null;
            }

            // d) DNI o Correo no deben existir previamente en la tabla 'persons'
            //    Usamos ActiveJDBC (Person hereda de Model) para buscar en la BD
            //    Person.findFirst(query_sql, parametros...)
            boolean dniExists = Person.count("dni = ?", dni) > 0; // Verifica si ya hay alguien con ese DNI
            boolean mailExists = Person.count("mail = ?", correo) > 0; // Verifica si ya hay alguien con ese correo

            if (dniExists) {
                // ¡CORREGIDO!
                String mensaje = "El DNI ingresado ya existe en el sistema.";
                res.redirect("/profesor/new?error=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8));
                return null;
            }
            if (mailExists) {
                // ¡CORREGIDO!
                String mensaje = "El correo electrónico ingresado ya existe en el sistema.";
                res.redirect("/profesor/new?error=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8));
                return null;
            }

            // 3. CREAR Y GUARDAR EL PROFESOR (Flujo Exitoso)
            try {
                // ActiveJDBC maneja transacciones implícitamente si Base.open/close están bien configurados (como en App.java)

                // a) Crear y guardar la entidad 'Person'
                Person nuevaPersona = new Person();
                nuevaPersona.setNombre(nombre.trim()); // Usamos los setters que definimos
                nuevaPersona.setApellido(apellido.trim());
                nuevaPersona.setMail(correo.trim());
                nuevaPersona.setDni(dni);
                // Ponemos valores por defecto para los campos no obligatorios en este formulario
                nuevaPersona.setTel(0); // O podrías añadir el campo 'tel' al formulario
                nuevaPersona.setDireccion("N/A"); // O podrías añadir el campo 'direccion'
                nuevaPersona.saveIt(); // ¡IMPORTANTE! Guarda la persona en la tabla 'persons'. ActiveJDBC obtiene el id_per generado.

                // b) Crear y guardar la entidad 'Professor' asociada
                Professor nuevoProfesor = new Professor();
                // ¡CLAVE! Establecemos la clave primaria/foránea.
                // ActiveJDBC es inteligente, al guardar Person, el ID ya está disponible con getId()
                // Si tu PK/FK se llama id_prof, ActiveJDBC necesita que llames a set("id_prof", ...)
                nuevoProfesor.setId(nuevaPersona.getId()); // Asigna el ID de la persona recién creada
                // nuevoProfesor.set("id_prof", nuevaPersona.getId()); // Alternativa si @IdName("id_prof") no funciona bien

                // Ponemos valores por defecto para legajo y cargo
                nuevoProfesor.setLegajo(0); // Podrías generar un legajo único o añadirlo al form
                nuevoProfesor.setCargo("Docente"); // O añadirlo al form
                Base.exec("INSERT INTO professors (id_prof, legajo, cargo) VALUES (?, ?, ?)", nuevaPersona.getId(), 0, "Docente");

                // 4. REDIRIGIR CON MENSAJE DE ÉXITO
                // ¡CORREGIDO!
                String mensaje = "Profesor '" + nombre + " " + apellido + "' registrado con éxito.";
                res.redirect("/profesor/new?message=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8));
                return null; // Detiene la ejecución

            } catch (Exception e) {
                // 5. MANEJO DE ERRORES INESPERADOS (ej: error de BD)
                System.err.println("Error al guardar profesor: " + e.getMessage());
                e.printStackTrace(); // Imprime el detalle del error en la consola del servidor
                // ¡CORREGIDO!
                String mensaje = "Ocurrió un error inesperado al guardar el profesor.";
                res.redirect("/profesor/new?error=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8));
                return null;
            }
        }); // Fin de la ruta POST
    } // Fin del método registerRoutes
} // Fin de la clase ProfessorController
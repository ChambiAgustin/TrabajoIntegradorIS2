package com.is1.proyecto.controllers;

import org.javalite.activejdbc.Base;
import com.is1.proyecto.models.Person;
import com.is1.proyecto.models.Professor;
import com.is1.proyecto.models.User;
import org.mindrot.jbcrypt.BCrypt;
import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;

public class ProfessorController {

    public static void registerRoutes() {

        // --- RUTA GET para MOSTRAR el formulario de alta ---
        get("/profesor/new", (req, res) -> {
            Map<String, Object> model = new HashMap<>();

            String errorMessage = req.queryParams("error");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                model.put("errorMessage", errorMessage);
            }
            String successMessage = req.queryParams("message");
            if (successMessage != null && !successMessage.isEmpty()) {
                model.put("successMessage", successMessage);
            }

            // Recuperar atributos flash temporales de la sesión (para evitar exponer la clave temporal en la URL)
            String flashTempPassword = req.session().attribute("flashTempPassword");
            String flashProfName = req.session().attribute("flashProfName");
            if (flashTempPassword != null) {
                model.put("tempPassword", flashTempPassword);
                model.put("profName", flashProfName);
                model.put("showTempPasswordAlert", true);
                req.session().removeAttribute("flashTempPassword");
                req.session().removeAttribute("flashProfName");
            }

            return new ModelAndView(model, "profesor_form.mustache");
        }, new MustacheTemplateEngine());


        // --- RUTA POST para PROCESAR el formulario de alta ---
        post("/profesor/new", (req, res) -> {
            String nombre = req.queryParams("nombre");
            String apellido = req.queryParams("apellido");
            String correo = req.queryParams("correo");
            String dniStr = req.queryParams("dni");
            String telefonoStr = req.queryParams("telefono");
            String direccion = req.queryParams("direccion");
            String cargo = req.queryParams("cargo");

            // Crear modelo para preservar los datos ingresados ante cualquier error
            Map<String, Object> model = new HashMap<>();
            model.put("nombre", nombre);
            model.put("apellido", apellido);
            model.put("correo", correo);
            model.put("dni", dniStr);
            model.put("telefono", telefonoStr);
            model.put("direccion", direccion);
            model.put("cargo", cargo);

            // a) Campos obligatorios no vacíos
            if (nombre == null || nombre.trim().isEmpty() ||
                apellido == null || apellido.trim().isEmpty() ||
                correo == null || correo.trim().isEmpty() ||
                dniStr == null || dniStr.trim().isEmpty())
            {
                res.status(400);
                model.put("errorMessage", "Todos los campos son obligatorios.");
                return new ModelAndView(model, "profesor_form.mustache");
            }

            // b) Formato de correo (validación simple)
            if (!correo.contains("@") || !correo.contains(".")) {
                res.status(400);
                model.put("errorMessage", "Formato de correo electrónico inválido.");
                return new ModelAndView(model, "profesor_form.mustache");
            }

            // c) DNI debe ser numérico (intentamos convertirlo)
            int dni;
            try {
                dni = Integer.parseInt(dniStr.trim());
            } catch (NumberFormatException e) {
                res.status(400);
                model.put("errorMessage", "El DNI debe ser un número.");
                return new ModelAndView(model, "profesor_form.mustache");
            }

            // c.2) Teléfono debe ser numérico si es provisto
            long telefono = 0;
            if (telefonoStr != null && !telefonoStr.trim().isEmpty()) {
                try {
                    telefono = Long.parseLong(telefonoStr.trim());
                } catch (NumberFormatException e) {
                    res.status(400);
                    model.put("errorMessage", "El teléfono debe ser un valor numérico entero.");
                    return new ModelAndView(model, "profesor_form.mustache");
                }
            }

            // d) DNI o Correo no deben existir previamente en la tabla 'persons'
            boolean dniExists = Person.count("dni = ?", dni) > 0;
            boolean mailExists = Person.count("mail = ?", correo) > 0;

            if (dniExists) {
                res.status(400);
                model.put("errorMessage", "El DNI ingresado ya existe en el sistema.");
                return new ModelAndView(model, "profesor_form.mustache");
            }
            if (mailExists) {
                res.status(400);
                model.put("errorMessage", "El correo electrónico ingresado ya existe en el sistema.");
                return new ModelAndView(model, "profesor_form.mustache");
            }

            // 3. CREAR Y GUARDAR EL PROFESOR (Flujo Exitoso)
            try {
                // a) Crear y guardar la entidad 'Person'
                Person nuevaPersona = new Person();
                nuevaPersona.setNombre(nombre.trim());
                nuevaPersona.setApellido(apellido.trim());
                nuevaPersona.setMail(correo.trim());
                nuevaPersona.setDni(dni);
                
                nuevaPersona.setTel(telefono); 
                nuevaPersona.setDireccion((direccion != null && !direccion.trim().isEmpty()) ? direccion.trim() : "N/A"); 
                nuevaPersona.saveIt();

                // b) Crear y guardar la cuenta de 'User' con clave temporal segura
                SecureRandom random = new SecureRandom();
                byte[] bytes = new byte[12];
                random.nextBytes(bytes);
                String tempPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

                User nuevoUser = new User();
                nuevoUser.setName(dniStr.trim()); // Usuario = DNI
                nuevoUser.setPassword(BCrypt.hashpw(tempPassword, BCrypt.gensalt()));
                nuevoUser.setTipoUsuario("DOCENTE");
                nuevoUser.setPersonId((Integer) nuevaPersona.getId());
                nuevoUser.set("must_change_password", 1);
                nuevoUser.set("temp_password_created_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                nuevoUser.saveIt();

                // c) Crear y guardar la entidad 'Professor' asociada
                Professor nuevoProfesor = new Professor();
                nuevoProfesor.setId(nuevaPersona.getId());
                nuevoProfesor.setLegajo(dni);
                nuevoProfesor.setCargo((cargo != null && !cargo.trim().isEmpty()) ? cargo.trim() : "Docente");
                nuevoProfesor.insert();

                // 4. REDIRIGIR CON MENSAJE DE ÉXITO USANDO FLASH ATTRIBUTES (Evita leak en URL/Logs)
                req.session().attribute("flashTempPassword", tempPassword);
                req.session().attribute("flashProfName", nombre.trim() + " " + apellido.trim());
                
                res.redirect("/profesor/new");
                return null;

            } catch (Exception e) {
                System.err.println("Error al guardar profesor: " + e.getMessage());
                e.printStackTrace();
                res.status(500);
                model.put("errorMessage", "Ocurrió un error inesperado al guardar el profesor.");
                return new ModelAndView(model, "profesor_form.mustache");
            }
        }, new MustacheTemplateEngine());
    }
}
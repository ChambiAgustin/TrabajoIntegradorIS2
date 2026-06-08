package com.is1.proyecto.controllers;

import com.is1.proyecto.daos.UsuarioDAO;
import org.mindrot.jbcrypt.BCrypt;
import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static spark.Spark.*;

/**
 * Controlador para gestionar el registro público de docentes y alumnos.
 */
public class AuthController {

    private static final Pattern PATTERN_EMAIL = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    public static void registerRoutes() {

        get("/registro", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            return new ModelAndView(model, "registro.mustache");
        }, new MustacheTemplateEngine());

        post("/registro", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            List<String> errores = new ArrayList<>();

            // Recuperar parámetros
            String nombre = req.queryParams("nombre");
            String apellido = req.queryParams("apellido");
            String email = req.queryParams("email");
            String dniStr = req.queryParams("dni");
            String telStr = req.queryParams("tel");
            String direccion = req.queryParams("direccion");
            String rol = req.queryParams("rol");
            String password = req.queryParams("password");
            String confirmPassword = req.queryParams("confirm_password");

            // Conservar campos ingresados en el modelo (excepto contraseñas)
            model.put("nombre", nombre);
            model.put("apellido", apellido);
            model.put("email", email);
            model.put("dni", dniStr);
            model.put("tel", telStr);
            model.put("direccion", direccion);
            
            // Flags para restaurar el rol seleccionado
            if ("ALUMNO".equalsIgnoreCase(rol)) {
                model.put("rolAlumnoSelected", true);
            } else if ("DOCENTE".equalsIgnoreCase(rol)) {
                model.put("rolDocenteSelected", true);
            }

            // 1. Validar campos obligatorios
            if (isEmpty(nombre) || isEmpty(apellido) || isEmpty(email) || isEmpty(dniStr) || 
                isEmpty(telStr) || isEmpty(direccion) || isEmpty(rol) || 
                isEmpty(password) || isEmpty(confirmPassword)) {
                errores.add("Todos los campos son obligatorios.");
            }

            // 2. Validar formato de email
            if (!isEmpty(email) && !PATTERN_EMAIL.matcher(email.trim()).matches()) {
                errores.add("El formato del email no es válido.");
            }

            // 3. Validar tipo de rol permitido
            if (!isEmpty(rol) && !"ALUMNO".equalsIgnoreCase(rol) && !"DOCENTE".equalsIgnoreCase(rol)) {
                errores.add("Rol no permitido.");
            }

            // 4. Validar DNI y Teléfono numéricos
            int dni = 0;
            long tel = 0;
            if (!isEmpty(dniStr)) {
                try {
                    dni = Integer.parseInt(dniStr.trim());
                    if (dni <= 0) {
                        errores.add("El DNI debe ser un número positivo.");
                    }
                } catch (NumberFormatException e) {
                    errores.add("El DNI debe ser un valor numérico entero.");
                }
            }
            if (!isEmpty(telStr)) {
                try {
                    tel = Long.parseLong(telStr.trim());
                } catch (NumberFormatException e) {
                    errores.add("El teléfono debe ser un valor numérico entero.");
                }
            }

            // 5. Validar contraseñas
            if (!isEmpty(password) && !isEmpty(confirmPassword)) {
                if (password.length() < 8) {
                    errores.add("La contraseña debe tener al menos 8 caracteres.");
                }
                if (!password.equals(confirmPassword)) {
                    errores.add("Las contraseñas ingresadas no coinciden.");
                }
            }

            // 6. Si no hay errores de formato, validar duplicados en la base de datos
            if (errores.isEmpty()) {
                try {
                    if (UsuarioDAO.existeEmail(email)) {
                        errores.add("El email ingresado ya se encuentra registrado.");
                    }
                    if (UsuarioDAO.existeDni(dni)) {
                        errores.add("El DNI ingresado ya se encuentra registrado.");
                    }
                } catch (Exception e) {
                    errores.add("Error interno al validar duplicados: " + e.getMessage());
                }
            }

            // Si hay errores, volver a renderizar el formulario
            if (!errores.isEmpty()) {
                model.put("errores", errores);
                model.put("hasErrores", true);
                return new ModelAndView(model, "registro.mustache");
            }

            // Si pasa todas las validaciones, hashear contraseña y crear usuario
            try {
                String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
                UsuarioDAO.crear(
                        nombre.trim(),
                        apellido.trim(),
                        email.trim(),
                        dni,
                        tel,
                        direccion.trim(),
                        rol.trim().toUpperCase(),
                        hashedPassword
                );

                res.redirect("/registro/exito");
                return null;
            } catch (Exception e) {
                errores.add("Error al procesar el registro en la base de datos: " + e.getMessage());
                model.put("errores", errores);
                model.put("hasErrores", true);
                return new ModelAndView(model, "registro.mustache");
            }
        }, new MustacheTemplateEngine());

        get("/registro/exito", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            return new ModelAndView(model, "registro_exitoso.mustache");
        }, new MustacheTemplateEngine());
    }

    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}

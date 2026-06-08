package com.is1.proyecto.controllers;

import com.is1.proyecto.daos.UsuarioDAO;
import org.javalite.activejdbc.Base;
import com.is1.proyecto.models.Inscripcion;
import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static spark.Spark.*;

/**
 * Controlador para las acciones administrativas de aprobación, rechazo y gestión de usuarios.
 */
public class AdminController {

    public static void registerRoutes() {

        // Filtro de seguridad: solo administradores pueden acceder a las rutas de /admin/*
        before("/admin/*", (req, res) -> {
            Boolean loggedIn = req.session().attribute("loggedIn");
            String role = req.session().attribute("tipoUsuario");

            if (loggedIn == null || !loggedIn || (!"ADMIN".equalsIgnoreCase(role) && !"ADMINISTRADOR".equalsIgnoreCase(role))) {
                res.redirect("/?error=" + URLEncoder.encode("Acceso denegado: Se requieren permisos de administrador.", StandardCharsets.UTF_8));
                halt();
            }
        });

        // GET: Listar solicitudes de registro pendientes
        get("/admin/pendientes", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            initCommonModel(req, model);

            List<Map> pendientes = UsuarioDAO.listarPorEstado("pendiente");
            model.put("pendientes", pendientes);
            model.put("hasPendientes", !pendientes.isEmpty());

            // Listar solicitudes de cambio de comisión pendientes
            String sql = "SELECT sc.id_solicitud, sc.id_alumno, sc.comision_solicitada, sc.fecha, sc.anio_lectivo, sc.cuatrimestre, " +
                         "       p.nombre AS alumno_nombre, p.apellido AS alumno_apellido, p.mail AS alumno_email " +
                         "FROM solicitudes_comision sc " +
                         "JOIN persons p ON sc.id_alumno = p.id_per " +
                         "WHERE sc.estado = 'PENDIENTE' " +
                         "ORDER BY sc.fecha DESC";
            List<Map> solicitudesComision = Base.findAll(sql);
            for (Map reqMap : solicitudesComision) {
                Integer idAlumno = ((Number) reqMap.get("id_alumno")).intValue();
                Integer anio = ((Number) reqMap.get("anio_lectivo")).intValue();
                String periodo = (String) reqMap.get("cuatrimestre");
                
                String currentCommSql = "SELECT c.nombre " +
                                        "FROM inscripciones i " +
                                        "JOIN comisiones c ON i.id_comision = c.id_comision " +
                                        "WHERE i.id_alumno = ? AND i.anio_lectivo = ? AND i.cuatrimestre = ? " +
                                        "LIMIT 1";
                Object currentCommVal = Base.firstCell(currentCommSql, idAlumno, anio, periodo);
                reqMap.put("comision_actual", currentCommVal != null ? currentCommVal.toString() : "Sin asignar");
            }
            model.put("solicitudesComision", solicitudesComision);
            model.put("hasSolicitudesComision", !solicitudesComision.isEmpty());

            return new ModelAndView(model, "admin_pendientes.mustache");
        }, new MustacheTemplateEngine());

        // POST: Aprobar solicitud de registro
        post("/admin/aprobar/:id", (req, res) -> {
            String idStr = req.params(":id");
            try {
                int id = Integer.parseInt(idStr);
                UsuarioDAO.actualizarEstado(id, "activo");
                res.redirect("/admin/pendientes?message=" + URLEncoder.encode("Usuario aprobado con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/admin/pendientes?error=" + URLEncoder.encode("Error al aprobar usuario: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });

        // POST: Rechazar solicitud de registro
        post("/admin/rechazar/:id", (req, res) -> {
            String idStr = req.params(":id");
            try {
                int id = Integer.parseInt(idStr);
                UsuarioDAO.actualizarEstado(id, "rechazado");
                res.redirect("/admin/pendientes?message=" + URLEncoder.encode("Solicitud rechazada con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/admin/pendientes?error=" + URLEncoder.encode("Error al rechazar usuario: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });

        // POST: Aprobar solicitud de cambio de comisión
        post("/admin/solicitudes-comision/aprobar/:id", (req, res) -> {
            String idStr = req.params(":id");
            try {
                int idSolicitud = Integer.parseInt(idStr);
                List<Map> rows = Base.findAll("SELECT id_alumno, comision_solicitada, anio_lectivo, cuatrimestre FROM solicitudes_comision WHERE id_solicitud = ?", idSolicitud);
                if (!rows.isEmpty()) {
                    Map solicitud = rows.get(0);
                    Integer idAlumno = ((Number) solicitud.get("id_alumno")).intValue();
                    String requestedCommName = (String) solicitud.get("comision_solicitada");
                    Integer anio = ((Number) solicitud.get("anio_lectivo")).intValue();
                    String periodo = (String) solicitud.get("cuatrimestre");

                    // 1. Marcar como aprobada
                    Base.exec("UPDATE solicitudes_comision SET estado = 'APROBADO' WHERE id_solicitud = ?", idSolicitud);

                    // 2. Actualizar las inscripciones del alumno
                    List<Inscripcion> activeInscs = Inscripcion.where("id_alumno = ? AND anio_lectivo = ? AND cuatrimestre = ?", idAlumno, anio, periodo);
                    for (Inscripcion activeIns : activeInscs) {
                        int idMateria = activeIns.getInteger("id_materia");
                        Object commIdVal = Base.firstCell("SELECT id_comision FROM comisiones WHERE id_materia = ? AND nombre = ? LIMIT 1", idMateria, requestedCommName);
                        if (commIdVal != null) {
                            activeIns.set("id_comision", ((Number) commIdVal).intValue());
                            activeIns.saveIt();
                        }
                    }

                    res.redirect("/admin/pendientes?message=" + URLEncoder.encode("Solicitud de cambio de comisión aprobada con éxito.", StandardCharsets.UTF_8));
                } else {
                    res.redirect("/admin/pendientes?error=" + URLEncoder.encode("No se encontró la solicitud.", StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                res.redirect("/admin/pendientes?error=" + URLEncoder.encode("Error al aprobar la solicitud: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });

        // POST: Rechazar solicitud de cambio de comisión
        post("/admin/solicitudes-comision/rechazar/:id", (req, res) -> {
            String idStr = req.params(":id");
            try {
                int idSolicitud = Integer.parseInt(idStr);
                Base.exec("UPDATE solicitudes_comision SET estado = 'RECHAZADO' WHERE id_solicitud = ?", idSolicitud);
                res.redirect("/admin/pendientes?message=" + URLEncoder.encode("Solicitud de cambio de comisión rechazada.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/admin/pendientes?error=" + URLEncoder.encode("Error al rechazar la solicitud: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });

        // GET: Listar todos los usuarios
        get("/admin/usuarios", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            initCommonModel(req, model);

            List<Map> usuariosRaw = UsuarioDAO.listarTodos();
            List<Map> docentes = new java.util.ArrayList<>();
            Map<String, List<Map>> alumnosPorAnioMap = new java.util.LinkedHashMap<>();

            // Mapear banderas auxiliares para renderizado en Mustache
            for (Map u : usuariosRaw) {
                String estado = (String) u.get("estado");
                u.put("isActivo", "activo".equalsIgnoreCase(estado));
                u.put("isDeshabilitado", "deshabilitado".equalsIgnoreCase(estado));
                u.put("isPendiente", "pendiente".equalsIgnoreCase(estado));
                u.put("isRechazado", "rechazado".equalsIgnoreCase(estado));

                String rol = (String) u.get("rol");
                if ("DOCENTE".equalsIgnoreCase(rol)) {
                    docentes.add(u);
                } else if ("ALUMNO".equalsIgnoreCase(rol)) {
                    String creadoEn = (String) u.get("creado_en");
                    String anio = "Sin año";
                    if (creadoEn != null && creadoEn.length() >= 4) {
                        anio = creadoEn.substring(0, 4);
                    }
                    alumnosPorAnioMap.computeIfAbsent(anio, k -> new java.util.ArrayList<>()).add(u);
                }
            }

            // Convertir la estructura a una lista compatible con Mustache
            List<Map<String, Object>> alumnosAniosList = new java.util.ArrayList<>();
            int totalAlumnos = 0;
            for (Map.Entry<String, List<Map>> entry : alumnosPorAnioMap.entrySet()) {
                Map<String, Object> anioGroup = new HashMap<>();
                anioGroup.put("anio", entry.getKey());
                anioGroup.put("alumnos", entry.getValue());
                anioGroup.put("count", entry.getValue().size());
                alumnosAniosList.add(anioGroup);
                totalAlumnos += entry.getValue().size();
            }

            model.put("docentes", docentes);
            model.put("docentesCount", docentes.size());
            model.put("hasDocentes", !docentes.isEmpty());

            model.put("alumnosAniosList", alumnosAniosList);
            model.put("alumnosCount", totalAlumnos);
            model.put("hasAlumnos", totalAlumnos > 0);

            model.put("hasUsuarios", !usuariosRaw.isEmpty());

            return new ModelAndView(model, "admin_usuarios.mustache");
        }, new MustacheTemplateEngine());

        // POST: Deshabilitar cuenta activa
        post("/admin/deshabilitar/:id", (req, res) -> {
            String idStr = req.params(":id");
            try {
                int id = Integer.parseInt(idStr);
                UsuarioDAO.actualizarEstado(id, "deshabilitado");
                res.redirect("/admin/usuarios?message=" + URLEncoder.encode("Usuario deshabilitado con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/admin/usuarios?error=" + URLEncoder.encode("Error al deshabilitar usuario: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });

        // POST: Rehabilitar / Habilitar cuenta
        post("/admin/habilitar/:id", (req, res) -> {
            String idStr = req.params(":id");
            try {
                int id = Integer.parseInt(idStr);
                UsuarioDAO.actualizarEstado(id, "activo");
                res.redirect("/admin/usuarios?message=" + URLEncoder.encode("Usuario habilitado con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/admin/usuarios?error=" + URLEncoder.encode("Error al habilitar usuario: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });

        // POST: Eliminar cuenta permanentemente
        post("/admin/eliminar/:id", (req, res) -> {
            String idStr = req.params(":id");
            try {
                int id = Integer.parseInt(idStr);
                UsuarioDAO.eliminar(id);
                res.redirect("/admin/usuarios?message=" + URLEncoder.encode("Usuario eliminado con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/admin/usuarios?error=" + URLEncoder.encode("Error al eliminar usuario: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });
    }

    private static void initCommonModel(spark.Request req, Map<String, Object> model) {
        String username = req.session().attribute("currentUserUsername");
        String role = req.session().attribute("tipoUsuario");

        model.put("currentUser", username);
        model.put("userRole", role);

        String errorMsg = req.queryParams("error");
        if (errorMsg != null && !errorMsg.isEmpty()) {
            model.put("errorMessage", errorMsg);
        }
        String successMsg = req.queryParams("message");
        if (successMsg != null && !successMsg.isEmpty()) {
            model.put("successMessage", successMsg);
        }
    }
}

package com.is1.proyecto.controllers;

import com.is1.proyecto.models.Message;
import com.is1.proyecto.models.User;
import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.javalite.activejdbc.Base;

import static spark.Spark.get;
import static spark.Spark.post;

/**
 * Controlador encargado de gestionar la mensajería privada entre Alumnos y
 * Docentes.
 * Registra las rutas GET /messages y POST /messages.
 */
public class MessageController {

    public static void registerRoutes() {

        // GET: Ruta para mostrar la mensajería privada
        get("/messages", (req, res) -> {
            Map<String, Object> model = new HashMap<>();

            // 1. Verificar si el usuario está logueado
            String currentUsername = req.session().attribute("currentUserUsername");
            Boolean loggedIn = req.session().attribute("loggedIn");
            Object currentUserIdObj = req.session().attribute("userId");

            if (currentUsername == null || loggedIn == null || !loggedIn || currentUserIdObj == null) {
                res.redirect("/?error=Debes iniciar sesión para acceder a los mensajes.");
                return null;
            }

            int currentUserId = ((Number) currentUserIdObj).intValue();
            User currentUser = User.findById(currentUserId);
            if (currentUser == null) {
                res.redirect("/?error=Usuario no encontrado.");
                return null;
            }

            model.put("currentUser", currentUsername);

            // 2. Obtener lista de contactos dependiente de nuestro rol
            String userRole = currentUser.getString("tipo_usuario");
            String sidebarTitle = "Docentes Disponibles";
            String sidebarSubtitle = "Selecciona un docente para iniciar una conversación privada.";

            if ("DOCENTE".equals(userRole)) {
                sidebarTitle = "Alumnos Disponibles";
                sidebarSubtitle = "Selecciona un alumno para ver o responder su mensaje.";
            }

            model.put("sidebarTitle", sidebarTitle);
            model.put("sidebarSubtitle", sidebarSubtitle);

            String selectedWithParam = req.queryParams("with");
            Integer selectedContactId = null;
            if (selectedWithParam != null && !selectedWithParam.isEmpty()) {
                try {
                    selectedContactId = Integer.parseInt(selectedWithParam);
                } catch (NumberFormatException e) {
                    // Ignorar id inválido
                }
            }

            String selectedCommissionParam = req.queryParams("commission");
            Integer selectedCommissionId = null;
            if (selectedCommissionParam != null && !selectedCommissionParam.isEmpty()) {
                try {
                    selectedCommissionId = Integer.parseInt(selectedCommissionParam);
                } catch (NumberFormatException e) {
                    // Ignorar comisión inválida
                }
            }

            User selectedContact = null;
            if (selectedContactId != null && selectedCommissionId != null) {
                selectedContact = User.findById(selectedContactId);
                if (selectedContact != null) {
                    Base.exec(
                            "UPDATE mensajes SET leido = 1 WHERE id_receptor = ? AND id_emisor = ? AND id_comision = ?",
                            currentUserId, selectedContactId, selectedCommissionId);
                }
            }

            boolean isDocente = "DOCENTE".equals(userRole);
            model.put("isDocente", isDocente);

            String unreadSql = "SELECT id_comision, id_emisor, COUNT(*) as unread_count FROM mensajes WHERE id_receptor = ? AND leido = 0 GROUP BY id_comision, id_emisor";
            List<Map> unreadRows = Base.findAll(unreadSql, currentUserId);
            Map<String, Integer> unreadCounts = new HashMap<>();
            for (Map uRow : unreadRows) {
                Integer cId = ((Number) uRow.get("id_comision")).intValue();
                Integer eId = ((Number) uRow.get("id_emisor")).intValue();
                Integer count = ((Number) uRow.get("unread_count")).intValue();
                unreadCounts.put(cId + "-" + eId, count);
            }

            String lastMsgSql = "SELECT id_comision, CASE WHEN id_emisor = ? THEN id_receptor ELSE id_emisor END as contact_id, MAX(fecha) as max_fecha FROM mensajes WHERE id_emisor = ? OR id_receptor = ? GROUP BY id_comision, contact_id";
            List<Map> lastMsgRows = Base.findAll(lastMsgSql, currentUserId, currentUserId, currentUserId);
            Map<String, String> lastMessageDates = new HashMap<>();
            for (Map mRow : lastMsgRows) {
                Integer cId = ((Number) mRow.get("id_comision")).intValue();
                Integer contactId = ((Number) mRow.get("contact_id")).intValue();
                String date = (String) mRow.get("max_fecha");
                lastMessageDates.put(cId + "-" + contactId, date);
            }

            int idPersona = currentUser.getInteger("id_persona") != null ? currentUser.getInteger("id_persona") : 0;
            String sql;
            if (isDocente) {
                sql = "SELECT m.id_materia, m.nombre AS materia_nombre, m.codigo AS materia_codigo, " +
                        "       c.id_comision, c.horario AS comision_horario, " +
                        "       u.id AS contact_user_id, u.name AS contact_username, " +
                        "       p.nombre AS contact_nombre, p.apellido AS contact_apellido " +
                        "FROM docente_comision dc " +
                        "JOIN comisiones c ON dc.id_comision = c.id_comision " +
                        "JOIN materias m ON c.id_materia = m.id_materia " +
                        "JOIN inscripciones i ON c.id_comision = i.id_comision " +
                        "JOIN users u ON i.id_alumno = u.id_persona " +
                        "JOIN persons p ON u.id_persona = p.id_per " +
                        "WHERE dc.id_prof = ? " +
                        "ORDER BY m.nombre, c.horario, p.apellido, p.nombre";
            } else {
                sql = "SELECT m.id_materia, m.nombre AS materia_nombre, m.codigo AS materia_codigo, " +
                        "       c.id_comision, c.horario AS comision_horario, " +
                        "       u.id AS contact_user_id, u.name AS contact_username, " +
                        "       p.nombre AS contact_nombre, p.apellido AS contact_apellido " +
                        "FROM inscripciones i " +
                        "JOIN comisiones c ON i.id_comision = c.id_comision " +
                        "JOIN materias m ON c.id_materia = m.id_materia " +
                        "JOIN docente_comision dc ON c.id_comision = dc.id_comision " +
                        "JOIN users u ON dc.id_prof = u.id_persona " +
                        "JOIN persons p ON u.id_persona = p.id_per " +
                        "WHERE i.id_alumno = ? " +
                        "ORDER BY m.nombre, c.horario, p.apellido, p.nombre";
            }

            List<Map> rows = Base.findAll(sql, idPersona);

            List<Map<String, Object>> subjectsList = new ArrayList<>();
            Map<Integer, Map<String, Object>> subjectsMap = new LinkedHashMap<>();
            Map<Integer, Map<String, Object>> commissionsMap = new LinkedHashMap<>();

            for (Map row : rows) {
                Integer materiaId = ((Number) row.get("id_materia")).intValue();
                Integer comisionId = ((Number) row.get("id_comision")).intValue();
                Integer contactUserId = ((Number) row.get("contact_user_id")).intValue();
                String contactUsername = (String) row.get("contact_username");
                String contactNombre = (String) row.get("contact_nombre");
                String contactApellido = (String) row.get("contact_apellido");

                // 1. Get or create subject
                Map<String, Object> subject = subjectsMap.get(materiaId);
                if (subject == null) {
                    subject = new HashMap<>();
                    subject.put("id", materiaId);
                    subject.put("nombre", row.get("materia_nombre"));
                    subject.put("codigo", row.get("materia_codigo"));
                    subject.put("comisiones", new ArrayList<Map<String, Object>>());
                    subject.put("unreadCount", 0);
                    subject.put("hasUnread", false);
                    subjectsMap.put(materiaId, subject);
                    subjectsList.add(subject);
                }

                // 2. Get or create commission
                Map<String, Object> commission = commissionsMap.get(comisionId);
                if (commission == null) {
                    commission = new HashMap<>();
                    commission.put("id", comisionId);
                    commission.put("horario", row.get("comision_horario"));
                    commission.put("contacts", new ArrayList<Map<String, Object>>());
                    commission.put("unreadCount", 0);
                    commission.put("hasUnread", false);
                    commissionsMap.put(comisionId, commission);

                    List<Map<String, Object>> comisionesList = (List<Map<String, Object>>) subject.get("comisiones");
                    comisionesList.add(commission);
                }

                // 3. Create contact entry
                Map<String, Object> contact = new HashMap<>();
                contact.put("id", contactUserId);
                contact.put("name", contactUsername);
                String fullName = contactApellido + ", " + contactNombre;
                contact.put("fullName", fullName);

                String initials = "";
                if (contactNombre != null && !contactNombre.isEmpty() && contactApellido != null
                        && !contactApellido.isEmpty()) {
                    initials = contactNombre.substring(0, 1).toUpperCase()
                            + contactApellido.substring(0, 1).toUpperCase();
                } else if (contactUsername != null && contactUsername.length() >= 2) {
                    initials = contactUsername.substring(0, 2).toUpperCase();
                } else {
                    initials = "CO";
                }
                contact.put("initials", initials);

                boolean isSelected = selectedContactId != null && selectedContactId.equals(contactUserId) &&
                        selectedCommissionId != null && selectedCommissionId.equals(comisionId);
                contact.put("selected", isSelected);
                contact.put("commissionId", comisionId);

                int unreadCount = unreadCounts.getOrDefault(comisionId + "-" + contactUserId, 0);
                contact.put("unreadCount", unreadCount);
                contact.put("hasUnread", unreadCount > 0);

                int commUnread = (Integer) commission.get("unreadCount") + unreadCount;
                commission.put("unreadCount", commUnread);
                commission.put("hasUnread", commUnread > 0);

                int subjUnread = (Integer) subject.get("unreadCount") + unreadCount;
                subject.put("unreadCount", subjUnread);
                subject.put("hasUnread", subjUnread > 0);

                String lastMsgDate = lastMessageDates.get(comisionId + "-" + contactUserId);
                contact.put("lastMessageDate", lastMsgDate);

                List<Map<String, Object>> contactsList = (List<Map<String, Object>>) commission.get("contacts");
                contactsList.add(contact);
            }

            for (Map<String, Object> subj : subjectsList) {
                List<Map<String, Object>> comms = (List<Map<String, Object>>) subj.get("comisiones");
                if (comms != null) {
                    for (Map<String, Object> comm : comms) {
                        List<Map<String, Object>> contactsList = (List<Map<String, Object>>) comm.get("contacts");
                        if (contactsList != null) {
                            contactsList.sort((c1, c2) -> {
                                String d1 = (String) c1.get("lastMessageDate");
                                String d2 = (String) c2.get("lastMessageDate");
                                if (d1 == null && d2 == null)
                                    return 0;
                                if (d1 == null)
                                    return 1;
                                if (d2 == null)
                                    return -1;
                                return d1.compareTo(d2);
                            });
                        }
                    }
                }
            }

            model.put("subjects", subjectsList);

            // 3. Si hay un contacto seleccionado, recuperar el hilo de conversación
            if (selectedContact != null && selectedCommissionId != null) {
                Map<String, Object> selContactMap = new HashMap<>();
                selContactMap.put("id", selectedContact.getInteger("id"));
                String scName = selectedContact.getString("name");
                selContactMap.put("name", scName);
                model.put("selectedContact", selContactMap);

                String initials = scName.length() >= 2 ? scName.substring(0, 2).toUpperCase() : scName.toUpperCase();
                model.put("selectedContactInitials", initials);

                model.put("selectedCommissionId", selectedCommissionId);

                // Recuperar mensajes intercambiados entre emisor y receptor para esa comisión
                // (ordenados por fecha)
                List<Message> dbMessages = Message.where(
                        "((id_emisor = ? AND id_receptor = ?) OR (id_emisor = ? AND id_receptor = ?)) AND id_comision = ?",
                        currentUserId, selectedContactId, selectedContactId, currentUserId, selectedCommissionId)
                        .orderBy("fecha ASC, id_mensaje ASC");

                List<Map<String, Object>> messagesList = new ArrayList<>();
                for (Message m : dbMessages) {
                    Map<String, Object> mMap = new HashMap<>();
                    mMap.put("contenido", m.getContenido());
                    mMap.put("fecha", m.getFecha());

                    boolean isSender = m.getEmisorId().equals(currentUserId);
                    mMap.put("isSender", isSender);
                    messagesList.add(mMap);
                }
                model.put("messages", messagesList);
            }

            return new ModelAndView(model, "messages.mustache");
        }, new MustacheTemplateEngine());

        // POST: Maneja el envío de un nuevo mensaje privado
        post("/messages", (req, res) -> {
            // 1. Verificar si el usuario está logueado
            String currentUsername = req.session().attribute("currentUserUsername");
            Boolean loggedIn = req.session().attribute("loggedIn");
            Object currentUserIdObj = req.session().attribute("userId");

            if (currentUsername == null || loggedIn == null || !loggedIn || currentUserIdObj == null) {
                res.status(401);
                res.redirect("/?error=Sesión expirada.");
                return "";
            }

            int currentUserId = ((Number) currentUserIdObj).intValue();
            User currentUser = User.findById(currentUserId);
            if (currentUser == null) {
                res.status(404);
                res.redirect("/?error=Usuario no encontrado.");
                return "";
            }

            String userRole = currentUser.getString("tipo_usuario");

            // 2. Obtener y validar parámetros
            String idReceptorStr = req.queryParams("id_receptor");
            String contenido = req.queryParams("contenido");
            String idComisionStr = req.queryParams("id_comision");

            if (idReceptorStr == null || idReceptorStr.isEmpty() || contenido == null || contenido.trim().isEmpty()
                    || idComisionStr == null || idComisionStr.isEmpty()) {
                res.status(400);
                res.redirect("/messages?error=El contenido, el receptor y la comisión son requeridos.");
                return "";
            }

            int idReceptor;
            int idComision;
            try {
                idReceptor = Integer.parseInt(idReceptorStr);
                idComision = Integer.parseInt(idComisionStr);
            } catch (NumberFormatException e) {
                res.status(400);
                res.redirect("/messages?error=Receptor o comisión inválidos.");
                return "";
            }

            // Validar que el receptor exista
            User receptor = User.findById(idReceptor);
            if (receptor == null) {
                res.status(404);
                res.redirect("/messages?error=El destinatario no existe.");
                return "";
            }

            String receptorRole = receptor.getString("tipo_usuario");

            // Validar reglas de envío entre roles:
            // - Si el emisor es ALUMNO, el receptor debe ser DOCENTE
            // - Si el emisor es DOCENTE, el receptor debe ser ALUMNO
            if ("ALUMNO".equals(userRole) && !"DOCENTE".equals(receptorRole)) {
                res.status(403);
                res.redirect("/messages?error=Los alumnos solo pueden enviar mensajes a docentes.");
                return "";
            } else if ("DOCENTE".equals(userRole) && !"ALUMNO".equals(receptorRole)) {
                res.status(403);
                res.redirect("/messages?error=Los docentes solo pueden responder a alumnos.");
                return "";
            } else if (!"ALUMNO".equals(userRole) && !"DOCENTE".equals(userRole)) {
                res.status(403);
                res.redirect("/messages?error=Rol no autorizado para mensajería.");
                return "";
            }

            // Validar que compartan la comisión/materia y que coincida con idComision
            boolean compartenComision = false;
            if ("ALUMNO".equals(userRole)) {
                compartenComision = User.count(
                        "id = ? AND id_persona IN (" +
                                "  SELECT dc.id_prof FROM docente_comision dc " +
                                "  WHERE dc.id_comision = ?" +
                                ") AND ? IN (" +
                                "  SELECT id_comision FROM inscripciones " +
                                "  WHERE id_alumno = (SELECT id_persona FROM users WHERE id = ?)" +
                                ")",
                        idReceptor, idComision, idComision, currentUserId) > 0;
            } else if ("DOCENTE".equals(userRole)) {
                compartenComision = User.count(
                        "id = ? AND id_persona IN (" +
                                "  SELECT i.id_alumno FROM inscripciones i " +
                                "  WHERE i.id_comision = ?" +
                                ") AND ? IN (" +
                                "  SELECT id_comision FROM docente_comision " +
                                "  WHERE id_prof = (SELECT id_persona FROM users WHERE id = ?)" +
                                ")",
                        idReceptor, idComision, idComision, currentUserId) > 0;
            }

            if (!compartenComision) {
                res.status(403);
                res.redirect("/messages?error=No tienes permitido enviar mensajes en esta comisión.");
                return "";
            }

            // 3. Crear y guardar el mensaje
            try {
                Message message = new Message();
                message.setEmisorId(currentUserId);
                message.setReceptorId(idReceptor);
                message.setContenido(contenido.trim());
                message.setComisionId(idComision);

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                message.setFecha(timestamp);
                message.setLeido(false);
                message.saveIt();

                res.status(201);
                res.redirect("/messages?with=" + idReceptor + "&commission=" + idComision);
                return "";
            } catch (Exception e) {
                System.err.println("Error al guardar mensaje: " + e.getMessage());
                e.printStackTrace();
                res.status(500);
                res.redirect("/messages?with=" + idReceptor + "&commission=" + idComision
                        + "&error=Error interno al enviar el mensaje.");
                return "";
            }
        });

    } // Fin del método registerRoutes
} // Fin de la clase MessageController

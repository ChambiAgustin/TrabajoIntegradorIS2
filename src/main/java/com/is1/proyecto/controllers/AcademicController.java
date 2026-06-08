package com.is1.proyecto.controllers;

import com.is1.proyecto.models.*;
import spark.ModelAndView;
import spark.template.mustache.MustacheTemplateEngine;
import org.javalite.activejdbc.Base;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static spark.Spark.*;

import java.util.stream.Collectors;

import org.mindrot.jbcrypt.BCrypt;

import javax.servlet.MultipartConfigElement;
import javax.servlet.http.Part;
import java.io.*;


public class AcademicController {

    public static void registerRoutes() {

        // --- FILTRO DE SEGURIDAD / CONTROL DE ACCESO ---
        // Este filtro se ejecuta para interceptar accesos no autorizados a los módulos protegidos.
        before("/inscripcion*", (req, res) -> checkAuth(req, res, List.of("ALUMNO")));
        before("/planificacion*", (req, res) -> checkAuth(req, res, List.of("DOCENTE", "ADMIN", "ADMINISTRADOR")));
        before("/comunidad*", (req, res) -> checkAuth(req, res, List.of("ALUMNO", "DOCENTE")));
        before("/evaluacion*", (req, res) -> checkAuth(req, res, List.of("DOCENTE")));
        before("/reportes*", (req, res) -> checkAuth(req, res, List.of("ADMIN", "ADMINISTRADOR")));
        before("/notificaciones*", (req, res) -> checkAuth(req, res, List.of("ALUMNO", "DOCENTE", "ADMIN", "ADMINISTRADOR")));
        before("/situacion-academica*", (req, res) -> checkAuth(req, res, List.of("ALUMNO")));

        // --- MÓDULO DE INSCRIPCIÓN (Alumnos) ---

        get("/inscripcion", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            initCommonModel(req, model);

            int currentUserId = req.session().attribute("userId");
            User currentUser = User.findById(currentUserId);
            Integer idPersona = currentUser.getInteger("id_persona");

            boolean inscripcionActiva = false;
            String periodoAbierto = "";
            String anioAbierto = "2026";
            try {
                Object val = Base.firstCell("SELECT valor FROM configuraciones WHERE clave = 'inscripcion_activa'");
                inscripcionActiva = "true".equals(val);
                Object pVal = Base.firstCell("SELECT valor FROM configuraciones WHERE clave = 'inscripcion_periodo'");
                periodoAbierto = pVal != null ? pVal.toString() : "";
                Object yVal = Base.firstCell("SELECT valor FROM configuraciones WHERE clave = 'inscripcion_anio'");
                anioAbierto = yVal != null ? yVal.toString() : "2026";
            } catch (Exception e) {
                inscripcionActiva = true;
            }
            model.put("inscripcionActiva", inscripcionActiva);
            model.put("inscripcionPeriodo", periodoAbierto);
            model.put("inscripcionAnio", anioAbierto);

            // 1. Obtener todas las condiciones del alumno en todo su historial
            List<Map> studentInsc = Base.findAll("SELECT id_materia, condicion FROM inscripciones WHERE id_alumno = ?", idPersona);
            Map<Integer, String> studentConditions = new HashMap<>();
            for (Map si : studentInsc) {
                Integer mId = ((Number) si.get("id_materia")).intValue();
                String cond = (String) si.get("condicion");
                String existing = studentConditions.get(mId);
                if (existing == null || ("R".equals(existing) && "A".equals(cond))) {
                    studentConditions.put(mId, cond);
                }
            }

            // 2. Obtener todas las correlatividades
            List<Map> allCorrelatives = Base.findAll("SELECT id_materia, id_correlativa, tipo FROM correlativas");
            Map<Integer, List<Map>> correlativesMap = new HashMap<>();
            for (Map c : allCorrelatives) {
                Integer mId = ((Number) c.get("id_materia")).intValue();
                correlativesMap.computeIfAbsent(mId, k -> new ArrayList<>()).add(c);
            }

            // 3. Obtener materias e indicación de si el alumno está inscrito en el período/año actual y su comisión asignada (si la hay)
            String sql = "SELECT m.id_materia, m.nombre AS materia_nombre, m.codigo AS materia_codigo, " +
                         "       m.anio AS materia_anio, m.periodo AS materia_periodo, " +
                         "       (i.id_inscripcion IS NOT NULL) AS inscrito, " +
                         "       c.nombre AS comision_nombre, c.horario AS comision_horario " +
                         "FROM materias m " +
                         "LEFT JOIN inscripciones i ON m.id_materia = i.id_materia " +
                         "    AND i.id_alumno = ? " +
                         "    AND i.anio_lectivo = ? " +
                         "    AND i.cuatrimestre = ? " +
                         "LEFT JOIN comisiones c ON i.id_comision = c.id_comision " +
                         "ORDER BY m.anio, m.periodo, m.nombre";
            List<Map> rows = Base.findAll(sql, idPersona, Integer.parseInt(anioAbierto), periodoAbierto);

            List<Map<String, Object>> subjects = new ArrayList<>();
            for (Map row : rows) {
                int mId = ((Number) row.get("id_materia")).intValue();
                boolean inscrito = ((Number) row.get("inscrito")).intValue() > 0;
                String currentCond = studentConditions.get(mId);

                // Si está inscrito activamente (no aprobado y no regularizado) en el periodo actual lo mostramos
                boolean isActivamenteInscrito = inscrito && !"A".equals(currentCond) && !"R".equals(currentCond);
                
                // Si ya aprobó o regularizó la materia en el historial, no la mostramos para inscribir
                if (!isActivamenteInscrito) {
                    if ("A".equals(currentCond) || "R".equals(currentCond)) {
                        continue;
                    }
                }

                // Si no está inscrito en el periodo actual, comprobar que cumpla las correlativas y el período habilitado
                if (!inscrito) {
                    String matPeriodo = (String) row.get("materia_periodo");
                    boolean matchesPeriod = false;
                    if ("1° Cuatrimestre".equals(periodoAbierto)) {
                        matchesPeriod = "1° Cuatrimestre".equals(matPeriodo) || "Anual".equals(matPeriodo);
                    } else if ("2° Cuatrimestre".equals(periodoAbierto)) {
                        matchesPeriod = "2° Cuatrimestre".equals(matPeriodo);
                    }
                    
                    if (!matchesPeriod) {
                        continue;
                    }

                    List<Map> reqs = correlativesMap.get(mId);
                    boolean cumpleCorrelativas = true;
                    if (reqs != null) {
                        for (Map r : reqs) {
                            int reqId = ((Number) r.get("id_correlativa")).intValue();
                            String reqTipo = (String) r.get("tipo");
                            String cond = studentConditions.get(reqId);

                            if ("A".equals(reqTipo)) {
                                if (cond == null || !"A".equals(cond)) {
                                    cumpleCorrelativas = false;
                                    break;
                                }
                            } else if ("R".equals(reqTipo)) {
                                if (cond == null || (!"A".equals(cond) && !"R".equals(cond))) {
                                    cumpleCorrelativas = false;
                                    break;
                                }
                            }
                        }
                    }

                    // Si no cumple correlativas, la ocultamos de la lista
                    if (!cumpleCorrelativas) {
                        continue;
                    }
                }

                Map<String, Object> subject = new HashMap<>();
                subject.put("id", mId);
                subject.put("nombre", row.get("materia_nombre"));
                subject.put("codigo", row.get("materia_codigo"));
                subject.put("anio", row.get("materia_anio"));
                subject.put("periodo", row.get("materia_periodo"));
                subject.put("inscrito", isActivamenteInscrito);
                
                Object commNombre = row.get("comision_nombre");
                if (isActivamenteInscrito && commNombre != null) {
                    subject.put("comision_nombre", commNombre);
                    subject.put("comision_horario", row.get("comision_horario"));
                    subject.put("has_comision", true);
                } else {
                    subject.put("has_comision", false);
                }
                subjects.add(subject);
            }

            // Recuperar el nombre de la comisión asignada actual del alumno (si la hay)
            String currentCommissionName = null;
            String currentCommSql = "SELECT c.nombre " +
                                    "FROM inscripciones i " +
                                    "JOIN comisiones c ON i.id_comision = c.id_comision " +
                                    "WHERE i.id_alumno = ? AND i.anio_lectivo = ? AND i.cuatrimestre = ? " +
                                    "LIMIT 1";
            Object currentCommVal = Base.firstCell(currentCommSql, idPersona, Integer.parseInt(anioAbierto), periodoAbierto);
            if (currentCommVal != null) {
                currentCommissionName = currentCommVal.toString();
            }

            // Recuperar el nombre de la comisión solicitada (pendiente) si la hay
            String pendingCommissionName = null;
            String pendingSql = "SELECT comision_solicitada FROM solicitudes_comision WHERE id_alumno = ? AND estado = 'PENDIENTE' LIMIT 1";
            Object pendingVal = Base.firstCell(pendingSql, idPersona);
            if (pendingVal != null) {
                pendingCommissionName = pendingVal.toString();
            }

            // Obtener todos los nombres de comisiones únicas disponibles
            List<Map> commNamesRows = Base.findAll("SELECT DISTINCT nombre FROM comisiones ORDER BY nombre");
            List<Map<String, Object>> commissionOptions = new ArrayList<>();
            for (Map rName : commNamesRows) {
                String name = (String) rName.get("nombre");
                Map<String, Object> opt = new HashMap<>();
                opt.put("nombre", name);
                opt.put("selected", name.equals(currentCommissionName));
                commissionOptions.add(opt);
            }

            boolean hasActiveEnrollments = false;
            for (Map<String, Object> s : subjects) {
                if ((Boolean) s.get("inscrito")) {
                    hasActiveEnrollments = true;
                    break;
                }
            }

            model.put("currentCommissionName", currentCommissionName);
            model.put("pendingCommissionName", pendingCommissionName);
            model.put("hasPendingRequest", pendingCommissionName != null);
            model.put("commissionOptions", commissionOptions);
            model.put("hasActiveEnrollments", hasActiveEnrollments);

            model.put("subjects", subjects);
            return new ModelAndView(model, "inscripcion.mustache");
        }, new MustacheTemplateEngine());

        post("/inscripcion", (req, res) -> {
            boolean inscripcionActiva = false;
            String periodoAbierto = "";
            String anioAbierto = "2026";
            try {
                Object val = Base.firstCell("SELECT valor FROM configuraciones WHERE clave = 'inscripcion_activa'");
                inscripcionActiva = "true".equals(val);
                Object pVal = Base.firstCell("SELECT valor FROM configuraciones WHERE clave = 'inscripcion_periodo'");
                periodoAbierto = pVal != null ? pVal.toString() : "";
                Object yVal = Base.firstCell("SELECT valor FROM configuraciones WHERE clave = 'inscripcion_anio'");
                anioAbierto = yVal != null ? yVal.toString() : "2026";
            } catch (Exception e) {
                inscripcionActiva = true;
            }
            if (!inscripcionActiva) {
                res.redirect("/inscripcion?error=" + URLEncoder.encode("El periodo de inscripción a materias se encuentra cerrado.", StandardCharsets.UTF_8));
                return null;
            }

            int currentUserId = req.session().attribute("userId");
            User currentUser = User.findById(currentUserId);
            Integer idPersona = currentUser.getInteger("id_persona");

            String idMateriaStr = req.queryParams("id_materia");
            if (idMateriaStr == null || idMateriaStr.isEmpty()) {
                res.redirect("/inscripcion?error=" + URLEncoder.encode("Materia requerida.", StandardCharsets.UTF_8));
                return null;
            }

            int idMateria = Integer.parseInt(idMateriaStr);
            Materia mat = Materia.findById(idMateria);
            if (mat == null) {
                res.redirect("/inscripcion?error=" + URLEncoder.encode("La materia no existe.", StandardCharsets.UTF_8));
                return null;
            }

            // Validar cuatrimestre de la materia
            String matPeriodo = mat.getString("periodo");
            boolean matchesPeriod = false;
            if ("1° Cuatrimestre".equals(periodoAbierto)) {
                matchesPeriod = "1° Cuatrimestre".equals(matPeriodo) || "Anual".equals(matPeriodo);
            } else if ("2° Cuatrimestre".equals(periodoAbierto)) {
                matchesPeriod = "2° Cuatrimestre".equals(matPeriodo);
            }
            if (!matchesPeriod) {
                res.redirect("/inscripcion?error=" + URLEncoder.encode("No puedes inscribirte a una materia del período no habilitado.", StandardCharsets.UTF_8));
                return null;
            }

            // Validar si ya está inscrito en el período actual
            Object currentInsVal = Base.firstCell("SELECT id_inscripcion FROM inscripciones WHERE id_alumno = ? AND id_materia = ? AND anio_lectivo = ? AND cuatrimestre = ?", idPersona, idMateria, Integer.parseInt(anioAbierto), periodoAbierto);
            if (currentInsVal != null) {
                res.redirect("/inscripcion?error=" + URLEncoder.encode("Ya te encuentras inscrito en esta materia para el período actual.", StandardCharsets.UTF_8));
                return null;
            }

            // Validar si ya aprobó o regularizó esta materia en el historial general
            List<Map> pastInscs = Base.findAll("SELECT condicion FROM inscripciones WHERE id_alumno = ? AND id_materia = ?", idPersona, idMateria);
            for (Map pi : pastInscs) {
                String cond = (String) pi.get("condicion");
                if ("A".equals(cond) || "R".equals(cond)) {
                    res.redirect("/inscripcion?error=" + URLEncoder.encode("Ya tienes aprobada o regularizada esta materia.", StandardCharsets.UTF_8));
                    return null;
                }
            }

            // Validar correlativas
            List<Map> reqs = Base.findAll("SELECT id_correlativa, tipo FROM correlativas WHERE id_materia = ?", idMateria);
            for (Map r : reqs) {
                int reqId = ((Number) r.get("id_correlativa")).intValue();
                String reqTipo = (String) r.get("tipo");
                
                // Buscar si cumple la correlativa en todo su historial
                List<Map> condVals = Base.findAll("SELECT condicion FROM inscripciones WHERE id_alumno = ? AND id_materia = ?", idPersona, reqId);
                String cond = null;
                for (Map cv : condVals) {
                    String c = (String) cv.get("condicion");
                    if ("A".equals(c)) {
                        cond = "A";
                    } else if ("R".equals(c) && !"A".equals(cond)) {
                        cond = "R";
                    }
                }
                
                if ("A".equals(reqTipo)) {
                    if (cond == null || !"A".equals(cond)) {
                        res.redirect("/inscripcion?error=" + URLEncoder.encode("No cumples con las correlatividades para inscribirte en esta materia.", StandardCharsets.UTF_8));
                        return null;
                    }
                } else if ("R".equals(reqTipo)) {
                    if (cond == null || (!"A".equals(cond) && !"R".equals(cond))) {
                        res.redirect("/inscripcion?error=" + URLEncoder.encode("No cumples con las correlatividades para inscribirte en esta materia.", StandardCharsets.UTF_8));
                        return null;
                    }
                }
            }

            // Autodetectar e inscribir en comisión balanceada
            Integer assignedComisionId = null;

            // 1. Verificar si el alumno ya tiene alguna comisión asignada en este cuatrimestre
            String existingCommSql = "SELECT c.nombre " +
                                     "FROM inscripciones i " +
                                     "JOIN comisiones c ON i.id_comision = c.id_comision " +
                                     "WHERE i.id_alumno = ? AND i.anio_lectivo = ? AND i.cuatrimestre = ? " +
                                     "LIMIT 1";
            Object existingCommName = Base.firstCell(existingCommSql, idPersona, Integer.parseInt(anioAbierto), periodoAbierto);

            if (existingCommName != null) {
                // El alumno ya tiene una comisión asignada en otra materia. Buscamos si existe la misma comisión en la nueva materia.
                String nameToFind = existingCommName.toString();
                Object sameCommId = Base.firstCell("SELECT id_comision FROM comisiones WHERE id_materia = ? AND nombre = ? LIMIT 1", idMateria, nameToFind);
                if (sameCommId != null) {
                    assignedComisionId = ((Number) sameCommId).intValue();
                }
            }

            if (assignedComisionId == null) {
                // No tiene comisión asignada aún, o la materia no posee la comisión de su cohorte.
                // Aplicamos algoritmo de balanceo:
                // Consultamos las comisiones disponibles para esta materia
                List<Map> availableComms = Base.findAll("SELECT id_comision, nombre FROM comisiones WHERE id_materia = ? ORDER BY nombre", idMateria);
                if (!availableComms.isEmpty()) {
                    if (availableComms.size() == 1) {
                        assignedComisionId = ((Number) availableComms.get(0).get("id_comision")).intValue();
                    } else {
                        // Contar cuántos alumnos tiene asignado cada nombre de comisión en este cuatrimestre
                        String countSql = "SELECT c.nombre, COUNT(DISTINCT i.id_alumno) AS cnt " +
                                          "FROM inscripciones i " +
                                          "JOIN comisiones c ON i.id_comision = c.id_comision " +
                                          "WHERE i.anio_lectivo = ? AND i.cuatrimestre = ? " +
                                          "GROUP BY c.nombre";
                        List<Map> counts = Base.findAll(countSql, Integer.parseInt(anioAbierto), periodoAbierto);
                        Map<String, Integer> countMap = new HashMap<>();
                        for (Map row : counts) {
                            countMap.put((String) row.get("nombre"), ((Number) row.get("cnt")).intValue());
                        }

                        // Elegir la comisión disponible con menor cantidad de alumnos inscritos en su grupo
                        Map chosenComm = null;
                        int minCount = Integer.MAX_VALUE;
                        for (Map comm : availableComms) {
                            String name = (String) comm.get("nombre");
                            int cnt = countMap.getOrDefault(name, 0);
                            if (cnt < minCount) {
                                minCount = cnt;
                                chosenComm = comm;
                            }
                        }
                        if (chosenComm != null) {
                            assignedComisionId = ((Number) chosenComm.get("id_comision")).intValue();
                        }
                    }
                }
            }

            // Registrar inscripción
            Inscripcion ins = new Inscripcion();
            ins.set("id_alumno", idPersona);
            ins.set("id_materia", idMateria);
            ins.set("id_comision", assignedComisionId);
            ins.set("fecha", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            ins.set("anio_lectivo", Integer.parseInt(anioAbierto));
            ins.set("cuatrimestre", periodoAbierto);
            ins.saveIt();

            // Enviar alerta de confirmación
            Notification notif = new Notification();
            notif.set("id_usuario", currentUserId);
            notif.set("contenido", "Te has inscrito con éxito en la materia: " + mat.getString("nombre"));
            notif.set("fecha", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            notif.set("leida", false);
            notif.saveIt();

            res.redirect("/inscripcion?message=" + URLEncoder.encode("Inscripción realizada exitosamente.", StandardCharsets.UTF_8));
            return null;
        });

        post("/inscripcion/solicitar-cambio", (req, res) -> {
            boolean inscripcionActiva = false;
            String periodoAbierto = "";
            String anioAbierto = "2026";
            try {
                Object val = Base.firstCell("SELECT valor FROM configuraciones WHERE clave = 'inscripcion_activa'");
                inscripcionActiva = "true".equals(val);
                Object pVal = Base.firstCell("SELECT valor FROM configuraciones WHERE clave = 'inscripcion_periodo'");
                periodoAbierto = pVal != null ? pVal.toString() : "";
                Object yVal = Base.firstCell("SELECT valor FROM configuraciones WHERE clave = 'inscripcion_anio'");
                anioAbierto = yVal != null ? yVal.toString() : "2026";
            } catch (Exception e) {
                inscripcionActiva = true;
            }
            if (!inscripcionActiva) {
                res.redirect("/inscripcion?error=" + URLEncoder.encode("El periodo de inscripción se encuentra cerrado.", StandardCharsets.UTF_8));
                return null;
            }

            int currentUserId = req.session().attribute("userId");
            User currentUser = User.findById(currentUserId);
            Integer idPersona = currentUser.getInteger("id_persona");

            String newCommName = req.queryParams("nueva_comision");
            if (newCommName == null || newCommName.trim().isEmpty()) {
                res.redirect("/inscripcion?error=" + URLEncoder.encode("Comisión no válida.", StandardCharsets.UTF_8));
                return null;
            }

            // Eliminar cualquier solicitud pendiente previa del alumno en este período
            Base.exec("DELETE FROM solicitudes_comision WHERE id_alumno = ? AND estado = 'PENDIENTE'", idPersona);

            // Crear la solicitud de cambio
            Base.exec("INSERT INTO solicitudes_comision (id_alumno, comision_solicitada, fecha, estado, anio_lectivo, cuatrimestre) VALUES (?, ?, ?, ?, ?, ?)",
                      idPersona,
                      newCommName.trim(),
                      LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                      "PENDIENTE",
                      Integer.parseInt(anioAbierto),
                      periodoAbierto);

            res.redirect("/inscripcion?message=" + URLEncoder.encode("Solicitud de cambio de comisión enviada con éxito.", StandardCharsets.UTF_8));
            return null;
        });


        // --- MÓDULO DE PLANIFICACIÓN (Docentes y Admins) ---

        get("/planificacion", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            initCommonModel(req, model);

            String userRole = req.session().attribute("tipoUsuario");
            boolean isAdmin = "ADMIN".equals(userRole) || "ADMINISTRADOR".equals(userRole);
            model.put("isAdmin", isAdmin);

            if (isAdmin) {
                boolean inscripcionActiva = false;
                String inscripcionPeriodo = "";
                String inscripcionAnio = "";
                try {
                    Object val = Base.firstCell("SELECT valor FROM configuraciones WHERE clave = 'inscripcion_activa'");
                    inscripcionActiva = "true".equals(val);
                    Object pVal = Base.firstCell("SELECT valor FROM configuraciones WHERE clave = 'inscripcion_periodo'");
                    inscripcionPeriodo = pVal != null ? pVal.toString() : "";
                    Object yVal = Base.firstCell("SELECT valor FROM configuraciones WHERE clave = 'inscripcion_anio'");
                    inscripcionAnio = yVal != null ? yVal.toString() : "";
                } catch (Exception e) {
                    inscripcionActiva = true;
                }
                model.put("inscripcionActiva", inscripcionActiva);
                model.put("inscripcionPeriodo", inscripcionPeriodo);
                model.put("inscripcionAnio", inscripcionAnio);
            }

            // 1. Obtener todas las materias según el rol para el Plan de Estudio
            List<Materia> materiasList;
            
            if (isAdmin) {
                // Admins see all subjects
                materiasList = Materia.findAll().orderBy("anio ASC, periodo ASC, nombre ASC");
                
                // Obtener el conteo de comisiones activas por materia
                List<Map> countRows = Base.findAll("SELECT id_materia, COUNT(*) as c_count FROM comisiones GROUP BY id_materia");
                Map<Integer, Integer> commCounts = new HashMap<>();
                for (Map row : countRows) {
                    commCounts.put(((Number) row.get("id_materia")).intValue(), ((Number) row.get("c_count")).intValue());
                }

                // 2. Construir la estructura jerárquica para el Plan de Estudio: Años -> Periodos -> Materias
                Map<Integer, Map<String, List<Map<String, Object>>>> planMap = new LinkedHashMap<>();
                
                for (Materia mat : materiasList) {
                    Integer anio = mat.getAnio();
                    if (anio == null) anio = 1;
                    String periodo = mat.getPeriodo();
                    if (periodo == null || periodo.trim().isEmpty()) periodo = "Anual";
                    
                    Map<String, List<Map<String, Object>>> yearMap = planMap.computeIfAbsent(anio, k -> new LinkedHashMap<>());
                    List<Map<String, Object>> subjectList = yearMap.computeIfAbsent(periodo, k -> new ArrayList<>());
                    
                    Map<String, Object> subjectMap = new HashMap<>();
                    subjectMap.put("id_materia", mat.getIdMateria());
                    subjectMap.put("codigo", mat.getCodigo());
                    subjectMap.put("nombre", mat.getNombre());
                    subjectMap.put("carga_horaria", mat.getCargaHoraria());
                    subjectMap.put("periodo", mat.getPeriodo());
                    
                    int count = commCounts.getOrDefault(mat.getIdMateria(), 0);
                    subjectMap.put("comisionesCount", count);
                    subjectMap.put("hasComisiones", count > 0);
                    subjectMap.put("comisionesText", count == 1 ? "1 comisión" : count + " comisiones");
                    
                    subjectList.add(subjectMap);
                }

                // 3. Transformar planMap en una estructura de listas para Mustache
                List<Map<String, Object>> planEstudioList = new ArrayList<>();
                for (Map.Entry<Integer, Map<String, List<Map<String, Object>>>> yearEntry : planMap.entrySet()) {
                    Map<String, Object> yearNode = new HashMap<>();
                    yearNode.put("anio", yearEntry.getKey());
                    
                    List<Map<String, Object>> periodList = new ArrayList<>();
                    for (Map.Entry<String, List<Map<String, Object>>> periodEntry : yearEntry.getValue().entrySet()) {
                        Map<String, Object> periodNode = new HashMap<>();
                        periodNode.put("periodoNombre", periodEntry.getKey());
                        periodNode.put("materias", periodEntry.getValue());
                        periodList.add(periodNode);
                    }
                    
                    yearNode.put("periodos", periodList);
                    planEstudioList.add(yearNode);
                }
                
                model.put("planEstudio", planEstudioList);
                model.put("hasPlanEstudio", !planEstudioList.isEmpty());
            } else {
                // Docentes see subjects they are associated with (via subject assignment or commission assignment)
                int currentUserId = req.session().attribute("userId");
                User currentUser = User.findById(currentUserId);
                Integer idPersona = currentUser.getInteger("id_persona");
                
                List<Map> mRows = Base.findAll("SELECT DISTINCT id_materia FROM docente_materia WHERE id_prof = ? " +
                                               "UNION " +
                                               "SELECT DISTINCT c.id_materia FROM docente_comision dc JOIN comisiones c ON dc.id_comision = c.id_comision WHERE dc.id_prof = ?", idPersona, idPersona);
                Set<Integer> teacherMateriaIds = new HashSet<>();
                for (Map row : mRows) {
                    teacherMateriaIds.add(((Number) row.get("id_materia")).intValue());
                }
                
                if (!teacherMateriaIds.isEmpty()) {
                    String placeholders = teacherMateriaIds.stream().map(v -> "?").collect(Collectors.joining(", "));
                    materiasList = Materia.where("id_materia IN (" + placeholders + ")", teacherMateriaIds.toArray())
                                          .orderBy("anio ASC, periodo ASC, nombre ASC");
                } else {
                    materiasList = new ArrayList<>();
                }

                model.put("misMaterias", materiasList);
                model.put("hasMisMaterias", !materiasList.isEmpty());

                // Build periodosDocente (Only 1° and 2° Cuatrimestre, with Anual in both)
                List<Map<String, Object>> periodosDocente = new ArrayList<>();
                for (String pName : List.of("1° Cuatrimestre", "2° Cuatrimestre")) {
                    Map<String, Object> pMap = new HashMap<>();
                    pMap.put("periodoNombre", pName);
                    
                    long count = 0;
                    if (idPersona != null) {
                        String sqlCount = "SELECT COUNT(DISTINCT c.id_comision) FROM comisiones c " +
                                          "JOIN docente_comision dc ON c.id_comision = dc.id_comision " +
                                          "JOIN materias m ON c.id_materia = m.id_materia " +
                                          "WHERE dc.id_prof = ? AND (m.periodo = ? OR m.periodo = 'Anual')";
                        count = ((Number) Base.firstCell(sqlCount, idPersona, pName)).longValue();
                    }
                    pMap.put("comisionesCount", count);
                    pMap.put("hasComisiones", count > 0);
                    pMap.put("comisionesText", count == 1 ? "1 comisión asignada" : count + " comisiones asignadas");
                    periodosDocente.add(pMap);
                }
                model.put("periodosDocente", periodosDocente);
            }

            return new ModelAndView(model, "planificacion.mustache");
        }, new MustacheTemplateEngine());

        get("/planificacion/materia/:id", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            initCommonModel(req, model);

            String idStr = req.params(":id");
            if (idStr == null || idStr.isEmpty()) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Materia no especificada.", StandardCharsets.UTF_8));
                return null;
            }

            int idMateria;
            try {
                idMateria = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("ID de materia inválido.", StandardCharsets.UTF_8));
                return null;
            }

            Materia materia = Materia.findById(idMateria);
            if (materia == null) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("La materia no existe.", StandardCharsets.UTF_8));
                return null;
            }

            String userRole = req.session().attribute("tipoUsuario");
            boolean isAdmin = "ADMIN".equals(userRole) || "ADMINISTRADOR".equals(userRole);
            model.put("isAdmin", isAdmin);

            // Cargar datos de la materia
            model.put("materiaId", materia.getIdMateria());
            model.put("materiaNombre", materia.getNombre());
            model.put("materiaCodigo", materia.getCodigo());
            model.put("materiaCarga", materia.getCargaHoraria());
            model.put("materiaPeriodo", materia.getPeriodo());
            model.put("materiaAnio", materia.getAnio());

            // 1. Obtener la lista de docentes asignados a ESTA materia (docente_materia)
            String queryAssigned = "SELECT p.id_prof, pe.nombre, pe.apellido " +
                                   "FROM docente_materia dm " +
                                   "JOIN professors p ON dm.id_prof = p.id_prof " +
                                   "JOIN persons pe ON p.id_prof = pe.id_per " +
                                   "WHERE dm.id_materia = ? " +
                                   "ORDER BY pe.apellido, pe.nombre";
            List<Map> docentesAsignados = Base.findAll(queryAssigned, idMateria);
            model.put("docentesAsignados", docentesAsignados);
            model.put("hasDocentesAsignados", !docentesAsignados.isEmpty());

            // 2. Obtener TODAS las comisiones de esta materia
            List<Map> rawComms;
            if (isAdmin) {
                String sqlComms = "SELECT c.id_comision, c.horario, c.nombre, " +
                                  "       pe.id_per AS docente_id, pe.nombre AS docente_nombre, pe.apellido AS docente_apellido " +
                                  "FROM comisiones c " +
                                  "LEFT JOIN docente_comision dc ON c.id_comision = dc.id_comision " +
                                  "LEFT JOIN persons pe ON dc.id_prof = pe.id_per " +
                                  "WHERE c.id_materia = ? " +
                                  "ORDER BY c.horario ASC";
                rawComms = Base.findAll(sqlComms, idMateria);
            } else {
                // Docentes see only their own commissions in this subject
                int currentUserId = req.session().attribute("userId");
                User currentUser = User.findById(currentUserId);
                Integer idPersona = currentUser.getInteger("id_persona");
                String sqlComms = "SELECT c.id_comision, c.horario, c.nombre, " +
                                  "       pe.id_per AS docente_id, pe.nombre AS docente_nombre, pe.apellido AS docente_apellido " +
                                  "FROM comisiones c " +
                                  "JOIN docente_comision dc ON c.id_comision = dc.id_comision " +
                                  "LEFT JOIN persons pe ON dc.id_prof = pe.id_per " +
                                  "WHERE c.id_materia = ? AND dc.id_prof = ? " +
                                  "ORDER BY c.horario ASC";
                rawComms = Base.findAll(sqlComms, idMateria, idPersona);
            }

            // Para cada comisión, formatear la info y pasar los docentes habilitados para su asignación
            List<Map<String, Object>> comisionesList = new ArrayList<>();
            for (Map rc : rawComms) {
                Map<String, Object> comm = new HashMap<>();
                comm.put("id_comision", rc.get("id_comision"));
                comm.put("horario", rc.get("horario"));
                comm.put("nombre", rc.get("nombre"));
                comm.put("docente_id", rc.get("docente_id"));
                comm.put("docente_nombre", rc.get("docente_nombre"));
                comm.put("docente_apellido", rc.get("docente_apellido"));
                comisionesList.add(comm);
            }
            model.put("comisiones", comisionesList);
            model.put("hasComisiones", !comisionesList.isEmpty());

            // 3. Obtener la lista de todos los docentes del sistema que no estén asignados a esta materia
            String allTeachersSql = "SELECT p.id_prof, pe.nombre, pe.apellido " +
                                     "FROM professors p " +
                                     "JOIN persons pe ON p.id_prof = pe.id_per " +
                                     "WHERE p.id_prof NOT IN (SELECT id_prof FROM docente_materia WHERE id_materia = ?) " +
                                     "ORDER BY pe.apellido, pe.nombre";
            List<Map> todosDocentes = Base.findAll(allTeachersSql, idMateria);
            model.put("todosDocentes", todosDocentes);

            // 4. Obtener todos los alumnos inscriptos en esta materia en el año seleccionado
            String selectedAnioStr = req.queryParams("anio_lectivo");
            Integer selectedAnio = null;
            if (selectedAnioStr != null && !selectedAnioStr.trim().isEmpty()) {
                try {
                    selectedAnio = Integer.parseInt(selectedAnioStr.trim());
                } catch (NumberFormatException e) {
                    // Ignorar y usar por defecto
                }
            }

            if (selectedAnio == null) {
                // Por defecto, se observan los inscriptos de la ultima inscripcion
                Object maxAnio = Base.firstCell("SELECT MAX(anio_lectivo) FROM inscripciones WHERE id_materia = ?", idMateria);
                if (maxAnio != null) {
                    selectedAnio = ((Number) maxAnio).intValue();
                } else {
                    // Si no hay inscripciones, obtener el año de la configuración
                    Object yVal = Base.firstCell("SELECT valor FROM configuraciones WHERE clave = 'inscripcion_anio'");
                    selectedAnio = yVal != null ? Integer.parseInt(yVal.toString()) : 2026;
                }
            }
            model.put("selectedAnioLectivo", selectedAnio);

            String sqlInscriptos = "SELECT i.id_inscripcion, pe.id_per AS alumno_id, pe.nombre AS alumno_nombre, pe.apellido AS alumno_apellido, i.id_comision, i.anio_lectivo, i.cuatrimestre " +
                                   "FROM inscripciones i " +
                                   "JOIN persons pe ON i.id_alumno = pe.id_per " +
                                   "WHERE i.id_materia = ? AND i.anio_lectivo = ? " +
                                   "ORDER BY pe.apellido, pe.nombre";
            List<Map> rawInscriptos = Base.findAll(sqlInscriptos, idMateria, selectedAnio);
            
            // También necesitamos la lista simple de comisiones de esta materia para armar los dropdowns
            String sqlCommsSimple = "SELECT id_comision, nombre FROM comisiones WHERE id_materia = ? ORDER BY nombre";
            List<Map> comisionesSimple = Base.findAll(sqlCommsSimple, idMateria);

            List<Map<String, Object>> inscriptosList = new ArrayList<>();
            for (Map ri : rawInscriptos) {
                Map<String, Object> insMap = new HashMap<>();
                insMap.put("id_inscripcion", ri.get("id_inscripcion"));
                insMap.put("alumno_id", ri.get("alumno_id"));
                insMap.put("alumno_nombre", ri.get("alumno_nombre"));
                insMap.put("alumno_apellido", ri.get("alumno_apellido"));
                insMap.put("anio_lectivo", ri.get("anio_lectivo"));
                insMap.put("cuatrimestre", ri.get("cuatrimestre"));
                
                Object assignedCommId = ri.get("id_comision");
                insMap.put("id_comision", assignedCommId);
                insMap.put("comision_asignada", assignedCommId != null);
                
                // Buscar el nombre de la comisión asignada para mostrarlo
                String nombreComisionAsignada = "Sin asignar";
                if (assignedCommId != null) {
                    for (Map cSimp : comisionesSimple) {
                        if (cSimp.get("id_comision").equals(assignedCommId)) {
                            nombreComisionAsignada = (String) cSimp.get("nombre");
                            break;
                        }
                    }
                }
                insMap.put("nombre_comision_asignada", nombreComisionAsignada);

                List<Map<String, Object>> commOptions = new ArrayList<>();
                for (Map cSimp : comisionesSimple) {
                    Map<String, Object> opt = new HashMap<>();
                    opt.put("id_comision", cSimp.get("id_comision"));
                    opt.put("nombre", cSimp.get("nombre"));
                    opt.put("selected", cSimp.get("id_comision").equals(assignedCommId));
                    commOptions.add(opt);
                }
                insMap.put("comisionesOpciones", commOptions);
                inscriptosList.add(insMap);
            }
            model.put("inscriptos", inscriptosList);
            model.put("hasInscriptos", !inscriptosList.isEmpty());

            return new ModelAndView(model, "materia_detalle.mustache");
        }, new MustacheTemplateEngine());

        get("/planificacion/materia/:id/editar", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            initCommonModel(req, model);

            String userRole = req.session().attribute("tipoUsuario");
            boolean isAdmin = "ADMIN".equals(userRole) || "ADMINISTRADOR".equals(userRole);
            if (!isAdmin) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Acceso denegado.", StandardCharsets.UTF_8));
                return null;
            }
            model.put("isAdmin", isAdmin);

            String idStr = req.params(":id");
            if (idStr == null || idStr.isEmpty()) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Materia no especificada.", StandardCharsets.UTF_8));
                return null;
            }

            int idMateria;
            try {
                idMateria = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("ID de materia inválido.", StandardCharsets.UTF_8));
                return null;
            }

            Materia materia = Materia.findById(idMateria);
            if (materia == null) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("La materia no existe.", StandardCharsets.UTF_8));
                return null;
            }

            // Cargar datos de la materia
            model.put("materiaId", materia.getIdMateria());
            model.put("materiaNombre", materia.getNombre());
            model.put("materiaCodigo", materia.getCodigo());
            model.put("materiaCarga", materia.getCargaHoraria());
            model.put("materiaPeriodo", materia.getPeriodo());
            model.put("materiaAnio", materia.getAnio());

            // 5. Obtener las correlativas configuradas para esta materia
            String sqlCorrelativas = "SELECT c.id_correlativa, m.nombre AS correlativa_nombre, m.codigo AS correlativa_codigo, c.tipo " +
                                     "FROM correlativas c " +
                                     "JOIN materias m ON c.id_correlativa = m.id_materia " +
                                     "WHERE c.id_materia = ? " +
                                     "ORDER BY m.nombre";
            List<Map> rawCorrelativas = Base.findAll(sqlCorrelativas, idMateria);
            List<Map<String, Object>> correlativasList = new ArrayList<>();
            List<Integer> excludedMateriaIds = new ArrayList<>();
            excludedMateriaIds.add(idMateria); // No se puede agregar a sí misma

            for (Map rc : rawCorrelativas) {
                Map<String, Object> cMap = new HashMap<>();
                cMap.put("id_correlativa", rc.get("id_correlativa"));
                cMap.put("codigo", rc.get("correlativa_codigo"));
                cMap.put("nombre", rc.get("correlativa_nombre"));
                
                String tipo = (String) rc.get("tipo");
                cMap.put("tipo", tipo);
                cMap.put("is_aprobada", "A".equals(tipo));
                cMap.put("is_regularizada", "R".equals(tipo));
                
                correlativasList.add(cMap);
                excludedMateriaIds.add(((Number) rc.get("id_correlativa")).intValue());
            }
            model.put("correlativas", correlativasList);
            model.put("hasCorrelativas", !correlativasList.isEmpty());

            // 6. Obtener las materias que se pueden seleccionar como correlativas
            List<Materia> todasMaterias = Materia.findAll().orderBy("nombre ASC");
            List<Map<String, Object>> materiasElegibles = new ArrayList<>();
            for (Materia m : todasMaterias) {
                if (!excludedMateriaIds.contains(m.getIdMateria())) {
                    Map<String, Object> mOption = new HashMap<>();
                    mOption.put("id_materia", m.getIdMateria());
                    mOption.put("nombre", m.getNombre());
                    mOption.put("codigo", m.getCodigo());
                    materiasElegibles.add(mOption);
                }
            }
            model.put("materiasElegibles", materiasElegibles);
            model.put("hasMateriasElegibles", !materiasElegibles.isEmpty());

            return new ModelAndView(model, "materia_editar.mustache");
        }, new MustacheTemplateEngine());

        post("/planificacion/materia", (req, res) -> {
            String userRole = req.session().attribute("tipoUsuario");
            if (!"ADMIN".equals(userRole) && !"ADMINISTRADOR".equals(userRole)) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Acceso denegado.", StandardCharsets.UTF_8));
                return null;
            }

            String codigo = req.queryParams("codigo");
            String nombre = req.queryParams("nombre");
            String cargaStr = req.queryParams("carga_horaria");
            String periodo = req.queryParams("periodo");
            String anioStr = req.queryParams("anio");

            if (codigo == null || codigo.trim().isEmpty() || nombre == null || nombre.trim().isEmpty()) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Código y Nombre son requeridos.", StandardCharsets.UTF_8));
                return null;
            }

            // Validar si el código ya existe
            boolean exists = Materia.count("UPPER(codigo) = ?", codigo.trim().toUpperCase()) > 0;
            if (exists) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Código existente.", StandardCharsets.UTF_8));
                return null;
            }

            try {
                Materia mat = new Materia();
                mat.set("codigo", codigo.trim().toUpperCase());
                mat.set("nombre", nombre.trim());
                mat.set("carga_horaria", cargaStr != null && !cargaStr.isEmpty() ? Integer.parseInt(cargaStr) : 0);
                mat.set("periodo", periodo != null ? periodo.trim() : "Anual");
                mat.set("anio", anioStr != null && !anioStr.isEmpty() ? Integer.parseInt(anioStr) : 1);
                mat.saveIt();

                res.redirect("/planificacion?message=" + URLEncoder.encode("Materia registrada con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("UNIQUE") || errorMsg.contains("constraint") || errorMsg.contains("codigo"))) {
                    res.redirect("/planificacion?error=" + URLEncoder.encode("Código existente.", StandardCharsets.UTF_8));
                } else {
                    res.redirect("/planificacion?error=" + URLEncoder.encode("Error al registrar materia: " + e.getMessage(), StandardCharsets.UTF_8));
                }
            }
            return null;
        });

        post("/planificacion/materia/editar", (req, res) -> {
            String userRole = req.session().attribute("tipoUsuario");
            if (!"ADMIN".equals(userRole) && !"ADMINISTRADOR".equals(userRole)) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Acceso denegado.", StandardCharsets.UTF_8));
                return null;
            }

            String idStr = req.queryParams("id_materia");
            if (idStr == null || idStr.isEmpty()) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Materia no especificada.", StandardCharsets.UTF_8));
                return null;
            }

            int idMateria;
            try {
                idMateria = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("ID de materia inválido.", StandardCharsets.UTF_8));
                return null;
            }

            Materia mat = Materia.findById(idMateria);
            if (mat == null) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("La materia no existe.", StandardCharsets.UTF_8));
                return null;
            }

            String nombre = req.queryParams("nombre");
            String codigo = req.queryParams("codigo");
            String anioStr = req.queryParams("anio");
            String periodo = req.queryParams("periodo");
            String cargaStr = req.queryParams("carga_horaria");

            if (nombre == null || nombre.trim().isEmpty() || codigo == null || codigo.trim().isEmpty()) {
                res.redirect("/planificacion/materia/" + idMateria + "/editar?error=" + URLEncoder.encode("Nombre y Código son requeridos.", StandardCharsets.UTF_8));
                return null;
            }

            // Validar si el código ya existe en otra materia
            boolean exists = Materia.count("UPPER(codigo) = ? AND id_materia != ?", codigo.trim().toUpperCase(), idMateria) > 0;
            if (exists) {
                res.redirect("/planificacion/materia/" + idMateria + "/editar?error=" + URLEncoder.encode("Código existente.", StandardCharsets.UTF_8));
                return null;
            }

            try {
                mat.set("nombre", nombre.trim());
                mat.set("codigo", codigo.trim().toUpperCase());
                mat.set("anio", anioStr != null && !anioStr.isEmpty() ? Integer.parseInt(anioStr) : 1);
                mat.set("periodo", periodo != null ? periodo.trim() : "1° Cuatrimestre");
                mat.set("carga_horaria", cargaStr != null && !cargaStr.isEmpty() ? Integer.parseInt(cargaStr) : 0);
                mat.saveIt();

                res.redirect("/planificacion/materia/" + idMateria + "/editar?message=" + URLEncoder.encode("Materia actualizada con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && (errorMsg.contains("UNIQUE") || errorMsg.contains("constraint") || errorMsg.contains("codigo"))) {
                    res.redirect("/planificacion/materia/" + idMateria + "/editar?error=" + URLEncoder.encode("Código existente.", StandardCharsets.UTF_8));
                } else {
                    res.redirect("/planificacion/materia/" + idMateria + "/editar?error=" + URLEncoder.encode("Error al actualizar la materia: " + e.getMessage(), StandardCharsets.UTF_8));
                }
            }
            return null;
        });

        post("/planificacion/materia/eliminar", (req, res) -> {
            String userRole = req.session().attribute("tipoUsuario");
            if (!"ADMIN".equals(userRole) && !"ADMINISTRADOR".equals(userRole)) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Acceso denegado.", StandardCharsets.UTF_8));
                return null;
            }

            String idMateriaStr = req.queryParams("id_materia");
            if (idMateriaStr == null || idMateriaStr.isEmpty()) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Materia no especificada.", StandardCharsets.UTF_8));
                return null;
            }

            try {
                int idMateria = Integer.parseInt(idMateriaStr);

                // 1. Eliminar docente_comision para comisiones de esta materia
                Base.exec("DELETE FROM docente_comision WHERE id_comision IN (SELECT id_comision FROM comisiones WHERE id_materia = ?)", idMateria);

                // 2. Eliminar inscripciones para esta materia
                Base.exec("DELETE FROM inscripciones WHERE id_materia = ?", idMateria);

                // 3. Eliminar evaluaciones para comisiones de esta materia
                Base.exec("DELETE FROM evaluaciones WHERE id_comision IN (SELECT id_comision FROM comisiones WHERE id_materia = ?)", idMateria);

                // 4. Eliminar mensajes para comisiones de esta materia
                Base.exec("DELETE FROM mensajes WHERE id_comision IN (SELECT id_comision FROM comisiones WHERE id_materia = ?)", idMateria);

                // 5. Eliminar comisiones de la materia
                Base.exec("DELETE FROM comisiones WHERE id_materia = ?", idMateria);

                // 6. Eliminar docente_materia de la materia
                Base.exec("DELETE FROM docente_materia WHERE id_materia = ?", idMateria);

                // 7. Eliminar la materia
                Base.exec("DELETE FROM materias WHERE id_materia = ?", idMateria);

                res.redirect("/planificacion?message=" + URLEncoder.encode("Materia eliminada permanentemente del plan de estudio con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Error al eliminar la materia: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });

        post("/planificacion/materia/correlativa/agregar", (req, res) -> {
            String userRole = req.session().attribute("tipoUsuario");
            if (!"ADMIN".equals(userRole) && !"ADMINISTRADOR".equals(userRole)) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Acceso denegado.", StandardCharsets.UTF_8));
                return null;
            }

            String idMateriaStr = req.queryParams("id_materia");
            String idCorrelativaStr = req.queryParams("id_correlativa");
            String tipo = req.queryParams("tipo");

            if (idMateriaStr == null || idCorrelativaStr == null || tipo == null || tipo.isEmpty()) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Datos de correlatividad incompletos.", StandardCharsets.UTF_8));
                return null;
            }

            try {
                int idMateria = Integer.parseInt(idMateriaStr);
                int idCorrelativa = Integer.parseInt(idCorrelativaStr);

                if (idMateria == idCorrelativa) {
                    res.redirect("/planificacion/materia/" + idMateria + "/editar?error=" + URLEncoder.encode("Una materia no puede ser correlativa de sí misma.", StandardCharsets.UTF_8));
                    return null;
                }

                long count = Correlativa.count("id_materia = ? AND id_correlativa = ?", idMateria, idCorrelativa);
                if (count == 0) {
                    Correlativa c = new Correlativa();
                    c.set("id_materia", idMateria);
                    c.set("id_correlativa", idCorrelativa);
                    c.set("tipo", tipo);
                    c.saveIt();
                }

                res.redirect("/planificacion/materia/" + idMateria + "/editar?message=" + URLEncoder.encode("Correlatividad agregada con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/planificacion/materia/" + idMateriaStr + "/editar?error=" + URLEncoder.encode("Error al agregar correlatividad: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });

        post("/planificacion/materia/correlativa/eliminar", (req, res) -> {
            String userRole = req.session().attribute("tipoUsuario");
            if (!"ADMIN".equals(userRole) && !"ADMINISTRADOR".equals(userRole)) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Acceso denegado.", StandardCharsets.UTF_8));
                return null;
            }

            String idMateriaStr = req.queryParams("id_materia");
            String idCorrelativaStr = req.queryParams("id_correlativa");

            if (idMateriaStr == null || idCorrelativaStr == null) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Datos incompletos.", StandardCharsets.UTF_8));
                return null;
            }

            try {
                int idMateria = Integer.parseInt(idMateriaStr);
                int idCorrelativa = Integer.parseInt(idCorrelativaStr);

                Base.exec("DELETE FROM correlativas WHERE id_materia = ? AND id_correlativa = ?", idMateria, idCorrelativa);

                res.redirect("/planificacion/materia/" + idMateria + "/editar?message=" + URLEncoder.encode("Correlatividad eliminada con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/planificacion/materia/" + idMateriaStr + "/editar?error=" + URLEncoder.encode("Error al eliminar correlatividad: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });

        post("/planificacion/materia/asignar-comision", (req, res) -> {
            String userRole = req.session().attribute("tipoUsuario");
            if (!"ADMIN".equals(userRole) && !"ADMINISTRADOR".equals(userRole)) {
                res.status(403);
                return "No autorizado";
            }

            String idInscripcionStr = req.queryParams("id_inscripcion");
            String idComisionStr = req.queryParams("id_comision");
            String idMateriaStr = req.queryParams("id_materia");

            if (idInscripcionStr == null || idInscripcionStr.isEmpty() || idMateriaStr == null || idMateriaStr.isEmpty()) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Datos de asignación incompletos.", StandardCharsets.UTF_8));
                return null;
            }

            try {
                int idInscripcion = Integer.parseInt(idInscripcionStr);
                Inscripcion ins = Inscripcion.findById(idInscripcion);
                if (ins == null) {
                    res.redirect("/planificacion/materia/" + idMateriaStr + "?error=" + URLEncoder.encode("La inscripción no existe.", StandardCharsets.UTF_8));
                    return null;
                }

                if (idComisionStr == null || idComisionStr.isEmpty()) {
                    ins.set("id_comision", null);
                } else {
                    int idComision = Integer.parseInt(idComisionStr);
                    Comision com = Comision.findById(idComision);
                    if (com == null) {
                        res.redirect("/planificacion/materia/" + idMateriaStr + "?error=" + URLEncoder.encode("La comisión elegida no existe.", StandardCharsets.UTF_8));
                        return null;
                    }
                    ins.set("id_comision", idComision);
                }
                ins.saveIt();

                res.redirect("/planificacion/materia/" + idMateriaStr + "?message=" + URLEncoder.encode("Comisión asignada con éxito.", StandardCharsets.UTF_8));
                return null;
            } catch (Exception e) {
                res.redirect("/planificacion/materia/" + idMateriaStr + "?error=" + URLEncoder.encode("Error al asignar comisión: " + e.getMessage(), StandardCharsets.UTF_8));
                return null;
            }
        });

        post("/planificacion/toggle-inscripcion", (req, res) -> {
            String userRole = req.session().attribute("tipoUsuario");
            if (!"ADMIN".equals(userRole) && !"ADMINISTRADOR".equals(userRole)) {
                res.status(403);
                return "No autorizado";
            }

            String activaStr = req.queryParams("activa");
            String nuevoEstado = "true".equals(activaStr) ? "true" : "false";
            Base.exec("UPDATE configuraciones SET valor = ? WHERE clave = 'inscripcion_activa'", nuevoEstado);

            if ("true".equals(nuevoEstado)) {
                String periodo = req.queryParams("periodo");
                if (periodo != null && !periodo.isEmpty()) {
                    Base.exec("INSERT OR REPLACE INTO configuraciones (clave, valor) VALUES ('inscripcion_periodo', ?)", periodo);
                }
                String anio = req.queryParams("anio");
                if (anio != null && !anio.isEmpty()) {
                    Base.exec("INSERT OR REPLACE INTO configuraciones (clave, valor) VALUES ('inscripcion_anio', ?)", anio);
                }
            }

            String msg = "true".equals(nuevoEstado) ? "Periodo de inscripción habilitado con éxito." : "Periodo de inscripción deshabilitado con éxito.";
            res.redirect("/planificacion?message=" + URLEncoder.encode(msg, StandardCharsets.UTF_8));
            return null;
        });

        post("/planificacion/comision", (req, res) -> {
            String userRole = req.session().attribute("tipoUsuario");
            if (!"ADMIN".equals(userRole) && !"ADMINISTRADOR".equals(userRole)) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Acceso denegado.", StandardCharsets.UTF_8));
                return null;
            }

            String idMateriaStr = req.queryParams("id_materia");
            String nombre = req.queryParams("nombre");
            String idDocenteStr = req.queryParams("id_docente");

            String[] dias = req.queryParamsValues("dias[]");
            String[] inicios = req.queryParamsValues("inicios[]");
            String[] fines = req.queryParamsValues("fines[]");
            
            if (dias == null) {
                dias = req.queryParamsValues("dias");
                inicios = req.queryParamsValues("inicios");
                fines = req.queryParamsValues("fines");
            }

            int idMateria;
            try {
                idMateria = Integer.parseInt(idMateriaStr);
            } catch (NumberFormatException e) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Materia inválida.", StandardCharsets.UTF_8));
                return null;
            }

            Materia mat = Materia.findById(idMateria);
            int anio = mat != null ? mat.getAnio() : 1;
            String periodo = mat != null ? (mat.getPeriodo() != null ? mat.getPeriodo() : "1° Cuatrimestre") : "1° Cuatrimestre";
            String encodedPeriodo = URLEncoder.encode(periodo, StandardCharsets.UTF_8).replace("+", "%20");

            if (dias == null || dias.length == 0) {
                res.redirect("/planificacion/cronograma/" + anio + "/" + encodedPeriodo + "?error=" + URLEncoder.encode("Días y horarios son requeridos.", StandardCharsets.UTF_8));
                return null;
            }

            try {
                // Concatenar horarios dinámicos
                StringBuilder horarioBuilder = new StringBuilder();
                for (int i = 0; i < dias.length; i++) {
                    if (i > 0) {
                        horarioBuilder.append(" y ");
                    }
                    String d = dias[i];
                    String start = (inicios != null && i < inicios.length) ? inicios[i] : "08:00";
                    String end = (fines != null && i < fines.length) ? fines[i] : "10:00";
                    horarioBuilder.append(d).append(" ").append(start).append(" - ").append(end);
                }
                String horario = horarioBuilder.toString();

                Comision com = new Comision();
                com.set("id_materia", idMateria);
                com.set("horario", horario);
                com.set("nombre", nombre != null ? nombre.trim() : "Comisión 1");
                com.saveIt();

                if (idDocenteStr != null && !idDocenteStr.trim().isEmpty()) {
                    int idDocente = Integer.parseInt(idDocenteStr.trim());
                    Base.exec("INSERT INTO docente_comision (id_prof, id_comision) VALUES (?, ?)", idDocente, com.getId());
                }

                res.redirect("/planificacion/cronograma/" + anio + "/" + encodedPeriodo + "?comision=" + URLEncoder.encode(com.getString("nombre"), StandardCharsets.UTF_8) + "&message=" + URLEncoder.encode("Comisión planificada con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/planificacion/cronograma/" + anio + "/" + encodedPeriodo + "?error=" + URLEncoder.encode("Error al planificar comisión: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });

        post("/planificacion/comision/editar", (req, res) -> {
            String userRole = req.session().attribute("tipoUsuario");
            if (!"ADMIN".equals(userRole) && !"ADMINISTRADOR".equals(userRole)) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Acceso denegado.", StandardCharsets.UTF_8));
                return null;
            }

            String idComisionStr = req.queryParams("id_comision");
            String idMateriaStr = req.queryParams("id_materia");
            String nombre = req.queryParams("nombre");

            String[] dias = req.queryParamsValues("dias[]");
            String[] inicios = req.queryParamsValues("inicios[]");
            String[] fines = req.queryParamsValues("fines[]");

            if (dias == null) {
                dias = req.queryParamsValues("dias");
                inicios = req.queryParamsValues("inicios");
                fines = req.queryParamsValues("fines");
            }

            if (idComisionStr == null || idComisionStr.isEmpty()) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Comisión no especificada.", StandardCharsets.UTF_8));
                return null;
            }

            int idComision = Integer.parseInt(idComisionStr);
            Comision com = Comision.findById(idComision);
            if (com == null) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("La comisión no existe.", StandardCharsets.UTF_8));
                return null;
            }

            int idMateria = Integer.parseInt(idMateriaStr);
            Materia mat = Materia.findById(idMateria);
            int anio = mat != null ? mat.getAnio() : 1;
            String periodo = mat != null ? (mat.getPeriodo() != null ? mat.getPeriodo() : "1° Cuatrimestre") : "1° Cuatrimestre";
            String encodedPeriodo = URLEncoder.encode(periodo, StandardCharsets.UTF_8).replace("+", "%20");

            if (dias == null || dias.length == 0) {
                res.redirect("/planificacion/cronograma/" + anio + "/" + encodedPeriodo + "?error=" + URLEncoder.encode("Días y horarios son requeridos.", StandardCharsets.UTF_8));
                return null;
            }

            try {
                // Concatenar horarios dinámicos
                StringBuilder horarioBuilder = new StringBuilder();
                for (int i = 0; i < dias.length; i++) {
                    if (i > 0) {
                        horarioBuilder.append(" y ");
                    }
                    String d = dias[i];
                    String start = (inicios != null && i < inicios.length) ? inicios[i] : "08:00";
                    String end = (fines != null && i < fines.length) ? fines[i] : "10:00";
                    horarioBuilder.append(d).append(" ").append(start).append(" - ").append(end);
                }
                String horario = horarioBuilder.toString();

                com.set("id_materia", idMateria);
                com.set("horario", horario);
                com.set("nombre", nombre != null ? nombre.trim() : "Comisión 1");
                com.saveIt();

                res.redirect("/planificacion/cronograma/" + anio + "/" + encodedPeriodo + "?comision=" + URLEncoder.encode(com.getString("nombre"), StandardCharsets.UTF_8) + "&message=" + URLEncoder.encode("Comisión actualizada con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/planificacion/cronograma/" + anio + "/" + encodedPeriodo + "?error=" + URLEncoder.encode("Error al actualizar comisión: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });

        post("/planificacion/comision/eliminar", (req, res) -> {
            String userRole = req.session().attribute("tipoUsuario");
            if (!"ADMIN".equals(userRole) && !"ADMINISTRADOR".equals(userRole)) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Acceso denegado.", StandardCharsets.UTF_8));
                return null;
            }

            String nombre = req.queryParams("nombre");
            String anioStr = req.queryParams("anio");
            String periodo = req.queryParams("periodo");

            if (nombre == null || nombre.trim().isEmpty() || anioStr == null || periodo == null) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Datos de comisión incompletos.", StandardCharsets.UTF_8));
                return null;
            }

            int anio;
            try {
                anio = Integer.parseInt(anioStr);
            } catch (NumberFormatException e) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Año inválido.", StandardCharsets.UTF_8));
                return null;
            }
            String encodedPeriodo = URLEncoder.encode(periodo, StandardCharsets.UTF_8).replace("+", "%20");

            try {
                // 1. Obtener todas las materias de este año y período
                List<Materia> materias = Materia.where("anio = ? AND periodo = ?", anio, periodo);
                if (!materias.isEmpty()) {
                    List<Integer> materiaIds = materias.stream().map(Materia::getIdMateria).collect(Collectors.toList());
                    String placeholders = materiaIds.stream().map(v -> "?").collect(Collectors.joining(", "));

                    // 2. Buscar las comisiones asociadas a estas materias que tengan el nombre especificado
                    String sqlComms = "SELECT id_comision FROM comisiones WHERE nombre = ? AND id_materia IN (" + placeholders + ")";
                    List<Object> args = new ArrayList<>();
                    args.add(nombre);
                    args.addAll(materiaIds);

                    List<Map> comsToDelete = Base.findAll(sqlComms, args.toArray());
                    for (Map cMap : comsToDelete) {
                        int idComision = ((Number) cMap.get("id_comision")).intValue();
                        Base.exec("DELETE FROM docente_comision WHERE id_comision = ?", idComision);
                        Base.exec("UPDATE inscripciones SET id_comision = NULL WHERE id_comision = ?", idComision);
                        Base.exec("DELETE FROM evaluaciones WHERE id_comision = ?", idComision);
                        Base.exec("DELETE FROM mensajes WHERE id_comision = ?", idComision);
                        Base.exec("DELETE FROM comisiones WHERE id_comision = ?", idComision);
                    }
                }

                res.redirect("/planificacion/cronograma/" + anio + "/" + encodedPeriodo + "?message=" + URLEncoder.encode("Comisión " + nombre + " eliminada con éxito del cronograma.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/planificacion/cronograma/" + anio + "/" + encodedPeriodo + "?error=" + URLEncoder.encode("Error al eliminar comisión: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });

        post("/planificacion/asignar-docente", (req, res) -> {
            String userRole = req.session().attribute("tipoUsuario");
            if (!"ADMIN".equals(userRole) && !"ADMINISTRADOR".equals(userRole)) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Acceso denegado.", StandardCharsets.UTF_8));
                return null;
            }

            String idComisionStr = req.queryParams("id_comision");
            String idProfStr = req.queryParams("id_prof");
            String redirectUrl = req.queryParams("redirect_url");

            if (idComisionStr == null || idComisionStr.isEmpty()) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Comisión es requerida.", StandardCharsets.UTF_8));
                return null;
            }

            Integer idMateria = null;
            try {
                int idComision = Integer.parseInt(idComisionStr);

                Comision com = Comision.findById(idComision);
                if (com != null) {
                    idMateria = com.getInteger("id_materia");
                }

                String successMsg;
                if (idProfStr == null || idProfStr.isEmpty() || "0".equals(idProfStr)) {
                    // Eliminar asignación previa para esta comisión (docente a cargo único por comisión)
                    Base.exec("DELETE FROM docente_comision WHERE id_comision = ?", idComision);
                    successMsg = "Docente desasignado con éxito de la comisión.";
                } else {
                    int idProf = Integer.parseInt(idProfStr);
                    // Eliminar asignación previa para esta comisión
                    Base.exec("DELETE FROM docente_comision WHERE id_comision = ?", idComision);
                    // Insertar nueva asignación
                    Base.exec("INSERT INTO docente_comision (id_prof, id_comision) VALUES (?, ?)", idProf, idComision);
                    successMsg = "Docente asignado con éxito a la comisión.";
                }

                if (redirectUrl != null && !redirectUrl.isEmpty()) {
                    res.redirect(redirectUrl + (redirectUrl.contains("?") ? "&" : "?") + "message=" + URLEncoder.encode(successMsg, StandardCharsets.UTF_8));
                } else if (idMateria != null) {
                    res.redirect("/planificacion/materia/" + idMateria + "?message=" + URLEncoder.encode(successMsg, StandardCharsets.UTF_8));
                } else {
                    res.redirect("/planificacion?message=" + URLEncoder.encode(successMsg, StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                String errorMsg = "Error al asignar docente: " + e.getMessage();
                if (redirectUrl != null && !redirectUrl.isEmpty()) {
                    res.redirect(redirectUrl + (redirectUrl.contains("?") ? "&" : "?") + "error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8));
                } else if (idMateria != null) {
                    res.redirect("/planificacion/materia/" + idMateria + "?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8));
                } else {
                    res.redirect("/planificacion?error=" + URLEncoder.encode(errorMsg, StandardCharsets.UTF_8));
                }
            }
            return null;
        });

        post("/planificacion/asignar-materia", (req, res) -> {
            String userRole = req.session().attribute("tipoUsuario");
            if (!"ADMIN".equals(userRole) && !"ADMINISTRADOR".equals(userRole)) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Acceso denegado.", StandardCharsets.UTF_8));
                return null;
            }

            String idMateriaStr = req.queryParams("id_materia");
            String idProfStr = req.queryParams("id_prof");

            if (idMateriaStr == null || idMateriaStr.isEmpty() || idProfStr == null || idProfStr.isEmpty()) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Materia y Docente son requeridos.", StandardCharsets.UTF_8));
                return null;
            }

            try {
                int idMateria = Integer.parseInt(idMateriaStr);
                int idProf = Integer.parseInt(idProfStr);

                // Verificar si ya existe la relación para evitar violaciones de clave primaria
                Number countNum = (Number) Base.firstCell("SELECT COUNT(*) FROM docente_materia WHERE id_prof = ? AND id_materia = ?", idProf, idMateria);
                long count = countNum != null ? countNum.longValue() : 0;
                if (count == 0) {
                    Base.exec("INSERT INTO docente_materia (id_prof, id_materia) VALUES (?, ?)", idProf, idMateria);
                }

                res.redirect("/planificacion/materia/" + idMateria + "?message=" + URLEncoder.encode("Docente asignado a la materia con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/planificacion/materia/" + idMateriaStr + "?error=" + URLEncoder.encode("Error al asignar docente a materia: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });

        post("/planificacion/remover-docente-materia", (req, res) -> {
            String userRole = req.session().attribute("tipoUsuario");
            if (!"ADMIN".equals(userRole) && !"ADMINISTRADOR".equals(userRole)) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Acceso denegado.", StandardCharsets.UTF_8));
                return null;
            }

            String idMateriaStr = req.queryParams("id_materia");
            String idProfStr = req.queryParams("id_prof");

            if (idMateriaStr == null || idMateriaStr.isEmpty() || idProfStr == null || idProfStr.isEmpty()) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Materia y Docente son requeridos.", StandardCharsets.UTF_8));
                return null;
            }

            try {
                int idMateria = Integer.parseInt(idMateriaStr);
                int idProf = Integer.parseInt(idProfStr);

                // 1. Eliminar la relación de docente_materia
                Base.exec("DELETE FROM docente_materia WHERE id_prof = ? AND id_materia = ?", idProf, idMateria);

                // 2. Eliminar la asignación de este docente en las comisiones asociadas a esta materia
                Base.exec("DELETE FROM docente_comision WHERE id_prof = ? AND id_comision IN (SELECT id_comision FROM comisiones WHERE id_materia = ?)", idProf, idMateria);

                res.redirect("/planificacion/materia/" + idMateria + "?message=" + URLEncoder.encode("Docente removido de la materia con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/planificacion/materia/" + idMateriaStr + "?error=" + URLEncoder.encode("Error al remover docente de la materia: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });


        // --- TABLERO DE COMUNIDAD (Comisiones) ---

        get("/comunidad", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            initCommonModel(req, model);

            int currentUserId = req.session().attribute("userId");
            User currentUser = User.findById(currentUserId);
            Integer idPersona = currentUser.getInteger("id_persona");
            String userRole = req.session().attribute("tipoUsuario");

            // Obtener comisiones asociadas al usuario
            String sql;
            if ("DOCENTE".equals(userRole)) {
                sql = "SELECT c.id_comision, c.horario, m.nombre AS materia_nombre " +
                      "FROM docente_comision dc " +
                      "JOIN comisiones c ON dc.id_comision = c.id_comision " +
                      "JOIN materias m ON c.id_materia = m.id_materia " +
                      "WHERE dc.id_prof = ? " +
                      "ORDER BY m.nombre, c.horario";
            } else {
                sql = "SELECT c.id_comision, c.horario, m.nombre AS materia_nombre " +
                      "FROM inscripciones i " +
                      "JOIN comisiones c ON i.id_comision = c.id_comision " +
                      "JOIN materias m ON c.id_materia = m.id_materia " +
                      "WHERE i.id_alumno = ? " +
                      "ORDER BY m.nombre, c.horario";
            }
            List<Map> myCommissions = Base.findAll(sql, idPersona);
            model.put("myCommissions", myCommissions);

            // Si se seleccionó una comisión específica, recuperar las publicaciones principales
            String selectedCommParam = req.queryParams("commission");
            if (selectedCommParam != null && !selectedCommParam.isEmpty()) {
                int commissionId = Integer.parseInt(selectedCommParam);
                model.put("selectedCommissionId", commissionId);

                // Obtener detalles de la materia/comisión seleccionada
                Map commInfo = (Map) Base.findAll(
                    "SELECT c.horario, m.nombre AS materia_nombre FROM comisiones c JOIN materias m ON c.id_materia = m.id_materia WHERE c.id_comision = ?", 
                    commissionId
                ).stream().findFirst().orElse(null);
                
                if (commInfo != null) {
                    model.put("commissionTitle", commInfo.get("materia_nombre") + " - " + commInfo.get("horario"));
                }

                // Recuperar publicaciones principales (id_padre IS NULL)
                String msgSql = "SELECT m.id_mensaje, m.contenido, m.fecha, u.name AS emisor_nombre, u.tipo_usuario AS emisor_rol " +
                               "FROM mensajes m " +
                               "JOIN users u ON m.id_emisor = u.id " +
                               "WHERE m.id_comision = ? AND m.id_padre IS NULL " +
                               "ORDER BY m.fecha DESC, m.id_mensaje DESC";
                List<Map> postsRaw = Base.findAll(msgSql, commissionId);
                List<Map<String, Object>> posts = new ArrayList<>();

                for (Map r : postsRaw) {
                    Map<String, Object> post = new HashMap<>(r);
                    int postMsgId = ((Number) r.get("id_mensaje")).intValue();
                    
                    // Adjuntos de esta publicación
                    List<Map> attachments = Base.findAll("SELECT id_adjunto, nombre_archivo FROM adjuntos WHERE id_mensaje = ?", postMsgId);
                    post.put("attachments", attachments);
                    post.put("hasAttachments", !attachments.isEmpty());
                    
                    // Comentarios de esta publicación
                    String commentsSql = "SELECT m.id_mensaje, m.contenido, m.fecha, u.name AS emisor_nombre, u.tipo_usuario AS emisor_rol " +
                                         "FROM mensajes m " +
                                         "JOIN users u ON m.id_emisor = u.id " +
                                         "WHERE m.id_padre = ? " +
                                         "ORDER BY m.fecha ASC, m.id_mensaje ASC";
                    List<Map> commentsRaw = Base.findAll(commentsSql, postMsgId);
                    List<Map<String, Object>> comments = new ArrayList<>();
                    for (Map cr : commentsRaw) {
                        Map<String, Object> comment = new HashMap<>(cr);
                        String cRole = (String) cr.get("emisor_rol");
                        comment.put("isDocenteEmisor", "DOCENTE".equals(cRole));
                        String cName = (String) cr.get("emisor_nombre");
                        comment.put("emisorInitials", cName != null && cName.length() >= 2 ? cName.substring(0, 2).toUpperCase() : "U");
                        comments.add(comment);
                    }
                    post.put("comments", comments);
                    post.put("commentsCount", comments.size());
                    post.put("hasComments", !comments.isEmpty());
                    
                    String role = (String) r.get("emisor_rol");
                    post.put("isDocenteEmisor", "DOCENTE".equals(role));
                    String name = (String) r.get("emisor_nombre");
                    post.put("emisorInitials", name != null && name.length() >= 2 ? name.substring(0, 2).toUpperCase() : "U");
                    
                    posts.add(post);
                }
                model.put("posts", posts);
            }

            return new ModelAndView(model, "comunidad.mustache");
        }, new MustacheTemplateEngine());

        post("/comunidad", (req, res) -> {
            int currentUserId = req.session().attribute("userId");
            String userRole = req.session().attribute("tipoUsuario");
            
            // Para soportar multipart/form-data, configuramos el parser de Jetty si es necesario.
            boolean isMultipart = req.raw().getContentType() != null && req.raw().getContentType().startsWith("multipart/form-data");
            if (isMultipart) {
                if (req.raw().getAttribute("org.eclipse.jetty.multipartConfig") == null) {
                    MultipartConfigElement multipartConfigElement = new MultipartConfigElement(
                        System.getProperty("java.io.tmpdir"),
                        10 * 1024 * 1024, // 10MB máx por archivo
                        50 * 1024 * 1024, // 50MB máx por request
                        1024 * 1024       // 1MB límite en memoria
                    );
                    req.raw().setAttribute("org.eclipse.jetty.multipartConfig", multipartConfigElement);
                }
            }

            String idComisionStr = req.queryParams("id_comision");
            String contenido = req.queryParams("contenido");
            String idPadreStr = req.queryParams("id_padre");

            if (idComisionStr == null || idComisionStr.isEmpty() || contenido == null || contenido.trim().isEmpty()) {
                res.redirect("/comunidad?error=" + URLEncoder.encode("Campos inválidos.", StandardCharsets.UTF_8));
                return null;
            }

            int idComision = Integer.parseInt(idComisionStr);
            Integer idPadre = null;
            if (idPadreStr != null && !idPadreStr.trim().isEmpty()) {
                idPadre = Integer.parseInt(idPadreStr.trim());
            }

            // Si es un post principal, validar que sea docente o admin
            if (idPadre == null) {
                if (!"DOCENTE".equals(userRole) && !"ADMIN".equals(userRole) && !"ADMINISTRADOR".equals(userRole)) {
                    res.redirect("/comunidad?commission=" + idComision + "&error=" + URLEncoder.encode("Solo los docentes pueden crear publicaciones.", StandardCharsets.UTF_8));
                    return null;
                }
            }

            // Crear el mensaje (foro/comunidad)
            Message msg = new Message();
            msg.set("id_emisor", currentUserId);
            msg.set("id_receptor", null);
            msg.set("id_comision", idComision);
            msg.set("contenido", contenido.trim());
            msg.set("id_padre", idPadre);
            msg.set("fecha", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            msg.set("leido", false);
            msg.saveIt();

            // Guardar adjuntos si es un post principal y hay archivos
            if (idPadre == null && isMultipart) {
                try {
                    File uploadDir = new File("uploads");
                    if (!uploadDir.exists()) {
                        uploadDir.mkdirs();
                    }

                    for (Part part : req.raw().getParts()) {
                        if ("archivos".equals(part.getName()) && part.getSize() > 0) {
                            String submittedFileName = part.getSubmittedFileName();
                            if (submittedFileName != null && !submittedFileName.isEmpty()) {
                                // Sanitizar nombre de archivo
                                String fileName = new File(submittedFileName).getName();
                                // Evitar colisiones agregando timestamp
                                String uniqueFileName = System.currentTimeMillis() + "_" + fileName;
                                File targetFile = new File(uploadDir, uniqueFileName);

                                try (InputStream input = part.getInputStream();
                                     OutputStream output = new FileOutputStream(targetFile)) {
                                    byte[] buffer = new byte[4096];
                                    int bytesRead;
                                    while ((bytesRead = input.read(buffer)) != -1) {
                                        output.write(buffer, 0, bytesRead);
                                    }
                                }

                                // Guardar en base de datos
                                Attachment adj = new Attachment();
                                adj.set("id_mensaje", msg.getId());
                                adj.set("nombre_archivo", fileName);
                                adj.set("ruta_archivo", targetFile.getAbsolutePath());
                                adj.saveIt();
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error procesando archivos adjuntos: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Notificar a todos los miembros de la comisión sobre la nueva publicación/comentario
            String membersSql = "SELECT u.id FROM users u WHERE u.id_persona IN (" +
                                "  SELECT id_alumno FROM inscripciones WHERE id_comision = ? " +
                                "  UNION " +
                                "  SELECT id_prof FROM docente_comision WHERE id_comision = ?" +
                                ") AND u.id != ?";
            List<Map> members = Base.findAll(membersSql, idComision, idComision, currentUserId);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String notifText = idPadre == null ? "Nuevo anuncio publicado en el foro de comunidad." : "Nuevo comentario en el foro de comunidad.";
            for (Map m : members) {
                Notification n = new Notification();
                n.set("id_usuario", ((Number) m.get("id")).intValue());
                n.set("contenido", notifText);
                n.set("fecha", timestamp);
                n.set("leida", false);
                n.saveIt();
            }

            res.redirect("/comunidad?commission=" + idComision);
            return null;
        });

        get("/comunidad/download/:id", (req, res) -> {
            String idStr = req.params(":id");
            if (idStr == null || idStr.isEmpty()) {
                res.status(400);
                return "ID inválido";
            }

            int idAdjunto = Integer.parseInt(idStr);
            Attachment adj = Attachment.findById(idAdjunto);
            if (adj == null) {
                res.status(404);
                return "Adjunto no encontrado";
            }

            String nombreArchivo = adj.getNombreArchivo();
            String rutaArchivo = adj.getRutaArchivo();
            File file = new File(rutaArchivo);
            if (!file.exists()) {
                res.status(404);
                return "El archivo físico no existe";
            }

            res.type("application/octet-stream");
            res.header("Content-Disposition", "attachment; filename=\"" + URLEncoder.encode(nombreArchivo, StandardCharsets.UTF_8).replace("+", "%20") + "\"");

            try (InputStream is = new FileInputStream(file);
                 OutputStream os = res.raw().getOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
            return res.raw();
        });



        // --- MÓDULO DE EVALUACIÓN (Docentes) ---

        get("/evaluacion", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            initCommonModel(req, model);

            int currentUserId = req.session().attribute("userId");
            User currentUser = User.findById(currentUserId);
            Integer idPersona = currentUser.getInteger("id_persona");

            String selectedCommParam = req.queryParams("commission");

            // Comisiones asociadas al docente, recuperando también el nombre de la comisión y de la materia
            String sql = "SELECT c.id_comision, c.horario, c.nombre AS comision_nombre, m.nombre AS materia_nombre, m.id_materia " +
                         "FROM docente_comision dc " +
                         "JOIN comisiones c ON dc.id_comision = c.id_comision " +
                         "JOIN materias m ON c.id_materia = m.id_materia " +
                         "WHERE dc.id_prof = ? " +
                         "ORDER BY m.nombre, c.nombre, c.horario";
            List<Map> myCommissions = Base.findAll(sql, idPersona);

            // Agrupar por Materia en Java para renderizar acordeones colapsables en Mustache
            Map<Integer, Map<String, Object>> grouped = new LinkedHashMap<>();
            for (Map row : myCommissions) {
                Integer mId = ((Number) row.get("id_materia")).intValue();
                String mNombre = (String) row.get("materia_nombre");

                Map<String, Object> matMap = grouped.computeIfAbsent(mId, k -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("materiaId", mId);
                    map.put("materiaNombre", mNombre);
                    map.put("comisiones", new ArrayList<Map<String, Object>>());
                    map.put("hasSelectedCommission", false);
                    return map;
                });

                List<Map<String, Object>> commList = (List<Map<String, Object>>) matMap.get("comisiones");

                Map<String, Object> commMap = new HashMap<>();
                commMap.put("id_comision", row.get("id_comision"));
                commMap.put("horario", row.get("horario"));

                String commNombre = (String) row.get("comision_nombre");
                if (commNombre == null || commNombre.trim().isEmpty()) {
                    commNombre = "Comisión 1";
                }
                commMap.put("comision_nombre", commNombre);

                // Determinar si esta comisión específica está seleccionada
                boolean isSelected = false;
                if (selectedCommParam != null && !selectedCommParam.isEmpty()) {
                    isSelected = row.get("id_comision").toString().equals(selectedCommParam);
                }
                commMap.put("selected", isSelected);
                if (isSelected) {
                    matMap.put("hasSelectedCommission", true);
                }

                commList.add(commMap);
            }

            List<Map<String, Object>> mySubjects = new ArrayList<>(grouped.values());
            model.put("mySubjects", mySubjects);
            model.put("hasSubjects", !mySubjects.isEmpty());

            // Alumnos inscritos si se selecciona comisión
            if (selectedCommParam != null && !selectedCommParam.isEmpty()) {
                int commissionId = Integer.parseInt(selectedCommParam);
                model.put("selectedCommissionId", commissionId);

                // Obtener nombres detallados de materia y comisión para la cabecera de la planilla
                Map commInfo = (Map) Base.findAll(
                    "SELECT c.nombre AS comision_nombre, m.nombre AS materia_nombre " +
                    "FROM comisiones c " +
                    "JOIN materias m ON c.id_materia = m.id_materia " +
                    "WHERE c.id_comision = ?", commissionId
                ).stream().findFirst().orElse(null);
                if (commInfo != null) {
                    String comName = (String) commInfo.get("comision_nombre");
                    if (comName == null || comName.trim().isEmpty()) {
                        comName = "Comisión 1";
                    }
                    model.put("selectedMateriaNombre", commInfo.get("materia_nombre"));
                    model.put("selectedComisionNombre", comName);
                }

                String studentsSql = "SELECT p.id_per AS student_person_id, p.nombre, p.apellido, p.dni, " +
                                     "       u.id AS student_user_id, i.condicion, " +
                                     "       (SELECT e.nota FROM evaluaciones e WHERE e.id_alumno = p.id_per AND e.id_comision = ?) AS nota " +
                                     "FROM inscripciones i " +
                                     "JOIN users u ON i.id_alumno = u.id_persona " +
                                     "JOIN persons p ON u.id_persona = p.id_per " +
                                     "WHERE i.id_comision = ? " +
                                     "ORDER BY p.apellido, p.nombre";
                List<Map> studentsRaw = Base.findAll(studentsSql, commissionId, commissionId);
                List<Map<String, Object>> students = new ArrayList<>();
                for (Map row : studentsRaw) {
                    Map<String, Object> s = new HashMap<>();
                    s.put("student_person_id", row.get("student_person_id"));
                    s.put("nombre", row.get("nombre"));
                    s.put("apellido", row.get("apellido"));
                    s.put("dni", row.get("dni"));
                    s.put("student_user_id", row.get("student_user_id"));
                    s.put("nota", row.get("nota"));
                    
                    String cond = (String) row.get("condicion");
                    s.put("condicion", cond);
                    s.put("is_regular", "R".equals(cond));
                    s.put("is_aprobado", "A".equals(cond));
                    s.put("is_pendiente", cond == null || (!"R".equals(cond) && !"A".equals(cond)));
                    s.put("is_not_aprobado", !"A".equals(cond));
                    students.add(s);
                }
                model.put("students", students);
            }

            return new ModelAndView(model, "evaluacion.mustache");
        }, new MustacheTemplateEngine());

        post("/evaluacion", (req, res) -> {
            String idAlumnoStr = req.queryParams("id_alumno");
            String idComisionStr = req.queryParams("id_comision");
            String condicion = req.queryParams("condicion");
            String notaStr = req.queryParams("nota");

            if (idAlumnoStr == null || idComisionStr == null) {
                res.redirect("/evaluacion?error=" + URLEncoder.encode("Campos obligatorios incompletos.", StandardCharsets.UTF_8));
                return null;
            }

            if (condicion == null) {
                condicion = "";
            }

            try {
                int idAlumno = Integer.parseInt(idAlumnoStr);
                int idComision = Integer.parseInt(idComisionStr);

                Comision com = Comision.findById(idComision);
                if (com == null) {
                    res.redirect("/evaluacion?error=" + URLEncoder.encode("La comisión no existe.", StandardCharsets.UTF_8));
                    return null;
                }
                int idMateria = com.getInteger("id_materia");

                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                User studentUser = User.findFirst("id_persona = ?", idAlumno);

                if ("A".equals(condicion)) {
                    if (notaStr == null || notaStr.trim().isEmpty()) {
                        res.redirect("/evaluacion?commission=" + idComision + "&error=" + URLEncoder.encode("Se requiere una calificación para aprobar la materia.", StandardCharsets.UTF_8));
                        return null;
                    }
                    int nota = Integer.parseInt(notaStr.trim());
                    if (nota < 4 || nota > 10) {
                        res.redirect("/evaluacion?commission=" + idComision + "&error=" + URLEncoder.encode("La calificación para aprobar debe estar entre 4 y 10.", StandardCharsets.UTF_8));
                        return null;
                    }

                    // Guardar la condición en inscripciones
                    Base.exec("UPDATE inscripciones SET condicion = ? WHERE id_alumno = ? AND id_materia = ?", "A", idAlumno, idMateria);

                    // Guardar la nota en evaluaciones
                    long count = Evaluacion.count("id_alumno = ? AND id_comision = ?", idAlumno, idComision);
                    if (count > 0) {
                        Base.exec("UPDATE evaluaciones SET nota = ?, fecha = ? WHERE id_alumno = ? AND id_comision = ?", 
                                  nota, timestamp, idAlumno, idComision);
                    } else {
                        Evaluacion ev = new Evaluacion();
                        ev.set("id_alumno", idAlumno);
                        ev.set("id_comision", idComision);
                        ev.set("nota", nota);
                        ev.set("fecha", timestamp);
                        ev.saveIt();
                    }

                    // Generar notificación
                    if (studentUser != null) {
                        Notification n = new Notification();
                        n.set("id_usuario", studentUser.getId());
                        n.set("contenido", "Nueva nota cargada: Aprobaste la materia con un " + nota + ".");
                        n.set("fecha", timestamp);
                        n.set("leida", false);
                        n.saveIt();
                    }
                } else if ("R".equals(condicion)) {
                    // Regular (R)
                    Base.exec("UPDATE inscripciones SET condicion = ? WHERE id_alumno = ? AND id_materia = ?", "R", idAlumno, idMateria);
                    Base.exec("DELETE FROM evaluaciones WHERE id_alumno = ? AND id_comision = ?", idAlumno, idComision);

                    // Generar notificación
                    if (studentUser != null) {
                        Notification n = new Notification();
                        n.set("id_usuario", studentUser.getId());
                        n.set("contenido", "Tu condición académica ha sido actualizada a Regular (R).");
                        n.set("fecha", timestamp);
                        n.set("leida", false);
                        n.saveIt();
                    }
                } else {
                    // Cursando / Pendiente
                    Base.exec("UPDATE inscripciones SET condicion = NULL WHERE id_alumno = ? AND id_materia = ?", idAlumno, idMateria);
                    Base.exec("DELETE FROM evaluaciones WHERE id_alumno = ? AND id_comision = ?", idAlumno, idComision);

                    // Generar notificación
                    if (studentUser != null) {
                        Notification n = new Notification();
                        n.set("id_usuario", studentUser.getId());
                        n.set("contenido", "Tu condición académica ha sido restablecida a Cursando.");
                        n.set("fecha", timestamp);
                        n.set("leida", false);
                        n.saveIt();
                    }
                }

                res.redirect("/evaluacion?commission=" + idComision + "&message=" + URLEncoder.encode("Condición y calificación guardadas con éxito.", StandardCharsets.UTF_8));
            } catch (Exception e) {
                res.redirect("/evaluacion?error=" + URLEncoder.encode("Error al registrar: " + e.getMessage(), StandardCharsets.UTF_8));
            }
            return null;
        });


        // --- MÓDULO DE REPORTES Y ANALÍTICAS (Alumnos y Directivos) ---

        get("/reportes", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            initCommonModel(req, model);

            int currentUserId = req.session().attribute("userId");
            User currentUser = User.findById(currentUserId);
            String userRole = req.session().attribute("tipoUsuario");

            if ("ADMIN".equals(userRole) || "ADMINISTRADOR".equals(userRole)) {
                // Tablero Analítico para Directivos (ADMIN)
                model.put("isAdminReport", true);

                long totalStudents = User.count("tipo_usuario = 'ALUMNO'");
                long totalTeachers = User.count("tipo_usuario = 'DOCENTE'");
                model.put("totalStudents", totalStudents);
                model.put("totalTeachers", totalTeachers);

                // Nota Promedio General del Colegio
                List<Map> avgRow = Base.findAll("SELECT AVG(nota) as school_avg FROM evaluaciones");
                double schoolAvg = 0.0;
                if (!avgRow.isEmpty() && avgRow.get(0).get("school_avg") != null) {
                    schoolAvg = ((Number) avgRow.get(0).get("school_avg")).doubleValue();
                }
                model.put("schoolAverage", String.format("%.2f", schoolAvg));

                // Rendimiento promedio por materia
                String subjectStatsSql = "SELECT m.nombre AS materia_nombre, m.codigo AS materia_codigo, " +
                                         "       AVG(e.nota) AS avg_nota, COUNT(DISTINCT e.id_alumno) AS total_graded " +
                                         "FROM materias m " +
                                         "JOIN comisiones c ON m.id_materia = c.id_materia " +
                                         "JOIN evaluaciones e ON c.id_comision = e.id_comision " +
                                         "GROUP BY m.id_materia";
                List<Map> subjectStats = Base.findAll(subjectStatsSql);
                for (Map stats : subjectStats) {
                    stats.put("avg_nota", String.format("%.2f", ((Number) stats.get("avg_nota")).doubleValue()));
                }
                model.put("subjectStats", subjectStats);

                // Nivel de matriculación por comisión
                String commStatsSql = "SELECT m.nombre AS materia_nombre, c.horario, " +
                                      "       (SELECT COUNT(*) FROM inscripciones i WHERE i.id_comision = c.id_comision) AS total_enrolled " +
                                      "FROM comisiones c " +
                                      "JOIN materias m ON c.id_materia = m.id_materia " +
                                      "ORDER BY total_enrolled DESC";
                List<Map> commissionStats = Base.findAll(commStatsSql);
                model.put("commissionStats", commissionStats);
            } else {
                res.redirect("/dashboard");
                return null;
            }

            return new ModelAndView(model, "reportes.mustache");
        }, new MustacheTemplateEngine());


        // --- SISTEMA DE NOTIFICACIONES ---

        get("/notificaciones", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            initCommonModel(req, model);

            int currentUserId = req.session().attribute("userId");
            List<Notification> notifs = Notification.where("id_usuario = ?", currentUserId).orderBy("fecha DESC");
            model.put("notifications", notifs);

            return new ModelAndView(model, "notificaciones.mustache");
        }, new MustacheTemplateEngine());

        post("/notificaciones/read", (req, res) -> {
            int currentUserId = req.session().attribute("userId");
            String idNotifStr = req.queryParams("id_notificacion");

            if (idNotifStr != null && !idNotifStr.isEmpty()) {
                int idNotif = Integer.parseInt(idNotifStr);
                Base.exec("UPDATE notificaciones SET leida = 1 WHERE id_notificacion = ? AND id_usuario = ?", idNotif, currentUserId);
            } else {
                Base.exec("UPDATE notificaciones SET leida = 1 WHERE id_usuario = ?", currentUserId);
            }

            res.redirect("/notificaciones");
            return null;
        });

        // --- CAMBIO OBLIGATORIO DE CONTRASEÑA ---

        get("/password/change", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            initCommonModel(req, model);

            // Si por alguna razón no está logueado o no debe cambiar la contraseña, redirigir
            Boolean logueado = req.session().attribute("loggedIn");
            Boolean debeCambiar = req.session().attribute("mustChangePassword");
            if (logueado == null || !logueado) {
                res.redirect("/");
                return null;
            }
            if (!Boolean.TRUE.equals(debeCambiar)) {
                res.redirect("/dashboard");
                return null;
            }

            // Recuperar posibles errores de la sesión
            List<String> errores = req.session().attribute("changePasswordErrors");
            if (errores != null) {
                model.put("errores", errores);
                req.session().removeAttribute("changePasswordErrors");
            }

            return new ModelAndView(model, "password_change.mustache");
        }, new MustacheTemplateEngine());

        post("/password/change", (req, res) -> {
            Integer userId = req.session().attribute("userId");
            if (userId == null) {
                res.redirect("/");
                return null;
            }

            String nueva = req.queryParams("newPassword");
            String confirma = req.queryParams("confirmPassword");

            User user = User.findById(userId);
            String username = user.getString("name"); // El nombre de usuario (DNI)

            List<String> errores = new ArrayList<>();
            if (nueva == null || nueva.length() < 10) {
                errores.add("La contraseña debe tener al menos 10 caracteres.");
            }
            if (nueva != null && !nueva.equals(confirma)) {
                errores.add("Las contraseñas no coinciden.");
            }
            if (nueva != null && nueva.equals(username)) {
                errores.add("No puedes usar tu DNI como contraseña.");
            }
            if (nueva != null && BCrypt.checkpw(nueva, user.getString("password"))) {
                errores.add("La nueva contraseña no puede ser igual a la contraseña temporal.");
            }

            if (!errores.isEmpty()) {
                req.session().attribute("changePasswordErrors", errores);
                res.redirect("/password/change");
                return null;
            }

            // Actualizar contraseña y quitar banderas
            user.set("password", BCrypt.hashpw(nueva, BCrypt.gensalt()));
            user.set("must_change_password", 0);
            user.saveIt();

            req.session().attribute("mustChangePassword", false);
            res.redirect("/dashboard");
            return null;
        });

        get("/planificacion/cronograma/:anio/:periodo", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            initCommonModel(req, model);

            String anioStr = req.params(":anio");
            String periodo = req.params(":periodo");

            if (anioStr == null || periodo == null) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Parámetros inválidos.", StandardCharsets.UTF_8));
                return null;
            }

            int anio;
            try {
                anio = Integer.parseInt(anioStr);
            } catch (NumberFormatException e) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Año inválido.", StandardCharsets.UTF_8));
                return null;
            }

            model.put("anio", anio);
            model.put("periodo", periodo);
            model.put("periodoEncoded", URLEncoder.encode(periodo, StandardCharsets.UTF_8).replace("+", "%20"));

            String userRole = req.session().attribute("tipoUsuario");
            boolean isAdmin = "ADMIN".equals(userRole) || "ADMINISTRADOR".equals(userRole);
            model.put("isAdmin", isAdmin);

            // 1. Obtener todas las materias del cuatrimestre especificado más las de tipo "Anual" del año seleccionado
            List<Materia> materias = Materia.where("(periodo = ? OR periodo = 'Anual') AND anio = ?", periodo, anio);

            List<Map> comisionesRaw = new ArrayList<>();
            Map<Integer, Materia> materiaMap = new HashMap<>();

            if (!materias.isEmpty()) {
                List<Integer> materiaIds = materias.stream().map(Materia::getIdMateria).collect(Collectors.toList());
                String placeholders = materiaIds.stream().map(v -> "?").collect(Collectors.joining(", "));

                // Obtener todas las comisiones asociadas a estas materias (con filtrado por docente si no es admin)
                String sqlComms;
                if (isAdmin) {
                    sqlComms = "SELECT c.id_comision, c.horario, c.nombre AS comision_nombre, c.id_materia, dc.id_prof, " +
                                      "       pe.nombre AS docente_nombre, pe.apellido AS docente_apellido " +
                                      "FROM comisiones c " +
                                      "LEFT JOIN docente_comision dc ON c.id_comision = dc.id_comision " +
                                      "LEFT JOIN persons pe ON dc.id_prof = pe.id_per " +
                                      "WHERE c.id_materia IN (" + placeholders + ")";
                    comisionesRaw = Base.findAll(sqlComms, materiaIds.toArray());
                } else {
                    int currentUserId = req.session().attribute("userId");
                    User currentUser = User.findById(currentUserId);
                    Integer idPersona = currentUser.getInteger("id_persona");
                    sqlComms = "SELECT c.id_comision, c.horario, c.nombre AS comision_nombre, c.id_materia, dc.id_prof, " +
                                      "       pe.nombre AS docente_nombre, pe.apellido AS docente_apellido " +
                                      "FROM comisiones c " +
                                      "JOIN docente_comision dc ON c.id_comision = dc.id_comision " +
                                      "LEFT JOIN persons pe ON dc.id_prof = pe.id_per " +
                                      "WHERE c.id_materia IN (" + placeholders + ") AND dc.id_prof = ?";
                    Object[] params = new Object[materiaIds.size() + 1];
                    for (int i = 0; i < materiaIds.size(); i++) {
                        params[i] = materiaIds.get(i);
                    }
                    params[materiaIds.size()] = idPersona;
                    comisionesRaw = Base.findAll(sqlComms, params);
                }

                for (Materia m : materias) {
                    materiaMap.put(m.getIdMateria(), m);
                }
            }

            // 2. Extraer los nombres únicos de comisiones ordenados para el dropdown
            List<String> uniqueCommissionNames = new ArrayList<>();
            for (Map c : comisionesRaw) {
                String name = (String) c.get("comision_nombre");
                String nameClean = (name == null || name.trim().isEmpty()) ? "Comisión 1" : name.trim();
                if (!uniqueCommissionNames.contains(nameClean)) {
                    uniqueCommissionNames.add(nameClean);
                }
            }
            if (!uniqueCommissionNames.contains("Comisión 1")) {
                uniqueCommissionNames.add("Comisión 1");
            }
            Collections.sort(uniqueCommissionNames);

            // 3. Determinar qué comisión mostrar (por defecto la primera)
            String selectedComm = req.queryParams("comision");
            if (selectedComm == null || selectedComm.trim().isEmpty()) {
                selectedComm = "Comisión 1";
            } else {
                selectedComm = selectedComm.trim();
            }
            if (!uniqueCommissionNames.contains(selectedComm)) {
                uniqueCommissionNames.add(selectedComm);
                Collections.sort(uniqueCommissionNames);
            }
            model.put("selectedComision", selectedComm);

            // Determinar qué materias ya están agregadas a esta comisión
            List<Map<String, Object>> materiasList = new ArrayList<>();
            for (Materia m : materias) {
                Map<String, Object> mMap = new HashMap<>();
                mMap.put("id_materia", m.getIdMateria());
                mMap.put("codigo", m.getCodigo());
                mMap.put("nombre", m.getNombre());
                mMap.put("anio", m.getAnio());
                mMap.put("periodo", m.getPeriodo());

                boolean isAdded = false;
                for (Map c : comisionesRaw) {
                    Integer commMatId = ((Number) c.get("id_materia")).intValue();
                    String commName = (String) c.get("comision_nombre");
                    if (commName == null || commName.trim().isEmpty()) {
                        commName = "Comisión 1";
                    }
                    if (commMatId.equals(m.getIdMateria()) && commName.equalsIgnoreCase(selectedComm)) {
                        isAdded = true;
                        break;
                    }
                }
                mMap.put("isAdded", isAdded);
                materiasList.add(mMap);
            }
            model.put("materiasList", materiasList);
            model.put("hasMaterias", !materiasList.isEmpty());

            // Calcular el siguiente nombre de comisión para "+ Agregar Comisión"
            int maxCommNumber = 1;
            for (String name : uniqueCommissionNames) {
                if (name.startsWith("Comisión ")) {
                    try {
                        int num = Integer.parseInt(name.replace("Comisión ", "").trim());
                        if (num > maxCommNumber) {
                            maxCommNumber = num;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            int nextCommNumber = maxCommNumber + 1;
            String nextComisionName = "Comisión " + nextCommNumber;
            model.put("nextComisionName", nextComisionName);

            // Poblar las opciones del dropdown para la vista
            List<Map<String, Object>> comisionOptions = new ArrayList<>();
            for (String cName : uniqueCommissionNames) {
                Map<String, Object> opt = new HashMap<>();
                opt.put("nombre", cName);
                opt.put("selected", cName.equals(selectedComm));
                comisionOptions.add(opt);
            }
            model.put("comisionOptions", comisionOptions);
            model.put("hasComisionOptions", true);

            // 4. Parsear y filtrar los slots horarias de la comisión seleccionada
            List<ScheduleSlot> activeSlots = new ArrayList<>();
            for (Map cr : comisionesRaw) {
                String commNombre = (String) cr.get("comision_nombre");
                if (commNombre == null || commNombre.trim().isEmpty()) {
                    commNombre = "Comisión 1";
                }
                if (isAdmin && !commNombre.equals(selectedComm)) {
                    continue;
                }

                Integer mId = ((Number) cr.get("id_materia")).intValue();
                Materia mat = materiaMap.get(mId);
                if (mat == null) continue;

                String docNombre = (String) cr.get("docente_nombre");
                String docApellido = (String) cr.get("docente_apellido");
                String teacher = (docNombre != null && docApellido != null) ? docNombre + " " + docApellido : "";

                List<ScheduleSlot> parsedSlots = parseSchedule(
                    (String) cr.get("horario"),
                    mat.getCodigo(),
                    mat.getNombre(),
                    teacher
                );
                int idComision = ((Number) cr.get("id_comision")).intValue();
                int idMateria = ((Number) cr.get("id_materia")).intValue();
                String horarioRaw = (String) cr.get("horario");
                Integer tId = cr.get("id_prof") != null ? ((Number) cr.get("id_prof")).intValue() : null;
                Integer sYear = mat.getAnio();
                String sPeriod = mat.getPeriodo();

                for (ScheduleSlot slot : parsedSlots) {
                    slot.idComision = idComision;
                    slot.idMateria = idMateria;
                    slot.comisionNombre = commNombre;
                    slot.horarioRaw = horarioRaw;
                    slot.teacherId = tId;
                    slot.subjectYear = sYear;
                    slot.subjectPeriod = sPeriod;
                }
                activeSlots.addAll(parsedSlots);
            }

            // Deduplicar activeSlots por comisión, día y horario para evitar duplicados en el grid
            List<ScheduleSlot> deduplicatedActiveSlots = new ArrayList<>();
            Set<String> activeKeys = new HashSet<>();
            for (ScheduleSlot s : activeSlots) {
                String key = s.idComision + "-" + s.day + "-" + s.startHour + "-" + s.endHour;
                if (!activeKeys.contains(key)) {
                    activeKeys.add(key);
                    deduplicatedActiveSlots.add(s);
                }
            }
            activeSlots = deduplicatedActiveSlots;

            // 4.5 Obtener todas las comisiones en el mismo período (o anual) para evaluar superposición de docentes
            List<String> targetPeriodos = new ArrayList<>();
            targetPeriodos.add(periodo);
            if ("1° Cuatrimestre".equals(periodo) || "2° Cuatrimestre".equals(periodo)) {
                targetPeriodos.add("Anual");
            } else if ("Anual".equals(periodo)) {
                targetPeriodos.add("1° Cuatrimestre");
                targetPeriodos.add("2° Cuatrimestre");
            }

            String placeholdersPeriodos = targetPeriodos.stream().map(v -> "?").collect(Collectors.joining(", "));
            String sqlAllPeriodComms = "SELECT c.id_comision, c.horario, c.nombre AS comision_nombre, c.id_materia, " +
                                       "       m.codigo AS materia_codigo, m.nombre AS materia_nombre, m.anio AS materia_anio, m.periodo AS materia_periodo, " +
                                       "       dc.id_prof, pe.nombre AS docente_nombre, pe.apellido AS docente_apellido " +
                                       "FROM comisiones c " +
                                       "JOIN materias m ON c.id_materia = m.id_materia " +
                                       "LEFT JOIN docente_comision dc ON c.id_comision = dc.id_comision " +
                                       "LEFT JOIN persons pe ON dc.id_prof = pe.id_per " +
                                       "WHERE m.periodo IN (" + placeholdersPeriodos + ")";
            List<Map> allPeriodComms = Base.findAll(sqlAllPeriodComms, targetPeriodos.toArray());

            List<ScheduleSlot> allPeriodSlots = new ArrayList<>();
            for (Map cr : allPeriodComms) {
                String commNombre = (String) cr.get("comision_nombre");
                if (commNombre == null || commNombre.trim().isEmpty()) {
                    commNombre = "Comisión 1";
                }
                String docNombre = (String) cr.get("docente_nombre");
                String docApellido = (String) cr.get("docente_apellido");
                String teacher = (docNombre != null && docApellido != null) ? docNombre + " " + docApellido : "";

                String horario = (String) cr.get("horario");
                String matCodigo = (String) cr.get("materia_codigo");
                String matNombre = (String) cr.get("materia_nombre");

                List<ScheduleSlot> parsedSlots = parseSchedule(horario, matCodigo, matNombre, teacher);

                Integer idComision = ((Number) cr.get("id_comision")).intValue();
                Integer idMateria = ((Number) cr.get("id_materia")).intValue();
                Integer matAnio = ((Number) cr.get("materia_anio")).intValue();
                String matPeriod = (String) cr.get("materia_periodo");
                Integer teacherId = cr.get("id_prof") != null ? ((Number) cr.get("id_prof")).intValue() : null;

                for (ScheduleSlot slot : parsedSlots) {
                    slot.idComision = idComision;
                    slot.idMateria = idMateria;
                    slot.comisionNombre = commNombre;
                    slot.horarioRaw = horario;
                    slot.teacherId = teacherId;
                    slot.subjectYear = matAnio;
                    slot.subjectPeriod = matPeriod;
                }
                allPeriodSlots.addAll(parsedSlots);
            }

            // Deduplicar allPeriodSlots por comisión, día y horario para evitar duplicaciones en la detección de conflictos
            List<ScheduleSlot> deduplicatedAllPeriodSlots = new ArrayList<>();
            Set<String> periodKeys = new HashSet<>();
            for (ScheduleSlot s : allPeriodSlots) {
                String key = s.idComision + "-" + s.day + "-" + s.startHour + "-" + s.endHour;
                if (!periodKeys.contains(key)) {
                    periodKeys.add(key);
                    deduplicatedAllPeriodSlots.add(s);
                }
            }
            allPeriodSlots = deduplicatedAllPeriodSlots;

            // 5. Construir la lista de comisiones planificadas (plannedCommissions) en lugar de la grilla
            List<Map> allProfessors = Base.findAll(
                "SELECT p.id_prof, pe.nombre, pe.apellido " +
                "FROM professors p " +
                "JOIN persons pe ON p.id_prof = pe.id_per " +
                "ORDER BY pe.apellido, pe.nombre"
            );

            List<Map<String, Object>> plannedCommissions = new ArrayList<>();
            boolean commissionHasConflict = false;

            for (Map cr : comisionesRaw) {
                String commNombre = (String) cr.get("comision_nombre");
                if (commNombre == null || commNombre.trim().isEmpty()) {
                    commNombre = "Comisión 1";
                }
                if (isAdmin && !commNombre.equals(selectedComm)) {
                    continue;
                }

                int idComision = ((Number) cr.get("id_comision")).intValue();
                int idMateria = ((Number) cr.get("id_materia")).intValue();
                Materia mat = materiaMap.get(idMateria);
                if (mat == null) continue;

                String docNombre = (String) cr.get("docente_nombre");
                String docApellido = (String) cr.get("docente_apellido");
                String teacherName = (docNombre != null && docApellido != null) ? docNombre + " " + docApellido : "";
                Integer tId = cr.get("id_prof") != null ? ((Number) cr.get("id_prof")).intValue() : null;
                String horarioRaw = (String) cr.get("horario");

                // Parse slots for this specific commission to check conflicts
                List<ScheduleSlot> slots = parseSchedule(horarioRaw, mat.getCodigo(), mat.getNombre(), teacherName);

                boolean hasConf = false;
                boolean isTeachConf = false;
                List<Map<String, Object>> conflictsList = new ArrayList<>();

                for (ScheduleSlot slot : slots) {
                    slot.idComision = idComision;
                    slot.idMateria = idMateria;
                    slot.comisionNombre = commNombre;
                    slot.horarioRaw = horarioRaw;
                    slot.teacherId = tId;
                    slot.subjectYear = mat.getAnio();
                    slot.subjectPeriod = mat.getPeriodo();

                    // Check teacher conflicts: same teacher, different commission, overlapping day and time
                    if (tId != null) {
                        for (ScheduleSlot s : allPeriodSlots) {
                            if (s.teacherId != null && s.teacherId.equals(tId)
                                    && s.idComision != idComision
                                    && s.day.equalsIgnoreCase(slot.day)
                                    && s.startHour < slot.endHour && s.endHour > slot.startHour) {
                                hasConf = true;
                                isTeachConf = true;
                                commissionHasConflict = true;

                                Map<String, Object> cc = new HashMap<>();
                                cc.put("subjectCode", s.subjectCode);
                                cc.put("subjectName", s.subjectName);
                                cc.put("teacherName", s.teacherName);
                                cc.put("timeStr", s.startStr + " - " + s.endStr);
                                cc.put("idComision", s.idComision);
                                cc.put("idMateria", s.idMateria);
                                cc.put("comisionNombre", s.comisionNombre);
                                cc.put("horarioRaw", s.horarioRaw);
                                cc.put("anioMateria", s.subjectYear);
                                cc.put("isCurrentYear", s.subjectYear == anio);
                                conflictsList.add(cc);
                            }
                        }
                    }

                    // Check scheduling conflicts: same commission (in activeSlots), different subject, overlapping day and time
                    for (ScheduleSlot s : activeSlots) {
                        if (s.idMateria != idMateria
                                && s.day.equalsIgnoreCase(slot.day)
                                && s.startHour < slot.endHour && s.endHour > slot.startHour) {
                            hasConf = true;
                            commissionHasConflict = true;

                            Map<String, Object> cc = new HashMap<>();
                            cc.put("subjectCode", s.subjectCode);
                            cc.put("subjectName", s.subjectName);
                            cc.put("teacherName", s.teacherName);
                            cc.put("timeStr", s.startStr + " - " + s.endStr);
                            cc.put("idComision", s.idComision);
                            cc.put("idMateria", s.idMateria);
                            cc.put("comisionNombre", s.comisionNombre);
                            cc.put("horarioRaw", s.horarioRaw);
                            cc.put("anioMateria", s.subjectYear);
                            cc.put("isCurrentYear", s.subjectYear == anio);
                            conflictsList.add(cc);
                        }
                    }
                }

                // Deduplicate conflicts list by commission ID to keep it clean in UI
                List<Map<String, Object>> dedupedConflicts = new ArrayList<>();
                Set<Integer> seenCommIds = new HashSet<>();
                for (Map<String, Object> cItem : conflictsList) {
                    int cId = (int) cItem.get("idComision");
                    if (!seenCommIds.contains(cId)) {
                        seenCommIds.add(cId);
                        dedupedConflicts.add(cItem);
                    }
                }

                Map<String, Object> commMap = new HashMap<>();
                commMap.put("idComision", idComision);
                commMap.put("idMateria", idMateria);
                commMap.put("comisionNombre", commNombre);
                commMap.put("subjectCode", mat.getCodigo());
                commMap.put("subjectName", mat.getNombre());
                commMap.put("subjectYear", mat.getAnio());
                commMap.put("horarioRaw", horarioRaw);
                commMap.put("teacherId", tId);
                commMap.put("teacherName", teacherName);
                commMap.put("hasConflict", hasConf);
                commMap.put("isTeacherConflict", isTeachConf);
                commMap.put("conflicts", dedupedConflicts);

                // Build teacher options for this commission row dropdown
                List<Map<String, Object>> teachersList = new ArrayList<>();
                Map<String, Object> emptyOpt = new HashMap<>();
                emptyOpt.put("id_prof", "");
                emptyOpt.put("nombre", "Sin asignar");
                emptyOpt.put("apellido", "");
                emptyOpt.put("selected", tId == null);
                teachersList.add(emptyOpt);

                for (Map prof : allProfessors) {
                    Map<String, Object> opt = new HashMap<>();
                    int profId = ((Number) prof.get("id_prof")).intValue();
                    opt.put("id_prof", profId);
                    opt.put("nombre", prof.get("nombre"));
                    opt.put("apellido", prof.get("apellido"));
                    opt.put("selected", tId != null && tId.equals(profId));
                    teachersList.add(opt);
                }
                commMap.put("teachersList", teachersList);

                plannedCommissions.add(commMap);
            }

            model.put("plannedCommissions", plannedCommissions);
            model.put("hasPlannedCommissions", !plannedCommissions.isEmpty());
            model.put("hasConflict", commissionHasConflict);
            model.put("hasComisiones", true);

            return new ModelAndView(model, "cronograma.mustache");
        }, new MustacheTemplateEngine());

        get("/planificacion/periodo/:periodo/cronograma", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            initCommonModel(req, model);

            String periodo = req.params(":periodo");
            if (periodo == null) {
                res.redirect("/planificacion?error=" + URLEncoder.encode("Período académico no especificado.", StandardCharsets.UTF_8));
                return null;
            }

            model.put("periodo", periodo);

            String userRole = req.session().attribute("tipoUsuario");
            boolean isAdmin = "ADMIN".equals(userRole) || "ADMINISTRADOR".equals(userRole);
            model.put("isAdmin", isAdmin);

            int currentUserId = req.session().attribute("userId");
            User currentUser = User.findById(currentUserId);
            Integer idPersona = currentUser.getInteger("id_persona");

            // 1. Obtener todas las materias pertenecientes al cuatrimestre especificado más las de tipo "Anual"
            List<Materia> materias = Materia.where("periodo = ? OR periodo = 'Anual'", periodo);
            model.put("materiasList", materias);
            model.put("hasMaterias", !materias.isEmpty());

            List<Map> comisionesRaw = new ArrayList<>();
            Map<Integer, Materia> materiaMap = new HashMap<>();

            if (!materias.isEmpty()) {
                List<Integer> materiaIds = materias.stream().map(Materia::getIdMateria).collect(Collectors.toList());
                String placeholders = materiaIds.stream().map(v -> "?").collect(Collectors.joining(", "));

                // Consultar todas las comisiones asignadas a dichas materias para el docente logueado
                String sqlComms = "SELECT c.id_comision, c.horario, c.nombre AS comision_nombre, c.id_materia, dc.id_prof, " +
                                  "       pe.nombre AS docente_nombre, pe.apellido AS docente_apellido " +
                                  "FROM comisiones c " +
                                  "JOIN docente_comision dc ON c.id_comision = dc.id_comision " +
                                  "LEFT JOIN persons pe ON dc.id_prof = pe.id_per " +
                                  "WHERE c.id_materia IN (" + placeholders + ") AND dc.id_prof = ?";
                Object[] params = new Object[materiaIds.size() + 1];
                for (int i = 0; i < materiaIds.size(); i++) {
                    params[i] = materiaIds.get(i);
                }
                params[materiaIds.size()] = idPersona;
                comisionesRaw = Base.findAll(sqlComms, params);

                for (Materia m : materias) {
                    materiaMap.put(m.getIdMateria(), m);
                }
            }

            // Opciones de comisiones no son necesarias en la vista unificada del docente
            model.put("selectedComision", "Mis Horarios");
            model.put("nextComisionName", "Comisión 1");
            model.put("comisionOptions", new ArrayList<>());
            model.put("hasComisionOptions", false);

            // 4. Parsear todos los slots para todas las comisiones encontradas
            List<ScheduleSlot> activeSlots = new ArrayList<>();
            for (Map cr : comisionesRaw) {
                String commNombre = (String) cr.get("comision_nombre");
                if (commNombre == null || commNombre.trim().isEmpty()) {
                    commNombre = "Comisión 1";
                }

                Integer mId = ((Number) cr.get("id_materia")).intValue();
                Materia mat = materiaMap.get(mId);
                if (mat == null) continue;

                String docNombre = (String) cr.get("docente_nombre");
                String docApellido = (String) cr.get("docente_apellido");
                String teacher = (docNombre != null && docApellido != null) ? docNombre + " " + docApellido : "";

                List<ScheduleSlot> parsedSlots = parseSchedule(
                    (String) cr.get("horario"),
                    mat.getCodigo(),
                    mat.getNombre(),
                    teacher
                );
                int idComision = ((Number) cr.get("id_comision")).intValue();
                int idMateria = ((Number) cr.get("id_materia")).intValue();
                String horarioRaw = (String) cr.get("horario");
                Integer tId = cr.get("id_prof") != null ? ((Number) cr.get("id_prof")).intValue() : null;
                Integer sYear = mat.getAnio();
                String sPeriod = mat.getPeriodo();

                for (ScheduleSlot slot : parsedSlots) {
                    slot.idComision = idComision;
                    slot.idMateria = idMateria;
                    slot.comisionNombre = commNombre;
                    slot.horarioRaw = horarioRaw;
                    slot.teacherId = tId;
                    slot.subjectYear = sYear;
                    slot.subjectPeriod = sPeriod;
                }
                activeSlots.addAll(parsedSlots);
            }

            // Deduplicar activeSlots por comisión, día y horario para evitar duplicados en el grid del docente
            List<ScheduleSlot> deduplicatedActiveSlots = new ArrayList<>();
            Set<String> activeKeys = new HashSet<>();
            for (ScheduleSlot s : activeSlots) {
                String key = s.idComision + "-" + s.day + "-" + s.startHour + "-" + s.endHour;
                if (!activeKeys.contains(key)) {
                    activeKeys.add(key);
                    deduplicatedActiveSlots.add(s);
                }
            }
            activeSlots = deduplicatedActiveSlots;

            // 5. Construir la lista de comisiones planificadas (plannedCommissions) en lugar de la grilla
            List<Map<String, Object>> plannedCommissions = new ArrayList<>();
            boolean commissionHasConflict = false;

            for (Map cr : comisionesRaw) {
                String commNombre = (String) cr.get("comision_nombre");
                if (commNombre == null || commNombre.trim().isEmpty()) {
                    commNombre = "Comisión 1";
                }

                int idComision = ((Number) cr.get("id_comision")).intValue();
                int idMateria = ((Number) cr.get("id_materia")).intValue();
                Materia mat = materiaMap.get(idMateria);
                if (mat == null) continue;

                String docNombre = (String) cr.get("docente_nombre");
                String docApellido = (String) cr.get("docente_apellido");
                String teacherName = (docNombre != null && docApellido != null) ? docNombre + " " + docApellido : "";
                Integer tId = cr.get("id_prof") != null ? ((Number) cr.get("id_prof")).intValue() : null;
                String horarioRaw = (String) cr.get("horario");

                // Parse slots for this specific commission to check conflicts
                List<ScheduleSlot> slots = parseSchedule(horarioRaw, mat.getCodigo(), mat.getNombre(), teacherName);

                boolean hasConf = false;
                List<Map<String, Object>> conflictsList = new ArrayList<>();

                for (ScheduleSlot slot : slots) {
                    slot.idComision = idComision;
                    slot.idMateria = idMateria;
                    slot.comisionNombre = commNombre;
                    slot.horarioRaw = horarioRaw;
                    slot.teacherId = tId;
                    slot.subjectYear = mat.getAnio();
                    slot.subjectPeriod = mat.getPeriodo();

                    // Check schedule conflict: other commissions in teacher's active slots that overlap
                    for (ScheduleSlot s : activeSlots) {
                        if (s.idComision != idComision
                                && s.day.equalsIgnoreCase(slot.day)
                                && s.startHour < slot.endHour && s.endHour > slot.startHour) {
                            hasConf = true;
                            commissionHasConflict = true;

                            Map<String, Object> cc = new HashMap<>();
                            cc.put("subjectCode", s.subjectCode);
                            cc.put("subjectName", s.subjectName);
                            cc.put("teacherName", s.teacherName);
                            cc.put("timeStr", s.startStr + " - " + s.endStr);
                            cc.put("idComision", s.idComision);
                            cc.put("idMateria", s.idMateria);
                            cc.put("comisionNombre", s.comisionNombre);
                            cc.put("horarioRaw", s.horarioRaw);
                            cc.put("anioMateria", s.subjectYear);
                            cc.put("isCurrentYear", false);
                            conflictsList.add(cc);
                        }
                    }
                }

                // Deduplicate conflicts list by commission ID
                List<Map<String, Object>> dedupedConflicts = new ArrayList<>();
                Set<Integer> seenCommIds = new HashSet<>();
                for (Map<String, Object> cItem : conflictsList) {
                    int cId = (int) cItem.get("idComision");
                    if (!seenCommIds.contains(cId)) {
                        seenCommIds.add(cId);
                        dedupedConflicts.add(cItem);
                    }
                }

                Map<String, Object> commMap = new HashMap<>();
                commMap.put("idComision", idComision);
                commMap.put("idMateria", idMateria);
                commMap.put("comisionNombre", commNombre);
                commMap.put("subjectCode", mat.getCodigo());
                commMap.put("subjectName", mat.getNombre());
                commMap.put("subjectYear", mat.getAnio());
                commMap.put("horarioRaw", horarioRaw);
                commMap.put("teacherId", tId);
                commMap.put("teacherName", teacherName);
                commMap.put("hasConflict", hasConf);
                commMap.put("isTeacherConflict", false);
                commMap.put("conflicts", dedupedConflicts);

                plannedCommissions.add(commMap);
            }

            model.put("plannedCommissions", plannedCommissions);
            model.put("hasPlannedCommissions", !plannedCommissions.isEmpty());
            model.put("hasConflict", commissionHasConflict);
            model.put("hasComisiones", true);
            model.put("isUnifiedPeriodView", true);

            return new ModelAndView(model, "cronograma.mustache");
        }, new MustacheTemplateEngine());

        get("/situacion-academica", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            initCommonModel(req, model);

            int currentUserId = req.session().attribute("userId");
            User currentUser = User.findById(currentUserId);
            Integer idPersona = currentUser.getInteger("id_persona");

            String sql = "SELECT m.nombre AS materia_nombre, m.codigo AS materia_codigo, m.periodo AS materia_periodo, " +
                         "       c.horario, e.nota, e.fecha AS fecha_nota, i.condicion " +
                         "FROM inscripciones i " +
                         "JOIN materias m ON i.id_materia = m.id_materia " +
                         "LEFT JOIN comisiones c ON i.id_comision = c.id_comision " +
                         "LEFT JOIN evaluaciones e ON e.id_alumno = i.id_alumno AND e.id_comision = c.id_comision " +
                         "WHERE i.id_alumno = ? AND i.condicion IS NOT NULL";
            List<Map> rows = Base.findAll(sql, idPersona);

            List<Map<String, Object>> aprobadas = new ArrayList<>();
            List<Map<String, Object>> regularizadas = new ArrayList<>();

            for (Map row : rows) {
                String cond = (String) row.get("condicion");
                Map<String, Object> item = new HashMap<>();
                item.put("materia_nombre", row.get("materia_nombre"));
                item.put("materia_codigo", row.get("materia_codigo"));
                
                if ("A".equals(cond)) {
                    item.put("nota", row.get("nota"));
                    item.put("fecha_nota", row.get("fecha_nota"));
                    aprobadas.add(item);
                } else if ("R".equals(cond)) {
                    item.put("periodo", row.get("materia_periodo"));
                    item.put("horario", row.get("horario"));
                    regularizadas.add(item);
                }
            }

            model.put("aprobadas", aprobadas);
            model.put("regularizadas", regularizadas);
            model.put("totalAprobadas", aprobadas.size());
            model.put("totalRegularizadas", regularizadas.size());
            model.put("hasAprobadas", !aprobadas.isEmpty());
            model.put("hasRegularizadas", !regularizadas.isEmpty());

            return new ModelAndView(model, "situacion_academica.mustache");
        }, new MustacheTemplateEngine());

    }

    public static class ScheduleSlot {
        public String day;
        public String startStr;
        public String endStr;
        public double startHour;
        public double endHour;
        public String subjectCode;
        public String subjectName;
        public String teacherName;
        public boolean hasConflict;
        public int idComision;
        public int idMateria;
        public String comisionNombre;
        public String horarioRaw;
        // Campos adicionales para superposición de docentes
        public Integer teacherId;
        public Integer subjectYear;
        public String subjectPeriod;
    }

    private static List<ScheduleSlot> parseSchedule(String horario, String subjectCode, String subjectName, String teacherName) {
        List<ScheduleSlot> slots = new ArrayList<>();
        if (horario == null || horario.trim().isEmpty()) {
            return slots;
        }

        // Dividir por delimitadores " y " o "," o " y/o "
        String[] parts = horario.split("\\s+y\\s+|,\\s*");
        boolean parsedAllParts = true;
        List<ScheduleSlot> tempSlots = new ArrayList<>();

        java.util.regex.Pattern timePattern = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})\\s*(?:-|a)\\s*(\\d{1,2}):(\\d{2})");

        for (String part : parts) {
            String partLower = part.toLowerCase();
            List<String> partDays = new ArrayList<>();
            if (partLower.contains("lunes")) partDays.add("Lunes");
            if (partLower.contains("martes")) partDays.add("Martes");
            if (partLower.contains("miércoles") || partLower.contains("miercoles")) partDays.add("Miércoles");
            if (partLower.contains("jueves")) partDays.add("Jueves");
            if (partLower.contains("viernes")) partDays.add("Viernes");
            if (partLower.contains("sábado") || partLower.contains("sabado")) partDays.add("Sábado");
            if (partLower.contains("domingo")) partDays.add("Domingo");

            java.util.regex.Matcher matcher = timePattern.matcher(part);
            if (!partDays.isEmpty() && matcher.find()) {
                int h1 = Integer.parseInt(matcher.group(1));
                int m1 = Integer.parseInt(matcher.group(2));
                int h2 = Integer.parseInt(matcher.group(3));
                int m2 = Integer.parseInt(matcher.group(4));

                String startStr = String.format("%02d:%02d", h1, m1);
                String endStr = String.format("%02d:%02d", h2, m2);
                double startHour = h1 + (m1 / 60.0);
                double endHour = h2 + (m2 / 60.0);

                for (String day : partDays) {
                    ScheduleSlot slot = new ScheduleSlot();
                    slot.day = day;
                    slot.startStr = startStr;
                    slot.endStr = endStr;
                    slot.startHour = startHour;
                    slot.endHour = endHour;
                    slot.subjectCode = subjectCode;
                    slot.subjectName = subjectName;
                    slot.teacherName = teacherName;
                    slot.hasConflict = false;
                    tempSlots.add(slot);
                }
            } else {
                // Si alguna parte no contiene un día o rango horario, puede ser que sea el formato antiguo "Lunes y Miércoles 18:00 - 20:00"
                parsedAllParts = false;
                break;
            }
        }

        if (parsedAllParts && !tempSlots.isEmpty()) {
            return tempSlots;
        }

        // Fallback: Analizar toda la cadena como un único horario para múltiples días
        slots.clear();
        String lower = horario.toLowerCase();
        List<String> days = new ArrayList<>();
        if (lower.contains("lunes")) days.add("Lunes");
        if (lower.contains("martes")) days.add("Martes");
        if (lower.contains("miércoles") || lower.contains("miercoles")) days.add("Miércoles");
        if (lower.contains("jueves")) days.add("Jueves");
        if (lower.contains("viernes")) days.add("Viernes");
        if (lower.contains("sábado") || lower.contains("sabado")) days.add("Sábado");
        if (lower.contains("domingo")) days.add("Domingo");

        java.util.regex.Matcher matcher = timePattern.matcher(horario);
        
        String startStr = "00:00";
        String endStr = "00:00";
        double startHour = 0.0;
        double endHour = 0.0;

        if (matcher.find()) {
            int h1 = Integer.parseInt(matcher.group(1));
            int m1 = Integer.parseInt(matcher.group(2));
            int h2 = Integer.parseInt(matcher.group(3));
            int m2 = Integer.parseInt(matcher.group(4));

            startStr = String.format("%02d:%02d", h1, m1);
            endStr = String.format("%02d:%02d", h2, m2);
            startHour = h1 + (m1 / 60.0);
            endHour = h2 + (m2 / 60.0);
        }

        if (days.isEmpty()) {
            days.add("Sin Día");
        }

        for (String day : days) {
            ScheduleSlot slot = new ScheduleSlot();
            slot.day = day;
            slot.startStr = startStr;
            slot.endStr = endStr;
            slot.startHour = startHour;
            slot.endHour = endHour;
            slot.subjectCode = subjectCode;
            slot.subjectName = subjectName;
            slot.teacherName = teacherName;
            slot.hasConflict = false;
            slots.add(slot);
        }

        return slots;
    }

    // --- MÉTODOS DE SOPORTE E INICIALIZACIÓN ---

    private static void checkAuth(spark.Request req, spark.Response res, List<String> allowedRoles) {
        Boolean loggedIn = req.session().attribute("loggedIn");
        String role = req.session().attribute("tipoUsuario");
        
        // Mapear administradores a un rol común
        String normalizedRole = role;
        if ("ADMINISTRADOR".equals(role)) {
            normalizedRole = "ADMIN";
        }

        if (loggedIn == null || !loggedIn || role == null) {
            halt(401, "No autorizado. Inicie sesión.");
        }

        boolean authorized = false;
        for (String r : allowedRoles) {
            String normAllowed = "ADMINISTRADOR".equals(r) ? "ADMIN" : r;
            if (normAllowed.equals(normalizedRole)) {
                authorized = true;
                break;
            }
        }

        if (!authorized) {
            halt(403, "Acceso denegado: Permisos insuficientes para el rol " + role);
        }
    }

    private static void initCommonModel(spark.Request req, Map<String, Object> model) {
        String username = req.session().attribute("currentUserUsername");
        String role = req.session().attribute("tipoUsuario");
        Integer userId = req.session().attribute("userId");

        model.put("currentUser", username);
        model.put("userRole", role);
        model.put("isAlumnoRole", "ALUMNO".equals(role));
        model.put("isDocenteRole", "DOCENTE".equals(role));
        model.put("isAdminRole", "ADMIN".equals(role) || "ADMINISTRADOR".equals(role));

        // Obtener contador de notificaciones no leídas
        if (userId != null) {
            long unreadNotifications = Notification.count("id_usuario = ? AND leida = 0", userId);
            model.put("unreadNotifications", unreadNotifications);
            model.put("hasNotifications", unreadNotifications > 0);
        }

        // Recuperar y limpiar query parameters de alertas en las vistas
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

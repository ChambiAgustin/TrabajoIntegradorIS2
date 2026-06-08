package com.is1.proyecto.daos;

import com.is1.proyecto.models.*;
import org.javalite.activejdbc.Base;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object (DAO) para gestionar las operaciones de usuario
 * relativas al sistema de auto-registro y aprobación de administrador.
 */
public class UsuarioDAO {

    /**
     * Crea un nuevo usuario en la base de datos (Persona, Usuario, y Alumno/Profesor) en una sola transacción.
     */
    public static void crear(String nombre, String apellido, String email, int dni, long tel, String direccion, String rol, String passwordHash) throws Exception {
        Base.openTransaction();
        try {
            // 1. Crear Persona
            Person p = new Person();
            p.set("nombre", nombre);
            p.set("apellido", apellido);
            p.set("mail", email.trim().toLowerCase());
            p.set("dni", dni);
            p.set("tel", tel);
            p.set("direccion", direccion);
            p.saveIt();

            int idPersona = ((Number) p.getId()).intValue();

            // 2. Crear Usuario
            User u = new User();
            u.setName(String.valueOf(dni)); // El DNI se utiliza como nombre de usuario (username)
            u.setPassword(passwordHash);
            u.setTipoUsuario(rol.toUpperCase());
            u.setPersonId(idPersona);
            u.set("estado", "pendiente");
            u.set("active", 0); // Deshabilitado hasta que se apruebe
            u.set("creado_en", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            u.saveIt();

            // 3. Crear Alumno o Profesor según corresponda (se usa el DNI como legajo automáticamente)
            int legajoNum = dni;
            if ("ALUMNO".equalsIgnoreCase(rol)) {
                Alumno alum = new Alumno();
                alum.setId(idPersona);
                alum.set("legajo", legajoNum);
                alum.set("ingresante", true);
                alum.insert();
            } else if ("DOCENTE".equalsIgnoreCase(rol)) {
                Professor prof = new Professor();
                prof.setId(idPersona);
                prof.setLegajo(legajoNum);
                prof.setCargo("Interino");
                prof.insert();
            }

            Base.commitTransaction();
        } catch (Exception e) {
            Base.rollbackTransaction();
            throw e;
        }
    }

    /**
     * Busca un usuario por su email de persona asociado.
     */
    public static User buscarPorEmail(String email) {
        Person p = Person.findFirst("mail = ?", email.trim().toLowerCase());
        if (p != null) {
            return User.findFirst("id_persona = ?", p.getId());
        }
        return null;
    }

    /**
     * Busca un usuario por su ID de usuario.
     */
    public static User buscarPorId(int id) {
        return User.findById(id);
    }

    /**
     * Lista todos los usuarios con un determinado estado en formato de mapa (apto para Mustache).
     */
    public static List<Map> listarPorEstado(String estado) {
        String sql = "SELECT u.id, u.name as username, u.tipo_usuario as rol, u.estado, u.creado_en, " +
                     "p.nombre, p.apellido, p.mail as email, p.dni, p.tel, p.direccion " +
                     "FROM users u " +
                     "JOIN persons p ON u.id_persona = p.id_per " +
                     "WHERE u.estado = ? AND u.tipo_usuario != 'ADMIN' " +
                     "ORDER BY u.creado_en DESC";
        return Base.findAll(sql, estado);
    }

    /**
     * Lista todos los usuarios (excluyendo administradores) en formato de mapa.
     */
    public static List<Map> listarTodos() {
        String sql = "SELECT u.id, u.name as username, u.tipo_usuario as rol, u.estado, u.creado_en, " +
                     "p.nombre, p.apellido, p.mail as email, p.dni, p.tel, p.direccion " +
                     "FROM users u " +
                     "JOIN persons p ON u.id_persona = p.id_per " +
                     "WHERE u.tipo_usuario != 'ADMIN' " +
                     "ORDER BY u.creado_en DESC";
        return Base.findAll(sql);
    }

    /**
     * Actualiza el estado de aprobación de un usuario.
     */
    public static boolean actualizarEstado(int id, String estado) {
        User u = User.findById(id);
        if (u != null) {
            u.set("estado", estado);
            u.set("active", "activo".equals(estado) ? 1 : 0);
            u.saveIt();
            return true;
        }
        return false;
    }

    /**
     * Verifica si un email ya existe en la tabla persons.
     */
    public static boolean existeEmail(String email) {
        return Person.count("mail = ?", email.trim().toLowerCase()) > 0;
    }

    /**
     * Verifica si un DNI ya existe en la tabla persons.
     */
    public static boolean existeDni(int dni) {
        return Person.count("dni = ?", dni) > 0;
    }

    /**
     * Elimina un usuario por completo de la base de datos (con todas sus relaciones).
     */
    public static void eliminar(int id) throws Exception {
        User u = User.findById(id);
        if (u == null) {
            throw new Exception("El usuario no existe.");
        }
        Integer idPersona = u.getInteger("id_persona");
        String role = u.getString("tipo_usuario");

        Base.openTransaction();
        try {
            // 1. Eliminar filas hijas (Level 0)
            Base.exec("DELETE FROM mensajes WHERE id_emisor = ? OR id_receptor = ?", id, id);
            Base.exec("DELETE FROM notificaciones WHERE id_usuario = ?", id);
            
            if (idPersona != null) {
                Base.exec("DELETE FROM solicitudes_comision WHERE id_alumno = ?", idPersona);
                Base.exec("DELETE FROM inscripciones WHERE id_alumno = ?", idPersona);
                Base.exec("DELETE FROM evaluaciones WHERE id_alumno = ?", idPersona);
                Base.exec("DELETE FROM docente_comision WHERE id_prof = ?", idPersona);
                Base.exec("DELETE FROM docente_materia WHERE id_prof = ?", idPersona);
            }

            // 2. Eliminar filas intermedias (Level 1)
            Base.exec("DELETE FROM users WHERE id = ?", id);
            if (idPersona != null) {
                Base.exec("DELETE FROM alumnos WHERE id_alumno = ?", idPersona);
                Base.exec("DELETE FROM professors WHERE id_prof = ?", idPersona);
            }

            // 3. Eliminar fila principal (Level 2)
            if (idPersona != null) {
                Base.exec("DELETE FROM persons WHERE id_per = ?", idPersona);
            }

            Base.commitTransaction();
        } catch (Exception e) {
            Base.rollbackTransaction();
            throw e;
        }
    }
}

package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;
import org.javalite.activejdbc.annotations.IdName;

/**
 * Modelo ActiveJDBC que representa la tabla 'adjuntos'.
 */
@Table("adjuntos")
@IdName("id_adjunto")
public class Attachment extends Model {

    public Integer getMensajeId() {
        return getInteger("id_mensaje");
    }

    public void setMensajeId(Integer idMensaje) {
        set("id_mensaje", idMensaje);
    }

    public String getNombreArchivo() {
        return getString("nombre_archivo");
    }

    public void setNombreArchivo(String nombreArchivo) {
        set("nombre_archivo", nombreArchivo);
    }

    public String getRutaArchivo() {
        return getString("ruta_archivo");
    }

    public void setRutaArchivo(String rutaArchivo) {
        set("ruta_archivo", rutaArchivo);
    }
}

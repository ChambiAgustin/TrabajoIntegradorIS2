package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;
import org.javalite.activejdbc.annotations.IdName;

/**
 * Modelo ActiveJDBC que representa la tabla 'mensajes'.
 * Mapea la clase Message (inglés) a la tabla 'mensajes' (español) en la base de datos.
 */
@Table("mensajes")
@IdName("id_mensaje")
public class Message extends Model {

    public Integer getEmisorId() {
        return getInteger("id_emisor");
    }

    public void setEmisorId(Integer idEmisor) {
        set("id_emisor", idEmisor);
    }

    public Integer getReceptorId() {
        return getInteger("id_receptor");
    }

    public void setReceptorId(Integer idReceptor) {
        set("id_receptor", idReceptor);
    }

    public String getContenido() {
        return getString("contenido");
    }

    public void setContenido(String contenido) {
        set("contenido", contenido);
    }

    public String getFecha() {
        return getString("fecha");
    }

    public void setFecha(String fecha) {
        set("fecha", fecha);
    }

    public Boolean isLeido() {
        return getBoolean("leido");
    }

    public void setLeido(Boolean leido) {
        set("leido", leido);
    }

    public Integer getComisionId() {
        return getInteger("id_comision");
    }

    public void setComisionId(Integer idComision) {
        set("id_comision", idComision);
    }
}

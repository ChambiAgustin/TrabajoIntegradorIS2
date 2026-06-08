package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;
import org.javalite.activejdbc.annotations.IdName;

@Table("notificaciones")
@IdName("id_notificacion")
public class Notification extends Model {

    public Integer getIdNotificacion() {
        return getInteger("id_notificacion");
    }

    public Integer getId_notificacion() {
        return getInteger("id_notificacion");
    }

    public String getContenido() {
        return getString("contenido");
    }

    public String getFecha() {
        return getString("fecha");
    }

    public Boolean getLeida() {
        return getBoolean("leida");
    }

    public Integer getIdUsuario() {
        return getInteger("id_usuario");
    }

    public Integer getId_usuario() {
        return getInteger("id_usuario");
    }
}

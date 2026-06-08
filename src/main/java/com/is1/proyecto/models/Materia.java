package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;
import org.javalite.activejdbc.annotations.IdName;

@Table("materias")
@IdName("id_materia")
public class Materia extends Model {

    public Integer getIdMateria() {
        return getInteger("id_materia");
    }

    public Integer getId_materia() {
        return getInteger("id_materia");
    }

    public String getCodigo() {
        return getString("codigo");
    }

    public String getNombre() {
        return getString("nombre");
    }

    public Integer getCargaHoraria() {
        return getInteger("carga_horaria");
    }

    public String getPeriodo() {
        return getString("periodo");
    }

    public Integer getAnio() {
        return getInteger("anio");
    }
}

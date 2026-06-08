package com.is1.proyecto.models;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.Table;
import org.javalite.activejdbc.annotations.CompositePK;

@Table("correlativas")
@CompositePK({"id_materia", "id_correlativa"})
public class Correlativa extends Model {
}

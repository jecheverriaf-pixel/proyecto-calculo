package com.umg.calculo.model;

/** One of the assignment's predefined problem configurations (main exercise + 3 validation cases). */
public record PresetConfig(
        String id,
        String nombre,
        String f,
        String g,
        double a,
        double b,
        Metodo metodo,
        Eje eje,
        double volumenManual,
        String descripcion
) {}

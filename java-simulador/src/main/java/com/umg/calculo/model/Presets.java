package com.umg.calculo.model;

import java.util.List;

/** The assignment's fixed test cases: the main exercise plus the 3 required validation configs (Section 13). */
public final class Presets {

    private Presets() {}

    public static final PresetConfig MAIN_PROJECT = new PresetConfig(
            "main_project",
            "Ejercicio Principal (Arandelas - Eje X)",
            "x + 3", "x^2 + 1",
            -1, 2, Metodo.ARANDELAS, Eje.X,
            73.5133,
            "Sólido generado al girar y = x^2 + 1 e y = x + 3 en [-1, 2] alrededor del eje X."
    );

    public static final PresetConfig CONFIG_1 = new PresetConfig(
            "config_1",
            "Configuración 1 (Discos - Eje X)",
            "sqrt(x)", "0",
            0, 4, Metodo.DISCOS, Eje.X,
            25.1327, // pi * [x^2/2]_0^4 = 8*pi
            "f(x) = sqrt(x), g(x) = 0 en [0, 4] rotado en el Eje X."
    );

    public static final PresetConfig CONFIG_2 = new PresetConfig(
            "config_2",
            "Configuración 2 (Arandelas - Eje X)",
            "sqrt(x)", "x^2",
            0, 1, Metodo.ARANDELAS, Eje.X,
            0.9425, // pi * int_0^1 (x - x^4) dx = 0.3*pi
            "f(x) = sqrt(x), g(x) = x^2 en [0, 1] rotado en el Eje X."
    );

    public static final PresetConfig CONFIG_3 = new PresetConfig(
            "config_3",
            "Configuración 3 (Capas Cilíndricas - Eje Y)",
            "2*x", "x^2",
            0, 2, Metodo.CAPAS, Eje.Y,
            8.3776, // 2*pi * int_0^2 x*(2x - x^2) dx = 2*pi*(4/3) = 8*pi/3 = 8.37758...
            "f(x) = 2x, g(x) = x^2 en [0, 2] rotado en el Eje Y."
    );

    public static final List<PresetConfig> ALL = List.of(MAIN_PROJECT, CONFIG_1, CONFIG_2, CONFIG_3);
}

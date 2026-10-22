package com.umg.calculo.model;

public enum Metodo {
    DISCOS, ARANDELAS, CAPAS;

    public String etiqueta() {
        return switch (this) {
            case DISCOS -> "Método del Disco";
            case ARANDELAS -> "Método de Arandelas";
            case CAPAS -> "Método de Capas Cilíndricas";
        };
    }
}

package com.umg.calculo.engine;

import com.umg.calculo.model.Eje;
import com.umg.calculo.model.Metodo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/** Data-entry validation rules from the assignment (Section 6). */
public final class Validator {

    private Validator() {}

    public record Result(List<String> errores) {
        public boolean esValido() {
            return errores.isEmpty();
        }
    }

    public static Result validar(double a, double b, Metodo metodo, Eje eje,
                                  DoubleUnaryOperator f, DoubleUnaryOperator g) {
        List<String> errores = new ArrayList<>();

        if (Double.isNaN(a) || Double.isNaN(b)) {
            errores.add("Los límites de integración a y b deben ser números válidos.");
        } else if (a >= b) {
            errores.add("Debe cumplirse que a < b.");
        }

        if ((metodo == Metodo.DISCOS || metodo == Metodo.ARANDELAS) && eje != Eje.X) {
            errores.add("Los métodos de discos y arandelas deben ser alrededor del eje X.");
        }
        if (metodo == Metodo.CAPAS && eje != Eje.Y) {
            errores.add("El método de capas cilíndricas debe ser alrededor del eje Y.");
        }
        if (metodo == Metodo.CAPAS && a < 0) {
            errores.add("Para capas cilíndricas alrededor del eje Y debe cumplirse 0 <= a < b.");
        }

        if (!Double.isNaN(a) && !Double.isNaN(b) && a < b) {
            int steps = 200;
            double stepSize = (b - a) / steps;
            boolean invalidFunc = false;
            boolean lowerViolated = false;
            boolean negViolated = false;

            for (int i = 0; i <= steps; i++) {
                double x = a + i * stepSize;
                double fx = f.applyAsDouble(x);
                double gx = g.applyAsDouble(x);

                if (Double.isNaN(fx) || Double.isNaN(gx)) {
                    invalidFunc = true;
                    break;
                }
                if (fx < gx - 1e-9) {
                    lowerViolated = true;
                }
                if ((metodo == Metodo.DISCOS || metodo == Metodo.ARANDELAS) && gx < -1e-9) {
                    negViolated = true;
                }
            }

            if (invalidFunc) {
                errores.add("Las funciones no se pudieron evaluar en todo el intervalo [a, b].");
            }
            if (lowerViolated) {
                errores.add("Debe cumplirse f(x) >= g(x) en todo el intervalo [a, b].");
            }
            if (negViolated) {
                errores.add("Para discos y arandelas, debe cumplirse f(x) >= g(x) >= 0 en [a, b].");
            }
        }

        return new Result(errores);
    }
}

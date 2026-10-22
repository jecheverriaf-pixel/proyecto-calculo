package com.umg.calculo.engine;

import com.umg.calculo.model.Metodo;

import java.util.function.DoubleUnaryOperator;

/**
 * Numerical core: Simpson's 1/3 rule integration, and the volume integrand
 * for each of the three methods studied in the course. This is the piece the
 * assignment (Section 7) requires to match the manual derivation exactly.
 */
public final class VolumeCalculator {

    private VolumeCalculator() {}

    /** Composite Simpson's rule on [a, b] with n subintervals (bumped up to even if odd). */
    public static double simpsonIntegrate(DoubleUnaryOperator fn, double a, double b, int n) {
        if (a >= b) return 0.0;
        if (n % 2 != 0) n++;
        double h = (b - a) / n;
        double sum = fn.applyAsDouble(a) + fn.applyAsDouble(b);

        for (int i = 1; i < n; i++) {
            double x = a + i * h;
            double coef = (i % 2 == 0) ? 2.0 : 4.0;
            sum += coef * fn.applyAsDouble(x);
        }
        return (h / 3.0) * sum;
    }

    public static double simpsonIntegrate(DoubleUnaryOperator fn, double a, double b) {
        return simpsonIntegrate(fn, a, b, 800);
    }

    /**
     * Builds V(x)'s integrand for the given method:
     *  - Discos:    V = pi * int [f(x)]^2 dx
     *  - Arandelas: V = pi * int ([f(x)]^2 - [g(x)]^2) dx
     *  - Capas:     V = 2*pi * int x * (f(x) - g(x)) dx
     */
    public static DoubleUnaryOperator buildIntegrand(DoubleUnaryOperator f, DoubleUnaryOperator g, Metodo metodo) {
        return switch (metodo) {
            case DISCOS -> x -> Math.PI * Math.pow(f.applyAsDouble(x), 2);
            case ARANDELAS -> x -> Math.PI * (Math.pow(f.applyAsDouble(x), 2) - Math.pow(g.applyAsDouble(x), 2));
            case CAPAS -> x -> 2 * Math.PI * x * (f.applyAsDouble(x) - g.applyAsDouble(x));
        };
    }

    /** V_acum(x) = volumen generado desde a hasta x (Section 11). */
    public static double accumulatedVolume(DoubleUnaryOperator integrand, double a, double x) {
        return simpsonIntegrate(integrand, a, x, 200);
    }
}

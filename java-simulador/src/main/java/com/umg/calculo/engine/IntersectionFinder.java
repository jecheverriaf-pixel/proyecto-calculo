package com.umg.calculo.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Finds the intersection points of f(x) and g(x), i.e. the roots of
 * h(x) = f(x) - g(x) (part "a" of the main exercise). Scans [lo, hi] on a fine
 * grid looking for sign changes, then refines each one by bisection. Points
 * where f or g are undefined (NaN, e.g. sqrt of a negative) are skipped.
 */
public final class IntersectionFinder {

    public static final double DEFAULT_LO = -10;
    public static final double DEFAULT_HI = 10;
    private static final int SAMPLES = 4000;
    private static final double ZERO_TOL = 1e-12;

    private IntersectionFinder() {}

    /** Region between two consecutive intersections where f(x) >= g(x). */
    public record Interval(double a, double b) {}

    public static List<Double> findIntersections(DoubleUnaryOperator f, DoubleUnaryOperator g) {
        return findRoots(x -> f.applyAsDouble(x) - g.applyAsDouble(x), DEFAULT_LO, DEFAULT_HI, SAMPLES);
    }

    public static List<Double> findRoots(DoubleUnaryOperator h, double lo, double hi, int samples) {
        double step = (hi - lo) / samples;
        List<Double> roots = new ArrayList<>();
        double prevX = Double.NaN, prevH = Double.NaN;

        for (int i = 0; i <= samples; i++) {
            // lo + (hi - lo) * i / samples (not lo + i*step) so grid points like
            // x = 0 land exactly, which catches roots at a NaN boundary (sqrt(x) at 0).
            double x = lo + (hi - lo) * i / samples;
            double hx = h.applyAsDouble(x);
            if (Double.isNaN(hx)) { prevH = Double.NaN; continue; }

            if (Math.abs(hx) < ZERO_TOL) {
                addRoot(roots, x, step);
            } else if (!Double.isNaN(prevH) && Math.abs(prevH) >= ZERO_TOL && Math.signum(prevH) != Math.signum(hx)) {
                addRoot(roots, bisect(h, prevX, x), step);
            }
            prevX = x;
            prevH = hx;
        }
        return roots;
    }

    /**
     * First pair of consecutive intersections with f above g between them,
     * or null if there is none (fewer than two intersections, or g above f).
     */
    public static Interval regionBetween(List<Double> roots, DoubleUnaryOperator f, DoubleUnaryOperator g) {
        for (int i = 0; i + 1 < roots.size(); i++) {
            double r1 = roots.get(i), r2 = roots.get(i + 1);
            double mid = (r1 + r2) / 2;
            if (f.applyAsDouble(mid) > g.applyAsDouble(mid)) return new Interval(r1, r2);
        }
        return null;
    }

    private static double bisect(DoubleUnaryOperator h, double lo, double hi) {
        double hLo = h.applyAsDouble(lo);
        for (int i = 0; i < 100; i++) {
            double mid = (lo + hi) / 2;
            double hMid = h.applyAsDouble(mid);
            if (hMid == 0) return mid;
            if (Math.signum(hMid) == Math.signum(hLo)) { lo = mid; hLo = hMid; } else { hi = mid; }
        }
        return (lo + hi) / 2;
    }

    /** Skips near-duplicates (a flat h can be ~0 on several adjacent grid points). */
    private static void addRoot(List<Double> roots, double x, double step) {
        if (!roots.isEmpty() && x - roots.get(roots.size() - 1) < 1.5 * step) return;
        roots.add(x);
    }
}

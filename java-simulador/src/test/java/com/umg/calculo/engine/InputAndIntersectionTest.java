package com.umg.calculo.engine;

import com.umg.calculo.model.Eje;
import com.umg.calculo.model.Metodo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/** Constant parsing (a, b, V manual), intersection search and the Section 6 validation rules. */
class InputAndIntersectionTest {

    private static DoubleUnaryOperator fn(String expr) {
        return FunctionEvaluator.compile(expr);
    }

    // ------------------------------------------------------------ constants

    @Test
    void evaluateConstantAcceptsDecimalCommaPiAndFunctions() {
        assertEquals(73.5133, FunctionEvaluator.evaluateConstant("73,5133"), 1e-12);
        assertEquals(73.5133, FunctionEvaluator.evaluateConstant(" 73.5133 "), 1e-12);
        assertEquals(117 * Math.PI / 5, FunctionEvaluator.evaluateConstant("117*pi/5"), 1e-12);
        assertEquals(23.4 * Math.PI, FunctionEvaluator.evaluateConstant("23.4π"), 1e-12);
        assertEquals(Math.sqrt(2), FunctionEvaluator.evaluateConstant("sqrt(2)"), 1e-12);
        assertEquals(-1, FunctionEvaluator.evaluateConstant("-1"), 1e-12);
    }

    @Test
    void evaluateConstantRejectsInvalidInput() {
        assertTrue(Double.isNaN(FunctionEvaluator.evaluateConstant("")));
        assertTrue(Double.isNaN(FunctionEvaluator.evaluateConstant("abc")));
        assertTrue(Double.isNaN(FunctionEvaluator.evaluateConstant("x + 1")));
        assertTrue(Double.isNaN(FunctionEvaluator.evaluateConstant("1/0")));
    }

    // -------------------------------------------------------- intersections

    @Test
    void mainExerciseIntersectionsAreMinusOneAndTwo() {
        DoubleUnaryOperator f = fn("x + 3"), g = fn("x^2 + 1");
        List<Double> roots = IntersectionFinder.findIntersections(f, g);
        assertEquals(2, roots.size());
        assertEquals(-1, roots.get(0), 1e-9);
        assertEquals(2, roots.get(1), 1e-9);

        IntersectionFinder.Interval region = IntersectionFinder.regionBetween(roots, f, g);
        assertNotNull(region);
        assertEquals(-1, region.a(), 1e-9);
        assertEquals(2, region.b(), 1e-9);
    }

    @Test
    void findsRootOnTheEdgeOfTheDomain() {
        // sqrt(x) is undefined for x < 0, so the root at 0 has no sign change to bisect.
        DoubleUnaryOperator f = fn("sqrt(x)"), g = fn("x^2");
        List<Double> roots = IntersectionFinder.findIntersections(f, g);
        assertEquals(2, roots.size());
        assertEquals(0, roots.get(0), 1e-9);
        assertEquals(1, roots.get(1), 1e-9);
    }

    @Test
    void shellsConfigIntersectionsAreZeroAndTwo() {
        List<Double> roots = IntersectionFinder.findIntersections(fn("2*x"), fn("x^2"));
        assertEquals(List.of(0.0, 2.0), roots.stream().map(r -> (double) Math.round(r * 1e9) / 1e9).toList());
    }

    @Test
    void irrationalIntersectionsAreAccurate() {
        // x^2 = 2  ->  x = ±sqrt(2)
        List<Double> roots = IntersectionFinder.findIntersections(fn("2"), fn("x^2"));
        assertEquals(2, roots.size());
        assertEquals(-Math.sqrt(2), roots.get(0), 1e-9);
        assertEquals(Math.sqrt(2), roots.get(1), 1e-9);
    }

    @Test
    void regionIsNullWhenFunctionsAreSwapped() {
        DoubleUnaryOperator f = fn("x^2 + 1"), g = fn("x + 3");
        List<Double> roots = IntersectionFinder.findIntersections(f, g);
        assertNull(IntersectionFinder.regionBetween(roots, f, g));
    }

    @Test
    void noIntersectionsForParallelCurves() {
        assertTrue(IntersectionFinder.findIntersections(fn("x + 3"), fn("x")).isEmpty());
    }

    // ----------------------------------------------------------- validation

    @Test
    void validatorAcceptsTheMainExercise() {
        assertTrue(Validator.validar(-1, 2, Metodo.ARANDELAS, Eje.X, fn("x + 3"), fn("x^2 + 1")).esValido());
    }

    @Test
    void validatorRejectsReversedLimits() {
        var r = Validator.validar(2, -1, Metodo.ARANDELAS, Eje.X, fn("x + 3"), fn("x^2 + 1"));
        assertTrue(r.errores().contains("Debe cumplirse que a < b."));
    }

    @Test
    void validatorRejectsIncompatibleAxis() {
        assertFalse(Validator.validar(-1, 2, Metodo.ARANDELAS, Eje.Y, fn("x + 3"), fn("x^2 + 1")).esValido());
        assertFalse(Validator.validar(0, 2, Metodo.CAPAS, Eje.X, fn("2*x"), fn("x^2")).esValido());
    }

    @Test
    void validatorRejectsFBelowG() {
        assertFalse(Validator.validar(-1, 2, Metodo.ARANDELAS, Eje.X, fn("x^2 + 1"), fn("x + 3")).esValido());
    }

    @Test
    void validatorRejectsNegativeGForWashers() {
        assertFalse(Validator.validar(0, 1, Metodo.ARANDELAS, Eje.X, fn("1"), fn("x - 2")).esValido());
    }

    @Test
    void validatorRejectsNegativeAForShells() {
        assertFalse(Validator.validar(-1, 2, Metodo.CAPAS, Eje.Y, fn("x + 3"), fn("x^2 + 1")).esValido());
    }

    @Test
    void validatorRejectsFunctionsUndefinedOnTheInterval() {
        assertFalse(Validator.validar(-4, 4, Metodo.DISCOS, Eje.X, fn("sqrt(x)"), fn("0")).esValido());
    }
}

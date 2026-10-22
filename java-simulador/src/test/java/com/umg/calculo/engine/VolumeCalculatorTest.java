package com.umg.calculo.engine;

import com.umg.calculo.model.PresetConfig;
import com.umg.calculo.model.Presets;
import org.junit.jupiter.api.Test;

import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms the Java engine reproduces the same volumes already verified in
 * the web prototype (Section 13's required configurations + main exercise).
 */
class VolumeCalculatorTest {

    private double computeVolume(PresetConfig p) {
        DoubleUnaryOperator f = FunctionEvaluator.compile(p.f());
        DoubleUnaryOperator g = FunctionEvaluator.compile(p.g());
        DoubleUnaryOperator integrand = VolumeCalculator.buildIntegrand(f, g, p.metodo());
        return VolumeCalculator.simpsonIntegrate(integrand, p.a(), p.b(), 800);
    }

    @Test
    void mainProjectMatchesManualResult() {
        double v = computeVolume(Presets.MAIN_PROJECT);
        assertEquals(117 * Math.PI / 5, v, 1e-3);
        assertEquals(Presets.MAIN_PROJECT.volumenManual(), v, 0.001);
    }

    @Test
    void config1DiscosMatchesManualResult() {
        double v = computeVolume(Presets.CONFIG_1);
        assertEquals(8 * Math.PI, v, 1e-3);
    }

    @Test
    void config2ArandelasMatchesManualResult() {
        double v = computeVolume(Presets.CONFIG_2);
        assertEquals(0.3 * Math.PI, v, 1e-3);
    }

    @Test
    void config3CapasMatchesManualResult() {
        // 2*pi * int_0^2 x*(2x - x^2) dx = 2*pi * [2x^3/3 - x^4/4]_0^2 = 2*pi*(4/3) = 8*pi/3
        double v = computeVolume(Presets.CONFIG_3);
        assertEquals(8 * Math.PI / 3, v, 1e-3);
    }

    @Test
    void allPresetsAreWithinHalfPercentOfManualValue() {
        for (PresetConfig p : Presets.ALL) {
            double v = computeVolume(p);
            double errorPct = Math.abs(v - p.volumenManual()) / Math.abs(p.volumenManual()) * 100;
            assertTrue(errorPct < 0.5, p.id() + " error was " + errorPct + "%");
        }
    }

    @Test
    void functionEvaluatorReturnsNaNOutsideDomainInsteadOfThrowing() {
        FunctionEvaluator sqrtF = FunctionEvaluator.compile("sqrt(x)");
        assertTrue(Double.isNaN(sqrtF.applyAsDouble(-4)));
        assertEquals(2.0, sqrtF.applyAsDouble(4), 1e-9);
    }
}

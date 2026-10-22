package com.umg.calculo.engine;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

import java.util.function.DoubleUnaryOperator;

/**
 * Compiles a user-typed expression like "x^2 + 1" or "sqrt(x)" into something
 * that can be evaluated at any x. Evaluation failures (domain errors, e.g.
 * sqrt of a negative number) return NaN instead of throwing, so the caller's
 * validation logic can detect and report them uniformly.
 */
public final class FunctionEvaluator implements DoubleUnaryOperator {

    private final Expression expression;
    private final String rawExpression;

    private FunctionEvaluator(Expression expression, String rawExpression) {
        this.expression = expression;
        this.rawExpression = rawExpression;
    }

    /** @throws IllegalArgumentException if the expression has invalid syntax */
    public static FunctionEvaluator compile(String expr) {
        try {
            Expression e = new ExpressionBuilder(expr).variable("x").build();
            return new FunctionEvaluator(e, expr);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Expresión inválida: " + expr, ex);
        }
    }

    @Override
    public double applyAsDouble(double x) {
        try {
            expression.setVariable("x", x);
            double result = expression.evaluate();
            return Double.isFinite(result) ? result : Double.NaN;
        } catch (Exception ex) {
            return Double.NaN;
        }
    }

    public String rawExpression() {
        return rawExpression;
    }

    /**
     * Evaluates a constant typed by the user (limits a, b or the manual
     * volume). Besides plain numbers it accepts a decimal comma ("73,5133"),
     * pi ("117*pi/5", "23.4π") and functions ("sqrt(2)"), so a value copied
     * straight from the hand-written solution can be pasted as-is.
     * Returns NaN if the text is empty, invalid or depends on x.
     */
    public static double evaluateConstant(String text) {
        if (text == null || text.isBlank()) return Double.NaN;
        String s = text.trim().replace(',', '.').replace("π", "pi");
        try {
            double v = new ExpressionBuilder(s).build().evaluate();
            return Double.isFinite(v) ? v : Double.NaN;
        } catch (Exception ex) {
            return Double.NaN;
        }
    }
}

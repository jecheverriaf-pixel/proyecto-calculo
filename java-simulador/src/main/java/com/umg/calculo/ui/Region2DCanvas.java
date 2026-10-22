package com.umg.calculo.ui;

import com.umg.calculo.model.Eje;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.util.function.DoubleUnaryOperator;

/**
 * The 2D plane: f(x) (blue), g(x) (rose), the shaded region R being
 * revolved, the a/b integration bounds, the rotation axis, and — when active
 * — the cross-section slider marker. Same visual language as the 3D view.
 */
public class Region2DCanvas extends Canvas {

    // Data/curve colors — unchanged regardless of UI theme, these carry meaning.
    private static final Color COLOR_F = Color.web("#38bdf8");
    private static final Color COLOR_G = Color.web("#f43f5e");
    private static final Color COLOR_REGION = Color.web("#38bdf8", 0.18);
    private static final Color COLOR_REGION_EDGE = Color.web("#38bdf8", 0.5);
    private static final Color COLOR_AXIS = Color.web("#f59e0b");
    private static final Color COLOR_SLICE = Color.web("#fbbf24");
    // Chrome (grid/labels) — tuned for a light card background so they stay legible.
    private static final Color COLOR_GRID = Color.web("#e2e8f0");
    private static final Color COLOR_TEXT = Color.web("#475569");

    public Region2DCanvas(double width, double height) {
        super(width, height);
    }

    /** Full view: region shaded, a/b bounds, rotation axis all shown — the normal interactive mode. */
    public void update(DoubleUnaryOperator f, DoubleUnaryOperator g, double a, double b,
                        Eje eje, Double sliceX) {
        update(f, g, a, b, eje, sliceX, true, true, true, false);
    }

    /** Used by the guided step-by-step mode to reveal region/bounds/axis/intersections one at a time. */
    public void update(DoubleUnaryOperator f, DoubleUnaryOperator g, double a, double b,
                        Eje eje, Double sliceX,
                        boolean showRegion, boolean showBounds, boolean showAxis, boolean showIntersections) {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth(), h = getHeight();
        gc.clearRect(0, 0, w, h);

        double padding = Math.max((b - a) * 0.2, 0.5);
        double plotA = a - padding, plotB = b + padding;

        int samples = 220;
        double[] xs = new double[samples + 1];
        double[] fs = new double[samples + 1];
        double[] gs = new double[samples + 1];
        double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= samples; i++) {
            double x = plotA + (plotB - plotA) * i / samples;
            xs[i] = x;
            fs[i] = f.applyAsDouble(x);
            gs[i] = g.applyAsDouble(x);
            if (!Double.isNaN(fs[i])) { minY = Math.min(minY, fs[i]); maxY = Math.max(maxY, fs[i]); }
            if (!Double.isNaN(gs[i])) { minY = Math.min(minY, gs[i]); maxY = Math.max(maxY, gs[i]); }
        }
        minY = Math.min(minY, 0);
        maxY = Math.max(maxY, 0.5);
        double yPad = (maxY - minY) * 0.12 + 0.2;
        final double finalMinY = minY - yPad;
        final double finalMaxY = maxY + yPad;

        double left = 55, right = 20, top = 20, bottom = 36;
        double plotW = w - left - right, plotH = h - top - bottom;

        java.util.function.DoubleUnaryOperator toPx = x -> left + (x - plotA) / (plotB - plotA) * plotW;
        java.util.function.DoubleUnaryOperator toPy = y -> top + plotH - (y - finalMinY) / (finalMaxY - finalMinY) * plotH;

        drawGrid(gc, left, top, plotW, plotH, plotA, plotB, finalMinY, finalMaxY, toPx, toPy);

        // Shaded region R between f and g, over [a, b]
        if (showRegion) {
            gc.setFill(COLOR_REGION);
            gc.beginPath();
            int rs = 100;
            gc.moveTo(toPx.applyAsDouble(a), toPy.applyAsDouble(g.applyAsDouble(a)));
            for (int i = 0; i <= rs; i++) {
                double x = a + (b - a) * i / rs;
                gc.lineTo(toPx.applyAsDouble(x), toPy.applyAsDouble(f.applyAsDouble(x)));
            }
            for (int i = rs; i >= 0; i--) {
                double x = a + (b - a) * i / rs;
                gc.lineTo(toPx.applyAsDouble(x), toPy.applyAsDouble(g.applyAsDouble(x)));
            }
            gc.closePath();
            gc.fill();
            gc.setStroke(COLOR_REGION_EDGE);
            gc.setLineWidth(1);
            gc.stroke();
        }

        // f(x) and g(x) curves across the padded domain
        drawCurve(gc, xs, fs, toPx, toPy, COLOR_F);
        drawCurve(gc, xs, gs, toPx, toPy, COLOR_G);

        // a / b boundary lines
        if (showBounds) {
            gc.setStroke(Color.web("#94a3b8"));
            gc.setLineWidth(1.5);
            gc.setLineDashes(4.0, 4.0);
            gc.strokeLine(toPx.applyAsDouble(a), top, toPx.applyAsDouble(a), top + plotH);
            gc.strokeLine(toPx.applyAsDouble(b), top, toPx.applyAsDouble(b), top + plotH);
            gc.setLineDashes((double[]) null);
        }

        // Rotation axis
        if (showAxis) {
            gc.setStroke(COLOR_AXIS);
            gc.setLineWidth(2.5);
            gc.setLineDashes(6.0, 4.0);
            if (eje == Eje.X) {
                double y0 = toPy.applyAsDouble(0);
                gc.strokeLine(left, y0, left + plotW, y0);
            } else {
                double x0 = toPx.applyAsDouble(0);
                gc.strokeLine(x0, top, x0, top + plotH);
            }
            gc.setLineDashes((double[]) null);
        }

        // Intersection markers at x=a and x=b (guided "step 2" view)
        if (showIntersections) {
            double fa = f.applyAsDouble(a), ga = g.applyAsDouble(a);
            double fb = f.applyAsDouble(b), gb = g.applyAsDouble(b);
            double pxa = toPx.applyAsDouble(a), pya = toPy.applyAsDouble((fa + ga) / 2);
            double pxb = toPx.applyAsDouble(b), pyb = toPy.applyAsDouble((fb + gb) / 2);
            gc.setFill(Color.web("#0f172a"));
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.5);
            gc.fillOval(pxa - 6, pya - 6, 12, 12);
            gc.strokeOval(pxa - 6, pya - 6, 12, 12);
            gc.fillOval(pxb - 6, pyb - 6, 12, 12);
            gc.strokeOval(pxb - 6, pyb - 6, 12, 12);
        }

        // Cross-section slice marker
        if (sliceX != null && sliceX >= a && sliceX <= b) {
            double fx = f.applyAsDouble(sliceX), gx = g.applyAsDouble(sliceX);
            double px = toPx.applyAsDouble(sliceX);
            gc.setStroke(COLOR_SLICE);
            gc.setLineWidth(2.5);
            gc.strokeLine(px, toPy.applyAsDouble(Math.min(0, Math.min(fx, gx))), px, toPy.applyAsDouble(Math.max(fx, gx)));
            gc.setFill(COLOR_SLICE);
            gc.fillOval(px - 4.5, toPy.applyAsDouble(fx) - 4.5, 9, 9);
            gc.fillOval(px - 4.5, toPy.applyAsDouble(gx) - 4.5, 9, 9);
        }
    }

    private void drawCurve(GraphicsContext gc, double[] xs, double[] ys,
                            DoubleUnaryOperator toPx, DoubleUnaryOperator toPy, Color color) {
        gc.setStroke(color);
        gc.setLineWidth(2.5);
        gc.beginPath();
        boolean started = false;
        for (int i = 0; i < xs.length; i++) {
            if (Double.isNaN(ys[i])) { started = false; continue; }
            double px = toPx.applyAsDouble(xs[i]), py = toPy.applyAsDouble(ys[i]);
            if (!started) { gc.moveTo(px, py); started = true; } else gc.lineTo(px, py);
        }
        gc.stroke();
    }

    private void drawGrid(GraphicsContext gc, double left, double top, double plotW, double plotH,
                           double plotA, double plotB, double minY, double maxY,
                           DoubleUnaryOperator toPx, DoubleUnaryOperator toPy) {
        gc.setFont(Font.font(10));
        gc.setStroke(COLOR_GRID);
        gc.setLineWidth(0.75);

        double xStep = niceStep(plotB - plotA);
        for (double x = Math.ceil(plotA / xStep) * xStep; x <= plotB; x += xStep) {
            double px = toPx.applyAsDouble(x);
            gc.strokeLine(px, top, px, top + plotH);
            gc.setFill(COLOR_TEXT);
            gc.fillText(trimNumber(x), px - 8, top + plotH + 14);
        }
        double yStep = niceStep(maxY - minY);
        for (double y = Math.ceil(minY / yStep) * yStep; y <= maxY; y += yStep) {
            double py = toPy.applyAsDouble(y);
            gc.strokeLine(left, py, left + plotW, py);
            gc.setFill(COLOR_TEXT);
            gc.fillText(trimNumber(y), left - 32, py + 4);
        }

        // Zero lines, heavier
        gc.setStroke(Color.web("#64748b"));
        gc.setLineWidth(1.3);
        if (minY <= 0 && maxY >= 0) {
            double y0 = toPy.applyAsDouble(0);
            gc.strokeLine(left, y0, left + plotW, y0);
        }
        if (plotA <= 0 && plotB >= 0) {
            double x0 = toPx.applyAsDouble(0);
            gc.strokeLine(x0, top, x0, top + plotH);
        }
    }

    static String trimNumber(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-9) return String.valueOf(Math.round(v));
        return String.format("%.1f", v);
    }

    static double niceStep(double range) {
        if (range <= 0) return 1;
        double raw = range / 6.0;
        double mag = Math.pow(10, Math.floor(Math.log10(raw)));
        double norm = raw / mag;
        double step;
        if (norm < 1.5) step = 1;
        else if (norm < 3) step = 2;
        else if (norm < 7) step = 5;
        else step = 10;
        return step * mag;
    }
}

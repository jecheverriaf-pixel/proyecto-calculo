package com.umg.calculo.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

/** The V_acum(x) curve: how the volume grows as the sweep advances from a to x (Section 11). */
public class AccumCanvas extends Canvas {

    // Data color — unchanged regardless of UI theme.
    private static final Color COLOR_LINE = Color.web("#10b981");
    private static final Color COLOR_FILL = Color.web("#10b981", 0.15);
    private static final Color COLOR_SLICE = Color.web("#fbbf24");
    // Chrome (grid/labels) — tuned for a light card background.
    private static final Color COLOR_GRID = Color.web("#e2e8f0");
    private static final Color COLOR_TEXT = Color.web("#475569");

    public AccumCanvas(double width, double height) {
        super(width, height);
    }

    public record Point(double x, double v) {}

    public void update(java.util.List<Point> data, Double sliceX, Double sliceV) {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth(), h = getHeight();
        gc.clearRect(0, 0, w, h);
        if (data.isEmpty()) return;

        double minX = data.get(0).x(), maxX = data.get(data.size() - 1).x();
        double rawMaxV = 0;
        for (Point p : data) rawMaxV = Math.max(rawMaxV, p.v());
        final double maxV = Math.max(rawMaxV, 0.5) * 1.12;

        double left = 60, right = 20, top = 20, bottom = 36;
        double plotW = w - left - right, plotH = h - top - bottom;

        java.util.function.DoubleUnaryOperator toPx = x -> left + (x - minX) / (maxX - minX) * plotW;
        java.util.function.DoubleUnaryOperator toPy = v -> top + plotH - (v / maxV) * plotH;

        gc.setFont(Font.font(10));
        gc.setStroke(COLOR_GRID);
        gc.setLineWidth(0.75);
        for (int i = 0; i <= 5; i++) {
            double v = maxV * i / 5.0;
            double py = toPy.applyAsDouble(v);
            gc.strokeLine(left, py, left + plotW, py);
            gc.setFill(COLOR_TEXT);
            gc.fillText(String.format("%.2f", v), 6, py + 4);
        }
        for (int i = 0; i <= 5; i++) {
            double x = minX + (maxX - minX) * i / 5.0;
            double px = toPx.applyAsDouble(x);
            gc.strokeLine(px, top, px, top + plotH);
            gc.setFill(COLOR_TEXT);
            gc.fillText(String.format("%.2f", x), px - 12, top + plotH + 14);
        }

        gc.setFill(COLOR_FILL);
        gc.beginPath();
        gc.moveTo(toPx.applyAsDouble(data.get(0).x()), toPy.applyAsDouble(0));
        for (Point p : data) gc.lineTo(toPx.applyAsDouble(p.x()), toPy.applyAsDouble(p.v()));
        gc.lineTo(toPx.applyAsDouble(data.get(data.size() - 1).x()), toPy.applyAsDouble(0));
        gc.closePath();
        gc.fill();

        gc.setStroke(COLOR_LINE);
        gc.setLineWidth(2.8);
        gc.beginPath();
        gc.moveTo(toPx.applyAsDouble(data.get(0).x()), toPy.applyAsDouble(data.get(0).v()));
        for (Point p : data) gc.lineTo(toPx.applyAsDouble(p.x()), toPy.applyAsDouble(p.v()));
        gc.stroke();

        // Final-volume label
        Point last = data.get(data.size() - 1);
        gc.setFill(Color.web("#0f172a", 0.85));
        String label = String.format("V total ≈ %.4f u³", last.v());
        double lw = label.length() * 6.2 + 12;
        double lx = toPx.applyAsDouble(last.x()) - lw - 8, ly = toPy.applyAsDouble(last.v()) - 26;
        gc.fillRoundRect(lx, ly, lw, 20, 6, 6);
        gc.setStroke(COLOR_LINE);
        gc.setLineWidth(1);
        gc.strokeRoundRect(lx, ly, lw, 20, 6, 6);
        gc.setFill(Color.web("#d1fae5"));
        gc.fillText(label, lx + 6, ly + 14);

        if (sliceX != null && sliceV != null) {
            double px = toPx.applyAsDouble(sliceX), py = toPy.applyAsDouble(sliceV);
            gc.setStroke(COLOR_SLICE);
            gc.setLineWidth(2.2);
            gc.strokeLine(px, toPy.applyAsDouble(0), px, py);
            gc.setFill(COLOR_SLICE);
            gc.fillOval(px - 4.5, py - 4.5, 9, 9);
        }
    }
}

package com.umg.calculo.export;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Exports the x, f(x), g(x), V_acum(x) sample data required by Section 12. */
public final class DataExporter {

    private DataExporter() {}

    public record Row(double x, double fx, double gx, double vAcum) {}

    public static void exportCsv(Path file, List<Row> rows) throws IOException {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("x,f_x,g_x,V_acum\n");
            for (Row r : rows) {
                w.write(fmt(r.x()) + "," + fmt(r.fx()) + "," + fmt(r.gx()) + "," + fmt(r.vAcum()) + "\n");
            }
        }
    }

    public static void exportTxt(Path file, List<Row> rows) throws IOException {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write("x\tf(x)\tg(x)\tV_acum\n");
            for (Row r : rows) {
                w.write(fmt(r.x()) + "\t" + fmt(r.fx()) + "\t" + fmt(r.gx()) + "\t" + fmt(r.vAcum()) + "\n");
            }
        }
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.4f", v);
    }
}

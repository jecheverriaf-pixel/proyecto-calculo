package com.umg.calculo.engine;

import com.umg.calculo.model.Eje;
import com.umg.calculo.model.Metodo;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Builds the solid-of-revolution triangle mesh from a single CLOSED 2D
 * profile (radius, height) that is swept around the axis. Because the
 * profile itself traces outer curve -> end cap -> inner curve -> end cap
 * back to the start (a closed loop), sweeping it a full turn produces one
 * watertight solid — no separate cap/wall pieces needed.
 *
 * Convention (matches the coordinate mapping used by the calculus methods):
 *  - X-axis (discos/arandelas): radius = curve value, height = x.
 *  - Y-axis (capas): radius = x, height = curve value.
 */
public final class RevolutionMeshBuilder {

    private static final int PROFILE_SAMPLES = 60;

    private RevolutionMeshBuilder() {}

    /** One (radius, height) point of the profile, tagged with which curve (f or g) it belongs to. */
    public record ProfilePoint(double r, double h, boolean isCurveF) {}

    public static List<ProfilePoint> buildProfile(DoubleUnaryOperator f, DoubleUnaryOperator g,
                                                    double a, double b, Metodo metodo, Eje eje) {
        int n = PROFILE_SAMPLES;
        List<ProfilePoint> pts = new ArrayList<>(2 * n + 2);

        if (eje == Eje.X) {
            DoubleUnaryOperator rOuter = x -> Math.max(0, f.applyAsDouble(x));
            DoubleUnaryOperator rInner = x -> metodo == Metodo.ARANDELAS ? Math.max(0, g.applyAsDouble(x)) : 0.0;

            pts.add(new ProfilePoint(rInner.applyAsDouble(a), a, false));
            pts.add(new ProfilePoint(rOuter.applyAsDouble(a), a, true));
            for (int i = 1; i <= n; i++) {
                double x = a + (i / (double) n) * (b - a);
                pts.add(new ProfilePoint(rOuter.applyAsDouble(x), x, true));
            }
            pts.add(new ProfilePoint(rInner.applyAsDouble(b), b, false));
            for (int i = n - 1; i >= 1; i--) {
                double x = a + (i / (double) n) * (b - a);
                pts.add(new ProfilePoint(rInner.applyAsDouble(x), x, false));
            }
        } else {
            for (int i = 0; i <= n; i++) {
                double x = a + (i / (double) n) * (b - a);
                pts.add(new ProfilePoint(x, g.applyAsDouble(x), false));
            }
            pts.add(new ProfilePoint(b, f.applyAsDouble(b), true));
            for (int i = n - 1; i >= 1; i--) {
                double x = a + (i / (double) n) * (b - a);
                pts.add(new ProfilePoint(x, f.applyAsDouble(x), true));
            }
            pts.add(new ProfilePoint(a, f.applyAsDouble(a), true));
        }
        return pts;
    }

    /**
     * @param sweepFraction (0, 1]: 1 = full solid; less = partial reveal, used
     *                      by the sweep-generation animation.
     * @param radialSegments mesh resolution around the axis of revolution.
     */
    public static TriangleMesh buildMesh(List<ProfilePoint> profile, double sweepFraction, int radialSegments) {
        int nPts = profile.size();
        double phiLength = 2 * Math.PI * Math.max(0.001, Math.min(1, sweepFraction));

        float[] points = new float[nPts * (radialSegments + 1) * 3];
        float[] texCoords = new float[nPts * (radialSegments + 1) * 2];

        int pIdx = 0, tIdx = 0;
        for (int j = 0; j <= radialSegments; j++) {
            double theta = (j / (double) radialSegments) * phiLength;
            float cosT = (float) Math.cos(theta);
            float sinT = (float) Math.sin(theta);
            for (ProfilePoint pt : profile) {
                points[pIdx++] = (float) (pt.r() * cosT);
                points[pIdx++] = (float) pt.h();
                points[pIdx++] = (float) (pt.r() * sinT);

                // Samples a 2-color strip texture: near 0.25 = f(x) color,
                // near 0.75 = g(x) color (see Solid3DView's material texture).
                texCoords[tIdx++] = pt.isCurveF() ? 0.25f : 0.75f;
                texCoords[tIdx++] = 0.5f;
            }
        }

        int quadsPerRing = nPts - 1;
        int[] faces = new int[radialSegments * quadsPerRing * 12]; // 2 triangles * 3 verts * 2 (point+texcoord) idx
        int fIdx = 0;
        for (int j = 0; j < radialSegments; j++) {
            for (int i = 0; i < quadsPerRing; i++) {
                int a0 = j * nPts + i;
                int a1 = j * nPts + i + 1;
                int b0 = (j + 1) * nPts + i;
                int b1 = (j + 1) * nPts + i + 1;

                faces[fIdx++] = a0; faces[fIdx++] = a0;
                faces[fIdx++] = b0; faces[fIdx++] = b0;
                faces[fIdx++] = a1; faces[fIdx++] = a1;

                faces[fIdx++] = a1; faces[fIdx++] = a1;
                faces[fIdx++] = b0; faces[fIdx++] = b0;
                faces[fIdx++] = b1; faces[fIdx++] = b1;
            }
        }

        TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_TEXCOORD);
        mesh.getPoints().setAll(points);
        mesh.getTexCoords().setAll(texCoords);
        mesh.getFaces().setAll(faces);
        return mesh;
    }

    /** Bounding info used to center the mesh and frame the camera. */
    public record Bounds(double centerH, double maxExtent) {}

    public static Bounds computeBounds(List<ProfilePoint> profile) {
        double minH = Double.POSITIVE_INFINITY, maxH = Double.NEGATIVE_INFINITY, maxR = 0;
        for (ProfilePoint p : profile) {
            minH = Math.min(minH, p.h());
            maxH = Math.max(maxH, p.h());
            maxR = Math.max(maxR, Math.abs(p.r()));
        }
        double centerH = (minH + maxH) / 2.0;
        double halfHeight = (maxH - minH) / 2.0;
        double maxExtent = Math.max(maxR, halfHeight);
        return new Bounds(centerH, Math.max(maxExtent, 0.5));
    }
}

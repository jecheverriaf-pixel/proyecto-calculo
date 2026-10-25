package com.umg.calculo.ui;

import com.umg.calculo.engine.RevolutionMeshBuilder;
import com.umg.calculo.engine.RevolutionMeshBuilder.ProfilePoint;
import com.umg.calculo.model.Eje;
import com.umg.calculo.model.Metodo;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.control.Label;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Interactive 3D view of the solid of revolution: drag to rotate, scroll to
 * zoom, and a sweep-generation animation. Rebuilding the mesh on every
 * parameter change is cheap in pure Java (no GPU buffer round-trip like a
 * browser 3D library needs), so the animation just rebuilds the mesh each
 * frame at increasing sweep angle.
 *
 * The x/y axes are drawn in real math coordinates (through the origin, same
 * numbers as the 2D chart), with arrows, tick numbers and the a/b limits. Their
 * text lives in a 2D overlay on top of the 3D scene, re-projected whenever the
 * view moves, so it is always upright and readable however the solid is turned.
 */
public class Solid3DView {

    private static final int RADIAL_SEGMENTS = 64;
    // Package-visible: MainView's legend names the surface each color belongs to.
    static final Color COLOR_F = Color.web("#38bdf8");
    static final Color COLOR_G = Color.web("#f43f5e");
    private static final Color COLOR_HIGHLIGHT = Color.web("#fbbf24");
    // Package-visible (not private): MainView reuses these exact colors for the
    // legend under the view, so the legend can never drift from the 3D scene.
    static final Color COLOR_AXIS = Color.web("#f59e0b");      // the axis of rotation
    static final Color COLOR_AXIS_X = Color.web("#ef4444");
    static final Color COLOR_AXIS_Y = Color.web("#16a34a");
    static final Color COLOR_LIMIT = Color.web("#7c3aed");     // a and b
    private static final Color COLOR_TICK = Color.web("#475569");

    private final StackPane root;
    private final SubScene subScene;
    private final Pane overlay = new Pane();
    private final Group world = new Group();
    private final Group solidGroup = new Group();
    private final MeshView meshView = new MeshView();
    private final MeshView sliceMeshView = new MeshView();
    private final PerspectiveCamera camera = new PerspectiveCamera(true);

    // The mesh is built with its axis of revolution along JavaFX's Y, which points
    // DOWN on screen. This group turns it so it matches the math axes: lying along
    // +x for eje X, or standing up along +y for eje Y.
    private final Group orientGroup = new Group();
    private final Rotate orientation = new Rotate(-90, Rotate.Z_AXIS);

    // Axes, ticks and a/b markers. Its local coordinates ARE math coordinates
    // (y up, origin at (0,0)); its transforms line it up with the centered mesh.
    private final Group mathGroup = new Group();
    private final List<OverlayLabel> overlayLabels = new ArrayList<>();
    private String axesKey = "";

    // The plane region R (between g and f on [a, b]) drawn in the same math plane
    // as the 2D chart: it is exactly the profile the solid is swept from (θ = 0).
    // In half-cut mode it caps the open cut face, and its mirror caps the other side.
    static final Color COLOR_REGION = Color.web("#7dd3fc");
    private static final Color COLOR_REGION_MIRROR = Color.web("#bae6fd");
    private final MeshView regionCap = new MeshView();
    private final MeshView mirrorCap = new MeshView();
    private boolean halfCut = false;
    // Half-cut sweeps θ in [0, π]. For eje Y the orientation flips z, which would put
    // the remaining half in front of the camera and hide the cut; turning the mesh
    // 180° about its own axis first keeps the cut face toward the viewer.
    private final Rotate halfTurn = new Rotate(0, Rotate.Y_AXIS);

    // Wireframe ("malla") mode: instead of the opaque solid, the surfaces swept by
    // f and g are drawn as see-through grids of meridians and parallels, blue and
    // pink like their curves in the 2D chart. Built in math coordinates (mathGroup).
    private static final int WIRE_MERIDIANS = 24;      // lines along the axis, per full turn
    private static final int WIRE_PARALLELS = 20;      // circles across [a, b]
    private static final int WIRE_STEPS = 60;          // segments per meridian
    private static final int WIRE_CIRCLE_STEPS = 48;   // segments per full circle
    private final Group wireGroup = new Group();
    private final MeshView wireF = new MeshView();
    private final MeshView wireG = new MeshView();
    private boolean wireframe = false;

    private static final double DEFAULT_PITCH = -16;
    private static final double DEFAULT_YAW = 28;
    private final Rotate rotateX = new Rotate(DEFAULT_PITCH, Rotate.X_AXIS);
    private final Rotate rotateY = new Rotate(DEFAULT_YAW, Rotate.Y_AXIS);
    private double anchorX, anchorY, anchorAngleX, anchorAngleY;
    private double camDistance = -14;
    private boolean hasFramed = false;
    private double lastFramedExtent = -1;

    private List<ProfilePoint> currentProfile;
    private RevolutionMeshBuilder.Bounds currentBounds;
    private AnimationTimer sweepTimer;
    private Runnable onSweepFinished;

    /** A text label pinned to a 3D point (math coords), offset by (dx, dy) pixels on screen. */
    private record OverlayLabel(Point3D anchor, Label label, double dx, double dy) {}

    public Solid3DView(double width, double height) {
        meshView.setMaterial(twoToneMaterial());
        meshView.setCullFace(CullFace.NONE);

        sliceMeshView.setMaterial(new PhongMaterial(COLOR_HIGHLIGHT));
        sliceMeshView.setCullFace(CullFace.NONE);
        sliceMeshView.setVisible(false);

        solidGroup.getChildren().addAll(meshView, sliceMeshView);
        solidGroup.getTransforms().add(halfTurn);
        orientGroup.getChildren().add(solidGroup);

        regionCap.setMaterial(flatMaterial(COLOR_REGION));
        regionCap.setCullFace(CullFace.NONE);
        mirrorCap.setMaterial(flatMaterial(COLOR_REGION_MIRROR));
        mirrorCap.setCullFace(CullFace.NONE);

        wireF.setMaterial(flatMaterial(COLOR_F));
        wireF.setCullFace(CullFace.NONE);
        wireG.setMaterial(flatMaterial(COLOR_G));
        wireG.setCullFace(CullFace.NONE);
        wireGroup.getChildren().addAll(wireF, wireG);
        wireGroup.setVisible(false);
        orientGroup.getTransforms().add(orientation);
        world.getChildren().addAll(mathGroup, orientGroup);
        world.getTransforms().addAll(rotateY, rotateX);

        camera.setNearClip(0.05);
        camera.setFarClip(500);
        camera.setTranslateZ(camDistance);
        camera.setFieldOfView(35);

        Group cameraGroup = new Group(camera);
        Group sceneRoot = new Group(world, cameraGroup,
                new AmbientLight(Color.color(0.55, 0.55, 0.6)),
                keyLight());

        subScene = new SubScene(sceneRoot, width, height, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.TRANSPARENT);
        subScene.setCamera(camera);

        overlay.setMouseTransparent(true);
        overlay.setPrefSize(width, height);
        root = new StackPane(subScene, overlay);
        root.setPrefSize(width, height);
        root.setMaxSize(width, height);

        // Re-project the labels whenever the view moves.
        rotateX.angleProperty().addListener((o, ov, nv) -> refreshOverlay());
        rotateY.angleProperty().addListener((o, ov, nv) -> refreshOverlay());
        camera.translateZProperty().addListener((o, ov, nv) -> refreshOverlay());
        root.sceneProperty().addListener((o, ov, nv) -> Platform.runLater(this::refreshOverlay));
        overlay.widthProperty().addListener((o, ov, nv) -> refreshOverlay());

        attachMouseControls();
    }

    /** The 3D scene plus its label overlay (this is what gets added to the layout and exported as PNG). */
    public Node getNode() {
        return root;
    }

    /** Resizes the view (MainView fits it to the window width); the camera keeps its framing. */
    public void setSize(double width, double height) {
        subScene.setWidth(width);
        subScene.setHeight(height);
        overlay.setPrefSize(width, height);
        root.setPrefSize(width, height);
        root.setMaxSize(width, height);
        Platform.runLater(this::refreshOverlay);
    }

    private PointLight keyLight() {
        PointLight light = new PointLight(Color.color(1, 1, 1, 0.9));
        light.setTranslateX(8);
        light.setTranslateY(-10);
        light.setTranslateZ(-8);
        return light;
    }

    private PhongMaterial twoToneMaterial() {
        int w = 4, h = 2;
        WritableImage img = new WritableImage(w, h);
        PixelWriter pw = img.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pw.setColor(x, y, x < w / 2 ? COLOR_F : COLOR_G);
            }
        }
        PhongMaterial mat = new PhongMaterial();
        mat.setDiffuseMap(img);
        mat.setSpecularColor(Color.color(1, 1, 1, 0.25));
        mat.setSpecularPower(24);
        return mat;
    }

    /**
     * Unlit, flat color: the region must read as the same light blue as the 2D
     * chart from any angle. (Lit, the region and its mirror face opposite ways
     * and one of them came out dark.)
     */
    private static PhongMaterial flatMaterial(Color color) {
        WritableImage img = new WritableImage(1, 1);
        img.getPixelWriter().setColor(0, 0, color);
        PhongMaterial mat = new PhongMaterial(Color.BLACK);
        mat.setSelfIlluminationMap(img);
        mat.setSpecularColor(Color.TRANSPARENT);
        return mat;
    }

    private void attachMouseControls() {
        // Every handler consumes its event — otherwise drag/scroll gestures
        // bubble up to the outer ScrollPane (the simulator tab scrolls the
        // whole page at the same time as the camera rotates/zooms).
        subScene.setOnMousePressed(e -> {
            anchorX = e.getSceneX();
            anchorY = e.getSceneY();
            anchorAngleX = rotateX.getAngle();
            anchorAngleY = rotateY.getAngle();
            e.consume();
        });
        subScene.setOnMouseDragged(e -> {
            // Direct-manipulation feel: dragging right should turn the solid's
            // near face to the right (as if grabbing it), not the reverse.
            // Fully free orbit on both axes — no pitch clamp — so the user can
            // flip the solid to see it from directly above/below too.
            rotateX.setAngle(anchorAngleX - (e.getSceneY() - anchorY) * 0.25);
            rotateY.setAngle(anchorAngleY - (e.getSceneX() - anchorX) * 0.25);
            e.consume();
        });
        subScene.setOnScroll(e -> {
            double factor = 1 + e.getDeltaY() * 0.0015;
            // The closest-allowed distance scales with the solid's own size —
            // a fixed "-2" let the camera zoom past small solids entirely and
            // end up inside them.
            double minAbsDistance = currentBounds != null ? Math.max(1.2, currentBounds.maxExtent() * 0.8) : 2;
            camDistance = clamp(camDistance / factor, -80, -minAbsDistance);
            camera.setTranslateZ(camDistance);
            e.consume();
        });
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Rebuilds the solid for the current parameters. sweepFraction=1 for the normal, fully-formed view. */
    public void update(DoubleUnaryOperator f, DoubleUnaryOperator g, double a, double b,
                        Metodo metodo, Eje eje, Double sliceX, double sweepFraction) {
        currentProfile = RevolutionMeshBuilder.buildProfile(f, g, a, b, metodo, eje);
        currentBounds = RevolutionMeshBuilder.computeBounds(currentProfile);
        // Eje X: -90° about Z maps the mesh's +Y (x increasing) onto screen +x, so the
        // solid lies along the x axis. Eje Y: 180° about X flips it so y grows upward
        // and x stays to the right (a rotation, not a mirror, so lighting stays correct).
        orientation.setAxis(eje == Eje.X ? Rotate.Z_AXIS : Rotate.X_AXIS);
        orientation.setAngle(eje == Eje.X ? -90 : 180);
        halfTurn.setAngle(halfCut && eje == Eje.Y ? 180 : 0);

        double sweep = halfCut ? sweepFraction * 0.5 : sweepFraction;
        meshView.setMesh(RevolutionMeshBuilder.buildMesh(currentProfile, sweep, RADIAL_SEGMENTS));
        meshView.setTranslateY(-currentBounds.centerH());

        // Only auto-frame the camera the first time, or when the solid's size
        // changed a lot (new preset/function at a very different scale) — not
        // on every settled update, which looked like an unwanted zoom jump
        // right as the sweep animation finished.
        double extent = currentBounds.maxExtent();
        boolean sizeChangedALot = hasFramed && Math.abs(extent - lastFramedExtent) / Math.max(extent, 1e-6) > 0.4;
        if (sweepFraction >= 0.999 && (!hasFramed || sizeChangedALot)) {
            frameCamera(extent);
            lastFramedExtent = extent;
        }

        rebuildAxes(f, g, a, b, metodo, eje);
        // The mirror cap only makes sense once the half solid is complete; mid-sweep
        // it would float on its own.
        mirrorCap.setVisible(halfCut && sweepFraction > 0.999);

        meshView.setVisible(!wireframe);
        wireGroup.setVisible(wireframe);
        if (wireframe) buildWire(f, g, a, b, metodo, eje, sweepFraction);

        if (sliceX != null && sweepFraction > 0.999) {
            showSlice(f, g, a, b, metodo, eje, sliceX, currentBounds.centerH());
        } else {
            sliceMeshView.setVisible(false);
        }
    }

    private void frameCamera(double extent) {
        // Leaves margin around the solid and the axis arrows sticking out of it (35° FOV).
        camDistance = -Math.max(4, extent * 5.2);
        camera.setTranslateZ(camDistance);
        hasFramed = true;
    }

    // ------------------------------------------------------------------ axes

    /**
     * Draws the x and y axes through the real origin, in the same units as the
     * 2D chart: arrow + name at the positive end, tick numbers, and violet marks
     * at a and b. The axis of rotation is drawn orange (and thicker) instead of
     * in its own color, so it is obvious which axis the region turns around.
     */
    private void rebuildAxes(DoubleUnaryOperator f, DoubleUnaryOperator g, double a, double b,
                             Metodo metodo, Eje eje) {
        double minH = Double.POSITIVE_INFINITY, maxH = Double.NEGATIVE_INFINITY, maxR = 0;
        for (ProfilePoint p : currentProfile) {
            minH = Math.min(minH, p.h());
            maxH = Math.max(maxH, p.h());
            maxR = Math.max(maxR, Math.abs(p.r()));
        }
        // Sweep frames call update() dozens of times with the same solid: skip the rebuild.
        // (f and g are recompiled on every edit, so their identity marks a new function.)
        String key = a + "|" + b + "|" + metodo + "|" + eje + "|" + minH + "|" + maxH + "|" + maxR +
                "|" + System.identityHashCode(f) + "|" + System.identityHashCode(g);
        if (key.equals(axesKey)) return;
        axesKey = key;

        // Math-space bounding box of the full solid (it spans both sides of its axis).
        double xMin, xMax, yMin, yMax, cx, cy;
        if (eje == Eje.X) {
            xMin = minH; xMax = maxH; yMin = -maxR; yMax = maxR;
            cx = currentBounds.centerH(); cy = 0;
        } else {
            xMin = -maxR; xMax = maxR; yMin = minH; yMax = maxH;
            cx = 0; cy = currentBounds.centerH();
        }
        // Math point (x, y) -> JavaFX (x - cx, cy - y): same placement as the centered mesh.
        mathGroup.getTransforms().setAll(new Translate(-cx, cy, 0), new Rotate(180, Rotate.X_AXIS));
        mathGroup.getChildren().clear();
        overlayLabels.clear();
        overlay.getChildren().clear();

        double extent = currentBounds.maxExtent();
        double pad = extent * 0.4;
        double xLo = Math.min(xMin, 0) - pad, xHi = Math.max(xMax, 0) + pad;
        double yLo = Math.min(yMin, 0) - pad, yHi = Math.max(yMax, 0) + pad;
        double r = Math.max(0.01, extent * 0.008);

        boolean xIsRotation = eje == Eje.X;
        addAxis(true, xLo, xHi, r, xIsRotation ? COLOR_AXIS : COLOR_AXIS_X,
                xIsRotation ? "x · eje de rotación" : "x");
        addAxis(false, yLo, yHi, r, xIsRotation ? COLOR_AXIS_Y : COLOR_AXIS,
                xIsRotation ? "y" : "y · eje de rotación");

        // Tick numbers (same "nice" steps as the 2D chart). 0 is labeled once, at the origin.
        double xStep = Region2DCanvas.niceStep(xHi - xLo);
        for (double t = Math.ceil(xLo / xStep) * xStep; t < xHi - 0.4 * xStep; t += xStep) {
            double v = Math.abs(t) < 1e-9 ? 0 : t;
            addTick(new Point3D(v, 0, 0), r, Region2DCanvas.trimNumber(v), 0, 15);
        }
        double yStep = Region2DCanvas.niceStep(yHi - yLo);
        for (double t = Math.ceil(yLo / yStep) * yStep; t < yHi - 0.4 * yStep; t += yStep) {
            if (Math.abs(t) < 1e-9) continue;
            addTick(new Point3D(0, t, 0), r, Region2DCanvas.trimNumber(t), -18, 0);
        }

        // a and b are always x values: the ends of [a, b] (disks/washers) or the
        // inner/outer radius (shells). Labeled above the axis, numbers go below.
        addLimit(a, "a = " + Region2DCanvas.trimNumber(a), extent, r);
        addLimit(b, "b = " + Region2DCanvas.trimNumber(b), extent, r);

        // Region R in the math plane (z = 0), same shape as the 2D chart. Disks have
        // no inner curve (radius 0); disks/washers clamp radii at 0 like the mesh does.
        DoubleUnaryOperator upper = eje == Eje.X ? x -> Math.max(0, f.applyAsDouble(x)) : f;
        DoubleUnaryOperator lower = metodo == Metodo.DISCOS ? x -> 0.0
                : eje == Eje.X ? x -> Math.max(0, g.applyAsDouble(x)) : g;
        regionCap.setMesh(regionMesh(upper, lower, a, b, 1, 1));
        // The other cut face of the half solid: the region reflected across the axis of rotation.
        mirrorCap.setMesh(eje == Eje.X ? regionMesh(upper, lower, a, b, 1, -1)
                                        : regionMesh(upper, lower, a, b, -1, 1));
        mathGroup.getChildren().addAll(regionCap, mirrorCap, wireGroup);

        refreshOverlay();
    }

    /** Flat strip between lower(x) and upper(x) on [a, b], with each point scaled by (sx, sy) to mirror it. */
    private static TriangleMesh regionMesh(DoubleUnaryOperator upper, DoubleUnaryOperator lower,
                                           double a, double b, double sx, double sy) {
        int n = 120;
        TriangleMesh mesh = new TriangleMesh();
        mesh.getTexCoords().addAll(0, 0);
        for (int i = 0; i <= n; i++) {
            double x = a + (b - a) * i / n;
            mesh.getPoints().addAll((float) (sx * x), (float) (sy * lower.applyAsDouble(x)), 0);
            mesh.getPoints().addAll((float) (sx * x), (float) (sy * upper.applyAsDouble(x)), 0);
        }
        for (int i = 0; i < n; i++) {
            int lo0 = 2 * i, hi0 = 2 * i + 1, lo1 = 2 * i + 2, hi1 = 2 * i + 3;
            mesh.getFaces().addAll(lo0, 0, hi0, 0, lo1, 0);
            mesh.getFaces().addAll(hi0, 0, hi1, 0, lo1, 0);
        }
        return mesh;
    }

    /** Half-cut mode: shows only half a turn (θ from 0 to π) so the profile f/g is visible from inside. */
    public void setHalfCut(boolean on) {
        halfCut = on;
    }

    /** Switches between the opaque solid and the see-through wireframe ("malla") of its surfaces. */
    public void setWireframe(boolean on) {
        wireframe = on;
    }

    // ------------------------------------------------------------- wireframe

    private void buildWire(DoubleUnaryOperator f, DoubleUnaryOperator g, double a, double b,
                           Metodo metodo, Eje eje, double sweepFraction) {
        // Same angular extent as the solid mesh (half-cut and sweep animation included).
        double span = (halfCut ? Math.PI : 2 * Math.PI) * Math.max(0.01, Math.min(1, sweepFraction));
        DoubleUnaryOperator upper = eje == Eje.X ? x -> Math.max(0, f.applyAsDouble(x)) : f;
        DoubleUnaryOperator lower = eje == Eje.X ? x -> Math.max(0, g.applyAsDouble(x)) : g;
        double tube = Math.max(0.004, currentBounds.maxExtent() * 0.004);

        wireF.setMesh(surfaceWire(upper, a, b, eje, span, tube));
        // Disks have no inner surface (radius 0), so there is no g grid to draw.
        boolean hasInner = metodo != Metodo.DISCOS;
        wireG.setVisible(hasInner);
        if (hasInner) wireG.setMesh(surfaceWire(lower, a, b, eje, span, tube));
    }

    /**
     * Point at parameter t ∈ [a, b] and angle θ on the surface swept by {@code curve}, in math
     * coordinates. The θ direction matches the solid mesh after its orientation (and the
     * half-cut turn for eje Y), so the wireframe and the solid cover the same half.
     */
    private double[] wirePoint(DoubleUnaryOperator curve, double t, double theta, Eje eje) {
        double c = curve.applyAsDouble(t);
        if (eje == Eje.X) return new double[]{t, c * Math.cos(theta), -c * Math.sin(theta)};
        double s = halfCut ? -1 : 1;
        return new double[]{s * t * Math.cos(theta), c, s * t * Math.sin(theta)};
    }

    /** Meridians (curve at fixed θ) + parallels (circle at fixed t) of one surface, as thin tubes in one mesh. */
    private TriangleMesh surfaceWire(DoubleUnaryOperator curve, double a, double b, Eje eje, double span, double tube) {
        TubeMesh mesh = new TubeMesh(tube);
        boolean fullTurn = span > 2 * Math.PI - 1e-6;
        int meridians = Math.max(1, (int) Math.round(WIRE_MERIDIANS * span / (2 * Math.PI)));
        int lastMeridian = fullTurn ? meridians - 1 : meridians; // a full turn: θ = 2π repeats θ = 0
        for (int k = 0; k <= lastMeridian; k++) {
            double theta = span * k / meridians;
            double[] prev = null;
            for (int i = 0; i <= WIRE_STEPS; i++) {
                double[] p = wirePoint(curve, a + (b - a) * i / WIRE_STEPS, theta, eje);
                prev = mesh.segmentTo(prev, p);
            }
        }
        int circleSteps = Math.max(4, (int) Math.round(WIRE_CIRCLE_STEPS * span / (2 * Math.PI)));
        for (int j = 0; j <= WIRE_PARALLELS; j++) {
            double t = a + (b - a) * j / WIRE_PARALLELS;
            double[] prev = null;
            for (int i = 0; i <= circleSteps; i++) {
                prev = mesh.segmentTo(prev, wirePoint(curve, t, span * i / circleSteps, eje));
            }
        }
        return mesh.build();
    }

    /**
     * Accumulates line segments as thin triangular prisms in one TriangleMesh. JavaFX 3D
     * has no line primitive, and one mesh per surface stays fast even with thousands of
     * segments (a Cylinder node per segment would not).
     */
    private static final class TubeMesh {
        private final double radius;
        private float[] points = new float[4096];
        private int[] faces = new int[4096];
        private int nPoints = 0, nFaces = 0;

        TubeMesh(double radius) { this.radius = radius; }

        /** Adds the segment prev → p (skipped when either end is undefined) and returns p as the next start. */
        double[] segmentTo(double[] prev, double[] p) {
            boolean pOk = Double.isFinite(p[0]) && Double.isFinite(p[1]) && Double.isFinite(p[2]);
            if (!pOk) return null;
            if (prev != null) add(prev, p);
            return p;
        }

        private void add(double[] p, double[] q) {
            double dx = q[0] - p[0], dy = q[1] - p[1], dz = q[2] - p[2];
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1e-12) return;
            dx /= len; dy /= len; dz /= len;
            // u = d × axis, with axis = y (or x when d is nearly vertical); then v = d × u.
            double ax = Math.abs(dy) < 0.9 ? 0 : 1, ay = 1 - ax;
            double ux = -dz * ay, uy = dz * ax, uz = dx * ay - dy * ax;
            double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
            ux /= ul; uy /= ul; uz /= ul;
            double vx = dy * uz - dz * uy, vy = dz * ux - dx * uz, vz = dx * uy - dy * ux;

            int base = nPoints / 3;
            for (int end = 0; end < 2; end++) {
                double[] c = end == 0 ? p : q;
                for (int i = 0; i < 3; i++) {
                    double ang = 2 * Math.PI * i / 3;
                    double ox = radius * (Math.cos(ang) * ux + Math.sin(ang) * vx);
                    double oy = radius * (Math.cos(ang) * uy + Math.sin(ang) * vy);
                    double oz = radius * (Math.cos(ang) * uz + Math.sin(ang) * vz);
                    point(c[0] + ox, c[1] + oy, c[2] + oz);
                }
            }
            for (int i = 0; i < 3; i++) {
                int p0 = base + i, p1 = base + (i + 1) % 3, q0 = base + 3 + i, q1 = base + 3 + (i + 1) % 3;
                face(p0, q0, p1);
                face(p1, q0, q1);
            }
        }

        private void point(double x, double y, double z) {
            if (nPoints + 3 > points.length) points = java.util.Arrays.copyOf(points, points.length * 2);
            points[nPoints++] = (float) x; points[nPoints++] = (float) y; points[nPoints++] = (float) z;
        }

        private void face(int i0, int i1, int i2) {
            if (nFaces + 6 > faces.length) faces = java.util.Arrays.copyOf(faces, faces.length * 2);
            faces[nFaces++] = i0; faces[nFaces++] = 0;
            faces[nFaces++] = i1; faces[nFaces++] = 0;
            faces[nFaces++] = i2; faces[nFaces++] = 0;
        }

        TriangleMesh build() {
            TriangleMesh mesh = new TriangleMesh();
            mesh.getTexCoords().addAll(0, 0);
            mesh.getPoints().setAll(points, 0, nPoints);
            mesh.getFaces().setAll(faces, 0, nFaces);
            return mesh;
        }
    }

    /** One axis: a cylinder from lo to hi, an arrow cone at hi, and its name next to the tip. */
    private void addAxis(boolean horizontal, double lo, double hi, double r, Color color, String name) {
        boolean isRotation = color.equals(COLOR_AXIS);
        PhongMaterial mat = new PhongMaterial(color);

        Cylinder line = new Cylinder(isRotation ? r * 1.6 : r, hi - lo);
        line.setMaterial(mat);
        MeshView arrow = new MeshView(coneMesh(r * 4.5, r * 14, 24));
        arrow.setMaterial(mat);
        arrow.setCullFace(CullFace.NONE);

        if (horizontal) {
            line.getTransforms().add(new Rotate(90, Rotate.Z_AXIS));
            line.setTranslateX((lo + hi) / 2);
            arrow.getTransforms().add(new Rotate(-90, Rotate.Z_AXIS)); // cone's +y -> +x
            arrow.setTranslateX(hi);
        } else {
            line.setTranslateY((lo + hi) / 2);
            arrow.setTranslateY(hi);
        }
        mathGroup.getChildren().addAll(line, arrow);

        Label label = new Label(name);
        label.setStyle(labelStyle(color, 13, true));
        Point3D tip = horizontal ? new Point3D(hi + r * 14, 0, 0) : new Point3D(0, hi + r * 14, 0);
        // x: to the right of the arrow tip; y: centered just above it.
        pin(tip, label, 0, horizontal ? 0 : -14, horizontal);
    }

    private void addTick(Point3D at, double r, String text, double dx, double dy) {
        Sphere dot = new Sphere(r * 2);
        dot.setMaterial(new PhongMaterial(COLOR_TICK));
        dot.setTranslateX(at.getX());
        dot.setTranslateY(at.getY());
        mathGroup.getChildren().add(dot);

        Label label = new Label(text);
        label.setStyle(labelStyle(COLOR_TICK, 11, false));
        pin(at, label, dx, dy, false);
    }

    private void addLimit(double x, String text, double extent, double r) {
        Cylinder mark = new Cylinder(r * 1.3, extent * 0.14); // short vertical bar crossing the x axis
        mark.setMaterial(new PhongMaterial(COLOR_LIMIT));
        mark.setTranslateX(x);
        mathGroup.getChildren().add(mark);

        Label label = new Label(text);
        label.setStyle(labelStyle(COLOR_LIMIT, 12, true));
        pin(new Point3D(x, 0, 0), label, 0, -17, false);
    }

    private static String labelStyle(Color color, int size, boolean bold) {
        return "-fx-font-size: " + size + "px;" + (bold ? "-fx-font-weight: bold;" : "") +
                "-fx-text-fill: " + toWeb(color) + ";" +
                "-fx-background-color: rgba(255,255,255,0.8); -fx-background-radius: 4; -fx-padding: 0 3 0 3;";
    }

    private static String toWeb(Color c) {
        return String.format("#%02x%02x%02x",
                Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
    }

    /** @param leftAligned the label starts at the anchor (axis names) instead of centering on it */
    private void pin(Point3D anchor, Label label, double dx, double dy, boolean leftAligned) {
        overlay.getChildren().add(label);
        overlayLabels.add(new OverlayLabel(anchor, label, leftAligned ? Double.NaN : dx, dy));
    }

    /** Projects every label's 3D anchor to the screen and moves the label there. */
    private void refreshOverlay() {
        if (root.getScene() == null || overlayLabels.isEmpty()) return;
        for (OverlayLabel ol : overlayLabels) {
            Point3D p = ol.anchor();
            Point3D inScene = mathGroup.localToScene(p.getX(), p.getY(), p.getZ(), true);
            Point2D local = overlay.sceneToLocal(inScene.getX(), inScene.getY());
            Label l = ol.label();
            if (local == null) { l.setVisible(false); continue; }
            l.applyCss();
            double w = l.prefWidth(-1), h = l.prefHeight(-1);
            l.resize(w, h);
            double x = Double.isNaN(ol.dx()) ? local.getX() + 4 : local.getX() + ol.dx() - w / 2;
            l.relocate(x, local.getY() + ol.dy() - h / 2);
            l.setVisible(true);
        }
    }

    /** A cone along +y: base circle at y=0, apex at y=height (used for the axis arrows). */
    private static TriangleMesh coneMesh(double radius, double height, int segments) {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getTexCoords().addAll(0, 0);
        mesh.getPoints().addAll(0, (float) height, 0);   // 0: apex
        mesh.getPoints().addAll(0, 0, 0);                // 1: base center
        for (int i = 0; i < segments; i++) {
            double t = 2 * Math.PI * i / segments;
            mesh.getPoints().addAll((float) (radius * Math.cos(t)), 0, (float) (radius * Math.sin(t)));
        }
        for (int i = 0; i < segments; i++) {
            int p = 2 + i, q = 2 + (i + 1) % segments;
            mesh.getFaces().addAll(0, 0, q, 0, p, 0);   // side
            mesh.getFaces().addAll(1, 0, p, 0, q, 0);   // base
        }
        return mesh;
    }

    // ----------------------------------------------------------------- slice

    private void showSlice(DoubleUnaryOperator f, DoubleUnaryOperator g, double a, double b,
                            Metodo metodo, Eje eje, double sliceX, double centerH) {
        if (sliceX < a || sliceX > b) {
            sliceMeshView.setVisible(false);
            return;
        }
        // A thin highlighted ring/band at sliceX reuses the same lathe builder,
        // clipped to a narrow window around the slice position.
        List<ProfilePoint> highlightProfile = buildHighlightProfile(f, g, a, b, metodo, eje, sliceX);
        if (highlightProfile == null) {
            sliceMeshView.setVisible(false);
            return;
        }
        sliceMeshView.setMesh(RevolutionMeshBuilder.buildMesh(highlightProfile, halfCut ? 0.5 : 1.0, RADIAL_SEGMENTS));
        sliceMeshView.setTranslateY(-centerH);
        sliceMeshView.setVisible(true);
    }

    private List<ProfilePoint> buildHighlightProfile(DoubleUnaryOperator f, DoubleUnaryOperator g,
                                                       double a, double b, Metodo metodo, Eje eje, double sliceX) {
        double half = Math.max((b - a) * 0.01, 1e-3);
        double x0 = Math.max(a, sliceX - half);
        double x1 = Math.min(b, sliceX + half);
        if (x1 <= x0) return null;
        // A thin disc/washer (X-axis) or thin shell band (Y-axis) at the slider position.
        return RevolutionMeshBuilder.buildProfile(f, g, x0, x1, metodo, eje);
    }

    // ------------------------------------------------------------- animation

    /** Plays the 0deg -> 360deg sweep-generation animation. */
    public void playSweepAnimation(DoubleUnaryOperator f, DoubleUnaryOperator g, double a, double b,
                                    Metodo metodo, Eje eje, Double sliceX, long durationMs, Runnable onFinished) {
        playSweepAnimation(f, g, a, b, metodo, eje, sliceX, durationMs, null, onFinished);
    }

    /**
     * Same sweep animation, plus an onProgress callback fired every frame with the
     * eased 0..1 fraction — used by the guided step-by-step mode to reveal the
     * V_acum(x) chart in lockstep with the solid forming.
     */
    public void playSweepAnimation(DoubleUnaryOperator f, DoubleUnaryOperator g, double a, double b,
                                    Metodo metodo, Eje eje, Double sliceX, long durationMs,
                                    java.util.function.DoubleConsumer onProgress, Runnable onFinished) {
        if (sweepTimer != null) sweepTimer.stop();
        this.onSweepFinished = onFinished;
        final long start = System.nanoTime();
        final double durationNanos = durationMs * 1_000_000.0;

        sweepTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double t = Math.min(1.0, (now - start) / durationNanos);
                double eased = 1 - Math.pow(1 - t, 3);
                update(f, g, a, b, metodo, eje, sliceX, Math.max(0.01, eased));
                if (onProgress != null) onProgress.accept(eased);
                if (t >= 1.0) {
                    stop();
                    update(f, g, a, b, metodo, eje, sliceX, 1.0);
                    if (onSweepFinished != null) onSweepFinished.run();
                }
            }
        };
        sweepTimer.start();
    }

    public void stopSweepAnimation() {
        if (sweepTimer != null) sweepTimer.stop();
    }

    /** Restores the default orientation and re-frames the camera on the current solid. */
    public void resetView() {
        rotateX.setAngle(DEFAULT_PITCH);
        rotateY.setAngle(DEFAULT_YAW);
        if (currentBounds != null) {
            frameCamera(currentBounds.maxExtent());
            lastFramedExtent = currentBounds.maxExtent();
        }
    }
}

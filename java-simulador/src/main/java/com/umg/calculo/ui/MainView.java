package com.umg.calculo.ui;

import com.umg.calculo.engine.FunctionEvaluator;
import com.umg.calculo.engine.IntersectionFinder;
import com.umg.calculo.engine.Validator;
import com.umg.calculo.engine.VolumeCalculator;
import com.umg.calculo.export.DataExporter;
import com.umg.calculo.export.ImageExporter;
import com.umg.calculo.model.Eje;
import com.umg.calculo.model.Metodo;
import com.umg.calculo.model.PresetConfig;
import com.umg.calculo.model.Presets;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.transform.Scale;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;

public class MainView {

    private final BorderPane root = new BorderPane();
    private Stage stage;

    // Inputs
    private final TextField fField = new TextField();
    private final TextField gField = new TextField();
    private final TextField aField = new TextField();
    private final TextField bField = new TextField();
    private final ComboBox<Metodo> metodoCombo = new ComboBox<>();
    private final ToggleButton ejeXBtn = new ToggleButton("Eje X (Horizontal)");
    private final ToggleButton ejeYBtn = new ToggleButton("Eje Y (Vertical)");
    private final TextField manualVolField = new TextField();
    private final Label presetBadge = new Label();
    private final Button intersectBtn = new Button("Calcular intersecciones f(x) = g(x)");
    private final Label intersectionInfo = new Label();

    // Output widgets
    private final VBox errorBanner = new VBox(6);
    private final Label volumeValueLabel = new Label("---");
    private final Label volumeFormulaLabel = new Label();
    private final Label diffLabel = new Label("---");
    private final Label concordanceBanner = new Label();
    // 2D charts sit side-by-side (see buildRightPanel), so each gets roughly
    // half the width of the 3D view above them instead of the full width.
    private final Region2DCanvas region2D = new Region2DCanvas(430, 340);
    private final AccumCanvas accumCanvas = new AccumCanvas(430, 340);
    private final Solid3DView solid3D = new Solid3DView(900, 480);
    private final CheckBox showSliceCheck = new CheckBox("Corte transversal");
    private final CheckBox halfCutCheck = new CheckBox("Corte por la mitad");
    private final Slider sliceSlider = new Slider();
    private final Label sliceValueLabel = new Label("x = --");
    private final Label crossSectionFormulaLabel = new Label();
    // FlowPane, not HBox: with the surface colors added the legend can be wider than the card, so it wraps.
    private final FlowPane axisLegend = new FlowPane(18, 6);
    // Responsive layout of the simulator tab (applyResponsiveLayout).
    private VBox leftPanel, rightPanel;
    private final HBox wideLayout = new HBox(COLUMN_GAP);
    private final VBox narrowLayout = new VBox(16);
    private final VBox simContent = new VBox();
    private final FlowPane chartsRow = new FlowPane(16, 16);
    private final javafx.animation.PauseTransition resizeDebounce =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(60));
    private double lastViewportWidth = -1;
    private boolean panelsStacked = false;
    private final ToggleButton viewSolidBtn = new ToggleButton("Sólido");
    private final ToggleButton viewWireBtn = new ToggleButton("Malla");
    private final Button animateBtn = new Button("▶  Animar generación del sólido");
    private final Button resetViewBtn = new Button("⟲  Enderezar vista");
    private final TableView<ValidationRow> validationTable = new TableView<>();
    private final Label validationBadge = new Label();

    // Guided step-by-step mode (Sección pedagógica: graficar -> intersecciones -> diferencial -> integral)
    private final Label guidedStepLabel = new Label();
    private final Label guidedCaption = new Label();
    private final Button guidedStartBtn = new Button("▶  Iniciar Modo Guiado");
    private final Button guidedPrevBtn = new Button("‹  Anterior");
    private final Button guidedNextBtn = new Button("Siguiente  ›");
    private final Button guidedExitBtn = new Button("Salir del modo guiado");
    private int guidedStep = 0; // 0 = inactive, 1..4 = active step

    // State
    private Metodo metodo = Metodo.ARANDELAS;
    private Eje eje = Eje.X;
    private String selectedPresetId = "main_project";
    private Double sliceX = null;
    private List<DataExporter.Row> sampleData = new ArrayList<>();
    private Double calculatedVol = null;

    private DoubleUnaryOperator cachedF, cachedG;
    private double cachedA, cachedB;
    private boolean cachedValid = false;

    public MainView() {
        buildLayout();
        applyPreset(Presets.MAIN_PROJECT);
        wireEvents();
        recompute();
    }

    public Scene createScene() {
        // 1440 × 900 on a large monitor, but never bigger than the screen's usable
        // area (a laptop is often 1280 × 720 once Windows scaling is applied).
        javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(root, Math.min(1440, screen.getWidth()), Math.min(900, screen.getHeight()));
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        return scene;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    // ---------------------------------------------------------------- layout

    private void buildLayout() {
        root.getStyleClass().add("app-root");
        root.setTop(buildHeader());

        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("main-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab simTab = new Tab("Simulador 2D/3D", buildSimulatorTab());
        Tab valTab = new Tab("Tabla de Validación", buildValidationTab());
        Tab theoryTab = new Tab("Guía para Defensa", buildTheoryTab());
        tabs.getTabs().addAll(simTab, valTab, theoryTab);

        root.setCenter(tabs);
    }

    private Node buildHeader() {
        Label title = new Label("Emulador de Sólidos de Revolución");
        title.getStyleClass().add("app-title");
        Label subtitle = new Label("Universidad Mariano Gálvez de Guatemala • Cálculo II • Proyecto Final");
        subtitle.getStyleClass().add("app-subtitle");
        VBox titleBox = new VBox(2, title, subtitle);

        HBox presetBar = new HBox(8);
        for (PresetConfig p : Presets.ALL) {
            Button b = new Button(p.nombre().split("\\(")[0].trim());
            b.getStyleClass().add("preset-btn");
            b.setOnAction(e -> applyPreset(p));
            presetBar.getChildren().add(b);
        }

        HBox header = new HBox(24, titleBox, spacer(), presetBar);
        header.getStyleClass().add("header-bar");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 24, 14, 24));
        return header;
    }

    private Node buildSimulatorTab() {
        leftPanel = buildLeftPanel();
        leftPanel.setPrefWidth(LEFT_PANEL_WIDTH);
        leftPanel.setMinWidth(320);

        rightPanel = buildRightPanel();
        HBox.setHgrow(rightPanel, Priority.ALWAYS);

        wideLayout.getChildren().setAll(leftPanel, rightPanel);
        simContent.getChildren().setAll(wideLayout);
        simContent.setPadding(new Insets(PAGE_PADDING));

        ScrollPane scroll = new ScrollPane(simContent);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); // the content is sized to always fit the width
        scroll.getStyleClass().add("edge-to-edge");
        // Re-fit the 3D view and charts to the visible width. Debounced: dragging
        // the window edge fires dozens of viewport changes per second.
        resizeDebounce.setOnFinished(e -> applyResponsiveLayout(scroll.getViewportBounds().getWidth()));
        scroll.viewportBoundsProperty().addListener((o, ov, nv) -> resizeDebounce.playFromStart());
        return scroll;
    }

    // ------------------------------------------------------------ responsive

    private static final double LEFT_PANEL_WIDTH = 360;
    private static final double PAGE_PADDING = 18;
    private static final double COLUMN_GAP = 20;
    private static final double CARD_INSET = 2 * 18 + 2;      // .card padding on both sides + its 1px border
    private static final double STACK_PANELS_BELOW = 1000;    // narrower than this: parameters go above the views
    private static final double MIN_CHART_WIDTH = 300;

    /**
     * Fits the 3D view and the two charts to the scroll viewport's width (a laptop at
     * 1280 px, or 1920 px with Windows scaling at 150 %, is far narrower than the old
     * fixed 1440 px layout). Below STACK_PANELS_BELOW the parameters panel moves above
     * the views, and the two charts stack when they no longer fit side by side.
     */
    private void applyResponsiveLayout(double viewportWidth) {
        if (viewportWidth <= 0 || Math.abs(viewportWidth - lastViewportWidth) < 2) return;
        lastViewportWidth = viewportWidth;

        boolean stack = viewportWidth < STACK_PANELS_BELOW;
        if (stack != panelsStacked) {
            panelsStacked = stack;
            wideLayout.getChildren().clear();
            narrowLayout.getChildren().clear();
            (stack ? narrowLayout : wideLayout).getChildren().setAll(leftPanel, rightPanel);
            simContent.getChildren().setAll(stack ? narrowLayout : wideLayout);
        }

        // − 4: a few pixels of slack so rounding never makes the content 1 px wider than the viewport.
        double rightWidth = viewportWidth - 2 * PAGE_PADDING - (stack ? 0 : LEFT_PANEL_WIDTH + COLUMN_GAP) - 4;
        double inner = rightWidth - CARD_INSET;

        double solidWidth = clamp(inner, 300, 1300);
        solid3D.setSize(solidWidth, clamp(solidWidth * 0.55, 280, 560));

        double gap = chartsRow.getHgap();
        boolean sideBySide = (rightWidth - gap) / 2 - CARD_INSET >= MIN_CHART_WIDTH;
        double chartWidth = sideBySide ? (rightWidth - gap) / 2 - CARD_INSET : inner;
        double chartHeight = clamp(chartWidth * 0.8, 240, 420);
        for (javafx.scene.canvas.Canvas c : List.of(region2D, accumCanvas)) {
            c.setWidth(chartWidth);
            c.setHeight(chartHeight);
        }
        chartsRow.setPrefWrapLength(rightWidth);

        // Resizing clears a Canvas; redraw everything at the new size (the guided
        // mode redraws on its own next step).
        if (guidedStep == 0) refreshSliceViews();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private VBox buildLeftPanel() {
        VBox card = card();
        Label header = sectionTitle("Parámetros del Problema");
        presetBadge.getStyleClass().add("badge-ok");
        HBox headerRow = new HBox(header, spacer(), presetBadge);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        Label introHint = new Label(
                "Cómo usar: 1) escriba f(x) y g(x) y el intervalo [a, b]  →  " +
                "2) elija el método y el eje  →  3) el volumen, las gráficas y el sólido 3D se actualizan solos.");
        introHint.getStyleClass().add("intro-hint");
        introHint.setWrapText(true);

        fField.setPromptText("ej. x + 3 o sqrt(x)");
        gField.setPromptText("ej. x^2 + 1 o 0");
        fField.getStyleClass().addAll("mono-field", "field-f");
        gField.getStyleClass().addAll("mono-field", "field-g");
        fField.setTooltip(new Tooltip("Función SUPERIOR (curva de arriba). Se dibuja en azul, igual que en la gráfica 2D y el sólido 3D."));
        gField.setTooltip(new Tooltip("Función INFERIOR (curva de abajo). Se dibuja en rosa, igual que en la gráfica 2D y el sólido 3D. Use 0 para el método de discos."));

        aField.setPromptText("a");
        bField.setPromptText("b");
        aField.getStyleClass().add("mono-field");
        bField.getStyleClass().add("mono-field");
        aField.setTooltip(new Tooltip("Límite inferior de integración (dónde empieza la región a girar).\nAcepta expresiones: -1, 0,5, pi/2, sqrt(2)."));
        bField.setTooltip(new Tooltip("Límite superior de integración (dónde termina la región a girar).\nAcepta expresiones: 2, 1,5, pi, sqrt(3)."));
        HBox abRow = new HBox(10,
                labeledField("Límite Inferior (a)", aField),
                labeledField("Límite Superior (b)", bField));
        HBox.setHgrow(abRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(abRow.getChildren().get(1), Priority.ALWAYS);

        intersectBtn.getStyleClass().add("secondary-btn");
        intersectBtn.setMaxWidth(Double.MAX_VALUE);
        intersectBtn.setTooltip(new Tooltip(
                "Resuelve f(x) - g(x) = 0 en [" + fmtNum(IntersectionFinder.DEFAULT_LO) + ", " + fmtNum(IntersectionFinder.DEFAULT_HI) +
                "] (bisección) y usa como a y b las dos intersecciones que encierran la región donde f(x) ≥ g(x)."));
        intersectionInfo.getStyleClass().add("hint-text");
        intersectionInfo.setWrapText(true);
        intersectionInfo.setVisible(false);
        intersectionInfo.setManaged(false);

        metodoCombo.getItems().addAll(Metodo.DISCOS, Metodo.ARANDELAS, Metodo.CAPAS);
        metodoCombo.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Metodo m) {
                return switch (m) {
                    case DISCOS -> "Método del Disco (Eje X)";
                    case ARANDELAS -> "Método de Arandelas (Eje X)";
                    case CAPAS -> "Método de Capas Cilíndricas (Eje Y)";
                };
            }
            @Override public Metodo fromString(String s) { return null; }
        });
        metodoCombo.setMaxWidth(Double.MAX_VALUE);
        metodoCombo.setTooltip(new Tooltip(
                "Disco: g(x)=0, sólido macizo.\nArandelas: región con un hueco central, entre f(x) y g(x).\nCapas cilíndricas: se usa cuando se gira alrededor del eje Y."));

        ToggleGroup ejeGroup = new ToggleGroup();
        ejeXBtn.setToggleGroup(ejeGroup);
        ejeYBtn.setToggleGroup(ejeGroup);
        ejeXBtn.getStyleClass().add("eje-toggle");
        ejeYBtn.getStyleClass().add("eje-toggle");
        ejeXBtn.setTooltip(new Tooltip("Solo válido con Disco o Arandelas."));
        ejeYBtn.setTooltip(new Tooltip("Solo válido con Capas Cilíndricas."));
        HBox ejeRow = new HBox(8, ejeXBtn, ejeYBtn);
        ejeXBtn.setMaxWidth(Double.MAX_VALUE);
        ejeYBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(ejeXBtn, Priority.ALWAYS);
        HBox.setHgrow(ejeYBtn, Priority.ALWAYS);

        Label ejeNote = new Label("* Incompatibilidades restringidas según normativa del proyecto.");
        ejeNote.getStyleClass().add("hint-text");
        ejeNote.setWrapText(true);

        card.getChildren().addAll(
                headerRow, new Separator(), introHint,
                labeledField("Función Superior f(x)  —  curva azul:", fField),
                labeledField("Función Inferior g(x)  —  curva rosa:", gField),
                abRow,
                intersectBtn,
                intersectionInfo,
                labeledNode("Método de Cálculo:", metodoCombo),
                labeledNode("Eje de Rotación:", ejeRow),
                ejeNote
        );

        errorBanner.getStyleClass().add("error-banner");

        VBox comparisonCard = card();
        Label compTitle = sectionTitle("Comparación Procedimiento Manual");
        manualVolField.getStyleClass().add("mono-field");
        manualVolField.setTooltip(new Tooltip("Escriba aquí el volumen que obtuvo resolviendo la integral a mano, para compararlo contra el del simulador.\n" +
                "Acepta 73.5133, 73,5133 o la forma exacta: 117*pi/5, 23.4π."));
        VBox statsBox = new VBox(6);
        HBox simRow = statRow("Volumen Simulador:", diffLabel);
        concordanceBanner.getStyleClass().add("concordance-box");
        concordanceBanner.setWrapText(true);
        statsBox.getChildren().addAll(simRow, concordanceBanner);
        comparisonCard.getChildren().addAll(compTitle, new Separator(),
                labeledField("Volumen Manual (u³):", manualVolField), statsBox);

        VBox column = new VBox(16, card, errorBanner, comparisonCard);
        return column;
    }

    private HBox statRow(String label, Label valueLabel) {
        Label l = new Label(label);
        l.getStyleClass().add("hint-text");
        valueLabel.getStyleClass().add("mono-value");
        HBox row = new HBox(l, spacer(), valueLabel);
        return row;
    }

    private VBox buildRightPanel() {
        VBox guidedCard = card();
        Label guidedTitle = sectionTitle("Modo Guiado · Cómo se construye la integral");
        guidedStepLabel.getStyleClass().add("eyebrow");
        guidedCaption.setWrapText(true);
        guidedCaption.getStyleClass().add("hint-text");
        guidedStartBtn.getStyleClass().add("primary-btn");
        guidedPrevBtn.getStyleClass().add("secondary-btn");
        guidedNextBtn.getStyleClass().add("primary-btn");
        guidedExitBtn.getStyleClass().add("secondary-btn");
        guidedStartBtn.setTooltip(new Tooltip(
                "Recorre, paso a paso: 1) las curvas, 2) los límites [a,b], 3) el diferencial dV, 4) la integral acumulándose hasta el volumen total."));
        HBox guidedNav = new HBox(10, guidedPrevBtn, guidedNextBtn, guidedExitBtn);
        VBox guidedBody = new VBox(8, guidedStepLabel, guidedCaption, guidedNav);
        guidedCard.getChildren().addAll(guidedTitle, guidedStartBtn, guidedBody);

        VBox resultCard = card();
        Label resultTag = new Label("RESULTADO COMPUTACIONAL INTEGRAL");
        resultTag.getStyleClass().add("eyebrow");
        volumeValueLabel.getStyleClass().add("volume-value");
        volumeFormulaLabel.getStyleClass().add("hint-text");
        volumeFormulaLabel.setWrapText(true);

        Button exportCsvBtn = new Button("Exportar CSV");
        Button exportTxtBtn = new Button("Exportar TXT");
        exportCsvBtn.getStyleClass().add("secondary-btn");
        exportTxtBtn.getStyleClass().add("secondary-btn");
        exportCsvBtn.setTooltip(new Tooltip("Guarda x, f(x), g(x) y V_acum(x) de los 150 puntos usados en las gráficas (Sección 12 del proyecto)."));
        exportTxtBtn.setTooltip(new Tooltip("Lo mismo que el CSV, en formato de texto separado por tabulaciones."));
        exportCsvBtn.setOnAction(e -> exportData(true));
        exportTxtBtn.setOnAction(e -> exportData(false));
        Button exportPngBtn = new Button("Exportar gráficas PNG");
        exportPngBtn.getStyleClass().add("secondary-btn");
        exportPngBtn.setTooltip(new Tooltip("Guarda region_plana.png, solido_3d.png y volumen_acumulado.png (con título y ejes) en la carpeta que elija, para el informe."));
        exportPngBtn.setOnAction(e -> exportCharts());
        exportCsvBtn.setMaxWidth(Double.MAX_VALUE);
        exportTxtBtn.setMaxWidth(Double.MAX_VALUE);
        exportPngBtn.setMaxWidth(Double.MAX_VALUE);
        VBox exportBox = new VBox(8, exportCsvBtn, exportTxtBtn, exportPngBtn);

        HBox resultRow = new HBox(24, new VBox(4, resultTag, volumeValueLabel, volumeFormulaLabel), spacer(), exportBox);
        resultRow.setAlignment(Pos.CENTER_LEFT);
        resultCard.getChildren().add(resultRow);

        VBox solidCard = card();
        Label solidTitle = sectionTitle("Sólido 3D · arrastra para rotar, rueda para zoom");
        axisLegend.setAlignment(Pos.CENTER_LEFT);
        updateAxisLegend();

        // Vista: opaque solid or see-through wireframe of the f and g surfaces.
        ToggleGroup viewGroup = new ToggleGroup();
        viewSolidBtn.setToggleGroup(viewGroup);
        viewWireBtn.setToggleGroup(viewGroup);
        viewSolidBtn.setSelected(true);
        viewSolidBtn.getStyleClass().add("view-toggle");
        viewWireBtn.getStyleClass().add("view-toggle");
        viewSolidBtn.setTooltip(new Tooltip("Sólido opaco, como se vería la pieza real."));
        viewWireBtn.setTooltip(new Tooltip(
                "Malla transparente: líneas azules = superficie que genera f(x), líneas rosas = superficie que genera g(x). " +
                "Permite ver el hueco interior y la región R al mismo tiempo."));
        // A segmented choice must always have one option on: clicking the active one again keeps it.
        viewGroup.selectedToggleProperty().addListener((o, ov, nv) -> {
            if (nv == null) { ov.setSelected(true); return; }
            solid3D.setWireframe(nv == viewWireBtn);
            refreshSliceViews();
        });
        Label viewLabel = new Label("Vista:");
        viewLabel.getStyleClass().add("hint-text");
        HBox viewBox = new HBox(6, viewLabel, viewSolidBtn, viewWireBtn);
        viewBox.setAlignment(Pos.CENTER_RIGHT);
        HBox solidHeader = new HBox(solidTitle, spacer(), viewBox);
        solidHeader.setAlignment(Pos.CENTER_LEFT);

        showSliceCheck.setSelected(true);
        showSliceCheck.setTooltip(new Tooltip("Resalta en ámbar el disco/arandela/capa exacto que corresponde a la posición x del control deslizante."));
        sliceSlider.setShowTickMarks(false);
        sliceSlider.setTooltip(new Tooltip("Mueva x para ver, en vivo, qué sección transversal genera el volumen en ese punto."));
        sliceValueLabel.getStyleClass().add("mono-value");
        animateBtn.getStyleClass().add("primary-btn");
        animateBtn.setTooltip(new Tooltip("Anima la región 2D girando 0°→360° hasta formar el sólido completo."));
        resetViewBtn.getStyleClass().add("secondary-btn");
        resetViewBtn.setTooltip(new Tooltip("Vuelve la cámara a su ángulo y zoom iniciales, por si quedó en una orientación rara al arrastrar."));
        resetViewBtn.setOnAction(e -> solid3D.resetView());
        halfCutCheck.setTooltip(new Tooltip(
                "Muestra solo media vuelta del sólido para ver su perfil por dentro: la región R (azul) es la misma de la gráfica 2D, " +
                "f(x) forma la superficie exterior y g(x) el hueco interior."));
        // FlowPane, not HBox: on a laptop-width window the controls wrap to a second
        // row instead of being cut off at the card's edge.
        sliceSlider.setPrefWidth(220);
        HBox sliderGroup = new HBox(10, sliceSlider, sliceValueLabel);
        sliderGroup.setAlignment(Pos.CENTER_LEFT);
        FlowPane sliceControls = new FlowPane(14, 10, showSliceCheck, halfCutCheck, sliderGroup, animateBtn, resetViewBtn);
        sliceControls.setAlignment(Pos.CENTER_LEFT);
        sliceControls.getStyleClass().add("control-bar");

        crossSectionFormulaLabel.getStyleClass().add("formula-box");
        crossSectionFormulaLabel.setWrapText(true);
        crossSectionFormulaLabel.setVisible(false);
        crossSectionFormulaLabel.setManaged(false);

        // Sizes are never bound to the card itself: the card's preferred size is
        // partly derived from this same child, and that loop made the 3D view and
        // the charts grow without bound. They are set instead from the scroll
        // viewport's width (applyResponsiveLayout), which does not depend on them.
        StackPane solidHolder = new StackPane(solid3D.getNode());
        solidHolder.getStyleClass().add("solid-holder");
        solidHolder.setMinSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);
        solidHolder.setMaxSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);

        solidCard.getChildren().addAll(solidHeader, axisLegend, sliceControls, crossSectionFormulaLabel, solidHolder);

        // Side by side when there is room, so the two supporting charts take the
        // vertical space of one; on a narrow window the FlowPane stacks them.
        VBox region2DCard = card();
        region2DCard.getChildren().addAll(sectionTitle("Región Plana 2D y Eje de Rotación"), region2D);

        VBox accumCard = card();
        accumCard.getChildren().addAll(sectionTitle("Volumen Acumulado V_acum(x)"), accumCanvas);

        chartsRow.getChildren().setAll(region2DCard, accumCard);

        VBox column = new VBox(16, guidedCard, resultCard, solidCard, chartsRow);
        return column;
    }

    private Node buildValidationTab() {
        VBox container = card();
        Label title = sectionTitle("Sección 13 · Tabla de Validación del Simulador");
        validationBadge.getStyleClass().add("badge-ok");
        HBox headerRow = new HBox(title, spacer(), validationBadge);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        setupValidationTable();
        // No Vgrow: TableView otherwise stretches to fill all available
        // height, reserving dozens of empty phantom rows below the 4 real
        // ones. Sizing it to just fit its actual content reads far cleaner.
        container.getChildren().addAll(headerRow, new Separator(), validationTable);

        ScrollPane scroll = new ScrollPane(container);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        return scroll;
    }

    @SuppressWarnings("unchecked")
    private void setupValidationTable() {
        TableColumn<ValidationRow, String> colConfig = new TableColumn<>("Config.");
        colConfig.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().config()));
        TableColumn<ValidationRow, String> colFunciones = new TableColumn<>("Funciones");
        colFunciones.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().funciones()));
        TableColumn<ValidationRow, String> colIntervalo = new TableColumn<>("Intervalo");
        colIntervalo.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().intervalo()));
        TableColumn<ValidationRow, String> colMetodo = new TableColumn<>("Método / Eje");
        colMetodo.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().metodoEje()));
        TableColumn<ValidationRow, String> colManual = new TableColumn<>("V. Manual (u³)");
        colManual.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.format("%.4f", d.getValue().manual())));
        TableColumn<ValidationRow, String> colSim = new TableColumn<>("V. Simulador (u³)");
        colSim.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.format("%.4f", d.getValue().simulador())));
        TableColumn<ValidationRow, String> colError = new TableColumn<>("Error %");
        colError.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(String.format("%.2f%%", d.getValue().errorPct())));

        validationTable.getColumns().setAll(List.of(colConfig, colFunciones, colIntervalo, colMetodo, colManual, colSim, colError));
        validationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        List<ValidationRow> rows = new ArrayList<>();
        int validCount = 0;
        for (PresetConfig p : Presets.ALL) {
            try {
                DoubleUnaryOperator f = FunctionEvaluator.compile(p.f());
                DoubleUnaryOperator g = FunctionEvaluator.compile(p.g());
                DoubleUnaryOperator integrand = VolumeCalculator.buildIntegrand(f, g, p.metodo());
                double vSim = VolumeCalculator.simpsonIntegrate(integrand, p.a(), p.b(), 800);
                double errorPct = Math.abs(vSim - p.volumenManual()) / Math.abs(p.volumenManual()) * 100;
                if (errorPct < 0.5) validCount++;
                rows.add(new ValidationRow(
                        p.id().equals("main_project") ? "Principal" : String.valueOf(rows.size()),
                        "f(x)=" + p.f() + ", g(x)=" + p.g(),
                        "[" + fmtNum(p.a()) + ", " + fmtNum(p.b()) + "]",
                        p.metodo().etiqueta() + " (Eje " + p.eje() + ")",
                        p.volumenManual(), vSim, errorPct
                ));
            } catch (Exception ex) {
                rows.add(new ValidationRow(p.id(), p.f() + " / " + p.g(), "-", "-", p.volumenManual(), Double.NaN, Double.NaN));
            }
        }
        validationTable.getItems().setAll(rows);
        validationBadge.setText(validCount + "/" + Presets.ALL.size() + " Configuraciones Validadas");
        // Fit exactly to the row count instead of stretching to fill the window.
        validationTable.setPrefHeight(44 + rows.size() * 34);
        validationTable.setMaxHeight(44 + rows.size() * 34);
    }

    public record ValidationRow(String config, String funciones, String intervalo, String metodoEje,
                                 double manual, double simulador, double errorPct) {}

    private Node buildTheoryTab() {
        VBox container = new VBox(16);
        container.setPadding(new Insets(18));

        VBox derivation = card();
        derivation.getChildren().add(sectionTitle("Resolución Manual Paso a Paso — Ejercicio Principal"));

        VBox blockA = theoryBlock(
                "a. Puntos de Intersección",
                "Igualamos y₁ = x²+1 con y₂ = x+3:\n\n" +
                        "x² + 1 = x + 3  ⇒  x² - x - 2 = 0\n(x-2)(x+1) = 0\nx₁ = -1, x₂ = 2\n\n" +
                        "Intervalo de integración: [-1, 2].");
        VBox blockB = theoryBlock(
                "b. Función Superior e Inferior",
                "Evaluando en x=0: y₂(0)=3 (superior, f), y₁(0)=1 (inferior, g).\n" +
                        "f(x) = x + 3,  g(x) = x² + 1");
        HBox abRow2 = new HBox(14, blockA, blockB);
        HBox.setHgrow(blockA, Priority.ALWAYS);
        HBox.setHgrow(blockB, Priority.ALWAYS);
        blockA.setMaxWidth(Double.MAX_VALUE);
        blockB.setMaxWidth(Double.MAX_VALUE);

        VBox blockCD = theoryBlock(
                "c y d. Deducción y Cálculo del Volumen (Arandelas, eje X)",
                "V = π∫[-1,2] [(x+3)² - (x²+1)²] dx\n" +
                        "(x+3)² = x²+6x+9\n(x²+1)² = x⁴+2x²+1\n" +
                        "Diferencia: -x⁴ - x² + 6x + 8\n" +
                        "∫ = [-x⁵/5 - x³/3 + 3x² + 8x] evaluado de -1 a 2\n" +
                        "En x=2: 284/15   En x=-1: -67/15\n" +
                        "V = π(284/15 + 67/15) = π(351/15) = 117π/5 ≈ 73.51327 u³");

        derivation.getChildren().addAll(abRow2, blockCD);

        VBox defense = card();
        defense.getChildren().add(sectionTitle("Banco de Preguntas para la Defensa Individual"));

        VBox q1 = theoryBlock("1. ¿Arandelas de X a Y?",
                "Si el eje cambia a Y, hay que integrar respecto a dy con x=f(y). Para integrar en x alrededor de Y, se usa Capas Cilíndricas.");
        VBox q2 = theoryBlock("2. ¿Por qué V_acum(x) es creciente?",
                "El área transversal (integrando) es siempre ≥ 0, así que la integral acumulada es monótona no decreciente.");
        VBox q3 = theoryBlock("3. ¿Cómo se deduce dV en Capas Cilíndricas?",
                "dV = 2π·r·h·dx, con r=x y h=f(x)-g(x). Integrando: V = 2π∫ x(f(x)-g(x)) dx.");
        VBox q4 = theoryBlock("4. ¿Disco vs Arandelas?",
                "El disco es el caso particular de arandelas cuando g(x)=0 (sólido macizo, sin hueco central).");
        for (VBox q : List.of(q1, q2, q3, q4)) { HBox.setHgrow(q, Priority.ALWAYS); q.setMaxWidth(Double.MAX_VALUE); }
        HBox qRow1 = new HBox(14, q1, q2);
        HBox qRow2 = new HBox(14, q3, q4);
        defense.getChildren().addAll(qRow1, qRow2);

        container.getChildren().addAll(derivation, defense);
        ScrollPane scroll = new ScrollPane(container);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        return scroll;
    }

    private VBox theoryBlock(String heading, String body) {
        Label h = new Label(heading);
        h.getStyleClass().add("theory-heading");
        Text bodyText = new Text(body);
        bodyText.getStyleClass().add("theory-body");
        TextFlow flow = new TextFlow(bodyText);
        VBox box = new VBox(6, h, flow);
        box.getStyleClass().add("theory-block");
        return box;
    }

    // ------------------------------------------------------------- helpers

    private VBox card() {
        VBox box = new VBox(12);
        box.getStyleClass().add("card");
        return box;
    }

    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-title");
        return l;
    }

    private VBox labeledField(String label, TextField field) {
        return labeledNode(label, field);
    }

    private VBox labeledNode(String label, Node node) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        VBox box = new VBox(4, l, node);
        return box;
    }

    /** A small color swatch + label, e.g. for the 3D axis-color legend (red=X, green=Y, blue=Z). */
    private HBox axisLegendItem(String label, Color color) {
        javafx.scene.shape.Rectangle swatch = new javafx.scene.shape.Rectangle(12, 12, color);
        swatch.setArcWidth(3);
        swatch.setArcHeight(3);
        Label l = new Label(label);
        l.getStyleClass().add("hint-text");
        HBox box = new HBox(6, swatch, l);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    private static String fmtNum(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-9) return String.valueOf(Math.round(v));
        return String.format(Locale.US, "%.2f", v);
    }

    /** For values written back into input fields: up to 6 decimals, so nothing gets rounded off (0.9425 stays 0.9425). */
    private static String fmtInput(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-9) return String.valueOf(Math.round(v));
        return String.format(Locale.US, "%.6f", v).replaceAll("0+$", "");
    }

    // --------------------------------------------------------------- events

    private void wireEvents() {
        fField.textProperty().addListener((o, ov, nv) -> { selectedPresetId = "custom"; updatePresetBadge(); hideIntersectionInfo(); recompute(); });
        gField.textProperty().addListener((o, ov, nv) -> { selectedPresetId = "custom"; updatePresetBadge(); hideIntersectionInfo(); recompute(); });
        intersectBtn.setOnAction(e -> computeIntersections());
        aField.textProperty().addListener((o, ov, nv) -> { selectedPresetId = "custom"; updatePresetBadge(); recompute(); });
        bField.textProperty().addListener((o, ov, nv) -> { selectedPresetId = "custom"; updatePresetBadge(); recompute(); });
        manualVolField.textProperty().addListener((o, ov, nv) -> updateComparison());

        metodoCombo.valueProperty().addListener((o, ov, nv) -> {
            if (nv == null) return;
            metodo = nv;
            if (metodo == Metodo.DISCOS || metodo == Metodo.ARANDELAS) { eje = Eje.X; ejeXBtn.setSelected(true); }
            else { eje = Eje.Y; ejeYBtn.setSelected(true); }
            updateEjeButtonsEnabled();
            selectedPresetId = "custom"; updatePresetBadge();
            recompute();
        });
        ejeXBtn.setOnAction(e -> { eje = Eje.X; selectedPresetId = "custom"; updatePresetBadge(); recompute(); });
        ejeYBtn.setOnAction(e -> { eje = Eje.Y; selectedPresetId = "custom"; updatePresetBadge(); recompute(); });

        showSliceCheck.setOnAction(e -> refreshSliceViews());
        halfCutCheck.setOnAction(e -> { solid3D.setHalfCut(halfCutCheck.isSelected()); refreshSliceViews(); });
        sliceSlider.valueProperty().addListener((o, ov, nv) -> {
            sliceX = nv.doubleValue();
            sliceValueLabel.setText(String.format("x = %.3f", sliceX));
            refreshSliceViews();
        });
        animateBtn.setOnAction(e -> {
            if (!cachedValid) return;
            animateBtn.setDisable(true);
            solid3D.playSweepAnimation(cachedF, cachedG, cachedA, cachedB, metodo, eje,
                    showSliceCheck.isSelected() ? sliceX : null, 1800,
                    () -> Platform.runLater(() -> animateBtn.setDisable(false)));
        });

        guidedStartBtn.setOnAction(e -> {
            if (!cachedValid) return;
            showSliceCheck.setDisable(true);
            sliceSlider.setDisable(true);
            animateBtn.setDisable(true);
            guidedStep = 1;
            updateGuidedUI();
        });
        guidedPrevBtn.setOnAction(e -> { if (guidedStep > 1) { guidedStep--; updateGuidedUI(); } });
        guidedNextBtn.setOnAction(e -> { if (guidedStep > 0 && guidedStep < 4) { guidedStep++; updateGuidedUI(); } });
        guidedExitBtn.setOnAction(e -> exitGuidedMode());
        updateGuidedUI();
    }

    // ---------------------------------------------------------- guided mode

    private void updateGuidedUI() {
        boolean active = guidedStep > 0;
        guidedStartBtn.setVisible(!active);
        guidedStartBtn.setManaged(!active);
        guidedStepLabel.setVisible(active);
        guidedStepLabel.setManaged(active);
        guidedCaption.setVisible(active);
        guidedCaption.setManaged(active);
        guidedPrevBtn.setVisible(active);
        guidedPrevBtn.setManaged(active);
        guidedNextBtn.setVisible(active);
        guidedNextBtn.setManaged(active);
        guidedExitBtn.setVisible(active);
        guidedExitBtn.setManaged(active);
        if (!active) return;
        guidedPrevBtn.setDisable(guidedStep <= 1);
        guidedNextBtn.setDisable(guidedStep >= 4);
        applyGuidedStep(guidedStep);
    }

    private void applyGuidedStep(int step) {
        if (!cachedValid) return;
        double mid = (cachedA + cachedB) / 2;
        switch (step) {
            case 1 -> {
                guidedStepLabel.setText("Paso 1 de 4 · Graficar f(x) y g(x)");
                guidedCaption.setText(
                        "Se dibujan las dos funciones en el plano: f(x) en azul (curva superior) y g(x) en rosa (curva inferior). " +
                        "Todavía no se marca ninguna región ni límite de integración.");
                region2D.update(cachedF, cachedG, cachedA, cachedB, eje, null, false, false, false, false);
                solid3D.update(cachedF, cachedG, cachedA, cachedB, metodo, eje, null, 0.01);
                accumCanvas.update(List.of(), null, null);
                crossSectionFormulaLabel.setVisible(false);
                crossSectionFormulaLabel.setManaged(false);
            }
            case 2 -> {
                guidedStepLabel.setText("Paso 2 de 4 · Intersecciones y límites [a, b]");
                guidedCaption.setText(String.format(Locale.US,
                        "Los puntos marcados en x=%.3f y x=%.3f delimitan la región R sombreada entre f(x) y g(x). " +
                        "Esa región, al girar sobre el eje %s, es la que genera el sólido.",
                        cachedA, cachedB, eje == Eje.X ? "X" : "Y"));
                region2D.update(cachedF, cachedG, cachedA, cachedB, eje, null, true, true, true, true);
                solid3D.update(cachedF, cachedG, cachedA, cachedB, metodo, eje, null, 0.01);
                accumCanvas.update(List.of(), null, null);
                crossSectionFormulaLabel.setVisible(false);
                crossSectionFormulaLabel.setManaged(false);
            }
            case 3 -> {
                guidedStepLabel.setText("Paso 3 de 4 · El diferencial de volumen dV");
                guidedCaption.setText(
                        "Un rectángulo diferencial de ancho dx, al girar alrededor del eje, genera un disco/arandela/capa: " +
                        "el diferencial dV resaltado en ámbar sobre el sólido ya formado.");
                region2D.update(cachedF, cachedG, cachedA, cachedB, eje, mid, true, true, true, false);
                solid3D.update(cachedF, cachedG, cachedA, cachedB, metodo, eje, mid, 1.0);
                accumCanvas.update(List.of(), null, null);
                updateCrossSectionFormula(mid);
            }
            case 4 -> {
                guidedStepLabel.setText("Paso 4 de 4 · La integral: suma de todos los diferenciales");
                guidedCaption.setText(
                        "La integral acumula infinitos diferenciales dV desde a hasta b. Observe cómo el sólido se completa " +
                        "mientras el volumen acumulado V_acum(x) crece hasta el volumen total.");
                crossSectionFormulaLabel.setVisible(false);
                crossSectionFormulaLabel.setManaged(false);
                guidedPrevBtn.setDisable(true);
                guidedNextBtn.setDisable(true);
                accumCanvas.update(List.of(), null, null);
                region2D.update(cachedF, cachedG, cachedA, cachedB, eje, cachedA, true, true, true, false);
                solid3D.playSweepAnimation(cachedF, cachedG, cachedA, cachedB, metodo, eje, null, 2400,
                        frac -> Platform.runLater(() -> revealAccumUpTo(frac)),
                        () -> Platform.runLater(() -> guidedPrevBtn.setDisable(guidedStep <= 1)));
            }
        }
    }

    /** Guided step 4: reveals V_acum(x) and moves the 2D slice marker in lockstep with the solid's sweep animation. */
    private void revealAccumUpTo(double fraction) {
        if (!cachedValid || sampleData.isEmpty()) return;
        double x = cachedA + (cachedB - cachedA) * Math.max(0, Math.min(1, fraction));
        List<AccumCanvas.Point> pts = new ArrayList<>();
        for (DataExporter.Row r : sampleData) {
            if (r.x() > x) break;
            pts.add(new AccumCanvas.Point(r.x(), r.vAcum()));
        }
        if (pts.isEmpty()) return;
        accumCanvas.update(pts, x, pts.get(pts.size() - 1).v());
        region2D.update(cachedF, cachedG, cachedA, cachedB, eje, x, true, true, true, false);
    }

    private void exitGuidedMode() {
        solid3D.stopSweepAnimation();
        guidedStep = 0;
        showSliceCheck.setDisable(false);
        sliceSlider.setDisable(false);
        animateBtn.setDisable(false);
        updateGuidedUI();
        refreshSliceViews();
    }

    private void updateEjeButtonsEnabled() {
        ejeXBtn.setDisable(metodo == Metodo.CAPAS);
        ejeYBtn.setDisable(metodo == Metodo.DISCOS || metodo == Metodo.ARANDELAS);
    }

    private void applyPreset(PresetConfig p) {
        // Each setText/setValue below fires its own change listener, which
        // (correctly, for manual edits) marks the config as "custom" — but
        // that means selectedPresetId gets stomped back to "custom" partway
        // through applying a preset. Re-assert it at the end so the badge
        // reads "Preset activo" instead of incorrectly showing "Personalizado"
        // right after the user clicked a preset button.
        fField.setText(p.f());
        gField.setText(p.g());
        aField.setText(fmtNum(p.a()));
        bField.setText(fmtNum(p.b()));
        metodo = p.metodo();
        eje = p.eje();
        metodoCombo.setValue(metodo);
        ejeXBtn.setSelected(eje == Eje.X);
        ejeYBtn.setSelected(eje == Eje.Y);
        updateEjeButtonsEnabled();
        manualVolField.setText(fmtInput(p.volumenManual()));

        selectedPresetId = p.id();
        updatePresetBadge();
        recompute();
    }

    private void updatePresetBadge() {
        boolean custom = "custom".equals(selectedPresetId);
        presetBadge.setText(custom ? "Personalizado" : "Preset activo");
        presetBadge.getStyleClass().removeAll("badge-ok", "badge-warn");
        presetBadge.getStyleClass().add(custom ? "badge-warn" : "badge-ok");
    }

    // -------------------------------------------------------------- compute

    private void recompute() {
        if (guidedStep > 0) exitGuidedMode();
        updateAxisLegend();
        List<String> errors = new ArrayList<>();
        double a = FunctionEvaluator.evaluateConstant(aField.getText());
        double b = FunctionEvaluator.evaluateConstant(bField.getText());

        DoubleUnaryOperator f = null, g = null;
        try { f = FunctionEvaluator.compile(fField.getText()); } catch (Exception ex) { errors.add("Error de sintaxis en la función f(x)."); }
        try { g = FunctionEvaluator.compile(gField.getText()); } catch (Exception ex) { errors.add("Error de sintaxis en la función g(x)."); }

        if (f != null && g != null) {
            Validator.Result result = Validator.validar(a, b, metodo, eje, f, g);
            errors.addAll(result.errores());
        } else if (Double.isNaN(a) || Double.isNaN(b)) {
            errors.add("Los límites de integración a y b deben ser números válidos.");
        } else if (a >= b) {
            errors.add("Debe cumplirse que a < b.");
        }

        updateErrorBanner(errors);

        if (errors.isEmpty() && f != null && g != null) {
            cachedF = f; cachedG = g; cachedA = a; cachedB = b; cachedValid = true;

            DoubleUnaryOperator integrand = VolumeCalculator.buildIntegrand(f, g, metodo);
            calculatedVol = VolumeCalculator.simpsonIntegrate(integrand, a, b, 800);

            sampleData = new ArrayList<>();
            int numPoints = 150;
            for (int i = 0; i <= numPoints; i++) {
                double x = a + (b - a) * i / numPoints;
                double vAcum = VolumeCalculator.accumulatedVolume(integrand, a, x);
                sampleData.add(new DataExporter.Row(x, f.applyAsDouble(x), g.applyAsDouble(x), vAcum));
            }

            sliceSlider.setMin(a);
            sliceSlider.setMax(b);
            if (sliceX == null || sliceX < a || sliceX > b) {
                sliceX = (a + b) / 2;
                sliceSlider.setValue(sliceX);
            } else {
                sliceSlider.setValue(sliceX);
            }
            sliceValueLabel.setText(String.format("x = %.3f", sliceX));

            volumeValueLabel.setText(String.format(Locale.US, "%.4f u³", calculatedVol));
            volumeFormulaLabel.setText(formulaFor(metodo, a, b));

            refreshSliceViews();
            updateComparison();
        } else {
            calculatedVol = null;
            cachedValid = false;
            volumeValueLabel.setText("---");
            volumeFormulaLabel.setText("");
            diffLabel.setText("---");
            concordanceBanner.setText("Corrija los errores de validación para calcular el volumen.");
        }
    }

    /** Redraws the 2D/3D/accum views using the last validated f,g,a,b (cheap — no re-validation). */
    private void refreshSliceViews() {
        if (!cachedValid) return;
        Double activeSlice = showSliceCheck.isSelected() ? sliceX : null;
        region2D.update(cachedF, cachedG, cachedA, cachedB, eje, activeSlice);
        solid3D.update(cachedF, cachedG, cachedA, cachedB, metodo, eje, activeSlice, 1.0);

        List<AccumCanvas.Point> pts = new ArrayList<>();
        for (DataExporter.Row r : sampleData) pts.add(new AccumCanvas.Point(r.x(), r.vAcum()));

        Double sliceV = null;
        if (activeSlice != null) {
            DoubleUnaryOperator integrand = VolumeCalculator.buildIntegrand(cachedF, cachedG, metodo);
            sliceV = VolumeCalculator.accumulatedVolume(integrand, cachedA, activeSlice);
        }
        accumCanvas.update(pts, activeSlice, sliceV);
        updateCrossSectionFormula(activeSlice);
    }

    private void updateCrossSectionFormula(Double x) {
        if (x == null || !cachedValid) {
            crossSectionFormulaLabel.setVisible(false);
            crossSectionFormulaLabel.setManaged(false);
            return;
        }
        double fx = cachedF.applyAsDouble(x), gx = cachedG.applyAsDouble(x);
        String text = switch (metodo) {
            case DISCOS -> String.format(Locale.US, "A(x) = π[f(x)]² = π(%.3f)² ≈ %.4f u²",
                    fx, Math.PI * fx * fx);
            case ARANDELAS -> String.format(Locale.US, "A(x) = π([f(x)]²-[g(x)]²) ≈ π(%.3f²-%.3f²) ≈ %.4f u²",
                    fx, gx, Math.PI * (fx * fx - gx * gx));
            case CAPAS -> String.format(Locale.US, "dV/dx = 2πx(f(x)-g(x)) ≈ 2π(%.3f)(%.3f-%.3f) ≈ %.4f u²",
                    x, fx, gx, 2 * Math.PI * x * (fx - gx));
        };
        crossSectionFormulaLabel.setText(text);
        crossSectionFormulaLabel.setVisible(true);
        crossSectionFormulaLabel.setManaged(true);
    }

    private String formulaFor(Metodo m, double a, double b) {
        String range = String.format(Locale.US, "en [%.2f, %.2f]", a, b);
        return switch (m) {
            case DISCOS -> "V = π ∫ [f(x)]² dx " + range;
            case ARANDELAS -> "V = π ∫ ([f(x)]² - [g(x)]²) dx " + range;
            case CAPAS -> "V = 2π ∫ x (f(x) - g(x)) dx " + range;
        };
    }

    private void updateErrorBanner(List<String> errors) {
        errorBanner.getChildren().clear();
        errorBanner.getStyleClass().removeAll("error-banner", "success-banner");
        if (errors.isEmpty()) {
            errorBanner.getStyleClass().add("success-banner");
            Label ok = new Label("✓ Validación Exitosa — todos los datos satisfacen las condiciones del método.");
            ok.setWrapText(true);
            errorBanner.getChildren().add(ok);
        } else {
            errorBanner.getStyleClass().add("error-banner");
            Label title = new Label("⚠ Errores de Validación Detectados");
            title.getStyleClass().add("error-title");
            errorBanner.getChildren().add(title);
            for (String err : errors) {
                Label l = new Label("• " + err);
                l.setWrapText(true);
                l.getStyleClass().add("error-item");
                errorBanner.getChildren().add(l);
            }
        }
    }

    private void updateComparison() {
        if (calculatedVol == null) {
            diffLabel.setText("---");
            return;
        }
        diffLabel.setText(String.format(Locale.US, "%.4f u³", calculatedVol));
        String manualText = manualVolField.getText();
        double manual = FunctionEvaluator.evaluateConstant(manualText);
        if (Double.isNaN(manual)) {
            concordanceBanner.getStyleClass().removeAll("concordance-ok", "concordance-warn", "concordance-bad");
            concordanceBanner.setText(manualText.isBlank()
                    ? "Ingrese un resultado manual para comparar."
                    : "Resultado manual no válido. Ejemplos: 73.5133, 73,5133 o 117*pi/5.");
            return;
        }
        // Echo the evaluated value when it was typed as an expression (e.g. 117*pi/5).
        String manualEcho = isPlainNumber(manualText) ? "" : String.format(Locale.US, "Manual = %.4f u³. ", manual);

        double vSim = calculatedVol;
        double diffPct;
        String text;
        String styleClass;
        if (Math.abs(vSim) < 1e-12) {
            if (Math.abs(manual) < 1e-12) { diffPct = 0; text = "Ambos resultados son cero."; styleClass = "concordance-ok"; }
            else { diffPct = 100; text = "Los resultados no presentan concordancia."; styleClass = "concordance-bad"; }
        } else {
            diffPct = Math.abs(vSim - manual) / Math.abs(vSim) * 100;
            if (diffPct < 0.5) { text = "Excelente concordancia (< 0.5%)."; styleClass = "concordance-ok"; }
            else if (diffPct < 2.0) { text = "Diferencia pequeña (< 2%)."; styleClass = "concordance-ok"; }
            else if (diffPct < 5.0) { text = "Diferencia moderada (< 5%)."; styleClass = "concordance-warn"; }
            else { text = "Diferencia significativa (≥ 5%)."; styleClass = "concordance-bad"; }
        }
        concordanceBanner.getStyleClass().removeAll("concordance-ok", "concordance-warn", "concordance-bad");
        concordanceBanner.getStyleClass().add(styleClass);
        concordanceBanner.setText(manualEcho + String.format(Locale.US, "Diferencia: %.3f%% — %s", diffPct, text));
    }

    private static boolean isPlainNumber(String s) {
        try { Double.parseDouble(s.trim()); return true; } catch (NumberFormatException e) { return false; }
    }

    // -------------------------------------------------------- intersections

    private void computeIntersections() {
        DoubleUnaryOperator f, g;
        try {
            f = FunctionEvaluator.compile(fField.getText());
            g = FunctionEvaluator.compile(gField.getText());
        } catch (IllegalArgumentException ex) {
            showIntersectionInfo("Corrija la sintaxis de f(x) y g(x) antes de buscar intersecciones.");
            return;
        }

        List<Double> roots = IntersectionFinder.findIntersections(f, g);
        String rango = "[" + fmtNum(IntersectionFinder.DEFAULT_LO) + ", " + fmtNum(IntersectionFinder.DEFAULT_HI) + "]";
        if (roots.isEmpty()) {
            showIntersectionInfo("No se encontraron intersecciones en " + rango + ". Ingrese a y b manualmente.");
            return;
        }

        String lista = roots.stream()
                .map(r -> String.format(Locale.US, "x = %s (y = %s)", fmtRoot(r), fmtRoot(f.applyAsDouble(r))))
                .collect(Collectors.joining(";  "));
        IntersectionFinder.Interval region = IntersectionFinder.regionBetween(roots, f, g);
        if (region == null) {
            String motivo = roots.size() < 2
                    ? "Solo hay una intersección: ingrese el otro límite manualmente."
                    : "Entre las intersecciones g(x) queda por encima de f(x): intercambie f y g.";
            showIntersectionInfo("Intersecciones: " + lista + ".\n" + motivo);
            return;
        }

        aField.setText(fmtInput(region.a()));
        bField.setText(fmtInput(region.b()));
        showIntersectionInfo("Intersecciones: " + lista + ".\nSe usaron a = " + fmtRoot(region.a()) +
                " y b = " + fmtRoot(region.b()) + " (región donde f(x) ≥ g(x)).");
    }

    private static String fmtRoot(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-9) return String.valueOf(Math.round(v));
        return String.format(Locale.US, "%.4f", v);
    }

    private void showIntersectionInfo(String text) {
        intersectionInfo.setText(text);
        intersectionInfo.setVisible(true);
        intersectionInfo.setManaged(true);
    }

    private void hideIntersectionInfo() {
        intersectionInfo.setVisible(false);
        intersectionInfo.setManaged(false);
    }

    // -------------------------------------------------------------- export

    private void exportData(boolean csv) {
        if (sampleData.isEmpty()) return;
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(csv ? "datos_solido.csv" : "datos_solido.txt");
        chooser.getExtensionFilters().add(csv
                ? new FileChooser.ExtensionFilter("CSV", "*.csv")
                : new FileChooser.ExtensionFilter("Texto", "*.txt"));
        java.io.File file = chooser.showSaveDialog(stage);
        if (file == null) return;

        List<DataExporter.Row> rows = new ArrayList<>();
        for (DataExporter.Row r : sampleData) rows.add(r);
        try {
            Path path = file.toPath();
            if (csv) DataExporter.exportCsv(path, rows); else DataExporter.exportTxt(path, rows);
        } catch (IOException ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No se pudo exportar: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    /** Pixel density of the exported PNGs (2 = twice the on-screen resolution, sharp when printed). */
    private static final double PNG_SCALE = 2;

    /**
     * Section 9/10/17: saves the 2D region, 3D solid and V_acum charts as PNG.
     * The on-screen canvases rely on their card for the title, so each image is
     * framed with its own title, data summary and axis names before saving.
     */
    private void exportCharts() {
        if (!cachedValid) {
            new Alert(Alert.AlertType.WARNING, "Corrija los errores de validación antes de exportar las gráficas.").showAndWait();
            return;
        }
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Carpeta donde guardar las gráficas");
        java.io.File dir = chooser.showDialog(stage);
        if (dir == null) return;

        try {
            saveChartsTo(dir.toPath());
            new Alert(Alert.AlertType.INFORMATION,
                    "Se guardaron region_plana.png, solido_3d.png y volumen_acumulado.png en:\n" + dir).showAndWait();
        } catch (IOException ex) {
            new Alert(Alert.AlertType.ERROR, "No se pudieron exportar las gráficas: " + ex.getMessage()).showAndWait();
        }
    }

    private void saveChartsTo(Path folder) throws IOException {
        if (guidedStep > 0) exitGuidedMode();
        // Clean render (no amber slice marker) for the report figures.
        region2D.update(cachedF, cachedG, cachedA, cachedB, eje, null);
        solid3D.update(cachedF, cachedG, cachedA, cachedB, metodo, eje, null, 1.0);
        List<AccumCanvas.Point> pts = new ArrayList<>();
        for (DataExporter.Row r : sampleData) pts.add(new AccumCanvas.Point(r.x(), r.vAcum()));
        accumCanvas.update(pts, null, null);

        String datos = String.format(Locale.US, "f(x) = %s,  g(x) = %s,  [a, b] = [%s, %s],  %s alrededor del eje %s",
                fField.getText().trim(), gField.getText().trim(), fmtRoot(cachedA), fmtRoot(cachedB), metodo.etiqueta(), eje);
        String volumen = String.format(Locale.US, "V ≈ %.4f u³", calculatedVol);
        try {
            ImageExporter.savePng(framedChart(region2D, PNG_SCALE, "Región plana a rotar",
                    datos, "x", "y", null), folder.resolve("region_plana.png"));
            ImageExporter.savePng(framedChart(solid3D.getNode(), 1, "Sólido de revolución · " + volumen,
                    datos, null, null, solidLegend()), folder.resolve("solido_3d.png"));
            ImageExporter.savePng(framedChart(accumCanvas, PNG_SCALE, "Volumen acumulado V_acum(x)",
                    datos + "  ·  " + volumen, "x", "V_acum(x)  [u³]", null), folder.resolve("volumen_acumulado.png"));
        } finally {
            refreshSliceViews();
        }
    }

    private FlowPane solidLegend() {
        FlowPane legend = new FlowPane(18, 6, legendItemsFor(eje).toArray(Node[]::new));
        legend.setAlignment(Pos.CENTER);
        legend.setPrefWrapLength(860); // the 3D view is 900 px wide
        return legend;
    }

    /** The legend under the 3D view: the axis of rotation is the one drawn in orange. */
    private void updateAxisLegend() {
        axisLegend.getChildren().setAll(legendItemsFor(eje));
    }

    private List<Node> legendItemsFor(Eje e) {
        boolean ejeX = e == Eje.X;
        List<Node> items = new ArrayList<>(List.of(
                axisLegendItem(ejeX ? "Eje x = eje de rotación" : "Eje x",
                        ejeX ? Solid3DView.COLOR_AXIS : Solid3DView.COLOR_AXIS_X),
                axisLegendItem(ejeX ? "Eje y" : "Eje y = eje de rotación",
                        ejeX ? Solid3DView.COLOR_AXIS_Y : Solid3DView.COLOR_AXIS),
                axisLegendItem("Límites a y b", Solid3DView.COLOR_LIMIT),
                axisLegendItem("Región R (la que gira)", Solid3DView.COLOR_REGION),
                axisLegendItem("Superficie de f(x)", Solid3DView.COLOR_F)));
        // Disks have no inner surface: g(x) = 0 sweeps nothing.
        if (metodo != Metodo.DISCOS) items.add(axisLegendItem("Superficie de g(x)", Solid3DView.COLOR_G));
        return items;
    }

    /**
     * Snapshots {@code chart} at {@code scale}x and lays it out with a title, subtitle,
     * optional axis names and an optional footer, all sized to that same scale.
     * The 3D view must use scale 1: a scale transform on a SubScene snapshot also
     * stretches its perspective camera and distorts the solid.
     */
    private WritableImage framedChart(Node chart, double scale, String title, String subtitle,
                                      String xLabel, String yLabel, Node footer) {
        SnapshotParameters params = new SnapshotParameters();
        params.setTransform(new Scale(scale, scale));
        params.setFill(Color.WHITE);
        WritableImage plot = chart.snapshot(params, null);

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 15 * scale));
        titleLabel.setTextFill(Color.web("#0f172a"));
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setFont(Font.font(11 * scale));
        subtitleLabel.setTextFill(Color.web("#475569"));
        subtitleLabel.setWrapText(true);
        subtitleLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        subtitleLabel.setMaxWidth(plot.getWidth());
        VBox header = new VBox(3 * scale, titleLabel, subtitleLabel);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(0, 0, 8 * scale, 0));

        BorderPane frame = new BorderPane(new ImageView(plot));
        frame.setTop(header);
        if (yLabel != null) {
            Label y = axisName(yLabel, scale);
            y.setRotate(-90);
            StackPane left = new StackPane(new Group(y));
            left.setPadding(new Insets(0, 4 * scale, 0, 0));
            frame.setLeft(left);
        }
        VBox bottom = new VBox(6 * scale);
        bottom.setAlignment(Pos.CENTER);
        if (xLabel != null) bottom.getChildren().add(axisName(xLabel, scale));
        if (footer != null) {
            footer.setScaleX(scale);
            footer.setScaleY(scale);
            bottom.getChildren().add(new Group(footer));
        }
        if (!bottom.getChildren().isEmpty()) frame.setBottom(bottom);
        frame.setPadding(new Insets(16 * scale, 22 * scale, 14 * scale, 14 * scale));
        frame.setStyle("-fx-background-color: white;");

        Scene offscreen = new Scene(frame);
        offscreen.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        frame.applyCss();
        frame.layout();
        SnapshotParameters flat = new SnapshotParameters();
        flat.setFill(Color.WHITE);
        return frame.snapshot(flat, null);
    }

    private static Label axisName(String text, double scale) {
        Label l = new Label(text);
        l.setFont(Font.font("System", FontWeight.BOLD, 12 * scale));
        l.setTextFill(Color.web("#334155"));
        return l;
    }
}

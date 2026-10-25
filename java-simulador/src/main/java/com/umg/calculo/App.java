package com.umg.calculo;

import com.umg.calculo.ui.MainView;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Entry point: Emulador de Volumenes de Solidos de Revolucion - Calculo II (UMG). */
public class App extends Application {

    @Override
    public void start(Stage stage) {
        MainView mainView = new MainView();
        mainView.setStage(stage);
        stage.setScene(mainView.createScene());
        stage.setTitle("Emulador de Sólidos de Revolución — Cálculo II");
        // The layout adapts down to ~800 px wide, so the minimum only stops the
        // window from being dragged uselessly small (and never exceeds the screen).
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        stage.setMinWidth(Math.min(800, screen.getWidth()));
        stage.setMinHeight(Math.min(560, screen.getHeight()));
        // On a screen smaller than the full design size (laptops), use all of it.
        if (screen.getWidth() < 1440 || screen.getHeight() < 900) stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

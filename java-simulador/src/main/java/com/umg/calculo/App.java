package com.umg.calculo;

import com.umg.calculo.ui.MainView;
import javafx.application.Application;
import javafx.stage.Stage;

/** Entry point: Emulador de Volumenes de Solidos de Revolucion - Calculo II (UMG). */
public class App extends Application {

    @Override
    public void start(Stage stage) {
        MainView mainView = new MainView();
        mainView.setStage(stage);
        stage.setScene(mainView.createScene());
        stage.setTitle("Emulador de Sólidos de Revolución — Cálculo II");
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package com.umg.calculo;

/**
 * Entry point for the shaded jar (java -jar ...shaded.jar). Does NOT extend
 * javafx.application.Application: since Java 11, the java launcher refuses
 * to start a Main-Class that extends Application unless JavaFX is on the
 * module-path, even if the classes are already on the classpath inside a
 * fat jar. Routing through a plain class sidesteps that check.
 */
public final class Launcher {
    public static void main(String[] args) {
        App.main(args);
    }
}

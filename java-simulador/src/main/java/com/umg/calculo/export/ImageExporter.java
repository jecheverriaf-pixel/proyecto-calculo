package com.umg.calculo.export;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritablePixelFormat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Saves a JavaFX image as PNG (Section 17: graphs delivered as PNG). Copies the
 * pixels into an AWT BufferedImage so it doesn't need the extra javafx-swing module.
 */
public final class ImageExporter {

    private ImageExporter() {}

    public static void savePng(Image image, Path file) throws IOException {
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        int[] argb = new int[w * h];
        PixelReader reader = image.getPixelReader();
        reader.getPixels(0, 0, w, h, WritablePixelFormat.getIntArgbInstance(), argb, 0, w);

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, argb, 0, w);
        if (!ImageIO.write(out, "png", file.toFile())) {
            throw new IOException("No hay un escritor PNG disponible.");
        }
    }
}

package com.example.docchecker.pd.agent;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import javax.imageio.ImageIO;

/** Дешёвая проверка изображения до обращения к модели: крошечные и пустые картинки не стоит отправлять. */
public final class ImageStats {

    public record Stats(int width, int height, double entropy, boolean tiny, boolean blank) {}

    private ImageStats() {}

    public static Stats analyze(String base64Jpeg, int minSide) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64Jpeg)));
        if (img == null) {
            throw new IOException("не удалось декодировать изображение");
        }
        int w = img.getWidth();
        int h = img.getHeight();
        int step = Math.max(1, Math.max(w, h) / 160);
        int[] hist = new int[256];
        int total = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int rgb = img.getRGB(x, y);
                int lum = (299 * ((rgb >> 16) & 0xFF) + 587 * ((rgb >> 8) & 0xFF) + 114 * (rgb & 0xFF)) / 1000;
                hist[lum]++;
                total++;
            }
        }
        double entropy = 0;
        for (int c : hist) {
            if (c > 0) {
                double p = (double) c / total;
                entropy -= p * Math.log(p) / Math.log(2);
            }
        }
        return new Stats(w, h, entropy, Math.min(w, h) < minSide, entropy < 0.5);
    }
}

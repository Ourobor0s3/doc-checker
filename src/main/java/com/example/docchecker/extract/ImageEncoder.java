package com.example.docchecker.extract;

import com.example.docchecker.common.BadDocumentException;
import com.example.docchecker.config.AppProperties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/** Уменьшает изображение, убирает альфа-канал и кодирует в JPEG/base64 для отправки в Ollama. */
@Component
public class ImageEncoder {

    private static final long MAX_PIXELS = 60_000_000L;

    private final int maxSide;

    public ImageEncoder(AppProperties props) {
        this.maxSide = props.extract().maxImageSide();
    }

    public String encode(byte[] raw) {
        return encode(read(raw));
    }

    /** Для встроенных картинок DOCX: пропускает нечитаемые и слишком маленькие (иконки, линии). */
    public Optional<String> tryEncode(byte[] raw, int minSide) {
        try {
            BufferedImage img = read(raw);
            if (img.getWidth() < minSide || img.getHeight() < minSide) {
                return Optional.empty();
            }
            return Optional.of(encode(img));
        } catch (BadDocumentException e) {
            return Optional.empty();
        }
    }

    public String encode(BufferedImage src) {
        double scale = Math.min(1.0, (double) maxSide / Math.max(src.getWidth(), src.getHeight()));
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));

        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(rgb, "jpg", out)) {
                throw new BadDocumentException("Не удалось закодировать изображение");
            }
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new BadDocumentException("Не удалось закодировать изображение: " + e.getMessage());
        }
    }

    private BufferedImage read(byte[] raw) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(raw))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new BadDocumentException("Не удалось прочитать изображение: неизвестный формат");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new BadDocumentException("Изображение слишком большое по разрешению");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BadDocumentException("Не удалось прочитать изображение: " + e.getMessage());
        }
    }
}

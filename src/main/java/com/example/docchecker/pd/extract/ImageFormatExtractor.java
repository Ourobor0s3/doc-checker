package com.example.docchecker.pd.extract;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import com.example.docchecker.config.AppProperties;
import com.example.docchecker.extract.ImageEncoder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/** PNG, JPEG, GIF, TIFF, BMP: кадры/страницы отправляются в vision-модель, из EXIF берутся автор и геопозиция. */
@Component
public class ImageFormatExtractor implements FormatExtractor {

    private static final long MAX_PIXELS = 60_000_000L;

    private final ImageEncoder images;
    private final int maxFrames;

    public ImageFormatExtractor(ImageEncoder images, AppProperties props) {
        this.images = images;
        this.maxFrames = Math.max(1, props.pd().maxFrames());
    }

    @Override
    public Set<FileFormat> formats() {
        return Set.of(FileFormat.PNG, FileFormat.JPEG, FileFormat.GIF, FileFormat.TIFF, FileFormat.BMP);
    }

    @Override
    public void extract(byte[] data, String fileName, ExtractionResult out) throws Exception {
        exif(data, out);
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new ExtractException("Не удалось определить формат изображения (возможно, нестандартное сжатие TIFF)");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, false, true);
                int total;
                try {
                    total = Math.max(1, reader.getNumImages(true));
                } catch (IOException | RuntimeException e) {
                    total = 1;
                }
                int limit = Math.min(total, maxFrames);
                if (total > limit) {
                    out.partial("В файле " + total + " кадров/страниц, проанализированы первые " + limit);
                }
                String unit = fileName != null && fileName.toLowerCase(Locale.ROOT).matches(".*\\.tiff?$") ? "страница " : "кадр ";
                for (int k = 0; k < limit; k++) {
                    if ((long) reader.getWidth(k) * reader.getHeight(k) > MAX_PIXELS) {
                        out.partial("Слишком большое разрешение: " + unit + (k + 1) + " пропущен(а)");
                        continue;
                    }
                    BufferedImage img = reader.read(k);
                    out.image(total == 1 ? "изображение" : unit + (k + 1), images.encode(img));
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private static void exif(byte[] data, ExtractionResult out) {
        try {
            Metadata md = ImageMetadataReader.readMetadata(new ByteArrayInputStream(data));
            ExifIFD0Directory ifd0 = md.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null) {
                out.meta("exif.artist", ifd0.getString(ExifIFD0Directory.TAG_ARTIST));
                out.meta("exif.copyright", ifd0.getString(ExifIFD0Directory.TAG_COPYRIGHT));
            }
            GpsDirectory gps = md.getFirstDirectoryOfType(GpsDirectory.class);
            if (gps != null) {
                GeoLocation g = gps.getGeoLocation();
                if (g != null && !g.isZero()) {
                    out.meta("gps", g.getLatitude() + "," + g.getLongitude());
                }
            }
        } catch (Exception e) {
            // метаданные необязательны
        }
    }
}

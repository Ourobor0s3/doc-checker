package com.example.docchecker.extract;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ImageExtractor {

    private final ImageEncoder images;

    public ImageExtractor(ImageEncoder images) {
        this.images = images;
    }

    public ExtractedContent extract(byte[] data) {
        return new ExtractedContent(List.of(ContentChunk.ofImage("изображение", images.encode(data))), List.of());
    }
}

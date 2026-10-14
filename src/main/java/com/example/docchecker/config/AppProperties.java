package com.example.docchecker.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Все настройки приложения (префикс {@code app.*}). */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue Storage storage,
        @DefaultValue Ollama ollama,
        @DefaultValue Extract extract,
        @DefaultValue Check check,
        @DefaultValue Pd pd) {

    public record Storage(@DefaultValue("redis") String type) {
        public boolean isRedis() {
            return "redis".equalsIgnoreCase(type);
        }
    }

    public record Ollama(
            @DefaultValue("http://localhost:11434") String baseUrl,
            @DefaultValue("gemma3:4b") String textModel,
            @DefaultValue("gemma3:4b") String visionModel,
            Boolean think,
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("5m") Duration readTimeout,
            @DefaultValue("8192") int numCtx,
            @DefaultValue("0") double temperature,
            @DefaultValue("10m") String keepAlive) {}

    public record Extract(
            @DefaultValue("6000") int maxCharsPerChunk,
            @DefaultValue("60000") int maxTotalChars,
            @DefaultValue("10") int maxScannedPages,
            @DefaultValue("130") int pdfRenderDpi,
            @DefaultValue("40") int minTextCharsPerPage,
            @DefaultValue("1280") int maxImageSide,
            @DefaultValue("true") boolean docxImagesEnabled,
            @DefaultValue("3") int maxDocxImages) {}

    public record Check(
            @DefaultValue("1") int workers,
            @DefaultValue("20") int queueCapacity,
            @DefaultValue("24h") Duration resultTtl,
            @DefaultValue("100") int maxRequirements) {}

    /** Настройки модуля поиска персональных данных. */
    public record Pd(
            @DefaultValue("true") boolean maskValues,
            @DefaultValue("0.6") double yesThreshold,
            @DefaultValue("0.4") double minConfidence,
            @DefaultValue("true") boolean llmEnabled,
            @DefaultValue("true") boolean visionEnabled,
            @DefaultValue("3000") int llmChunkChars,
            @DefaultValue("30000") int llmMaxCharsPerFile,
            @DefaultValue("10") int maxImagesPerFile,
            @DefaultValue("5") int maxFrames,
            @DefaultValue("1000000") int maxUnits,
            @DefaultValue("500") int maxFindingsPerFile,
            @DefaultValue("5000") int maxArchiveEntries,
            @DefaultValue("1073741824") long maxArchiveTotalBytes,
            @DefaultValue("104857600") long maxEntryBytes,
            @DefaultValue("3") int maxArchiveDepth,
            @DefaultValue("24h") Duration resultTtl,
            @DefaultValue("1") int workers,
            @DefaultValue("5") int queueCapacity,
            @DefaultValue("") String legalBasisFile) {}
}

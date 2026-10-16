package com.example.docchecker.pd.agent;

import com.example.docchecker.config.AppProperties;
import com.example.docchecker.llm.LlmClient;
import com.example.docchecker.llm.LlmResponseFormatException;
import com.example.docchecker.llm.OllamaException;
import com.example.docchecker.llm.OllamaTimeoutException;
import com.example.docchecker.pd.agent.PdAnswers.OcrAnswer;
import com.example.docchecker.pd.agent.PdAnswers.PhotoCheckAnswer;
import com.example.docchecker.pd.agent.PdAnswers.SceneAnswer;
import com.example.docchecker.pd.extract.ExtractionResult;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Анализ одного изображения vision-моделью в несколько шагов, чтобы снизить число галлюцинаций:
 * 1) отсев крошечных и пустых картинок без обращения к модели;
 * 2) классификация («что это: фото человека, рисунок, логотип, документ…») с перечислением видимых черт лица;
 * 3) независимая проверка «это реальная фотография человека?» для кандидатов на лицо;
 * 4) чтение текста только там, где он, по словам модели, есть.
 */
@Component
public class ImageInspector {

    public record Inspection(boolean skipped, String skipReason, String description, FaceRules.Kind kind, int faces,
                             double faceConfidence, String faceDetector, String faceNote, String transcript,
                             String ocrError, boolean unreachable) {

        static Inspection skipped(String reason) {
            return new Inspection(true, reason, "", FaceRules.Kind.OTHER, 0, 0, "", null, "", null, false);
        }
    }

    private final LlmClient llm;
    private final AppProperties props;

    public ImageInspector(LlmClient llm, AppProperties props) {
        this.llm = llm;
        this.props = props;
    }

    /**
     * @throws OllamaTimeoutException       модель не ответила вовремя на классификацию
     * @throws LlmResponseFormatException   модель дала ответ в неверном формате
     * @throws OllamaException              Ollama недоступна
     */
    public Inspection inspect(ExtractionResult.ImageUnit img) {
        try {
            ImageStats.Stats st = ImageStats.analyze(img.base64Jpeg(), props.pd().minImageSide());
            if (st.tiny()) {
                return Inspection.skipped("слишком маленькое изображение (" + st.width() + "×" + st.height() + " px)");
            }
            if (st.blank()) {
                return Inspection.skipped("пустое изображение");
            }
        } catch (IOException | RuntimeException e) {
            // не удалось оценить картинку заранее — отправляем в модель как есть
        }

        String model = props.ollama().visionModel();
        SceneAnswer scene = llm.chatJson(model, PdPrompts.SCENE_SYSTEM, "Классифицируй изображение.",
                List.of(img.base64Jpeg()), PdPrompts.sceneSchema(), SceneAnswer.class);
        if (scene == null) {
            scene = new SceneAnswer("", "OTHER", 0, "", false);
        }
        FaceRules.Kind kind = FaceRules.kind(scene.imageKind());
        int faces = scene.facesCount() == null ? 0 : Math.max(0, scene.facesCount());
        FaceRules.Decision decision = FaceRules.decide(scene.imageKind(), faces, scene.faceEvidence());

        double confidence = decision.confidence();
        String note = decision.note();
        String detector = "VLM-SCENE";
        if (confidence >= props.pd().yesThreshold() && props.pd().verifyFaces()) {
            try {
                PhotoCheckAnswer check = llm.chatJson(model, PdPrompts.PHOTO_CHECK_SYSTEM, "Это реальная фотография человека?",
                        List.of(img.base64Jpeg()), PdPrompts.photoCheckSchema(), PhotoCheckAnswer.class);
                String verdict = check == null || check.verdict() == null ? "UNSURE" : check.verdict().strip().toUpperCase(Locale.ROOT);
                String reason = check == null || check.reason() == null ? "" : check.reason().strip();
                switch (verdict) {
                    case "REAL_PHOTO":
                        confidence = Math.min(0.85, confidence + 0.05);
                        detector = "VLM-SCENE+CHECK";
                        break;
                    case "NOT_PHOTO":
                        confidence = 0;
                        note = "Повторная проверка не подтвердила фотографию человека" + (reason.isEmpty() ? "" : " (" + reason + ")");
                        break;
                    default:
                        confidence = Math.min(confidence, 0.5);
                        note = "Повторная проверка не уверена, что это фотография человека: признак слабый";
                }
            } catch (LlmResponseFormatException e) {
                confidence = Math.min(confidence, 0.5);
                note = "Проверка лица не удалась (некорректный ответ модели): признак слабый";
            }
        }

        String transcript = "";
        String ocrError = null;
        boolean unreachable = false;
        if (FaceRules.needsOcr(kind, scene.hasReadableText())) {
            try {
                OcrAnswer ocr = llm.chatJson(model, PdPrompts.OCR_SYSTEM, "Перепиши текст изображения.",
                        List.of(img.base64Jpeg()), PdPrompts.ocrSchema(), OcrAnswer.class);
                transcript = ocr == null || ocr.transcribedText() == null ? "" : ocr.transcribedText().strip();
            } catch (OllamaTimeoutException e) {
                ocrError = "модель не успела прочитать текст изображения";
            } catch (LlmResponseFormatException e) {
                ocrError = "модель вернула некорректный ответ при чтении текста";
            } catch (OllamaException e) {
                ocrError = e.getMessage();
                unreachable = true;
            }
        }
        String description = scene.description() == null ? "" : scene.description().strip();
        if (description.length() > 200) {
            description = description.substring(0, 200) + "…";
        }
        return new Inspection(false, null, description, kind, faces, confidence, detector, note, transcript,
                ocrError, unreachable);
    }

    /** Второе, независимое чтение текста (другая формулировка запроса) для проверки найденных значений. */
    public String reread(ExtractionResult.ImageUnit img) {
        OcrAnswer a = llm.chatJson(props.ollama().visionModel(), PdPrompts.OCR_ALT_SYSTEM, "Выпиши текст изображения.",
                List.of(img.base64Jpeg()), PdPrompts.ocrSchema(), OcrAnswer.class);
        return a == null || a.transcribedText() == null ? "" : a.transcribedText().strip();
    }
}

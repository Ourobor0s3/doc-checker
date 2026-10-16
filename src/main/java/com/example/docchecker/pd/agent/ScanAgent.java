package com.example.docchecker.pd.agent;

import com.example.docchecker.config.AppProperties;
import com.example.docchecker.llm.LlmClient;
import com.example.docchecker.llm.LlmResponseFormatException;
import com.example.docchecker.llm.OllamaException;
import com.example.docchecker.llm.OllamaTimeoutException;
import com.example.docchecker.pd.agent.PdAnswers.EntityAnswer;
import com.example.docchecker.pd.agent.PdAnswers.EntityItem;
import com.example.docchecker.pd.detect.Masker;
import com.example.docchecker.pd.detect.RawMatch;
import com.example.docchecker.pd.detect.RuleEngine;
import com.example.docchecker.pd.extract.ExtractException;
import com.example.docchecker.pd.extract.ExtractionResult;
import com.example.docchecker.pd.extract.ExtractorRegistry;
import com.example.docchecker.pd.extract.FileFormat;
import com.example.docchecker.pd.extract.FormatDetector;
import com.example.docchecker.pd.extract.FormatExtractor;
import com.example.docchecker.pd.legal.LegalReference;
import com.example.docchecker.pd.model.FileReport;
import com.example.docchecker.pd.model.FileStatus;
import com.example.docchecker.pd.model.FileVerdict;
import com.example.docchecker.pd.model.Finding;
import com.example.docchecker.pd.model.PdType;
import com.example.docchecker.pd.model.TextKind;
import com.example.docchecker.pd.model.TextUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Агент проверки одного файла. Планирует шаги по формату и результатам предыдущих шагов:
 * определение формата → извлечение содержимого → детерминированные правила → LLM-поиск в тексте →
 * vision-анализ изображений (текст и лица) → объединение, маскирование, правовая привязка, вердикт.
 * Каждый шаг фиксируется в журнале {@code trace}.
 */
@Component
public class ScanAgent {

    private static final Logger log = LoggerFactory.getLogger(ScanAgent.class);
    private static final double OCR_CAP = 0.85;

    record Cand(PdType type, String value, String locator, double confidence, String detector) {}

    /** Состояние обработки одного файла. */
    private static final class Run {
        final List<Cand> cands = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        final List<String> trace = new ArrayList<>();
        boolean partial;
        boolean llmDown;
        int llmBadAnswers;
        String llmFirstBadAnswer;
        int llmCharsUsed;
        boolean llmTruncated;
    }

    private final FormatDetector detector;
    private final ExtractorRegistry registry;
    private final LlmClient llm;
    private final AppProperties props;
    private final LegalReference legal;
    private final RuleEngine rules;
    private final ImageInspector inspector;

    public ScanAgent(FormatDetector detector, ExtractorRegistry registry, LlmClient llm, AppProperties props,
                     LegalReference legal, RuleEngine rules, ImageInspector inspector) {
        this.inspector = inspector;
        this.detector = detector;
        this.registry = registry;
        this.llm = llm;
        this.props = props;
        this.legal = legal;
        this.rules = rules;
    }

    public FileReport scan(String path, byte[] data, boolean useLlm) {
        long t0 = System.nanoTime();
        Run run = new Run();
        FileFormat format = FileFormat.UNSUPPORTED;
        try {
            format = detector.detect(data, baseName(path));
            run.trace.add("Формат определён по содержимому: " + format);

            if (format == FileFormat.EMPTY) {
                run.notes.add("Файл пустой");
                return finish(path, format, data.length, FileStatus.OK, run, t0);
            }
            FormatExtractor extractor = registry.get(format);
            if (extractor == null) {
                run.notes.add(format == FileFormat.ZIP
                        ? "Вложенный архив не распакован (превышена допустимая вложенность)"
                        : "Формат файла не поддерживается");
                return finish(path, format, data.length, FileStatus.UNSUPPORTED, run, t0);
            }

            ExtractionResult er = new ExtractionResult(props.pd().maxUnits(), props.pd().maxImagesPerFile());
            extractor.extract(data, baseName(path), er);
            run.trace.add("Извлечено: фрагментов — " + er.units.size() + ", изображений — " + er.images.size());
            run.notes.addAll(er.notes);
            run.partial |= er.partial;

            ruleStep(er.units, 1.0, "", run);
            run.trace.add("Правила и контрольные суммы: найдено признаков — " + run.cands.size());

            boolean llmOn = useLlm && props.pd().llmEnabled();
            if (llmOn) {
                int before = run.cands.size();
                llmTextStep(er.units, 1.0, run);
                run.trace.add("LLM-поиск в тексте: новых признаков — " + (run.cands.size() - before));
            } else if (useLlm) {
                run.notes.add("LLM-поиск отключён настройкой app.pd.llm-enabled");
            }

            if (!er.images.isEmpty()) {
                visionStep(er, useLlm && props.pd().visionEnabled(), llmOn, run);
            }
            if (run.llmBadAnswers > 0) {
                run.notes.add("LLM не обработала " + run.llmBadAnswers + " фрагмент(ов) текста (" + run.llmFirstBadAnswer
                        + "): они проверены только правилами");
            }
            if (run.llmTruncated) {
                run.notes.add("LLM-поиск выполнен для первых " + props.pd().llmMaxCharsPerFile() + " символов текста");
            }
            return finish(path, format, data.length, run.partial ? FileStatus.PARTIAL : FileStatus.OK, run, t0);
        } catch (ExtractException e) {
            run.notes.add(e.getMessage());
            return finish(path, format, data.length, FileStatus.ERROR, run, t0);
        } catch (Exception e) {
            log.warn("Не удалось разобрать {}: {}", path, e.toString());
            run.notes.add("Не удалось разобрать файл: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return finish(path, format, data.length, FileStatus.ERROR, run, t0);
        }
    }

    // ---------------------------------------------------------------- шаги

    private void ruleStep(List<TextUnit> units, double cap, String detectorSuffix, Run run) {
        for (TextUnit u : units) {
            for (RawMatch m : rules.scan(u)) {
                run.cands.add(new Cand(m.type(), m.value(), u.locator(),
                        Math.min(m.confidence(), cap), m.detector() + detectorSuffix));
            }
        }
    }

    /** LLM ищет то, что трудно найти шаблонами. Цитата модели принимается, только если она найдена в исходном тексте. */
    private void llmTextStep(List<TextUnit> units, double cap, Run run) {
        List<TextUnit> eligible = new ArrayList<>();
        for (TextUnit u : units) {
            if (u.kind() == TextKind.TEXT || u.kind() == TextKind.FIELD) {
                eligible.addAll(split(u, props.pd().llmChunkChars()));
            }
        }
        List<TextUnit> chunk = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (TextUnit u : eligible) {
            String line = line(u);
            if (line.length() < 3) {
                continue;
            }
            if (run.llmCharsUsed + line.length() > props.pd().llmMaxCharsPerFile()) {
                run.llmTruncated = true;
                break;
            }
            if (sb.length() > 0 && sb.length() + line.length() + 1 > props.pd().llmChunkChars()) {
                llmChunk(chunk, sb.toString(), cap, run);
                chunk = new ArrayList<>();
                sb.setLength(0);
            }
            if (run.llmDown) {
                return;
            }
            sb.append(line).append('\n');
            chunk.add(u);
            run.llmCharsUsed += line.length();
        }
        if (!chunk.isEmpty() && !run.llmDown) {
            llmChunk(chunk, sb.toString(), cap, run);
        }
    }

    private void llmChunk(List<TextUnit> chunk, String text, double cap, Run run) {
        if (run.llmDown) {
            return;
        }
        try {
            EntityAnswer answer = llm.chatJson(props.ollama().textModel(), PdPrompts.ENTITY_SYSTEM,
                    PdPrompts.entityUser(text), List.of(), PdPrompts.entitySchema(), EntityAnswer.class);
            if (answer == null || answer.findings() == null) {
                return;
            }
            for (EntityItem item : answer.findings()) {
                PdType type = parseType(item.type());
                String quote = item.text() == null ? "" : item.text().strip();
                double confidence = type == null ? 0 : EntityRules.confidence(type, quote);
                if (confidence <= 0) {
                    continue;
                }
                for (TextUnit u : chunk) {
                    if (line(u).contains(quote)) {
                        run.cands.add(new Cand(type, quote, u.locator(), Math.min(confidence, cap), "LLM"));
                        break;
                    }
                }
            }
        } catch (OllamaTimeoutException e) {
            run.llmBadAnswers++;
            if (run.llmFirstBadAnswer == null) {
                run.llmFirstBadAnswer = e.getMessage();
            }
        } catch (LlmResponseFormatException e) {
            run.llmBadAnswers++;
            if (run.llmFirstBadAnswer == null) {
                run.llmFirstBadAnswer = e.getMessage();
            }
        } catch (OllamaException e) {
            run.llmDown = true;
            run.notes.add("LLM недоступна, поиск в тексте выполнен только правилами: " + e.getMessage());
        }
    }

    /**
     * Анализ изображений. Лицо засчитывается только после классификации и независимой проверки, текст читается
     * только там, где он есть, а значения, прочитанные с изображения, подтверждаются вторым чтением.
     */
    private void visionStep(ExtractionResult er, boolean visionOn, boolean llmTextOn, Run run) {
        if (!visionOn) {
            run.partial = true;
            run.notes.add("Изображения не проанализированы: vision-модель отключена (" + er.images.size() + " шт.)");
            return;
        }
        int analyzed = 0;
        int skippedByFilter = 0;
        int failed = 0;
        int consecutiveTimeouts = 0;
        boolean down = false;
        for (ExtractionResult.ImageUnit img : er.images) {
            if (down) {
                failed++;
                continue;
            }
            try {
                ImageInspector.Inspection ins = inspector.inspect(img);
                consecutiveTimeouts = 0;
                if (ins.skipped()) {
                    skippedByFilter++;
                    run.trace.add("Изображение «" + img.locator() + "» пропущено: " + ins.skipReason());
                    continue;
                }
                analyzed++;
                run.trace.add("Изображение «" + img.locator() + "»: " + (ins.description().isEmpty() ? "без описания" : ins.description())
                        + " (тип " + ins.kind() + ", лиц: " + ins.faces() + ")");
                if (ins.faceConfidence() > 0) {
                    run.cands.add(new Cand(PdType.FACE_PHOTO, "(лицо на изображении)", img.locator(),
                            ins.faceConfidence(), ins.faceDetector()));
                }
                if (ins.faceNote() != null) {
                    run.notes.add(img.locator() + ": " + ins.faceNote());
                }
                if (ins.ocrError() != null) {
                    failed++;
                    run.notes.add(img.locator() + ": " + ins.ocrError());
                    down = ins.unreachable();
                }
                if (ins.transcript().length() >= 3) {
                    textFromImage(img, ins.transcript(), llmTextOn, run);
                }
            } catch (OllamaTimeoutException e) {
                failed++;
                if (++consecutiveTimeouts >= 2) {
                    down = true;
                    run.notes.add("Модель дважды подряд не ответила вовремя: остальные изображения файла не проанализированы");
                } else {
                    run.notes.add(img.locator() + ": " + e.getMessage());
                }
            } catch (LlmResponseFormatException e) {
                failed++;
                run.notes.add(img.locator() + ": модель вернула ответ в неверном формате");
            } catch (OllamaException e) {
                down = true;
                failed++;
                run.notes.add("Vision-модель недоступна: " + e.getMessage());
            }
        }
        run.trace.add("Vision-анализ изображений: проанализировано — " + analyzed + ", пропущено фильтром — "
                + skippedByFilter + ", не удалось — " + failed);
        if (failed > 0) {
            run.partial = true;
            run.notes.add("Не проанализировано изображений: " + failed);
        }
    }

    private void textFromImage(ExtractionResult.ImageUnit img, String transcript, boolean llmTextOn, Run run) {
        List<TextUnit> units = List.of(new TextUnit(img.locator() + " → распознанный текст", transcript, null, TextKind.TEXT));
        int from = run.cands.size();
        ruleStep(units, OCR_CAP, "+VLM-OCR", run);
        if (llmTextOn) {
            llmTextStep(units, OCR_CAP, run);
        }
        verifyOcr(img, from, run);
    }

    /** Значения с изображения подтверждаются независимым вторым чтением; не подтверждённые становятся слабыми. */
    private void verifyOcr(ExtractionResult.ImageUnit img, int from, Run run) {
        if (!props.pd().verifyOcr()) {
            return;
        }
        boolean strong = false;
        for (int i = from; i < run.cands.size(); i++) {
            if (run.cands.get(i).confidence() >= props.pd().yesThreshold()) {
                strong = true;
            }
        }
        if (!strong) {
            return;
        }
        String second;
        try {
            second = inspector.reread(img);
        } catch (OllamaException e) {
            run.notes.add(img.locator() + ": повторное чтение не удалось, значения с изображения не подтверждены");
            return;
        }
        boolean unconfirmed = false;
        for (int i = from; i < run.cands.size(); i++) {
            Cand c = run.cands.get(i);
            if (OcrCompare.confirmed(c.type(), c.value(), second)) {
                run.cands.set(i, new Cand(c.type(), c.value(), c.locator(), Math.min(0.9, c.confidence() + 0.05),
                        c.detector() + "+OCR2"));
            } else {
                unconfirmed = true;
                run.cands.set(i, new Cand(c.type(), c.value(), c.locator(), c.confidence() * 0.6, c.detector() + "+UNCONFIRMED"));
            }
        }
        if (unconfirmed) {
            run.notes.add(img.locator() + ": часть значений не подтвердилась повторным чтением изображения и помечена как слабые признаки");
        }
    }

    // ---------------------------------------------------------------- итог

    private FileReport finish(String path, FileFormat format, long size, FileStatus status, Run run, long t0) {
        List<Cand> kept = merge(run.cands);
        double yes = props.pd().yesThreshold();
        double max = kept.stream().mapToDouble(Cand::confidence).max().orElse(0);

        FileVerdict verdict;
        if (max >= yes) {
            verdict = FileVerdict.YES;
        } else if (!kept.isEmpty()) {
            verdict = FileVerdict.UNDETERMINED;
            run.notes.add("Найдены только слабые признаки — нужна ручная проверка");
        } else {
            verdict = status == FileStatus.OK ? FileVerdict.NO : FileVerdict.UNDETERMINED;
        }

        Map<String, Integer> counts = new TreeMap<>();
        List<Finding> findings = new ArrayList<>();
        int cap = props.pd().maxFindingsPerFile();
        for (Cand c : kept) {
            counts.merge(c.type().name(), 1, Integer::sum);
            if (findings.size() < cap) {
                findings.add(new Finding(c.type(), legal.label(c.type()), shown(c), path, c.locator(),
                        c.detector(), round(c.confidence()), c.confidence() < yes, legal.basis(c.type())));
            }
        }
        if (kept.size() > cap) {
            run.notes.add("Показаны первые " + cap + " из " + kept.size() + " находок; счётчики по типам полные");
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        return new FileReport(path, format.name(), size, status, verdict, round(max), kept.size(), counts,
                findings, List.copyOf(run.notes), List.copyOf(run.trace), ms);
    }

    /** Объединяет одинаковые находки из разных источников; совпадение двух методов повышает уверенность. */
    private List<Cand> merge(List<Cand> all) {
        Map<String, Cand> map = new LinkedHashMap<>();
        for (Cand c : all) {
            if (c.confidence() < props.pd().minConfidence()) {
                continue;
            }
            String key = c.type() + "|" + c.value().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT) + "|" + c.locator();
            Cand prev = map.get(key);
            if (prev == null) {
                map.put(key, c);
            } else {
                boolean sameDetector = prev.detector().equals(c.detector());
                double conf = Math.max(prev.confidence(), c.confidence());
                if (!sameDetector) {
                    conf = Math.min(0.99, conf + 0.05);
                }
                String det = sameDetector || prev.detector().contains(c.detector())
                        ? prev.detector() : prev.detector() + "+" + c.detector();
                map.put(key, new Cand(prev.type(), prev.value(), prev.locator(), conf, det));
            }
        }
        return new ArrayList<>(map.values());
    }

    private String shown(Cand c) {
        return props.pd().maskValues() ? Masker.mask(c.type(), c.value()) : c.value();
    }

    // ---------------------------------------------------------------- утилиты

    private static String line(TextUnit u) {
        return u.kind() == TextKind.FIELD && u.hint() != null && !u.hint().isBlank()
                ? u.hint() + ": " + u.text() : u.text();
    }

    private static List<TextUnit> split(TextUnit u, int max) {
        if (u.text().length() <= max) {
            return List.of(u);
        }
        List<TextUnit> parts = new ArrayList<>();
        for (int i = 0; i < u.text().length(); i += max) {
            parts.add(new TextUnit(u.locator(), u.text().substring(i, Math.min(u.text().length(), i + max)), u.hint(), u.kind()));
        }
        return parts;
    }

    private static PdType parseType(String s) {
        if (s == null) {
            return null;
        }
        try {
            PdType t = PdType.valueOf(s.strip().toUpperCase(Locale.ROOT));
            return PdPrompts.LLM_TYPES.contains(t) ? t : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String baseName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}

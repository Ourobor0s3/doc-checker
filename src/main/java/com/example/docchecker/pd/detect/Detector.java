package com.example.docchecker.pd.detect;

import java.util.List;

public interface Detector {
    /**
     * @param s          анализируемая строка: {@code "<название поля>: <значение>"} или просто текст
     * @param valueStart индекс, с которого начинается само значение (до него — название поля)
     * @param hint       название поля/столбца или {@code null}
     */
    void detect(String s, int valueStart, String hint, List<RawMatch> out);
}

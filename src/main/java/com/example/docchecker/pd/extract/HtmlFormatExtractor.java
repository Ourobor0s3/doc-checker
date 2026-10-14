package com.example.docchecker.pd.extract;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

/** HTML: видимый текст по блокам, mailto/tel-ссылки, значения полей форм, атрибуты alt/title, мета-теги автора. */
@Component
public class HtmlFormatExtractor implements FormatExtractor {

    private static final Set<String> BLOCK = Set.of("p", "div", "li", "td", "th", "tr", "table", "ul", "ol",
            "h1", "h2", "h3", "h4", "h5", "h6", "section", "article", "header", "footer", "aside", "nav", "main",
            "blockquote", "pre", "dd", "dt", "dl", "form", "fieldset", "figure", "figcaption", "body", "html",
            "address", "caption", "tbody", "thead", "tfoot", "title", "option", "textarea", "button", "label");
    private static final Pattern SCHEME = Pattern.compile("(?i)^(?:mailto:|tel:)");
    private static final Set<String> NON_TEXT_INPUTS = Set.of("submit", "button", "reset", "checkbox", "radio",
            "image", "file", "password");

    @Override
    public Set<FileFormat> formats() {
        return Set.of(FileFormat.HTML);
    }

    @Override
    public void extract(byte[] data, String fileName, ExtractionResult out) {
        Document doc = Jsoup.parse(TextDecoder.decode(data));

        for (Element m : doc.select("meta[name]")) {
            String name = m.attr("name").toLowerCase();
            if (name.equals("author") || name.equals("creator") || name.equals("owner")) {
                out.meta("html." + name, m.attr("content"));
            }
        }
        doc.select("script, style, noscript, template").remove();

        Map<Element, StringBuilder> blocks = new LinkedHashMap<>();
        doc.traverse((node, depth) -> {
            if (node instanceof TextNode tn && !tn.isBlank()) {
                blocks.computeIfAbsent(blockAncestor(tn, doc), k -> new StringBuilder()).append(tn.text()).append(' ');
            }
        });
        for (Map.Entry<Element, StringBuilder> e : blocks.entrySet()) {
            if (!out.text(loc(e.getKey()), e.getValue().toString())) {
                return;
            }
        }

        for (Element a : doc.select("a[href^=mailto:], a[href^=tel:]")) {
            out.text(loc(a) + " [href]", SCHEME.matcher(a.attr("href")).replaceFirst(""));
        }
        for (Element e : doc.select("[alt], [title], [placeholder]")) {
            for (String attr : new String[] {"alt", "title", "placeholder"}) {
                String v = e.attr(attr);
                if (v.length() > 2) {
                    out.text(loc(e) + " [" + attr + "]", v);
                }
            }
        }
        for (Element in : doc.select("input[value]")) {
            if (NON_TEXT_INPUTS.contains(in.attr("type").toLowerCase())) {
                continue;
            }
            out.field(loc(in), in.attr("value"), firstNonBlank(in.attr("name"), in.id(), in.attr("placeholder"),
                    in.attr("aria-label")));
        }
    }

    private static Element blockAncestor(TextNode tn, Document root) {
        Node p = tn.parent();
        while (p != null) {
            if (p instanceof Element el && BLOCK.contains(el.normalName())) {
                return el;
            }
            p = p.parent();
        }
        return root;
    }

    private static String loc(Element e) {
        return e instanceof Document ? "html" : e.cssSelector();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}

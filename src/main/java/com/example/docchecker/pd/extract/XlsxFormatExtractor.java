package com.example.docchecker.pd.extract;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** XLSX: все листы (включая скрытые), первая строка листа — заголовки, если похожа на них. */
@Component
public class XlsxFormatExtractor implements FormatExtractor {

    @Override
    public Set<FileFormat> formats() {
        return Set.of(FileFormat.XLSX);
    }

    @Override
    public void extract(byte[] data, String fileName, ExtractionResult out) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            DataFormatter fmt = new DataFormatter();
            if (wb.getProperties() != null && wb.getProperties().getCoreProperties() != null) {
                out.meta("xlsx.creator", wb.getProperties().getCoreProperties().getCreator());
                out.meta("xlsx.lastModifiedBy", wb.getProperties().getCoreProperties().getLastModifiedByUser());
            }
            for (Sheet sheet : wb) {
                if (!sheet(sheet, fmt, out)) {
                    return;
                }
            }
        }
    }

    private boolean sheet(Sheet sheet, DataFormatter fmt, ExtractionResult out) {
        Map<Integer, String> headers = new HashMap<>();
        boolean first = true;
        for (Row row : sheet) {
            if (first) {
                first = false;
                if (looksLikeHeader(row, fmt)) {
                    for (Cell cell : row) {
                        String h = cellText(cell, fmt).strip();
                        if (!h.isBlank()) {
                            headers.put(cell.getColumnIndex(), h);
                            out.header("лист «" + sheet.getSheetName() + "», " + cell.getAddress().formatAsString(), h);
                        }
                    }
                    continue;
                }
            }
            for (Cell cell : row) {
                String locator = "лист «" + sheet.getSheetName() + "», " + cell.getAddress().formatAsString();
                Comment comment = cell.getCellComment();
                if (comment != null) {
                    out.meta("xlsx.comment.author", comment.getAuthor());
                    if (comment.getString() != null) {
                        out.text(locator + " [примечание]", comment.getString().getString());
                    }
                }
                String v = cellText(cell, fmt);
                if (v.isBlank()) {
                    continue;
                }
                if (!out.field(locator, v, headers.get(cell.getColumnIndex()))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean looksLikeHeader(Row row, DataFormatter fmt) {
        boolean any = false;
        for (Cell cell : row) {
            String v = cellText(cell, fmt).strip();
            if (v.isEmpty()) {
                continue;
            }
            any = true;
            if (v.length() > 60 || !v.matches(".*\\p{L}.*") || v.matches("[\\d.,:/\\- ]+")) {
                return false;
            }
        }
        return any;
    }

    /**
     * Значение ячейки. Для формул берётся сохранённый результат вычисления, а не текст формулы:
     * персональные данные могут быть собраны формулой (например, ФИО из трёх столбцов).
     */
    static String cellText(Cell cell, DataFormatter fmt) {
        if (cell.getCellType() != CellType.FORMULA) {
            return fmt.formatCellValue(cell);
        }
        switch (cell.getCachedFormulaResultType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return fmt.formatRawCellContents(cell.getNumericCellValue(), cell.getCellStyle().getDataFormat(),
                        cell.getCellStyle().getDataFormatString());
            default:
                return "";
        }
    }
}

package cn.wenchang.mcp;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

@Component
public class TabularArtifactWriter {

    public void writeCsv(Path path, List<String> fields, List<Map<String, String>> rows) throws IOException {
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(fields.toArray(String[]::new)).get();
            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (Map<String, String> row : rows) {
                    printer.printRecord(fields.stream().map(field -> csvSafe(row.get(field))).toList());
                }
            }
        }
    }

    public void writeXlsx(Path path, List<String> fields, List<Map<String, String>> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("文昌数据");
            sheet.createFreezePane(0, 1);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontName("Microsoft YaHei");
            headerStyle.setFont(headerFont);
            CellStyle linkStyle = workbook.createCellStyle();
            Font linkFont = workbook.createFont();
            linkFont.setUnderline(Font.U_SINGLE);
            linkFont.setColor((short) 12);
            linkFont.setFontName("Microsoft YaHei");
            linkStyle.setFont(linkFont);
            Row header = sheet.createRow(0);
            for (int index = 0; index < fields.size(); index++) {
                Cell cell = header.createCell(index);
                cell.setCellValue(fields.get(index));
                cell.setCellStyle(headerStyle);
            }
            CreationHelper helper = workbook.getCreationHelper();
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row excelRow = sheet.createRow(rowIndex + 1);
                Map<String, String> values = rows.get(rowIndex);
                for (int column = 0; column < fields.size(); column++) {
                    Cell cell = excelRow.createCell(column);
                    String value = values.getOrDefault(fields.get(column), "");
                    cell.setCellValue(value);
                    if (isHttpUrl(value)) {
                        var hyperlink = helper.createHyperlink(HyperlinkType.URL);
                        hyperlink.setAddress(value);
                        cell.setHyperlink(hyperlink);
                        cell.setCellStyle(linkStyle);
                    }
                }
            }
            for (int column = 0; column < fields.size(); column++) {
                sheet.autoSizeColumn(column);
                sheet.setColumnWidth(column, Math.min(18_000, Math.max(2_400, sheet.getColumnWidth(column) + 512)));
            }
            try (var output = Files.newOutputStream(path)) { workbook.write(output); }
        }
    }

    private String csvSafe(String value) {
        String text = value == null ? "" : value;
        String stripped = text.stripLeading();
        if (!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0) return "'" + text;
        return text;
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        }
        catch (IllegalArgumentException exception) { return false; }
    }
}

package cn.wenchang.mcp;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/** Human-readable CSV/XLSX export with Chinese labels and restrained business formatting. */
@Component
public class TabularArtifactWriter {

    private static final Map<String, String> LABELS = labels();
    private static final String FONT = "Microsoft YaHei";

    public void writeCsv(Path path, List<String> fields, List<Map<String, String>> rows) throws IOException {
        List<String> headers = fields.stream().map(this::label).toList();
        try (Writer writer = new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).get();
            try (CSVPrinter printer = new CSVPrinter(writer, format)) {
                for (Map<String, String> row : rows) {
                    printer.printRecord(fields.stream().map(field -> csvSafe(row.get(field))).toList());
                }
            }
        }
    }

    public void writeXlsx(Path path, String title, List<String> fields, List<Map<String, String>> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("数据清单");
            sheet.setDisplayGridlines(false);
            sheet.createFreezePane(0, 4);
            CellStyle titleStyle = titleStyle(workbook);
            CellStyle metaStyle = metaStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle bodyStyle = bodyStyle(workbook);
            CellStyle linkStyle = linkStyle(workbook, bodyStyle);

            Row titleRow = sheet.createRow(0); titleRow.setHeightInPoints(30);
            Cell titleCell = titleRow.createCell(0); titleCell.setCellValue(title); titleCell.setCellStyle(titleStyle);
            if (fields.size() > 1) sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, fields.size() - 1));
            Row metaRow = sheet.createRow(1); metaRow.setHeightInPoints(22);
            Cell metaCell = metaRow.createCell(0);
            metaCell.setCellValue("共 " + rows.size() + " 条记录 · 生成日期 " + LocalDate.now() + " · 来源列可直接点击打开");
            metaCell.setCellStyle(metaStyle);
            if (fields.size() > 1) sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, fields.size() - 1));

            Row header = sheet.createRow(3); header.setHeightInPoints(24);
            for (int index = 0; index < fields.size(); index++) {
                Cell cell = header.createCell(index); cell.setCellValue(label(fields.get(index))); cell.setCellStyle(headerStyle);
            }
            CreationHelper helper = workbook.getCreationHelper();
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row excelRow = sheet.createRow(rowIndex + 4); excelRow.setHeightInPoints(32);
                Map<String, String> values = rows.get(rowIndex);
                for (int column = 0; column < fields.size(); column++) {
                    Cell cell = excelRow.createCell(column);
                    String value = values.getOrDefault(fields.get(column), "");
                    cell.setCellValue(value); cell.setCellStyle(bodyStyle);
                    if (isHttpUrl(value)) {
                        var hyperlink = helper.createHyperlink(HyperlinkType.URL);
                        hyperlink.setAddress(value); cell.setHyperlink(hyperlink); cell.setCellStyle(linkStyle);
                    }
                }
            }
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(3, Math.max(3, rows.size() + 3), 0, fields.size() - 1));
            for (int column = 0; column < fields.size(); column++) {
                int width = preferredWidth(fields.get(column));
                sheet.setColumnWidth(column, Math.min(18_000, width * 256));
            }
            sheet.setRepeatingRows(new org.apache.poi.ss.util.CellRangeAddress(3, 3, -1, -1));
            try (var output = Files.newOutputStream(path)) { workbook.write(output); }
        }
    }

    private CellStyle titleStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont(); font.setFontName(FONT); font.setFontHeightInPoints((short) 18);
        font.setBold(true); font.setColor(IndexedColors.DARK_TEAL.getIndex());
        style.setFont(font); style.setVerticalAlignment(VerticalAlignment.CENTER); return style;
    }

    private CellStyle metaStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont(); font.setFontName(FONT); font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex()); style.setFont(font);
        style.setVerticalAlignment(VerticalAlignment.CENTER); return style;
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont(); font.setFontName(FONT); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font); style.setFillForegroundColor(new XSSFColor(new java.awt.Color(31, 111, 139), null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND); style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setAlignment(HorizontalAlignment.CENTER); style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN); style.setBottomBorderColor(IndexedColors.WHITE.getIndex());
        return style;
    }

    private CellStyle bodyStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont(); font.setFontName(FONT); font.setFontHeightInPoints((short) 10);
        style.setFont(font); style.setVerticalAlignment(VerticalAlignment.CENTER); style.setWrapText(true);
        style.setBorderBottom(BorderStyle.HAIR); style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        return style;
    }

    private CellStyle linkStyle(XSSFWorkbook workbook, CellStyle base) {
        CellStyle style = workbook.createCellStyle(); style.cloneStyleFrom(base);
        Font font = workbook.createFont(); font.setFontName(FONT); font.setFontHeightInPoints((short) 10);
        font.setUnderline(Font.U_SINGLE); font.setColor(IndexedColors.BLUE.getIndex()); style.setFont(font); return style;
    }

    private int preferredWidth(String field) {
        if (field.toLowerCase().contains("url")) return 42;
        if (List.of("summary", "description", "serviceScope", "learningPoints").contains(field)) return 36;
        if (List.of("name", "title", "organization", "sourceOrganization", "address").contains(field)) return 24;
        if (List.of("latitude", "longitude", "publishedAt", "published_at", "status").contains(field)) return 14;
        return 16;
    }

    private String label(String field) { return LABELS.getOrDefault(field, field); }

    private static Map<String, String> labels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("name", "名称"); labels.put("title", "标题"); labels.put("category", "类别");
        labels.put("town", "所在乡镇"); labels.put("address", "地址"); labels.put("summary", "简介");
        labels.put("description", "说明"); labels.put("serviceScope", "服务范围");
        labels.put("latitude", "纬度"); labels.put("longitude", "经度"); labels.put("suitableAge", "适合年龄");
        labels.put("organization", "发布机构"); labels.put("sourceOrganization", "来源机构");
        labels.put("sourceUrl", "原始来源"); labels.put("url", "原始来源"); labels.put("publishedAt", "发布日期");
        labels.put("published_at", "发布日期"); labels.put("status", "状态"); labels.put("source_id", "来源编号");
        labels.put("source_level", "来源级别"); labels.put("learningPoints", "学习要点");
        return Map.copyOf(labels);
    }

    private String csvSafe(String value) {
        String text = value == null ? "" : value;
        String stripped = text.stripLeading();
        return !stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0)) >= 0 ? "'" + text : text;
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value); String scheme = uri.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        } catch (IllegalArgumentException exception) { return false; }
    }
}

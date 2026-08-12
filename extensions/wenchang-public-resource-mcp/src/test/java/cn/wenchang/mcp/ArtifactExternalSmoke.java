package cn.wenchang.mcp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/** 对生产 MCP 已落盘的 Artifact 做只读完整性检查。 */
public final class ArtifactExternalSmoke {

    private ArtifactExternalSmoke() { }

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "../../data/artifacts/production-acceptance" : args[0])
                .toAbsolutePath().normalize();
        ObjectMapper mapper = new ObjectMapper();
        try (var manifests = Files.list(root)) {
            for (Path manifestPath : manifests.filter(path -> path.getFileName().toString().endsWith(".metadata.json"))
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                ArtifactManifest manifest = mapper.readValue(manifestPath.toFile(), ArtifactManifest.class);
                Path file = root.getParent().resolve(manifest.relativePath()).normalize();
                if (!file.startsWith(root.getParent()) || !Files.isRegularFile(file) || Files.size(file) == 0L) {
                    throw new IllegalStateException("Invalid artifact path or empty file: " + manifest.id());
                }
                String extension = extension(file.getFileName().toString());
                String detail;
                if (extension.equals("docx")) {
                    try (XWPFDocument document = new XWPFDocument(Files.newInputStream(file))) {
                        String text = document.getParagraphs().stream().map(paragraph -> paragraph.getText())
                                .reduce("", (left, right) -> left + "\n" + right);
                        if (!text.contains("来源") || text.length() < 30) {
                            throw new IllegalStateException("DOCX content validation failed: " + manifest.id());
                        }
                        detail = "paragraphs=" + document.getParagraphs().size() + ",chinese=true,sources=true";
                    }
                }
                else if (extension.equals("xlsx")) {
                    try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(file))) {
                        var sheet = workbook.getSheetAt(0);
                        if (sheet.getLastRowNum() < 1 || sheet.getRow(0) == null) {
                            throw new IllegalStateException("XLSX content validation failed: " + manifest.id());
                        }
                        detail = "sheet=" + sheet.getSheetName() + ",rows=" + sheet.getLastRowNum()
                                + ",header=" + sheet.getRow(0).getCell(0).getStringCellValue();
                    }
                }
                else if (extension.equals("csv")) {
                    String csv = Files.readString(file, StandardCharsets.UTF_8);
                    if (!csv.startsWith("\uFEFF") || csv.lines().count() < 2 || !csv.contains("文昌")) {
                        throw new IllegalStateException("CSV content validation failed: " + manifest.id());
                    }
                    detail = "utf8Bom=true,rows=" + (csv.lines().count() - 1) + ",chinese=true";
                }
                else throw new IllegalStateException("Unexpected extension: " + extension);
                System.out.println("ARTIFACT=" + manifest.id() + ";TYPE=" + manifest.type() + ";FILE=" + file
                        + ";BYTES=" + Files.size(file) + ";VALID=" + detail);
            }
        }
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }
}

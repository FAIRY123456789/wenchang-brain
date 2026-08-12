package cn.wenchang.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductionArtifactToolsTest {

    private Path temporaryDirectory;

    private ProductionArtifactTools tools;
    private ArtifactStore store;

    @BeforeEach
    void setUp() {
        temporaryDirectory = Path.of("target", "test-artifacts", "production-tools", UUID.randomUUID().toString());
        PublicResourceProperties dataProperties = new PublicResourceProperties();
        dataProperties.setDataRoot(Path.of("src/test/resources/fixtures"));
        DataAssetRepository dataRepository = new DataAssetRepository(dataProperties);
        dataRepository.load();
        ArtifactProperties artifactProperties = new ArtifactProperties();
        artifactProperties.setRoot(temporaryDirectory.resolve("artifacts"));
        artifactProperties.setSourcesIndexFile(temporaryDirectory.resolve("missing-sources.csv"));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        store = new ArtifactStore(artifactProperties, dataRepository);
        TaskDatasetRepository datasets = new TaskDatasetRepository(dataRepository, artifactProperties);
        tools = new ProductionArtifactTools(store, new WordArtifactWriter(), new TabularArtifactWriter(), datasets);
    }

    @Test
    void generatesReadableChineseWordAndManifest() throws Exception {
        var result = tools.createWenchangWordReport("文昌商业航天政策简报", "商业航天",
                "## 核心内容\n正文支持中文。\n\n| 序号 | 项目 | 说明 |\n|---:|---|---|\n|1|项目一|公开资料|",
                List.of("文昌市人民政府 - https://wenchang.hainan.gov.cn/"), "conversation-001",
                "policy", "policy-brief");

        Path documentPath = artifactFile(result.artifactId());
        assertThat(documentPath).hasExtension("docx");
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(documentPath))) {
            assertThat(document.getDocument().getBody().isSetSectPr()).isTrue();
            assertThat(document.getDocument().getBody().getSectPr().isSetPgSz()).isTrue();
            assertThat(document.getDocument().getBody().getSectPr().isSetPgMar()).isTrue();
            String text = document.getParagraphs().stream().map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(text).contains("文昌商业航天政策简报", "核心内容", "正文支持中文", "来源与核验", "文昌市人民政府")
                    .doesNotContain("{\"tool\"");
            assertThat(document.getTables()).hasSize(1);
            assertThat(document.getTables().get(0).getRow(1).getCell(1).getText()).contains("项目一");
        }
        ArtifactManifest manifest = manifest(result.artifactId());
        assertThat(manifest.filename()).isEqualTo("文昌商业航天政策简报.docx");
        assertThat(manifest.downloadUrl()).isEqualTo("/api/artifacts/" + result.artifactId() + "/download");
        assertThat(manifest.sourceCount()).isEqualTo(1);
    }

    @Test
    void exportsUtf8CsvAndReadableXlsxWithChineseAndUrl() throws Exception {
        var csv = tools.exportWenchangData("places", List.of("name", "sourceUrl"),
                Map.of("town", "龙楼"), "csv", "conversation-002", "study-tour", "data-export");
        String csvText = Files.readString(artifactFile(csv.artifactId()), StandardCharsets.UTF_8);
        assertThat(csvText).startsWith("\uFEFF名称,原始来源").contains("文昌", "https://");

        var xlsx = tools.exportWenchangData("places", List.of("name", "town", "sourceUrl"),
                Map.of("town", "龙楼"), "xlsx", "conversation-002", "study-tour", "data-export");
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(artifactFile(xlsx.artifactId())))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("文昌研学地点数据清单");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("名称");
            assertThat(sheet.getLastRowNum()).isGreaterThan(3);
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).contains("文昌");
            assertThat(sheet.getRow(4).getCell(2).getHyperlink()).isNotNull();
            assertThat(sheet.getPaneInformation()).isNotNull();
        }

        var schools = tools.exportWenchangData("publicServices", List.of("name", "town", "sourceUrl"),
                Map.of("category", "education", "name", "中学"), "xlsx", "conversation-002",
                "wenchang", "data-export");
        assertThat(schools.filename()).isEqualTo("文昌高中阶段学校候选清单.xlsx");
    }

    @Test
    void createsStudyTourPackageAndRejectsPathTraversal() throws Exception {
        var studyTour = tools.createStudyTourPackage("初二", "一天", List.of("航天"), List.of("科普"),
                "conversation-003", "study-tour", "study-tour-package");
        assertThat(studyTour.sourceCount()).isPositive();
        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(artifactFile(studyTour.artifactId())))) {
            String text = document.getParagraphs().stream().map(paragraph -> paragraph.getText())
                    .reduce("", (left, right) -> left + "\n" + right);
            assertThat(text).contains("研学主题", "适合对象", "时间安排", "路线顺序", "注意事项", "来源");
        }

        assertThatThrownBy(() -> tools.createWenchangWordReport("越界", "测试", "正文", List.of(),
                "../outside", "wenchang", "word-report"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("conversationId");
        assertThat(temporaryDirectory.resolve("outside")).doesNotExist();
    }

    private ArtifactManifest manifest(String id) throws Exception {
        Path manifest = Files.walk(store.root()).filter(path -> path.getFileName().toString()
                .equals(id + ".metadata.json")).findFirst().orElseThrow();
        return new ObjectMapper().findAndRegisterModules().readValue(manifest.toFile(), ArtifactManifest.class);
    }

    private Path artifactFile(String id) throws Exception {
        ArtifactManifest manifest = manifest(id);
        return store.root().resolve(manifest.relativePath()).normalize();
    }
}

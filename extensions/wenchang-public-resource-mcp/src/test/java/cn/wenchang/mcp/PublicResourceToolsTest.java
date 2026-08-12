package cn.wenchang.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublicResourceToolsTest {

    private PublicResourceTools tools;

    @BeforeEach
    void setUp() {
        PublicResourceProperties properties = new PublicResourceProperties();
        properties.setDataRoot(Path.of("src/test/resources/fixtures"));
        DataAssetRepository repository = new DataAssetRepository(properties);
        repository.load();
        tools = new PublicResourceTools(repository, properties);
    }

    @Test
    void filtersPublicServicesByCategoryAndTown() {
        PublicResourceTools.SearchResponse response = tools.searchPublicServices(null, "文化", "文城");

        assertThat(response.dataStatus()).isEqualTo("READY");
        assertThat(response.count()).isEqualTo(1);
        assertThat(response.results().get(0)).containsEntry("name", "文昌市图书馆");
        assertThat(response.results().get(0)).containsEntry("sourceId", "SRC-TEST-SERVICE-001");
        assertThat(response.results().get(0)).containsEntry("sourceOrganization", "文昌市人民政府");

        PublicResourceTools.SearchResponse multiTerm = tools.searchPublicServices("公共 阅读", null, "文城");
        assertThat(multiTerm.count()).isEqualTo(1);
    }

    @Test
    void returnsTownshipProfileWithRelatedResources() {
        PublicResourceTools.SearchResponse response = tools.searchTownshipProfile("龙楼镇");

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.results().get(0)).containsEntry("town", "龙楼镇");
        assertThat(response.results().get(0).get("studyTourPlaces")).asList().hasSize(1);
    }

    @Test
    void filtersStudyTourPlacesByThemeTownAndAge() {
        PublicResourceTools.SearchResponse response = tools.searchStudyTourPlaces("航天", "龙楼", "小学");

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.results().get(0)).containsEntry("name", "文昌航天科普中心");
        assertThat(response.results().get(0)).containsEntry("sourceId", "SRC-TEST-PLACE-001");
        assertThat(response.results().get(0)).containsEntry("sourceLevel", "P0");

        PublicResourceTools.SearchResponse olderStudents = tools.searchStudyTourPlaces("航天", "龙楼", "初中");
        assertThat(olderStudents.count()).isEqualTo(1);
    }
}

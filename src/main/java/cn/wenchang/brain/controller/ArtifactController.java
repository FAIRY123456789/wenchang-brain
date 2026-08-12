package cn.wenchang.brain.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;

import cn.wenchang.brain.artifact.ArtifactDescriptor;
import cn.wenchang.brain.artifact.ArtifactMetadata;
import cn.wenchang.brain.artifact.ArtifactService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artifacts")
public class ArtifactController {

    private final ArtifactService artifactService;

    public ArtifactController(ArtifactService artifactService) { this.artifactService = artifactService; }

    @GetMapping
    public List<ArtifactDescriptor> list(@RequestParam(required = false) String conversationId) {
        return artifactService.listDescriptors(conversationId);
    }

    @GetMapping("/{id}")
    public ArtifactDescriptor detail(@PathVariable String id) { return artifactService.require(id).descriptor(); }

    @GetMapping("/{id}/download")
    public ResponseEntity<FileSystemResource> download(@PathVariable String id) {
        ArtifactService.ArtifactFile artifact = artifactService.open(id);
        ArtifactMetadata metadata = artifact.metadata();
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(metadata.contentType()); }
        catch (RuntimeException exception) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(metadata.filename(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore());
        return ResponseEntity.ok().headers(headers).contentType(mediaType).contentLength(metadata.size())
                .body(new FileSystemResource(artifact.path()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        artifactService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

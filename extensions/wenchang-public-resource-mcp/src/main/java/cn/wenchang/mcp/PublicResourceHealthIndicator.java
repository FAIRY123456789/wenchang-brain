package cn.wenchang.mcp;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("publicResourceData")
public class PublicResourceHealthIndicator implements HealthIndicator {

    private final DataAssetRepository repository;

    public PublicResourceHealthIndicator(DataAssetRepository repository) {
        this.repository = repository;
    }

    @Override
    public Health health() {
        DataAssetRepository.Snapshot snapshot = repository.snapshot();
        boolean placesReady = "READY".equals(snapshot.places().status());
        boolean anyDomainAssetReady = "READY".equals(snapshot.publicServices().status())
                || "READY".equals(snapshot.townships().status());
        Health.Builder builder = placesReady && anyDomainAssetReady
                ? Health.up()
                : Health.status("DEGRADED");
        snapshot.statusDetails().forEach(builder::withDetail);
        return builder.build();
    }
}

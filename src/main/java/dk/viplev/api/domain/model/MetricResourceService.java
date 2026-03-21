package dk.viplev.api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "metric_resource_services")
@Getter
public class MetricResourceService {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private BenchmarkRun benchmarkRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "cpu_percentage")
    private Double cpuPercentage;

    @Column(name = "memory_usage_bytes")
    private Double memoryUsageBytes;

    @Column(name = "memory_limit_bytes")
    private Double memoryLimitBytes;

    @Column(name = "network_in_bytes")
    private Double networkInBytes;

    @Column(name = "network_out_bytes")
    private Double networkOutBytes;

    @Column(name = "block_in_bytes")
    private Double blockInBytes;

    @Column(name = "block_out_bytes")
    private Double blockOutBytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected MetricResourceService() {}

    public MetricResourceService(BenchmarkRun benchmarkRun, Service service, LocalDateTime collectedAt,
                                 Double cpuPercentage, Double memoryUsageBytes, Double memoryLimitBytes,
                                 Double networkInBytes, Double networkOutBytes,
                                 Double blockInBytes, Double blockOutBytes) {
        this.benchmarkRun = benchmarkRun;
        this.service = service;
        this.collectedAt = collectedAt;
        this.cpuPercentage = cpuPercentage;
        this.memoryUsageBytes = memoryUsageBytes;
        this.memoryLimitBytes = memoryLimitBytes;
        this.networkInBytes = networkInBytes;
        this.networkOutBytes = networkOutBytes;
        this.blockInBytes = blockInBytes;
        this.blockOutBytes = blockOutBytes;
    }
}

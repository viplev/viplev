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
@Table(name = "metric_resource_replicas")
@Getter
public class MetricResourceReplica {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private BenchmarkRun benchmarkRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replica_id", nullable = false)
    private ServiceReplica replica;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "cpu_percentage")
    private Double cpuPercentage;

    @Column(name = "memory_usage_bytes")
    private Long memoryUsageBytes;

    @Column(name = "memory_limit_bytes")
    private Long memoryLimitBytes;

    @Column(name = "network_in_bytes")
    private Long networkInBytes;

    @Column(name = "network_out_bytes")
    private Long networkOutBytes;

    @Column(name = "block_in_bytes")
    private Long blockInBytes;

    @Column(name = "block_out_bytes")
    private Long blockOutBytes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected MetricResourceReplica() {}

    public MetricResourceReplica(BenchmarkRun benchmarkRun, ServiceReplica replica, LocalDateTime collectedAt,
                                 Double cpuPercentage, Long memoryUsageBytes, Long memoryLimitBytes,
                                 Long networkInBytes, Long networkOutBytes,
                                 Long blockInBytes, Long blockOutBytes) {
        this.benchmarkRun = benchmarkRun;
        this.replica = replica;
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

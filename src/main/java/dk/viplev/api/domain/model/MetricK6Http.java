package dk.viplev.api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "metric_k6_http")
@Getter
public class MetricK6Http {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private BenchmarkRun benchmarkRun;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "url", length = 2048)
    private String url;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "request_group")
    private String requestGroup;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "expected_status")
    private Integer expectedStatus;

    @Column(name = "data_received_byte")
    private Integer dataReceivedByte;

    @Column(name = "data_sent_byte")
    private Integer dataSentByte;

    @Column(name = "http_req_duration_ms")
    private Integer httpReqDurationMs;

    @Column(name = "http_req_waiting_ms")
    private Integer httpReqWaitingMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected MetricK6Http() {}

    public MetricK6Http(BenchmarkRun benchmarkRun, LocalDateTime collectedAt, String url,
                        String httpMethod, String requestGroup, Integer httpStatus,
                        Integer expectedStatus, Integer dataReceivedByte, Integer dataSentByte,
                        Integer httpReqDurationMs, Integer httpReqWaitingMs) {
        this.benchmarkRun = benchmarkRun;
        this.collectedAt = collectedAt;
        this.url = url;
        this.httpMethod = httpMethod;
        this.requestGroup = requestGroup;
        this.httpStatus = httpStatus;
        this.expectedStatus = expectedStatus;
        this.dataReceivedByte = dataReceivedByte;
        this.dataSentByte = dataSentByte;
        this.httpReqDurationMs = httpReqDurationMs;
        this.httpReqWaitingMs = httpReqWaitingMs;
    }
}

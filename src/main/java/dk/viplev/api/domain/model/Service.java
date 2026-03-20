package dk.viplev.api.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "services", uniqueConstraints = @UniqueConstraint(columnNames = {"host_id", "service_name"}))
@Getter
@Setter
public class Service {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private Host host;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "image_sha")
    private String imageSha;

    @Column(name = "image_name")
    private String imageName;

    @Column(name = "cpu_limit")
    private Double cpuLimit;

    @Column(name = "cpu_reservation")
    private Double cpuReservation;

    @Column(name = "memory_limit_bytes")
    private Long memoryLimitBytes;

    @Column(name = "memory_reservation_bytes")
    private Long memoryReservationBytes;
}

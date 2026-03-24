package dk.viplev.api.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "hosts", uniqueConstraints = @UniqueConstraint(columnNames = {"environment_id", "machine_id"}))
@Getter
@Setter
public class Host {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environment;

    @Column(nullable = false)
    private String name;

    @Column(name = "machine_id", nullable = false)
    private String machineId;

    @Column(name = "ip_address")
    private String ipAddress;

    private String os;

    @Column(name = "os_version")
    private String osVersion;

    @Column(name = "cpu_model")
    private String cpuModel;

    @Column(name = "cpu_cores")
    private Integer cpuCores;

    @Column(name = "cpu_threads")
    private Integer cpuThreads;

    @Column(name = "ram_total_bytes")
    private Long ramTotalBytes;

    @Column(name = "ram_speed_mhz")
    private Integer ramSpeedMhz;

    @Column(name = "ram_type")
    private String ramType;

    @OneToMany(mappedBy = "host", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Service> services = new ArrayList<>();
}

package dk.viplev.api.util;

import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import dk.viplev.api.domain.model.User;
import dk.viplev.api.port.outbound.db.BenchmarkRepository;
import dk.viplev.api.port.outbound.db.BenchmarkRunRepository;
import dk.viplev.api.port.outbound.db.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class TestObjectFactory {

    @Autowired
    private BenchmarkRepository benchmarkRepository;

    @Autowired
    private BenchmarkRunRepository benchmarkRunRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public UUID createBenchmarkRunDirectly(UUID benchmarkUuid, String userEmail) {
        return createBenchmarkRunDirectly(benchmarkUuid, userEmail, BenchmarkRunStatus.PENDING_START);
    }

    public UUID createBenchmarkRunDirectly(UUID benchmarkUuid, String userEmail, BenchmarkRunStatus status) {
        Benchmark benchmark = benchmarkRepository.findById(benchmarkUuid).orElseThrow();
        User user = userRepository.findByEmail(userEmail).orElseThrow();

        BenchmarkRun run = new BenchmarkRun();
        run.setBenchmark(benchmark);
        run.setStartedByUser(user);
        run.setStatus(status);
        return benchmarkRunRepository.saveAndFlush(run).getId();
    }

    public void setRunCreatedAt(UUID runId, LocalDateTime createdAt) {
        int updatedRows = jdbcTemplate.update(
                "UPDATE benchmark_runs SET created_at = ? WHERE id = ?",
                Timestamp.valueOf(createdAt),
                runId
        );
        if (updatedRows != 1) {
            throw new IllegalStateException("Expected 1 row to be updated for run " + runId + ", but was " + updatedRows);
        }
    }
}

package dk.viplev.api.adapter.inbound.rest.mapper;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkDTO;
import dk.viplev.api.adapter.inbound.rest.dto.MessageDTO;
import dk.viplev.api.domain.model.Benchmark;
import dk.viplev.api.domain.model.BenchmarkRun;
import dk.viplev.api.domain.model.BenchmarkRunStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface MessageMapper {

    @Mapping(source = "benchmark.id", target = "benchmarkId")
    @Mapping(source = "id", target = "runId")
    @Mapping(source = "status", target = "messageType", qualifiedByName = "toMessageType")
    @Mapping(target = "benchmarkData", expression = "java(toBenchmarkData(benchmarkRun))")
    MessageDTO toDto(BenchmarkRun benchmarkRun);

    @Named("toMessageType")
    default MessageDTO.MessageTypeEnum toMessageType(BenchmarkRunStatus status) {
        return switch (status) {
            case PENDING_START -> MessageDTO.MessageTypeEnum.PENDING_START;
            case PENDING_STOP -> MessageDTO.MessageTypeEnum.PENDING_STOP;
            default -> throw new IllegalArgumentException("Unsupported message status: " + status);
        };
    }

    default BenchmarkDTO toBenchmarkData(BenchmarkRun benchmarkRun) {
        if (benchmarkRun.getStatus() != BenchmarkRunStatus.PENDING_START) {
            return null;
        }

        Benchmark benchmark = benchmarkRun.getBenchmark();
        if (benchmark == null) {
            return null;
        }

        BenchmarkDTO dto = new BenchmarkDTO();
        dto.setId(benchmark.getId());
        dto.setName(benchmark.getName());
        dto.setDescription(benchmark.getDescription());
        dto.setK6Instructions(benchmark.getK6Instructions());
        dto.setCreatedAt(benchmark.getCreatedAt());
        dto.setUpdatedAt(benchmark.getUpdatedAt());

        return dto;
    }
}

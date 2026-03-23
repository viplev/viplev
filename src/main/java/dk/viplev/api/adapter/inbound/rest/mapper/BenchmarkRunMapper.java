package dk.viplev.api.adapter.inbound.rest.mapper;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkRunDTO;
import dk.viplev.api.adapter.inbound.rest.dto.EnvironmentRunSummaryDTO;
import dk.viplev.api.domain.model.BenchmarkRun;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BenchmarkRunMapper {

    @Mapping(source = "startedByUser.id", target = "startedBy")
    BenchmarkRunDTO toDto(BenchmarkRun benchmarkRun);

    @Mapping(source = "benchmark.id", target = "benchmarkId")
    @Mapping(source = "id", target = "runId")
    EnvironmentRunSummaryDTO toSummaryDto(BenchmarkRun benchmarkRun);
}

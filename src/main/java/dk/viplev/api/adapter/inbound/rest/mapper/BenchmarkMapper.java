package dk.viplev.api.adapter.inbound.rest.mapper;

import dk.viplev.api.adapter.inbound.rest.dto.BenchmarkDTO;
import dk.viplev.api.domain.model.Benchmark;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BenchmarkMapper {

    BenchmarkDTO toDto(Benchmark benchmark);
}

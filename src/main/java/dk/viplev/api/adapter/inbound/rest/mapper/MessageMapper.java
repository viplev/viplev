package dk.viplev.api.adapter.inbound.rest.mapper;

import dk.viplev.api.adapter.inbound.rest.dto.MessageDTO;
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
    @Mapping(target = "benchmarkData", ignore = true)
    MessageDTO toDto(BenchmarkRun benchmarkRun);

    @Named("toMessageType")
    default MessageDTO.MessageTypeEnum toMessageType(BenchmarkRunStatus status) {
        return switch (status) {
            case PENDING_START -> MessageDTO.MessageTypeEnum.PENDING_START;
            case PENDING_STOP -> MessageDTO.MessageTypeEnum.PENDING_STOP;
            default -> throw new IllegalArgumentException("Unsupported message status: " + status);
        };
    }
}

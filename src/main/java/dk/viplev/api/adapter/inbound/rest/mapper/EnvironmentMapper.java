package dk.viplev.api.adapter.inbound.rest.mapper;

import dk.viplev.api.adapter.inbound.rest.dto.EnvironmentDTO;
import dk.viplev.api.domain.model.Environment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface EnvironmentMapper {

    @Mapping(target = "agentCommand", source = ".", qualifiedByName = "toAgentCommand")
    @Mapping(target = "type", source = "type", qualifiedByName = "toTypeEnum")
    EnvironmentDTO toDto(Environment environment);

    @Named("toTypeEnum")
    default EnvironmentDTO.TypeEnum toTypeEnum(String type) {
        return EnvironmentDTO.TypeEnum.fromValue(type);
    }

    @Named("toAgentCommand")
    default String toAgentCommand(Environment environment) {
        return switch (environment.getType()) {
            case "docker" -> "docker run -d"
                    + " --name viplev-agent"
                    + " -v /var/run/docker.sock:/var/run/docker.sock"
                    + " -v /proc:/host/proc:ro"
                    + " -v /etc/machine-id:/etc/machine-id:ro"
                    + " -e VIPLEV_URL=https://api.viplev.ringhus.dk"
                    + " -e VIPLEV_TOKEN=" + environment.getToken()
                    + " -e VIPLEV_ENVIRONMENT_ID=" + environment.getId()
                    + " -e AGENT_PROC_PATH=/host/proc"
                    + " -p 8080:8080"
                    + " ghcr.io/viplev/agent:latest";
            case "kubernetes" -> "kubectl apply -f - # VIPLEV_TOKEN=" + environment.getToken();
            default -> "";
        };
    }
}

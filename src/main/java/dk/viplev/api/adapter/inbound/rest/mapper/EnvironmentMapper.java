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
                    + " --pull always"
                    + " --restart unless-stopped"
                    + " --name viplev-agent"
                    + " -e VIPLEV_URL=https://api.viplev.ringhus.dk"
                    + " -e VIPLEV_TOKEN=" + environment.getToken()
                    + " -e VIPLEV_ENVIRONMENT_ID=" + environment.getId()
                    + " -e VIPLEV_CADVISOR_IMAGE=gcr.io/cadvisor/cadvisor:v0.51.0"
                    + " -e VIPLEV_NODE_EXPORTER_IMAGE=prom/node-exporter:v1.9.0"
                    + " -e VIPLEV_K6_IMAGE=grafana/k6:0.53.0"
                    + " -e VIPLEV_K6_TIMEOUT_MS=300000"
                    + " -e AGENT_MESSAGE_POLLING_ENABLED=false"
                    + " -v /var/run/docker.sock:/var/run/docker.sock"
                    + " ghcr.io/viplev/agent:latest";
            case "kubernetes" -> "kubectl apply -f - # VIPLEV_TOKEN=" + environment.getToken();
            default -> "";
        };
    }
}

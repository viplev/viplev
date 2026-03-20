package dk.viplev.api.adapter.inbound.rest.mapper;

import dk.viplev.api.adapter.inbound.rest.dto.ServiceDTO;
import dk.viplev.api.domain.model.Service;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ServiceMapper {

    ServiceDTO toDto(Service service);

    List<ServiceDTO> toDtoList(List<Service> services);
}

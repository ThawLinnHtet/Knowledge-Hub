package com.knowledgehub.api.collections;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CollectionMapper {

	@Mapping(target = "documentCount", source = "documentCount")
	CollectionController.CollectionResponse toResponse(
			CollectionEntity collection, long documentCount);
}

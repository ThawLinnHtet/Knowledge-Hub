package com.knowledgehub.api.documents;

import com.knowledgehub.api.collections.CollectionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentMapper {

	@Mapping(target = "filename", source = "originalFilename")
	@Mapping(target = "status", expression = "java(document.getStatus().name())")
	@Mapping(target = "uploadedAt", source = "createdAt")
	DocumentDtos.DocumentResponse toResponse(DocumentEntity document);

	DocumentDtos.CollectionRef toCollectionRef(CollectionEntity collection);
}

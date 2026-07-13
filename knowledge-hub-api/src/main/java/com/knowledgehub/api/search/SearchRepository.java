package com.knowledgehub.api.search;

import com.knowledgehub.api.search.SearchDtos.CollectionRef;
import com.knowledgehub.api.search.SearchDtos.Mode;
import com.knowledgehub.api.search.SearchDtos.SearchFilters;
import com.knowledgehub.api.search.SearchDtos.SearchItem;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SearchRepository {

	private static final String FROM = " from document_chunks chunk "
			+ "join documents document on document.id = chunk.document_id "
			+ "join collections collection on collection.id = document.collection_id ";
	private static final String KEYWORD_MATCH = "(chunk.search_vector @@ search_query.value "
			+ "or to_tsvector('english', document.original_filename) @@ search_query.value)";
	private static final String KEYWORD_SCORE = "least(1.0, case when " + KEYWORD_MATCH
			+ " then ts_rank_cd(chunk.search_vector, search_query.value, 32) + "
			+ "case when to_tsvector('english', document.original_filename) "
			+ "@@ search_query.value then 0.1 else 0 end else 0 end)";
	private static final String SEMANTIC_SCORE = "greatest(0.0, least(1.0, 1.0 - "
			+ "((chunk.embedding <=> cast(:queryEmbedding as vector)) / 2.0)))";
	private static final String RESULT_COLUMNS = "chunk.id as chunk_id, "
			+ "document.id as document_id, document.original_filename, "
			+ "collection.id as collection_id, collection.name as collection_name, "
			+ "document.media_type, document.file_extension, document.created_at, "
			+ "chunk.chunk_order, left(chunk.content, 500) as snippet, chunk.page_number, "
			+ "chunk.section, chunk.start_position, chunk.end_position, ";

	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final SearchProperties properties;

	public List<SearchItem> search(
			UUID userId,
			String query,
			Mode mode,
			float[] queryEmbedding,
			String embeddingModel,
			int embeddingDimension,
			SearchFilters filters,
			int limit) {
		MapSqlParameterSource parameters = parameters(
				userId,
				query,
				queryEmbedding,
				embeddingModel,
				embeddingDimension,
				filters,
				limit);
		String filtersSql = filtersSql(filters);
		String sql = switch (mode) {
			case KEYWORD -> keywordSql(filtersSql);
			case SEMANTIC -> semanticSql(filtersSql);
			case HYBRID -> hybridSql(filtersSql);
		};
		return jdbcTemplate.query(sql, parameters, this::mapItem);
	}

	private String keywordSql(String filters) {
		return "with search_query as (select websearch_to_tsquery('english', :query) as value) "
				+ "select " + RESULT_COLUMNS
				+ KEYWORD_SCORE + " as keyword_score, 0.0 as semantic_score, "
				+ KEYWORD_SCORE + " as score, 'KEYWORD' as match_type "
				+ FROM + "cross join search_query where " + filters
				+ " and " + KEYWORD_MATCH
				+ " order by score desc, document.id, chunk.chunk_order, chunk.id limit :limit";
	}

	private String semanticSql(String filters) {
		return "select " + RESULT_COLUMNS
				+ "0.0 as keyword_score, " + SEMANTIC_SCORE + " as semantic_score, "
				+ SEMANTIC_SCORE + " as score, 'SEMANTIC' as match_type "
				+ FROM + "where " + filters + embeddingFilter()
				+ " and " + SEMANTIC_SCORE + " >= :minimumSemanticScore "
				+ "order by chunk.embedding <=> cast(:queryEmbedding as vector), "
				+ "document.id, chunk.chunk_order, chunk.id limit :limit";
	}

	private String hybridSql(String filters) {
		return "with search_query as (select websearch_to_tsquery('english', :query) as value), "
				+ "keyword_candidates as materialized (select chunk.id, " + KEYWORD_SCORE
				+ " as keyword_score " + FROM + "cross join search_query where " + filters
				+ " and " + KEYWORD_MATCH + " order by keyword_score desc, chunk.id "
				+ "limit :candidateLimit), "
				+ "semantic_candidates as materialized (select chunk.id, " + SEMANTIC_SCORE
				+ " as semantic_score " + FROM + "where " + filters + embeddingFilter()
				+ " and " + SEMANTIC_SCORE + " >= :minimumSemanticScore "
				+ "order by chunk.embedding <=> cast(:queryEmbedding as vector), chunk.id "
				+ "limit :candidateLimit), "
				+ "candidate_ids as (select id from keyword_candidates union "
				+ "select id from semantic_candidates) "
				+ "select " + RESULT_COLUMNS
				+ "coalesce(keyword.keyword_score, 0.0) as keyword_score, "
				+ "coalesce(semantic.semantic_score, 0.0) as semantic_score, "
				+ "(0.5 * coalesce(keyword.keyword_score, 0.0) + "
				+ "0.5 * coalesce(semantic.semantic_score, 0.0)) as score, "
				+ "case when keyword.id is not null and semantic.id is not null then 'BOTH' "
				+ "when keyword.id is not null then 'KEYWORD' else 'SEMANTIC' end as match_type "
				+ FROM + "join candidate_ids candidate on candidate.id = chunk.id "
				+ "left join keyword_candidates keyword on keyword.id = chunk.id "
				+ "left join semantic_candidates semantic on semantic.id = chunk.id "
				+ "order by score desc, document.id, chunk.chunk_order, chunk.id limit :limit";
	}

	private String embeddingFilter() {
		return " and chunk.embedding_model = :embeddingModel "
				+ "and chunk.embedding_dimension = :embeddingDimension";
	}

	private String filtersSql(SearchFilters filters) {
		StringBuilder sql = new StringBuilder(
				"document.user_id = :userId and document.status = 'READY'");
		if (filters.collectionId() != null) {
			sql.append(" and document.collection_id = :collectionId");
		}
		if (filters.documentIds() != null && !filters.documentIds().isEmpty()) {
			sql.append(" and document.id in (:documentIds)");
		}
		if (filters.fileExtension() != null) {
			sql.append(" and document.file_extension = :fileExtension");
		}
		if (filters.uploadedFrom() != null) {
			sql.append(" and document.created_at >= :uploadedFrom");
		}
		if (filters.uploadedTo() != null) {
			sql.append(" and document.created_at <= :uploadedTo");
		}
		return sql.toString();
	}

	private MapSqlParameterSource parameters(
			UUID userId,
			String query,
			float[] queryEmbedding,
			String embeddingModel,
			int embeddingDimension,
			SearchFilters filters,
			int limit) {
		MapSqlParameterSource parameters = new MapSqlParameterSource()
				.addValue("query", query)
				.addValue("userId", userId)
				.addValue("limit", limit)
				.addValue("candidateLimit", Math.max(50, limit * 5))
				.addValue("minimumSemanticScore", properties.minimumSemanticScore());
		if (queryEmbedding != null) {
			parameters.addValue("queryEmbedding", vectorLiteral(queryEmbedding))
					.addValue("embeddingModel", embeddingModel)
					.addValue("embeddingDimension", embeddingDimension);
		}
		if (filters.collectionId() != null) {
			parameters.addValue("collectionId", filters.collectionId());
		}
		if (filters.documentIds() != null && !filters.documentIds().isEmpty()) {
			parameters.addValue("documentIds", filters.documentIds());
		}
		if (filters.fileExtension() != null) {
			parameters.addValue("fileExtension", filters.fileExtension());
		}
		if (filters.uploadedFrom() != null) {
			parameters.addValue("uploadedFrom", Timestamp.from(filters.uploadedFrom()));
		}
		if (filters.uploadedTo() != null) {
			parameters.addValue("uploadedTo", Timestamp.from(filters.uploadedTo()));
		}
		return parameters;
	}

	private SearchItem mapItem(ResultSet result, int row) throws SQLException {
		return new SearchItem(
				result.getObject("chunk_id", UUID.class),
				result.getObject("document_id", UUID.class),
				result.getString("original_filename"),
				new CollectionRef(
						result.getObject("collection_id", UUID.class),
						result.getString("collection_name")),
				result.getString("media_type"),
				result.getString("file_extension"),
				result.getTimestamp("created_at").toInstant(),
				result.getInt("chunk_order"),
				result.getString("snippet"),
				result.getObject("page_number", Integer.class),
				result.getString("section"),
				result.getObject("start_position", Integer.class),
				result.getObject("end_position", Integer.class),
				result.getDouble("score"),
				result.getDouble("keyword_score"),
				result.getDouble("semantic_score"),
				result.getString("match_type"));
	}

	private String vectorLiteral(float[] embedding) {
		StringBuilder value = new StringBuilder(embedding.length * 12).append('[');
		for (int index = 0; index < embedding.length; index++) {
			if (index > 0) {
				value.append(',');
			}
			value.append(embedding[index]);
		}
		return value.append(']').toString();
	}
}

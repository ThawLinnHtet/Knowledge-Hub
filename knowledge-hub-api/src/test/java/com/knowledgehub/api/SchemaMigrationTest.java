package com.knowledgehub.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchemaMigrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void migrationCreatesRequiredExtensionsAndCoreTables() {
		Boolean vectorInstalled = jdbcTemplate.queryForObject(
				"select exists(select 1 from pg_extension where extname = 'vector')", Boolean.class);
		List<String> tables = jdbcTemplate.queryForList(
				"select table_name from information_schema.tables where table_schema = 'public'", String.class);

		assertThat(vectorInstalled).isTrue();
		assertThat(tables)
				.contains(
						"users",
						"refresh_tokens",
						"password_reset_tokens",
						"upload_confirmation_tokens",
						"collections",
						"documents",
						"document_chunks",
						"chat_sessions",
						"chat_messages",
						"message_citations",
						"account_deletion_jobs")
				.doesNotContain("vector_store");
	}

	@Test
	void usersReceiveDatabaseGeneratedUuidByDefault() {
		UUID id = jdbcTemplate.queryForObject(
				"insert into users (email, password_hash) values (?, ?) returning id",
				UUID.class,
				"migration-test@example.com",
				"not-a-real-password-hash");

		assertThat(id).isNotNull();
	}

	@Test
	void collectionsCannotBeReferencedAcrossUserOwnershipBoundaries() {
		UUID firstUser = createUser("first-owner@example.com");
		UUID secondUser = createUser("second-owner@example.com");
		UUID collection = jdbcTemplate.queryForObject(
				"insert into collections (user_id, name) values (?, 'Private') returning id",
				UUID.class,
				firstUser);

		assertThatThrownBy(() -> jdbcTemplate.update(
						"insert into documents (user_id, collection_id, original_filename, object_key, "
								+ "media_type, file_extension, size_bytes, sha256_hash) "
								+ "values (?, ?, 'notes.txt', ?, 'text/plain', 'txt', 5, ?)",
						secondUser,
						collection,
						"objects/" + UUID.randomUUID(),
						"a".repeat(64)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private UUID createUser(String email) {
		return jdbcTemplate.queryForObject(
				"insert into users (email, password_hash) values (?, 'hash') returning id",
				UUID.class,
				email);
	}
}

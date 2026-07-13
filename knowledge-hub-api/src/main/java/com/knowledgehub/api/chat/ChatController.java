package com.knowledgehub.api.chat;

import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
public class ChatController {

	private final ChatService chatService;
	private final ChatStreamService streamService;

	@PostMapping
	ResponseEntity<ChatDtos.ChatResponse> create(
			@Valid @RequestBody ChatDtos.CreateRequest request, Principal principal) {
		ChatDtos.ChatResponse response =
				chatService.create(principal.getName(), request.title(), request.scope());
		return ResponseEntity.created(URI.create("/api/v1/chats/" + response.id())).body(response);
	}

	@GetMapping
	List<ChatDtos.ChatResponse> list(Principal principal) {
		return chatService.list(principal.getName());
	}

	@PatchMapping("/{id}")
	ChatDtos.ChatResponse rename(
			@PathVariable UUID id,
			@Valid @RequestBody ChatDtos.RenameRequest request,
			Principal principal) {
		return chatService.rename(principal.getName(), id, request.title());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable UUID id, Principal principal) {
		chatService.delete(principal.getName(), id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/messages")
	List<ChatDtos.MessageResponse> messages(@PathVariable UUID id, Principal principal) {
		return chatService.messages(principal.getName(), id);
	}

	@PostMapping(value = "/{id}/messages:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter stream(
			@PathVariable UUID id,
			@Valid @RequestBody ChatDtos.SendRequest request,
			Principal principal) {
		return streamService.stream(principal.getName(), id, request.content(), request.scope());
	}
}

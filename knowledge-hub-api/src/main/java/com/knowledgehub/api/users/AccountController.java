package com.knowledgehub.api.users;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

	private final AccountDeletionService accountDeletionService;

	@DeleteMapping
	ResponseEntity<Void> deleteAccount(
			Principal principal, @Valid @RequestBody DeleteAccountRequest request) {
		accountDeletionService.requestDeletion(
				principal.getName(), request.password(), request.confirmation());
		return ResponseEntity.accepted().build();
	}

	public record DeleteAccountRequest(
			@NotBlank @Size(max = 128) String password, @NotBlank String confirmation) {}
}

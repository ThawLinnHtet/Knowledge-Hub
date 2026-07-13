package com.knowledgehub.api.auth;

import com.knowledgehub.api.users.UserEntity;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtTokenProvider {

	private final JwtProperties properties;

	public String createAccessToken(UserEntity user) {
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(properties.issuer())
				.audience(properties.audience())
				.subject(user.getEmail())
				.claim("uid", user.getId().toString())
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plus(properties.accessTokenTtl())))
				.jwtID(UUID.randomUUID().toString())
				.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
		try {
			jwt.sign(new MACSigner(properties.secret().getBytes(StandardCharsets.UTF_8)));
			return jwt.serialize();
		} catch (JOSEException exception) {
			throw new IllegalStateException("Unable to sign access token", exception);
		}
	}
}

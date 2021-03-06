package io.cord3c.ssi.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cord3c.ssi.api.vc.SignatureVerificationException;
import io.cord3c.ssi.api.vc.VerifiableCredential;
import io.cord3c.ssi.api.internal.W3CHelper;
import io.cord3c.ssi.api.vc.VCCrypto;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@TestInstance(Lifecycle.PER_CLASS)
public class VCCryptoTest implements WithAssertions {

	private ECPublicKey publicKey;

	private ECPrivateKey privateKey;

	private SSIFactory factory;

	private VCCrypto crypto;

	private ObjectMapper claimMapper = new ObjectMapper();

	@BeforeAll
	public void setup() {
		factory = new SSIFactory();
		crypto = factory.getCrypto();

		KeyPair keyPair = crypto.generateKeyPair();
		publicKey = (ECPublicKey) keyPair.getPublic();
		privateKey = (ECPrivateKey) keyPair.getPrivate();
	}

	@Test
	public void encodeAndVerifyVerifiableCredential() {
		VerifiableCredential verifiableCredential = generateMockCredentials();

		assertThatCode(() -> crypto.verify(verifiableCredential, publicKey)).doesNotThrowAnyException();
	}

	@Test
	public void encodeAndVerifyVerifiableCredentialWithWrongPublicKey() {
		VerifiableCredential verifiableCredential = generateMockCredentials();

		ECPublicKey someOtherPublicKey = (ECPublicKey) crypto.generateKeyPair().getPublic();

		assertThatThrownBy(() -> crypto.verify(verifiableCredential, someOtherPublicKey)).isExactlyInstanceOf(SignatureVerificationException.class).hasMessageContaining("Signature verification failed");
	}

	@Test
	public void encodeTamperAndVerifyVerifiableCredentialFails() {
		VerifiableCredential verifiableCredential = generateMockCredentials();

		verifiableCredential.getClaims().put("hello", claimMapper.valueToTree("new world"));

		assertThatThrownBy(() -> crypto.verify(verifiableCredential, publicKey)).isExactlyInstanceOf(SignatureVerificationException.class).hasMessageContaining("Payloads don't match");
	}

	private VerifiableCredential generateMockCredentials() {
		Map<String, JsonNode> claims = new HashMap<>();
		claims.put("hello", claimMapper.valueToTree("world"));
		claims.put(W3CHelper.CLAIM_SUBJECT_ID, claimMapper.valueToTree("jane"));
		VerifiableCredential credential = new VerifiableCredential();
		credential.setContexts(Arrays.asList(W3CHelper.VC_CONTEXT_V1));
		credential.setTypes(Arrays.asList(W3CHelper.DEFAULT_VERIFIABLE_CREDENTIAL, "test"));
		credential.setId("did:mock-id");
		credential.setIssuanceDate(Instant.now());
		credential.setIssuer("did:doe");
		credential.setClaims(claims);
		return crypto.sign(credential, privateKey);
	}

}

package io.cord3c.ssi.serialization;

import io.cord3c.ssi.api.vc.KeyFactoryHelper;
import io.cord3c.ssi.api.vc.VerifiableCredential;
import io.cord3c.ssi.api.vc.W3CHelper;
import io.cord3c.ssi.serialization.internal.party.CordaPartyRegistry;
import io.cord3c.ssi.serialization.setup.VCTestState;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class VerifiableCredentialMapperTest implements WithAssertions {

	private VerifiableCredentialMapper mapper;

	private Party party0;

	private Party party1;

	private CordaPartyRegistry partyRegistry;

	@BeforeEach
	public void setup() {
		party0 = mockParty("STAR Labs");
		party1 = mockParty("Wayne Enterprises");

		Supplier<List<Party>> partySupplier = () -> Arrays.asList(party0, party1);

		partyRegistry = new CordaPartyRegistry("mock-networkmap.org", partySupplier);

		VCSerializationScheme scheme = new VCSerializationScheme(partyRegistry, "http://localhost");
		mapper = scheme.getCredentialMapper();
	}

	@Test
	public void verifyBidirectionalStateCredentialMapping() {
		VCTestState state = new VCTestState();
		state.setIssuerNode(party0);
		state.setSubjectNode(party1);
		state.setTimestamp(Instant.now());
		state.setValue(12);

		VerifiableCredential credential = mapper.toCredential(state);
		assertThat(credential.getIssuanceDate()).isEqualTo(state.getTimestamp());
		assertThat(credential.getClaims().get("value").intValue()).isEqualTo(12);
		assertThat(credential.getIssuer()).isEqualTo(partyRegistry.toDid(party0));
		assertThat(credential.getClaims().get(W3CHelper.CLAIM_SUBJECT_ID).textValue()).isEqualTo(partyRegistry.toDid(party1));
		assertThat(credential.getId()).isEqualTo("http://localhost/VerifiableCredential/FIXME");

		VCTestState mappedState = mapper.fromCredential(credential);
		assertThat(mappedState).isEqualToComparingFieldByField(state);
	}

	private Party mockParty(String name) {
		CordaX500Name cordaX500Name = new CordaX500Name(name, "Mock City", "US");
		PublicKey publicKey = KeyFactoryHelper.generateKeyPair().getPublic();
		return new Party(cordaX500Name, publicKey);
	}

}

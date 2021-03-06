package io.cord3c.ssi.corda.internal.information;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.base.Verify;
import io.cord3c.ssi.annotations.Claim;
import io.cord3c.ssi.annotations.Id;
import io.cord3c.ssi.annotations.IssuanceTimestamp;
import io.cord3c.ssi.annotations.Issuer;
import io.cord3c.ssi.annotations.Json;
import io.cord3c.ssi.annotations.Subject;
import io.cord3c.ssi.annotations.VerifiableCredentialRegistration;
import io.cord3c.ssi.annotations.VerifiableCredentialType;
import io.cord3c.ssi.api.internal.PropertyUtils;
import io.cord3c.ssi.api.internal.W3CHelper;
import io.cord3c.ssi.corda.internal.VerifiableCredentialUtils;
import io.cord3c.ssi.corda.internal.party.PartyAdapterAccessor;
import io.cord3c.ssi.corda.internal.party.PartyRegistry;
import io.crnk.core.engine.information.bean.BeanAttributeInformation;
import io.crnk.core.engine.information.bean.BeanInformation;
import io.crnk.core.engine.internal.utils.UrlUtils;
import lombok.Getter;
import lombok.Setter;
import net.corda.core.identity.Party;

public class VerifiableCredentialRegistry {

	@Getter
	@Setter
	private String baseUrl = PropertyUtils.getProperty(VCProperties.SERVER_URL, null);

	private final PartyRegistry partyRegistry;

	private Map<Class, VerifiableCredentialInformation> credentials = new ConcurrentHashMap<>();

	public VerifiableCredentialRegistry(PartyRegistry partyRegistry) {
		this.partyRegistry = Objects.requireNonNull(partyRegistry);

		ServiceLoader<VerifiableCredentialRegistration> registrations =
				ServiceLoader.load(VerifiableCredentialRegistration.class);
		for (VerifiableCredentialRegistration registration : registrations) {
			for (Class type : registration.getTypes()) {
				get(type);
			}
		}
	}

	public VerifiableCredentialInformation get(Class implementationClass) {
		if (!credentials.containsKey(implementationClass)) {
			credentials.put(implementationClass, constructInformation(implementationClass));
		}
		VerifiableCredentialInformation information = credentials.get(implementationClass);
		if (information == null) {
			throw new IllegalStateException(
					"no information registered about credentials of type " + implementationClass + ", available=" + credentials
							.keySet());
		}
		Verify.verifyNotNull(information);
		return information;
	}

	public VerifiableCredentialInformation get(List<String> types) {
		Optional<VerifiableCredentialInformation> information =
				credentials.values().stream().filter(it -> it.getTypes().equals(types)).findFirst();
		if (!information.isPresent()) {
			throw new IllegalStateException(
					"no information registered about credentials of type " + types + ", available=" + credentials.keySet());
		}
		return information.get();
	}

	private VerifiableCredentialInformation constructInformation(Class implementationClass) {
		VerifiableCredentialInformation information = new VerifiableCredentialInformation();
		information.setImplementationType(implementationClass);
		information.getTypes().addAll(deriveTypes(implementationClass));
		information.setTimestampAccessor(
				VerifiableCredentialUtils.getAccessorForAnnotation(IssuanceTimestamp.class, implementationClass));
		information.getContexts().addAll(createContexts());
		information.setIssuerAccessor(createPartyAccessor(Issuer.class, implementationClass));
		information.setSubjectAccessor(createPartyAccessor(Subject.class, implementationClass));
		information.setIdAccessor(createIdAccessor(information));
		information.setJsonAccessor(createJsonAccessor(information));

		BeanInformation beanInformation = BeanInformation.get(implementationClass);
		for (String name : beanInformation.getAttributeNames()) {
			BeanAttributeInformation attribute = beanInformation.getAttribute(name);
			if (attribute.getAnnotation(Claim.class).isPresent()) {
				ClaimInformation claimInformation = new ClaimInformation();
				claimInformation.setJsonName(attribute.getJsonName());
				claimInformation.setName(name);
				claimInformation.setAccessor(new ReflectionValueAccessor(attribute));
				information.getClaims().put(name, claimInformation);
			}
		}

		ClaimInformation subjectInformation = new ClaimInformation();
		subjectInformation.setJsonName(W3CHelper.CLAIM_SUBJECT_ID);
		subjectInformation.setName(W3CHelper.CLAIM_SUBJECT_ID);
		subjectInformation.setAccessor(information.getSubjectAccessor());
		information.getClaims().put(subjectInformation.getName(), subjectInformation);
		return information;
	}

	private ValueAccessor<String> createJsonAccessor(VerifiableCredentialInformation information) {
		return VerifiableCredentialUtils.getAccessorForAnnotation(Json.class, information.getImplementationType(), false);
	}

	private ValueAccessor<String> createIdAccessor(VerifiableCredentialInformation information) {
		ValueAccessor<String> idElementAccessor =
				VerifiableCredentialUtils.getAccessorForAnnotation(Id.class, information.getImplementationType());
		return new ValueAccessor<String>() {
			@Override
			public String getValue(Object state) {
				String type = information.getTypes().get(1);
				Object idElement = idElementAccessor.getValue(state);
				Verify.verify(baseUrl != null, "call setBaseUrl(...) first");
				if (idElement != null) {
					return UrlUtils.concat(baseUrl, type, idElement.toString());
				}
				return null;
			}

			@Override
			public void setValue(Object state, String fieldValue) {
				if (fieldValue != null) {
					int sep = fieldValue.lastIndexOf("/");
					idElementAccessor.setValue(state, fieldValue.substring(sep + 1));
				}
				else {
					idElementAccessor.setValue(state, null);
				}
			}

			@Override
			public Class<? extends String> getImplementationClass() {
				return String.class;
			}
		};
	}

	private ValueAccessor<String> createPartyAccessor(Class annotation, Class implementationClass) {
		ValueAccessor accessor = VerifiableCredentialUtils.getAccessorForAnnotation(annotation, implementationClass);
		if (accessor.getImplementationClass() == Party.class) {
			Objects.requireNonNull(partyRegistry);
			accessor = new PartyAdapterAccessor(accessor, partyRegistry);
		}
		return accessor;
	}


	private static List<String> createContexts() {
		List<String> contexts = new ArrayList<>();
		contexts.add(W3CHelper.VC_CONTEXT_V1);
		//	contexts.add(W3CHelper.DEFAULT_VC_CONTEXT_2);
		return contexts;
	}

	private static Collection<? extends String> deriveTypes(Class implementationClass) {
		List<String> types = new ArrayList<>();
		types.add(W3CHelper.DEFAULT_VERIFIABLE_CREDENTIAL);

		VerifiableCredentialType annotation =
				(VerifiableCredentialType) implementationClass.getAnnotation(VerifiableCredentialType.class);
		String type = annotation.type();
		if (!type.isEmpty()) {
			types.add(type);
		}
		return types;

	}
}

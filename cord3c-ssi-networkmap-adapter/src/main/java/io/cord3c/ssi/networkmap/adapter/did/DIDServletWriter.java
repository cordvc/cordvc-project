package io.cord3c.ssi.networkmap.adapter.did;

import io.cord3c.ssi.api.did.DIDDocument;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import javax.servlet.http.HttpServletResponse;
import java.io.Writer;

@UtilityClass
public class DIDServletWriter {


	@SneakyThrows
	public static void write(HttpServletResponse response, DIDDocument doc) {
		Writer writer = response.getWriter();
		doc.writePrettyJsonToWriter(writer);
		writer.close();

		response.setStatus(200);
		response.setContentType("application/json");
	}
}
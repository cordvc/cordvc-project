package io.cord3c.monitor.ping;

import lombok.Data;
import net.corda.core.serialization.CordaSerializable;

import java.util.LinkedHashMap;

@CordaSerializable
@Data
public class PingMessage {

	private String message;

	private LinkedHashMap<String, Object> data = new LinkedHashMap<>();
}
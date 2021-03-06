package io.cord3c.example.node;

import com.google.common.base.Verify;
import kotlin.Pair;
import lombok.SneakyThrows;
import net.corda.core.crypto.Crypto;
import net.corda.node.SharedNodeCmdLineOptions;
import net.corda.node.internal.Node;
import net.corda.node.internal.NodeStartup;
import net.corda.node.internal.RunAfterNodeInitialisation;
import net.corda.node.internal.subcommands.InitialRegistration;
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair;
import net.corda.nodeapi.internal.crypto.CertificateType;
import net.corda.nodeapi.internal.crypto.X509Utilities;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;

public class ExampleNode {

	private String networkMapUrl;

	private File configDir;

	private File dataDir;

	private File truststoreFile;

	private File nodeConfigFile;

	private int h2Port = 8084;

	private String databaseUser = "sa";

	private String databasePassword = "helloworld";

	private Server h2Server;

	private Logger log;

	private String env;

	private File cordappDir;

	public static void main(String[] args) {
		ExampleNode node = new ExampleNode();
		node.start();
	}

	@SneakyThrows
	private void start() {
		configure();
		configureLogging();
		if (networkMapUrl == null) {
			generateNetworkParameters();
		}
		configureNode();
		installCordapps();
		startDatabase();
		resetDatabase();
		downloadTrustStore();
		run();
	}

	private void resetDatabase() {
		// clear data in database (but not credentials)
		String url = "jdbc:h2:tcp://localhost:" + h2Port + "/node";
		ResetManager resetManager = new ResetManager(null, url, databaseUser, databasePassword);
		resetManager.softReset();
	}

	@SneakyThrows
	private void installCordapps() {
		// even for docker a bit of copying around to satisfy the Corda directory structure
		File cordappRuntimeDir = new File(dataDir, "cordapps");
		FileUtils.deleteDirectory(cordappRuntimeDir);
		cordappRuntimeDir.mkdirs();
		FileUtils.copyDirectory(cordappDir, cordappRuntimeDir);

		// consider applying liquibase
	}

	@SneakyThrows
	private void generateNetworkParameters() {
		File file = new File(dataDir, "network-parameters");
		if (networkMapUrl == null && !file.exists()) {
			FileUtils.copyFile(new File(configDir, file.getName()), file);
		}
	}

	private void configure() {
		File projectDir = new File("").getAbsoluteFile();
		if (!projectDir.getName().equals("cord3c-example-node")) {
			projectDir = new File(projectDir, "cord3c-example-node");
		}

		if (!projectDir.exists()) {
			configDir = new File("/etc/cord3c/");
			dataDir = new File("/var/cord3c");
			cordappDir = new File("/opt/cord3c/cordapps");
		} else {
			configDir = new File(projectDir, "src/main/extraFiles/etc/cord3c/");
			dataDir = new File(projectDir, "build/data");
			cordappDir = new File(projectDir, "build/extraFiles/opt/cord3c/cordapps");
		}
		dataDir.mkdirs();
		Verify.verify(configDir.exists(), configDir.getAbsolutePath());
		Verify.verify(cordappDir.exists(), cordappDir.getAbsolutePath());

		env = PropertyUtils.getProperty("cord3c.env", "dev");
		networkMapUrl = PropertyUtils.getProperty("cord3c.networkmap.url", null);
		System.setProperty("cord3c.server.url", "http://localhost:8090");
		System.setProperty("cord3c.networkmap.url", "http://localhost:8080");
		System.setProperty("cord3c.rest.contextPath", "/api/node/");
	}

	@SneakyThrows
	private void startDatabase() {
		h2Server = Server.createTcpServer("-tcpPassword", databasePassword, "-tcpPort", Integer.toString(h2Port),
				"-ifNotExists", "-tcpAllowOthers",
				"-tcpDaemon", "-baseDir", dataDir.getAbsolutePath()).start();
		if (h2Server.isRunning(true)) {
			log.info("H2 server was started and is running on port " + h2Port
					+ " use jdbc:h2:tcp://localhost:" + h2Port + "/node");
		} else {
			throw new IllegalStateException("Could not start H2 server.");
		}
	}


	@SneakyThrows
	private void configureNode() {
		nodeConfigFile = new File(configDir, "node-" + env + ".conf");
		String nodeConfig = FileUtils.readFileToString(nodeConfigFile, StandardCharsets.UTF_8);
		if (networkMapUrl != null) {
			System.setProperty("cord3c.networkmap.url", networkMapUrl);
			nodeConfig = nodeConfig.replace("${networkMapUrl}", networkMapUrl);
		} else {
			System.setProperty("cord3c.networkmap.url", "http://no-networkmap");
		}
		FileUtils.writeStringToFile(new File(dataDir, "node.conf"), nodeConfig, StandardCharsets.UTF_8);
		truststoreFile = new File(dataDir, "network-truststore.jks");
	}

	private void configureLogging() {
		File logFile = new File(configDir, "log4j2.xml");
		Verify.verify(logFile.exists(), logFile.getAbsolutePath());
		System.setProperty("log4j.configurationFile", logFile.getAbsolutePath());
		log = LoggerFactory.getLogger(getClass());
		System.setProperty("net.corda.node.printErrorsToStdErr", "true");

		log.info("starting up cord3c example app");
		log.info("using env={}", env);
		if (networkMapUrl != null) {
			log.info("using networkMap={}", networkMapUrl);
		} else {
			log.info("network map disabled");
		}
	}

	@SneakyThrows
	private void run() {
		NodeStartup startup = new NodeStartup();
		RunAfterNodeInitialisation registration = node -> {
			if (networkMapUrl != null && !isRegistered()) {
				Verify.verify(truststoreFile.exists());
				InitialRegistration initialRegistration = new InitialRegistration(dataDir.toPath(), truststoreFile.toPath(), "trustpass", startup);
				initialRegistration.run(node);
				log.warn("*************************************************************************************************************");
				log.warn("performed registration with network map, please restart or fix https://github.com/corda/corda/issues/6318 :-)");
				log.warn("*************************************************************************************************************");
				System.exit(0);
			}
			startup.startNode(node, System.currentTimeMillis());
		};
		boolean requireCertificates = false;

		SharedNodeCmdLineOptions cmdOptions = new SharedNodeCmdLineOptions();
		cmdOptions.setBaseDirectory(dataDir.toPath());
		cmdOptions.setDevMode(false);

		startup.initialiseAndRun(cmdOptions, registration, requireCertificates);
	}

	private boolean isRegistered() {
		return Arrays.asList(dataDir.listFiles()).stream().filter(it -> it.getName().startsWith("nodeInfo-")).findAny().isPresent();
	}

	@SneakyThrows
	private void downloadTrustStore() {
		if (networkMapUrl != null) {
			String url = networkMapUrl + "/network-map/truststore";
			try (CloseableHttpClient client = HttpClientBuilder.create().useSystemProperties().build()) {
				HttpGet get = new HttpGet(url);
				CloseableHttpResponse response = client.execute(get);
				Verify.verify(response.getStatusLine().getStatusCode() == 200);
				FileUtils.writeByteArrayToFile(truststoreFile, EntityUtils.toByteArray(response.getEntity()));
			}
		}
	}
}

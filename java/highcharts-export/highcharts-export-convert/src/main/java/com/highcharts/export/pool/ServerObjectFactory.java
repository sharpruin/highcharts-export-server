package com.highcharts.export.pool;

import com.highcharts.export.server.Server;
import com.highcharts.export.server.ServerState;
import com.highcharts.export.util.TempDir;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.TreeMap;

public class ServerObjectFactory implements ObjectFactory<Server> {

	public String exec;
	public String script;
	private String host;
	private int basePort;
	private int readTimeout;
	private int poolSize;
	private int connectTimeout;
	private int maxTimeout;
	private static TreeMap<Integer, PortStatus> portUsage = new TreeMap<>();
	protected static Logger logger = Logger.getLogger("pool");

	private enum PortStatus {
        BUSY,
        FREE;
	}

	@Override
	public Server create() {
		logger.debug("in makeObject, " + exec + ", " +  script + ", " +  host);
		Integer port = this.getAvailablePort();
		String separator = FileSystems.getDefault().getSeparator();

        if (script.isEmpty()) {
            // use the bundled highcharts-convert.js script
            script = TempDir.getPhantomJsDir().toAbsolutePath().toString() + separator + "highcharts-convert.js";
        }
        Server server = new Server(exec, script, host, port, connectTimeout, readTimeout, maxTimeout);
		portUsage.put(port, PortStatus.BUSY);
		return server;
	}

	@Override
	public boolean validate(Server server) {
		boolean isValid = false;
		try {
			if(server.getState() != ServerState.IDLE) {
				logger.debug("server didn\'t pass validation");
				return false;
			}
			String result = server.request("{\"status\":\"isok\"}");
			if(result.indexOf("OK") > -1) {
				isValid = true;
				logger.debug("server passed validation");
			} else {
				logger.debug("server didn\'t pass validation");
			}
		} catch (Exception e) {
			logger.error("Error while validating object in Pool: " + e.getMessage());
		}
		return isValid;
	}

	@Override
	public void destroy(Server server) {
		ServerObjectFactory.releasePort(server.getPort());
		server.cleanup();
	}

	@Override
	public void activate(Server server) {
		server.setState(ServerState.ACTIVE);
	}

	@Override
	public void passivate(Server server) {
		server.setState(ServerState.IDLE);
	}

	public static void releasePort(Integer port) {
		logger.debug("Releasing port " + port);
		portUsage.put(port, PortStatus.FREE);
	}

	public Integer getAvailablePort() {

		/* first we check within the defined port range from baseport
		* up to baseport + poolsize
		*/
		int port = basePort;
		for (; port < basePort + poolSize; port++) {

			if (portUsage.containsKey(port)) {
				if (portUsage.get(port) == PortStatus.FREE) {
					return port;
				}
			} else {
				// doesn't exist any longer, but is within the valid port range
				return port;
			}

		}

		// at this point there is no free port, we have to look outside of the valid port range
		logger.debug("Nothing free in Portusage " + portUsage.toString());
		return portUsage.lastKey() + 1;
	}

	/*Getters and Setters*/

	public String getExec() {
		return exec;
	}

	public void setExec(String exec) {
		this.exec = exec;
	}

	public String getScript() {
		return script;
	}

	public void setScript(String script) {
		this.script = script;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getBasePort() {
		return basePort;
	}

	public void setBasePort(int basePort) {
		this.basePort = basePort;
	}

	public int getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(int readTimeout) {
		this.readTimeout = readTimeout;
	}

	public int getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(int connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public int getMaxTimeout() {
		return maxTimeout;
	}

	public void setMaxTimeout(int maxTimeout) {
		this.maxTimeout = maxTimeout;
	}

	public int getPoolSize() {
		return poolSize;
	}

	public void setPoolSize(int poolSize) {
		this.poolSize = poolSize;
	}

	@PostConstruct
	public void afterBeanInit() {
		
		URL u = getClass().getProtectionDomain().getCodeSource().getLocation();
		URLClassLoader jarLoader = new URLClassLoader(new URL[]{u}, Thread.currentThread().getContextClassLoader());
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(jarLoader);
		try {
			Resource[] resources = resolver.getResources("classpath*:/phantomjs/*.js*");
			for (Resource resource : resources) {
				logger.info("Copying " + resource.getFilename() + " to " + TempDir.getPhantomJsDir());
				Path path = Paths.get(TempDir.getPhantomJsDir().toString(), resource.getFilename());
				File f = Files.createFile(path).toFile();
				f.deleteOnExit();

				try (InputStream in = resource.getInputStream();
				     OutputStream out=new FileOutputStream(f))
				{
					IOUtils.copy(in, out);
				}
			}
		} catch (IOException ioex) {
			logger.error("Error while setting up phantomjs environment: " + ioex.getMessage());
			ioex.printStackTrace();
		}
	}



}

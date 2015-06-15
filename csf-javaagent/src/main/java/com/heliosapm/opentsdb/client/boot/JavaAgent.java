/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.heliosapm.opentsdb.client.boot;

import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.util.Properties;

import javax.management.ObjectName;

import com.heliosapm.utils.classload.IsolatedClassLoader;

/**
 * <p>Title: JavaAgent</p>
 * <p>Description: The agent bootstrap used when the JVM is started with <b><code>-javaagent</code></b>
 * or using a hot install (attach).</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.boot.JavaAgent</code></p>
 */

public class JavaAgent {
	//  path will be [lib/csf-core-1.0-SNAPSHOT.jar]
	// version is [1.0-SNAPSHOT]
	// from manifest:   agent-core: lib/csf-core-1.0-SNAPSHOT.jar
	/*
	 * Add to conf/hbase-env.cmd[sh]:
:: ===============================================================================================================================================================================
::		Install tsdb-csf agent 
:: ===============================================================================================================================================================================
set HBASE_OPTS="-javaagent:c:\hprojects\tsdb-csf\csf-javaagent\target\csf-javaagent-1.0-SNAPSHOT.jar=-config file:/c:\hprojects\tsdb-csf\csf-core\hbase.xml" %HBASE_OPTS%
:: ===============================================================================================================================================================================

OR

# ===============================================================================================================================================================================
#		Install tsdb-csf agent 
# ===============================================================================================================================================================================
export JAGENT="/home/nwhitehead/hprojects/tsdb-csf/csf-javaagent/target/csf-javaagent-1.0-SNAPSHOT.jar"
export JAGENT_ARGS=""-config file:/home/nwhitehead/hprojects/tsdb-csf/csf-core/hbase.xml""
export HBASE_OPTS="-javaagent:$JAGENT=$JAGENT_ARGS $HBASE_OPTS"
# ===============================================================================================================================================================================

	 */
	
	/** The default class to instantiate and boot */
	public static final String MANUAL_LOAD_CLASS = "com.heliosapm.opentsdb.client.boot.ManualLoader";
	/** The XML loader boot class */
	public static final String XML_LOAD_CLASS = "com.heliosapm.opentsdb.client.boot.XMLLoader";
	/** The property set in system and agent props to indicate the agent is installed */
	public static final String AGENT_INSTALLED_PROP = "tsdb.csf.agent.installed"; 
	
	/** The JVM instrumentation instance */
	public static Instrumentation INSTRUMENTATION = null;
	/** The arguments passed to this agent */
	public static String AGENT_ARGS = null;
	
	/** The isolated classloader ObjectName */
	public static final String ISOL_OBJECT_NAME = "com.heliosapm:service=IsolatedClassLoader,source=agent-premain";
	
	/** The instrumentation capture service class name */
	public static final String INSTRUMENTATION_SERVICE_CLASS = "com.heliosapm.utils.instrumentation.Instrumentation";

	
	
	/** The full agent class loader */
	public static ClassLoader agentClassLoader = null;
	
	
	/**
	 * Creates a new JavaAgent
	 */
	private JavaAgent() {}
	
	/**
	 * Sets the agent-installed properties in the agent and system properties
	 */
	private static void markAgentInstalled() {
		try {
			Properties p = (Properties)Class.forName("sun.misc.VMSupport").getDeclaredMethod("getAgentProperties").invoke(null);
			p.setProperty(AGENT_INSTALLED_PROP, "true");			
		} catch (Exception ex) {
			/* No Op */
		}
		System.setProperty(AGENT_INSTALLED_PROP, "true");
	}
	
	/**
	 * System out format logger
	 * @param fmt The message format
	 * @param args The message args
	 */
	public static void log(final Object fmt, final Object...args) {
		System.out.println("[CSF-JavaAgent]" + String.format(fmt.toString(), args));
	}
	
	/**
	 * System err format logger
	 * @param fmt The message format
	 * @param args The message args
	 */
	public static void loge(final Object fmt, final Object...args) {
		System.err.println("[CSF-JavaAgent]" + String.format(fmt.toString(), args));
	}
	
	/**
	 * The agent boot entry point
	 * @param agentArgs The agent configuration arguments
	 * @param inst The instrumentation instance
	 */
	public static void premain(final String agentArgs, final Instrumentation inst) {
		log("Entering premain with agent args [%s]", agentArgs);
		INSTRUMENTATION = inst;
		final ClassLoader cl = JavaAgent.class.getClassLoader();
		final URL codeSource = JavaAgent.class.getProtectionDomain().getCodeSource().getLocation();
		final String version = JavaAgent.class.getPackage().getImplementationVersion();
		final String vname = JavaAgent.class.getPackage().getImplementationTitle();
		final String spec = JavaAgent.class.getPackage().getSpecificationTitle();
		
		log("\n\tjavaAgent Code Source: [%s]\n\tClassLoader: [%s]\n\tVersion: [%s]\n\tName: [%s]\n\tSpec: [%s]", codeSource, cl, version, vname, spec);
		try {
			agentClassLoader = IsolatedClassLoader.embeddedJarClassLoader(codeSource, "agent-core", new ObjectName(ISOL_OBJECT_NAME));
		} catch (Exception ex) {
			loge("Failed to created IsolatedClassLoader for agent core jar [" + codeSource + "]. Stack trace follows...");
			ex.printStackTrace(System.err);
			return;
		}
		try {
			final Thread bootThread = new Thread("csf-javaagent-boot-thread") {
				@Override
				public void run() {					
					Thread.currentThread().setContextClassLoader(agentClassLoader);
					bootAgent(agentArgs.trim());
					markAgentInstalled();
				}
			};
			bootThread.setDaemon(true);
			bootThread.start();
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}		
	}
	
	
	/**
	 * Boots the agent
	 * @param configUrl The URL of the config file
	 */
	protected static void bootAgent(final String configUrl) {
		log("Booting agent with config: [%s]", configUrl);
		System.setProperty("Log4jLogEventFactory", "org.apache.logging.log4j.core.impl.Log4jContextFactory");
		System.setProperty("log4j.configurationFactory", "com.heliosapm.opentsdb.client.logging.CSFLoggingConfiguration");
		try {
			Class<?> instrClass = Class.forName(INSTRUMENTATION_SERVICE_CLASS, true, agentClassLoader);
			instrClass.getDeclaredMethod("install", Instrumentation.class).invoke(null, INSTRUMENTATION);
			log("Instrumentation Service Installed.");
		} catch (Exception ex) {
			loge("Failed to install JVM instrumentation instance. Stack trace follows:");
			ex.printStackTrace(System.err);
		}		
		try {
			Class<?> bootClass = Class.forName(XML_LOAD_CLASS, true, agentClassLoader);
			bootClass.getDeclaredMethod("boot", String.class).invoke(null, configUrl);
		} catch (Exception ex) {
			loge("Failed to load XMLConfig. Stack trace follows:");
			ex.printStackTrace(System.err);
		}
	}

	/**
	 * Boots the agent on a javaagent enabled JVM boot
	 * @param agentArgs The agent arguments
	 */
	public static void premain(final String agentArgs) {		
		premain(agentArgs, null);
	}
	
//	protected static boolean dumpCoreJar(final URL url) {
//	InputStream is = null;
//	JarInputStream jis = null;
//	try {
//		is = url.openStream();
//		jis = new JarInputStream(is, true);
//		JarEntry je = null;
//		while((je = jis.getNextJarEntry())!=null) {
//			//log("\t\t--- [%s]", je.getName());
//		}
//		return true;
//	} catch (Exception ex) {
//		ex.printStackTrace(System.err);
//		return false;
//	} finally {
//		if(jis != null) try { jis.close(); } catch (Exception x) {/* No Op */}
//		if(is != null) try { is.close(); } catch (Exception x) {/* No Op */}
//	}		
//}
	
	
}

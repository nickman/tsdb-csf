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

import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import javax.management.ObjectName;

/**
 * <p>Title: JavaAgent</p>
 * <p>Description: The agent bootstrap used when the JVM is started with <b><code>-javaagent</code></b>
 * or using a hot install (attach).</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.boot.JavaAgent</code></p>
 */

public class JavaAgent {
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
	
	/** The isolated classloader class name */
	public static final String ISOL_CLASSLOADER_NAME = "com.heliosapm.utils.classload.IsolatedClassLoader";
	/** The isolated classloader ObjectName */
	public static final String ISOL_OBJECT_NAME = "com.heliosapm:service=IsolatedClassLoader";
	
	
	/** The agent args delimiter */
	public static final String ARGS_DELIM = "|~";
	/** The full agent class loader */
	public static ClassLoader agentClassLoader = null;
	
	/** The system class loader search path for classes and resources */ 
	private static volatile Object systemURLClassPath = null;
	
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
		
		final ClassLoader cl = JavaAgent.class.getClassLoader();
		final URL codeSource = JavaAgent.class.getProtectionDomain().getCodeSource().getLocation();
		final URLClassLoader systemcl = (URLClassLoader)ClassLoader.getSystemClassLoader();
		
		// systemcl.ucp.path (arraylist)
		// systemcl.ucp.urls (stack)
		final String version = JavaAgent.class.getPackage().getImplementationVersion();
		final String vname = JavaAgent.class.getPackage().getImplementationTitle();
		final String spec = JavaAgent.class.getPackage().getSpecificationTitle();
		
		log("\n\tjavaAgent Code Source: [%s]\n\tClassLoader: [%s]\n\tVersion: [%s]\n\tName: [%s]\n\tSpec: [%s]", codeSource, cl, version, vname, spec);
		//  path will be [lib/csf-core-1.0-SNAPSHOT.jar]
		// version is [1.0-SNAPSHOT]
		// from manifest:   agent-core: lib/csf-core-1.0-SNAPSHOT.jar
		/*
		 * Add to conf/hbase-env.cmd[sh]:
		 * :: ===============================================================================================================================================================================
		 * ::		Install tsdb-csf agent 
		 * :: ===============================================================================================================================================================================
		 * set HBASE_OPTS="-javaagent:c:\hprojects\tsdb-csf\csf-javaagent\target\csf-javaagent-1.0-SNAPSHOT.jar=-config file:/c:\hprojects\tsdb-csf\csf-core\hbase.xml" %HBASE_OPTS%
		 * :: ===============================================================================================================================================================================
		 */
		URL hiddenClassLoaderURL = null;
		InputStream is = null;
		JarInputStream jis = null;
		try {
			is = codeSource.openStream();
			jis = new JarInputStream(is, false);
			JarEntry je = null;
			while((je = jis.getNextJarEntry()) != null) {
				final String name = je.getName();
				log("--------------------------------------> [%s]", name);
			}
			
			
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		} finally {
			if(jis != null) try { jis.close(); } catch (Exception x) {/* No Op */}
			if(is != null) try { is.close(); } catch (Exception x) {/* No Op */}
		}
		agentmain(agentArgs + ARGS_DELIM + codeSource.toString(), inst);
	}
	
	/**
	 * The agent boot entry point
	 * @param agentArgs The agent configuration arguments
	 * @param inst The instrumentation instance
	 */
	public static void premainx(final String agentArgs, final Instrumentation inst) {
		ClassLoader isolatedClassLoader = null;
//		try {			
//			Class<?> isoClazz = Class.forName(ISOL_CLASSLOADER_NAME);
//			Constructor<?> ctor = isoClazz.getDeclaredConstructor(URL.class);
//			URL classpath = JavaAgent.class.getProtectionDomain().getCodeSource().getLocation();
//			log("Agent Classpath: [%s]", classpath);
//			isolatedClassLoader = new ParentLastURLClassLoader(classpath);
//			Thread.currentThread().setContextClassLoader(isolatedClassLoader);
//			MLet mlet = new MLet(new URL[0], isolatedClassLoader);
//			ObjectName objectName = new ObjectName("com.heliosapm.boot:service=AgentClassLoader");
//			ManagementFactory.getPlatformMBeanServer().registerMBean(mlet, objectName);			
//		} catch (Exception ex) {
//			ex.printStackTrace(System.err);
//			return;
//		}
//		AGENT_ARGS = agentArgs;
//		INSTRUMENTATION = inst;
//		try {			
//			if(!processArgs(agentArgs)) {				
//				log("Executing premain.....\n\tLoading class [" + MANUAL_LOAD_CLASS + "]");
//				Class<?> bootClass = Class.forName(MANUAL_LOAD_CLASS, true, isolatedClassLoader);
//				log("Loaded class [" + MANUAL_LOAD_CLASS + "]\n\tInvoking boot....");
//				bootClass.getDeclaredMethod("boot").invoke(null);				
//			}
//			markAgentInstalled();
//			log("\n\n\n\tAgent Booted. Bye...");			
//		} catch (Throwable ex) {
//			loge("Failed to boot. Stack trace follows:");
//			ex.printStackTrace(System.err);
//			log("Failed to boot. Stack trace follows:");
//			ex.printStackTrace(System.out);			
//		}
	}
	
	/**
	 * Processes the agent arguments passed at boot time (from the -javaagent) or from the agent installer
	 * @param argStr The agent arguments string
	 * @return true if a configuration directive was executed
	 */
	protected static boolean processArgs(final String argStr) {
		log("Processing AgentArgs: [%s]", argStr);
		if(argStr==null || argStr.trim().isEmpty()) return false;
		boolean configured = false;
		String[] options = argStr.split("\\|~");
		for(int i = 0; i < options.length; i++) {
			if("-p".equalsIgnoreCase(options[i])) {
				i++;
				String[] sysPropPair = options[i].split("=");
				System.setProperty(sysPropPair[0].trim(), sysPropPair[1].trim());
				System.out.println("Set SysProp [" + sysPropPair[0].trim() + "]=[" + sysPropPair[1].trim() + "]");
			} else if ("-config".equalsIgnoreCase(options[i])) {
				try {
					Class<?> bootClass = Class.forName(XML_LOAD_CLASS, true, agentClassLoader);
					i++;
					String resource = options[i]; 
					log("Loaded class [%s]\n\tInvoking boot with [%s]", XML_LOAD_CLASS, resource);
					bootClass.getDeclaredMethod("boot", String.class).invoke(null, resource);
					configured = true;
				} catch (Exception ex) {
					loge("Failed to load XMLConfig. Stack trace follows:");
					ex.printStackTrace(System.err);
				}

			}
		}
		return configured;
	}

	/**
	 * Boots the agent on a javaagent enabled JVM boot
	 * @param agentArgs The agent arguments
	 */
	public static void premain(final String agentArgs) {		
		premain(agentArgs, null);
	}
	
	/**
	 * Boots the agent on a hot install
	 * @param agentArgs The agent arguments
	 * @param inst The instrumentation instance
	 */
	public static void agentmain(final String agentArgs, final Instrumentation inst) {
		log("Entering agentmain (args,inst)");
		if(agentArgs==null || agentArgs.trim().isEmpty()) {
			log("No args. We're outa here....");
			return;
		}
		log("AgentArgs: [%s]", agentArgs);
		final String[] parsedArgs = agentArgs.trim().split("\\" + ARGS_DELIM);
		if(parsedArgs.length <2) {
			log("Insufficient args: \n[%s]\nWe're outa here...", Arrays.toString(parsedArgs));
			return;			
		}
		try {
			final ObjectName agentCLObjectName = new ObjectName(ISOL_OBJECT_NAME);
			final URL classLoaderClassLoaderUrl = JavaAgent.class.getProtectionDomain().getCodeSource().getLocation();
			log("JavaAgent Code Location: [%s]", JavaAgent.class.getProtectionDomain().getCodeSource().getLocation());
			final URLClassLoader classLoaderClassLoader = new URLClassLoader(new URL[]{classLoaderClassLoaderUrl}, JavaAgent.class.getClassLoader());			
			Class<?> isolClass = classLoaderClassLoader.loadClass(ISOL_CLASSLOADER_NAME);			
			Constructor<?> ctor = isolClass.getDeclaredConstructor(ObjectName.class, URL[].class);
			ctor.setAccessible(true);
			final URL classLoaderUrl = new URL(parsedArgs[parsedArgs.length-1]);
			log("Core agent classpath: [%s]", classLoaderUrl);
			try {
				Method m = URLClassLoader.class.getDeclaredMethod("close");
				m.setAccessible(true);
				m.invoke(classLoaderClassLoader);
			} catch (Exception x) { /* No Op */}
			agentClassLoader = (ClassLoader) ctor.newInstance(agentCLObjectName, new URL[]{classLoaderUrl});	
			Thread.currentThread().setContextClassLoader(agentClassLoader);
			// ==================
			final int index = agentArgs.lastIndexOf(ARGS_DELIM);
			processArgs(agentArgs.trim().substring(0, index));
			markAgentInstalled();
		} catch (Exception ex) {
			loge("Failed to process agent arguments. Stack trace follows...");
			ex.printStackTrace(System.err);
		}		
	}

	/**
	 * Boots the agent on a hot install
	 * @param agentArgs The agent arguments
	 */
	public static void agentmain(final String agentArgs) {
		log("Entering agentmain (args)");
		agentmain(agentArgs, null);
	}
	
	
}

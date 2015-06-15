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

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Properties;

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
	
	/** The instrumentation capture service class name */
	public static final String INSTRUMENTATION_SERVICE_CLASS = "com.heliosapm.utils.instrumentation.Instrumentation";
	
	/** The isolated classloader class name */
	public static final String ISOL_CLASSLOADER_NAME = "com.heliosapm.utils.classload.IsolatedClassLoader";
	/** The isolated classloader ObjectName */
	public static final String ISOL_OBJECT_NAME = "com.heliosapm:service=IsolatedClassLoader,source=agent-agentmain";
	
	
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
	 * Boots the agent
	 * @param configUrl The URL of the config file
	 */
	protected static void bootAgent(final String configUrl) {
		log("Booting agent with config: [%s] / [%s]", configUrl, agentClassLoader);
		System.setProperty("Log4jLogEventFactory", "org.apache.logging.log4j.core.impl.Log4jContextFactory");
		System.setProperty("log4j.configurationFactory", "com.heliosapm.opentsdb.client.logging.CSFLoggingConfiguration");
		try {
			log("Instrumentation Service Installing.....");
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
	
	private static URL toURL(final String s) {
		URL url = null;
		try {
			url = new URL(s.trim());
			return url;
		} catch (Exception ex) {
			File f = new File(s.trim());
			if(f.canRead()) {
				try {
					url = f.toURI().toURL();
				} catch (Exception ex2) {
					url = null;
				}
			}
		}
		return url;
	}
	
/*
"+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
[CSF-JavaAgent]Entering agentmain (args,inst)
[CSF-JavaAgent]AgentArgs: [hbase.xml,file:/C:/hprojects/tsdb-csf/csf-core/target/csf-core-1.0-SNAPSHOT.jar]
[CSF-JavaAgent]JavaAgent Code Location: [file:/C:/temp/tsdb-csf-javaagent5972264408179542890.jar]
[CSF-JavaAgent]Core agent classpath: [file:/C:/hprojects/tsdb-csf/csf-core/target/csf-core-1.0-SNAPSHOT.jar]
[CSF-JavaAgent]Booting agent with config: [hbase.xml] / [com.heliosapm.utils.classload.IsolatedClassLoader@3b6a62e5]
Exception in thread "Attach Listener" java.lang.reflect.InvocationTargetException
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:606)
        at sun.instrument.InstrumentationImpl.loadClassAndStartAgent(InstrumentationImpl.java:382)
        at sun.instrument.InstrumentationImpl.loadClassAndCallAgentmain(InstrumentationImpl.java:407)
Caused by: java.lang.NoClassDefFoundError: com/heliosapm/utils/classload/IsolatedClassLoader$ChildURLClassLoader$1
        at com.heliosapm.utils.classload.IsolatedClassLoader$ChildURLClassLoader._findClass(IsolatedClassLoader.java:341)
        at com.heliosapm.utils.classload.IsolatedClassLoader$ChildURLClassLoader.findClass(IsolatedClassLoader.java:395)
        at com.heliosapm.utils.classload.IsolatedClassLoader.loadClass(IsolatedClassLoader.java:206)
        at java.lang.ClassLoader.loadClass(ClassLoader.java:357)
        at java.lang.Class.forName0(Native Method)
        at java.lang.Class.forName(Class.java:270)
        at com.heliosapm.opentsdb.client.boot.JavaAgent.bootAgent(JavaAgent.java:113)
        at com.heliosapm.opentsdb.client.boot.JavaAgent.agentmain(JavaAgent.java:199)
        ... 6 more
Caused by: java.lang.ClassNotFoundException: com.heliosapm.utils.classload.IsolatedClassLoader$ChildURLClassLoader$1
        at java.net.URLClassLoader$1.run(URLClassLoader.java:366)
        at java.net.URLClassLoader$1.run(URLClassLoader.java:355)
        at java.security.AccessController.doPrivileged(Native Method)
        at java.net.URLClassLoader.findClass(URLClassLoader.java:354)
        at java.lang.ClassLoader.loadClass(ClassLoader.java:424)
        at sun.misc.Launcher$AppClassLoader.loadClass(Launcher.java:308)
        at java.lang.ClassLoader.loadClass(ClassLoader.java:357)
        ... 14 more
 */

	

	
	/**
	 * Boots the agent on a hot install
	 * @param agentArgs The agent arguments:  Comma separated <ol>
	 * 	<li>The fully qualified path of the core agent jar<li>
	 *  <li>The URL of the XML configuration file</li>
	 * </ol>
	 * @param inst The instrumentation instance
	 */
	public static void agentmain(final String agentArgs, final Instrumentation inst) {
		log("Entering agentmain (args,inst)");
		INSTRUMENTATION = inst;
		if(agentArgs==null || agentArgs.trim().isEmpty()) {
			log("No args. We're outa here....");
			return;
		}
		log("AgentArgs: [%s]", agentArgs);
		final String[] parsedArgs = agentArgs.trim().split(",");
		if(parsedArgs.length <2) {
			log("Insufficient args: \n[%s]\nWe're outa here...", Arrays.toString(parsedArgs));
			return;			
		}
		URL configUrl = toURL(parsedArgs[1]);
		if(configUrl==null) {
			loge("\n\tInvalid XML Configuration URL: [%s]\n", parsedArgs[0]);
			return;
		}
		
		final String CORE_AGENT_JAR = configUrl.toString();
		final String CORE_AGENT_CONFG_XML = parsedArgs[0];
		URLClassLoader classLoaderClassLoader = null;
		try {
			final ObjectName agentCLObjectName = new ObjectName(ISOL_OBJECT_NAME);
			final URL classLoaderClassLoaderUrl = JavaAgent.class.getProtectionDomain().getCodeSource().getLocation();
			log("JavaAgent Code Location: [%s]", JavaAgent.class.getProtectionDomain().getCodeSource().getLocation());
			classLoaderClassLoader = new URLClassLoader(new URL[]{classLoaderClassLoaderUrl}, JavaAgent.class.getClassLoader());			
			Class<?> isolClass = classLoaderClassLoader.loadClass(ISOL_CLASSLOADER_NAME);			
			Constructor<?> ctor = isolClass.getDeclaredConstructor(ObjectName.class, URL[].class);
			ctor.setAccessible(true);
			final URL classLoaderUrl = new URL(CORE_AGENT_JAR);
			log("Core agent classpath: [%s]", classLoaderUrl);
			agentClassLoader = (ClassLoader) ctor.newInstance(agentCLObjectName, new URL[]{classLoaderUrl});	
			log("Core Agent Class Loader: [%s]", agentClassLoader.toString());
			Thread.currentThread().setContextClassLoader(agentClassLoader);
			// ==================
			bootAgent(CORE_AGENT_CONFG_XML);
			markAgentInstalled();
		} catch (Exception ex) {
			loge("Failed to process agent arguments. Stack trace follows...");
			ex.printStackTrace(System.err);
		} finally {
			try {
				Method m = URLClassLoader.class.getDeclaredMethod("close");
				m.setAccessible(true);
				m.invoke(classLoaderClassLoader);
			} catch (Exception x) { /* No Op */}			
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

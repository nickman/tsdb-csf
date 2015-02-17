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
import java.util.Properties;

/**
 * <p>Title: JavaAgent</p>
 * <p>Description: </p> 
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

	
	public static void main(final String[] args) {
		// Commands
			// List running JVMs
			//install to other JVM
	}
	
	/**
	 * The agent boot entry point
	 * @param agentArgs The agent configuration arguments
	 * @param inst The instrumentation instance
	 */
	public static void premain(final String agentArgs, final Instrumentation inst) {
		AGENT_ARGS = agentArgs;
		INSTRUMENTATION = inst;
		try {			
			if(!processArgs(agentArgs)) {
				ClassLoader cl = JavaAgent.class.getClassLoader();
				System.out.println("Executing premain.....\n\tLoading class [" + MANUAL_LOAD_CLASS + "]");
				Class<?> bootClass = Class.forName(MANUAL_LOAD_CLASS, true, cl);
				System.out.println("Loaded class [" + MANUAL_LOAD_CLASS + "]\n\tInvoking boot....");
				bootClass.getDeclaredMethod("boot").invoke(null);				
			}
			markAgentInstalled();
			log("\n\n\n\tAgent Booted. Bye...");			
		} catch (Throwable ex) {
			loge("Failed to boot. Stack trace follows:");
			ex.printStackTrace(System.err);
			log("Failed to boot. Stack trace follows:");
			ex.printStackTrace(System.out);
			
		}
	}
	
	/**
	 * Processes the agent arguments passed at boot time (from the -javaagent) or from the agent installer
	 * @param argStr The agent arguments string
	 * @return true if a configuration directive was executed
	 */
	protected static boolean processArgs(final String argStr) {
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
				ClassLoader cl = JavaAgent.class.getClassLoader();
				try {
					Class<?> bootClass = Class.forName(XML_LOAD_CLASS, true, cl);
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

	public static void premain(final String agentArgs) {
		premain(agentArgs, null);
	}
	
	public static void agentmain(final String agentArgs, final Instrumentation inst) {
		premain(agentArgs, inst);
	}

	public static void agentmain(final String agentArgs) {
		premain(agentArgs, null);
	}
	
	
	
	

}

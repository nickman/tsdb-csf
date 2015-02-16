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

/**
 * <p>Title: JavaAgent</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.boot.JavaAgent</code></p>
 */

public class JavaAgent {
	/** The class to instantiate and boot */
	public static final String LOAD_CLASS = "com.heliosapm.opentsdb.client.boot.ManualLoader";
	
	public static Instrumentation INSTRUMENTATION = null;
	public static String AGENT_ARGS = null;
	
	/**
	 * Creates a new JavaAgent
	 */
	private JavaAgent() {}
	
	public static void main(final String[] args) {
		// Commands
			// List running JVMs
			//install to other JVM
	}
	
	public static void premain(final String agentArgs, final Instrumentation inst) {
		AGENT_ARGS = agentArgs;
		INSTRUMENTATION = inst;
		try {
			processArgs(agentArgs);
			ClassLoader cl = JavaAgent.class.getClassLoader();
			System.out.println("Executing premain.....\n\tLoading class [" + LOAD_CLASS + "]");
			Class<?> bootClass = Class.forName(LOAD_CLASS, true, cl);
			System.out.println("Loaded class [" + LOAD_CLASS + "]\n\tInvoking boot....");
			bootClass.getDeclaredMethod("boot").invoke(null);
			System.out.println("Booted. Bye...");			
		} catch (Throwable ex) {
			ex.printStackTrace(System.err);
		}
	}
	
	protected static void processArgs(final String argStr) {
		if(argStr==null || argStr.trim().isEmpty()) return;
		String[] options = argStr.split("\\|~");
		for(int i = 0; i < options.length; i++) {
			if("-p".equalsIgnoreCase(options[i])) {
				i++;
				String[] sysPropPair = options[i].split("=");
				System.setProperty(sysPropPair[0].trim(), sysPropPair[1].trim());
				System.out.println("Set SysProp [" + sysPropPair[0].trim() + "]=[" + sysPropPair[1].trim() + "]");
			}
		}
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

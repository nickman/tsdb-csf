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
package com.heliosapm.opentsdb.client.util;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;

import com.heliosapm.opentsdb.client.opentsdb.Constants;

/**
 * <p>Title: Util</p>
 * <p>Description: Misc utility functions</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.util.Util</code></p>
 */

public class Util {
	
	/** IP4 address pattern matcher */
	public static final Pattern IP4_ADDRESS = Pattern.compile("((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])");
	/** IP6 address pattern matcher */
	public static final Pattern IP6_ADDRESS = Pattern.compile("(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])).*?");
	
	
	/** A set of shutdown hooks */
	private static final Set<Runnable> shutdownHookRunnables = new CopyOnWriteArraySet<Runnable>();
	
	static {
		final Thread shutdownHook = new Thread("PendingShutdownHook") {
			public void run() {
				Thread.currentThread().setName("RunningShutdownHook");
				for(Iterator<Runnable> riter = shutdownHookRunnables.iterator(); riter.hasNext();) {
					Runnable t = riter.next();
					try { t.run(); } catch (Throwable tx) {/* No Op */}
					riter.remove();
				}
			}
		};
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}
	
	/**
	 * Adds a task to be run on JVM shutdown
	 * @param r The task to run
	 */
	public static void sdhook(final Runnable r) {
		if(r!=null) shutdownHookRunnables.add(r);
	}
	 
	/**
	 * Adds a task to delete the passed files on JVM shutdown
	 * @param files The files to delete
	 */
	public static void sdhook(final File...files) {
		shutdownHookRunnables.add(new Runnable() {
			public void run() {
				if(files!=null) {
					for(File f: files) {
						if(f==null) continue;
						f.delete();
					}
				}
			}
		});
	}
	
	
	
	
	/**
	 * Attempts a series of methods of divining the host name
	 * @return the determined host name
	 */
	public static String hostName() {	
		String host = System.getProperty(Constants.PROP_HOST_NAME, "").trim();
		if(host!=null && !host.isEmpty()) return host;
		host = getHostNameByNic();
		if(host!=null) return host;		
		host = getHostNameByInet();
		if(host!=null) return host;
		host = System.getenv(Constants.IS_WIN ? "COMPUTERNAME" : "HOSTNAME");
		if(host!=null && !host.trim().isEmpty()) return host;
		return Constants.HOST;
	}	
	
	
	/**
	 * Attempts to find a reliable app name
	 * @return the app name
	 */
	public static String appName() {
		String appName = System.getProperty(Constants.PROP_APP_NAME, "").trim();
		if(appName!=null && !appName.isEmpty()) return appName;
		appName = getSysPropAppName();
		if(appName!=null && !appName.trim().isEmpty()) return appName.trim();
		appName = getJSAppName();
		if(appName!=null && !appName.trim().isEmpty()) return appName.trim();		
		appName = System.getProperty("sun.java.command", null);
		if(appName!=null && !appName.trim().isEmpty()) {
			String app = cleanAppName(appName);
			if(app!=null && !app.trim().isEmpty()) {
				return app;
			}
		}
		appName = getVMSupportAppName();
		if(appName!=null && !appName.trim().isEmpty()) return appName;
		//  --main from args ?
		return Constants.SPID;
	}
	
//	public static final String SYSPROP_APP_NAME = "tsdb.id.app.prop";
//	public static final String JS_APP_NAME = "tsdb.id.app.js";
	
	/**
	 * Attempts to determine the app name by looking up the value of the 
	 * system property named in the value of the system prop {@link Constants#SYSPROP_APP_NAME}
	 * @return The app name or null if {@link Constants#SYSPROP_APP_NAME} was not defined
	 * or did not resolve.
	 */
	public static String getSysPropAppName() {
		String appProp = System.getProperty(Constants.SYSPROP_APP_NAME, "").trim();
		if(appProp==null || appProp.isEmpty()) return null;
		boolean env = appProp==null || appProp.isEmpty();
		String appName = env ? System.getenv(appProp) : System.getProperty(appProp, "").trim();
		if(appName!=null && !appName.trim().isEmpty()) return appName;
		return null;		
	}
	
	/**
	 * Attempts to determine the app name by looking up the value of the 
	 * system property {@link Constants#JS_APP_NAME}, and compiling its value
	 * as a JS script, then returning the value of the evaluation of the script.
	 * The following binds are passed to the script: <ul>
	 * 	<li><b>sysprops</b>: The system properties</li>
	 * 	<li><b>agprops</b>: The agent properties which will be an empty properties instance if {@link #getAgentProperties()} failed.</li>
	 *  <li><b>envs</b>: A map of environment variables</li>
	 *  <li><b>mbs</b>: The platform MBeanServer</li>
	 *  <li><b>cla</b>: The command line arguments as an array of strings</li>
	 * </ul>
	 * @return The app name or null if {@link Constants#JS_APP_NAME} was not defined
	 * or did not compile, or did not return a valid app name
	 */
	public static String getJSAppName() {
		String js = System.getProperty(Constants.JS_APP_NAME, "").trim();
		if(js==null || js.isEmpty()) return null;
		try {
			ScriptEngine se = new ScriptEngineManager().getEngineByExtension("js");
			Bindings b = new SimpleBindings();
			b.put("sysprops", System.getProperties());
			b.put("envs", System.getenv());
			b.put("mbs", ManagementFactory.getPlatformMBeanServer());
			b.put("cla", ManagementFactory.getRuntimeMXBean().getInputArguments().toArray(new String[0]));
			Properties p = getAgentProperties();
			if(p!=null) {
				b.put("agprops", getAgentProperties());
			} else {
				b.put("agprops", new Properties());
			}
			Object value = se.eval(js, b);
			if(value!=null && !value.toString().trim().isEmpty()) return value.toString().trim();
			return null;
		} catch (Exception ex) {
			return null;
		}
	}
	
	
	/**
	 * Attempts to find an app name from a few different properties
	 * found in <b><code>sun.misc.VMSupport.getAgentProperties()</code></b>.
	 * Current properties are: <ul>
	 * 	<li>sun.java.command</li>
	 * 	<li>program.name</li>
	 * </ul>
	 * @return an app name or null if the reflective invocation fails,
	 * or no property was found, or if the clean of the found app names
	 * did not return an acceptable name.
	 */
	public static String getVMSupportAppName() {
		Properties p = getAgentProperties();
		String app = p.getProperty("sun.java.command", null);
		if(app!=null && !app.trim().isEmpty()) {
			app = cleanAppName(app);			
			if(app!=null && !app.trim().isEmpty()) return app;
		}
		app = p.getProperty("program.name", null);
		if(app!=null && !app.trim().isEmpty()) {
			app = cleanAppName(app);			
			if(app!=null && !app.trim().isEmpty()) return app;				
		}
		return null;
	}
	
	/**
	 * Returns the agent properties
	 * @return the agent properties or null if reflective call failed
	 */
	public static Properties getAgentProperties() {
		try {
			Class<?> clazz = Class.forName("sun.misc.VMSupport");
			Method m = clazz.getDeclaredMethod("getAgentProperties");
			m.setAccessible(true);
			Properties p = (Properties)m.invoke(null);
			return p;
		} catch (Throwable t) {
			return null;
		}
		
	}
	
	/**
	 * Cleans an app name
	 * @param appName The app name
	 * @return the cleaned name or null if the result is no good
	 */
	public static String cleanAppName(final String appName) {
		final String[] frags = appName.split("\\s+");
		if(appName.contains(".jar")) {
			
			for(String s: frags) {
				if(s.endsWith(".jar")) {
					String[] jfrags = s.split("\\.");
					return jfrags[jfrags.length-1];
				}
			}
		} else {
			String className = frags[0];
			Class<?> clazz = loadClassByName(className, null);
			if(clazz!=null) {
				return clazz.getSimpleName();
			}
		}
		
		
		return null;
	}
	
	/**
	 * Loads a class by name
	 * @param className The class name
	 * @param loader The optional class loader
	 * @return The class of null if the name could not be resolved
	 */
	public static Class<?> loadClassByName(final String className, final ClassLoader loader) {
		try {
			if(loader!=null) {
				return Class.forName(className, true, loader);
			} 
			return Class.forName(className);
		} catch (Exception ex) {
			return null;
		}
	}
	

	
	/**
	 * Uses <b><code>InetAddress.getLocalHost().getCanonicalHostName()</code></b> to get the host name.
	 * If the value is null, empty or equals <b><code>localhost</code></b>, returns null.
	 * @return The host name or null if one was not found.
	 */
	public static String getHostNameByInet() {
		try {
			String inetHost = InetAddress.getLocalHost().getCanonicalHostName();
			if(inetHost==null || inetHost.trim().isEmpty() || "localhost".equalsIgnoreCase(inetHost.trim())) return null;
			return inetHost.trim();
		} catch (Exception x) {
			return null;
		}
	}
	
	/**
	 * Iterates through the found NICs, extracting the host name if the NIC is not the loopback interface.
	 * The host name is extracted from the first address bound to the first matching NIC that does not 
	 * have a canonical name that is an IP address.
	 * @return The host name or null if one was not found.
	 */
	public static String getHostNameByNic() {
		try {
			for(Enumeration<NetworkInterface> nicEnum = NetworkInterface.getNetworkInterfaces(); nicEnum.hasMoreElements();) {
				NetworkInterface nic = nicEnum.nextElement();
				if(nic!=null && nic.isUp() && !nic.isLoopback()) {
					for(Enumeration<InetAddress> nicAddr = nic.getInetAddresses(); nicAddr.hasMoreElements();) {
						InetAddress addr = nicAddr.nextElement();
						String chost = addr.getCanonicalHostName();
						if(chost!=null && !chost.trim().isEmpty()) {
							if(!IP4_ADDRESS.matcher(chost).matches() && !IP6_ADDRESS.matcher(chost).matches()) {
								return chost;
							}
						}
					}
				}
			}
			return null;
		} catch (Exception x) {
			return null;
		}		
	}

	/**
	 * Cleans the passed stringy to make it more likely to not be rejected by OpenTSDB
	 * @param cs The stringy to clean
	 * @return the cleaned stringy
	 */
	public static String clean(final CharSequence cs) {
		if(cs==null || cs.toString().trim().isEmpty()) return "";
		String s = cs.toString().trim();
		final int index = s.indexOf('/');
		if(index!=-1) {
			s = s.substring(index+1);
		}
		return s.replace(" ", "_");
	}

	
}

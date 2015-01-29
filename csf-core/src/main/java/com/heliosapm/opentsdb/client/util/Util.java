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

import javax.management.ObjectName;
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
			@Override
			public void run() {
				Thread.currentThread().setName("RunningShutdownHook");
				for(Iterator<Runnable> riter = shutdownHookRunnables.iterator(); riter.hasNext();) {
					Runnable t = riter.next();
					try { t.run(); } catch (Throwable tx) {/* No Op */}					
				}
				shutdownHookRunnables.clear();
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
			@Override
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
	 * Creates a JMX ObjectName from the passed stringy
	 * @param cs The stringy to create an ObjectName from
	 * @return the built ObjectName
	 */
	public static ObjectName objectName(final CharSequence cs) {
		if(cs==null || cs.toString().trim().isEmpty()) throw new IllegalArgumentException("The passed name was null or empty");
		try {
			return new ObjectName(cs.toString().trim());
		} catch (Exception ex) {
			throw new RuntimeException("Failed to create ObjectName from [" + cs + "]", ex);
		}
	}
	
	/**
	 * Creates a JMX ObjectName from the passed metric name and tags
	 * @param metric The ObjectName's domain
	 * @param tags The ObjectName's properties in the form of <b>=</b> separated key value pairs
	 * @return the built ObjectName
	 */
	public static ObjectName objectName(final String metric, final String...tags) {
		if(metric==null || metric.trim().isEmpty()) throw new IllegalArgumentException("The passed metric name was null or empty");
		if(tags.length==0) throw new IllegalArgumentException("The passed tags array was zero length");
		StringBuilder b = new StringBuilder(clean(metric)).append(":");
		int tcount = 0;
		for(String tag: tags) {
			String s = clean(tag);
			if(s==null || s.trim().isEmpty() || s.indexOf('=')==0) continue;
			b.append(s).append(",");
			tcount++;
		}
		if(tcount==0) if(tags.length==0) throw new IllegalArgumentException("The passed tags array contained no legal tags");
		b.deleteCharAt(b.length()-1);
		return objectName(b);
	}
	
	/**
	 * Registers an MBean on the platform MBeanServer 
	 * @param bean The mbean to register
	 * @param objectName The ObjectName to register under
	 */
	public static void registerMBean(final Object bean, final ObjectName objectName) {
		if(bean==null) throw new IllegalArgumentException("The passed bean was null");
		if(objectName==null) throw new IllegalArgumentException("The passed ObjectName was null");
		try {
			ManagementFactory.getPlatformMBeanServer().registerMBean(bean, objectName);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to register the bean under [" + objectName + "]", ex);
		}		
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
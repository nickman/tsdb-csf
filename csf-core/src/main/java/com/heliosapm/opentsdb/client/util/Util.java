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
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Pattern;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

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
	 * Registers a new classloader MBean (an MLet) on the passed MBeanServer
	 * @param server The MBeanServer on which to register
	 * @param objectName The JMX object name of the new MBean
	 * @param delegateToCLR True if, when a class is not found in either the parent ClassLoader or the URLs, the MLet should delegate to its containing MBeanServer's ClassLoaderRepository.
	 * @param privateClassLoader If true, registers a private MLet, otherwise, registers a public one
	 * @param urls The URLs from which to load classes and resources.
	 * @return the ObjectName of the classloader
	 */
	public static ObjectName publishClassLoader(MBeanServerConnection server, CharSequence objectName, boolean delegateToCLR, boolean privateClassLoader, URL...urls) {
		ObjectName on = objectName(objectName);
		String className = privateClassLoader ? "javax.management.loading.PrivateMLet" : "javax.management.loading.MLet"; 
		try {
			server.createMBean(className, on, new Object[]{urls, delegateToCLR}, new String[]{URL[].class.getName(), "boolean"});
			return on;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to register classloader MBean [" + objectName + "]", ex);
		}
	}
	
	/**
	 * Registers a new classloader MBean (an MLet) on the platform MBeanServer
	 * @param objectName The JMX object name of the new MBean
	 * @param delegateToCLR True if, when a class is not found in either the parent ClassLoader or the URLs, the MLet should delegate to its containing MBeanServer's ClassLoaderRepository.
	 * @param privateClassLoader If true, registers a private MLet, otherwise, registers a public one
	 * @param urls The URLs from which to load classes and resources.
	 * @return the ObjectName of the classloader
	 */
	public static ObjectName publishClassLoader(CharSequence objectName, boolean delegateToCLR, boolean privateClassLoader, URL...urls) {
		return publishClassLoader(ManagementFactory.getPlatformMBeanServer(), objectName, delegateToCLR, privateClassLoader, urls);
	}
	
	
	/**
	 * Registers a new public classloader MBean (an MLet) on the default MBeanServer
	 * @param objectName The JMX object name of the new MBean
	 * @param delegateToCLR True if, when a class is not found in either the parent ClassLoader or the URLs, the MLet should delegate to its containing MBeanServer's ClassLoaderRepository.
	 * @param urls The URLs from which to load classes and resources.
	 * @return the ObjectName of the classloader
	 */
	public static ObjectName publishClassLoader(CharSequence objectName, boolean delegateToCLR, URL...urls) {
		return publishClassLoader(ManagementFactory.getPlatformMBeanServer(), objectName, delegateToCLR, false, urls);
	}
	
	/**
	 * Registers a new public and CLR delegating classloader MBean (an MLet) on the default MBeanServer
	 * @param objectName The JMX object name of the new MBean
	 * @param urls The URLs from which to load classes and resources.
	 * @return the ObjectName of the classloader 
	 */
	public static ObjectName publishClassLoader(CharSequence objectName, URL...urls) {
		return publishClassLoader(ManagementFactory.getPlatformMBeanServer(), objectName, true, false, urls);
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
	 * Determines if the passed stringy is a valid object name
	 * @param cs The stringy to test
	 * @return true if the stringy is a valid object name, false otherwise
	 */
	public static boolean isObjectName(final CharSequence cs) {
		if(cs==null || cs.toString().trim().isEmpty()) return false;
		try {
			objectName(cs);
			return true;
		} catch (Exception ex) {
			return false;
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
	 * Registers the passed MBean on all located MBeanServers
	 * @param bean The bean to register
	 * @param objectName The ObjectName to register the bean with
	 * @return the number of MBeanServers registered with
	 */
	public static int registerMBeanEverywhere(final Object bean, final ObjectName objectName) {
		int cnt = 0;
		for(MBeanServer mbs: MBeanServerFactory.findMBeanServer(null)) {
			if(!mbs.isRegistered(objectName)) {
				try {
					mbs.registerMBean(bean, objectName);
					cnt++;
				} catch (Exception ex) {/* No Op */}
			}
		}
		return cnt;
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
		return s.replace(" ", "");
	}

    /**
     * Returns the next highest power of 2
     * @param value The value to get the next power of 2 for
     * @return the next power of 2
     */
    public static int findNextPositivePowerOfTwo(final int value) {
    	return  1 << (32 - Integer.numberOfLeadingZeros((int)value - 1));
	}
    
    private static final int[] POW2 = new int[]{1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144, 524288, 1048576, 2097152, 4194304, 8388608, 16777216, 33554432, 67108864, 134217728, 268435456, 536870912, 1073741824};
    
    public static int pow2Index(final int x) {
    	if(x<0 || x > 30) throw new IllegalArgumentException("Unsupported Value [" + x + "]. Only supported for values between 0 and 30 inclusive");
    	return POW2[x];
    }
    
	/**
	 * Calculates a percent
	 * @param part The part 
	 * @param whole The whole
	 * @return The percentage that the part is of the whole
	 */
	public static int percent(double part, double whole) {
		if(part==0d || whole==0d) return 0;
		double p = part/whole*100;
		return (int) Math.round(p);
	}

	
	
}

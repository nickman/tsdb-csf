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

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.loading.MLet;

/**
 * <p>Title: ManualLoader</p>
 * <p>Description: A manual or programatic loader</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.boot.ManualLoader</code></p>
 * TODO:  Configs for:
 * Transformer Switch
 * MBeanObservers
 * Install instrumentation Agent
 * Install time instrumentation
 * 
 */

public class ManualLoader {
	/** The classloader ObjectName */
	public static final ObjectName OBJECT_NAME;
	
	private static final AtomicBoolean booted = new AtomicBoolean(false);
	
	static {
		try {
			OBJECT_NAME = new ObjectName("com.heliosapm.opentsdb:service=TSDBCSF");
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Loads tsdb-csf into the current environment, assuming the jar has been appended to the sys classpath
	 * @param mbs The MBeanServer where the classloader will be registered
	 * @throws ClassNotFoundException 
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 */
	public static void boot(final MBeanServer mbs) throws ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		if(booted.compareAndSet(false, true)) {
			final Class<?> logConfClazz = Class.forName("com.heliosapm.opentsdb.client.logging.LoggingConfiguration");
			logConfClazz.getDeclaredMethod("getInstance").invoke(null);
			System.out.println("Initialized tsdb-csf Logging");
			final Class<?> observerClass = Class.forName("com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSet");
			observerClass.getDeclaredMethod("build", MBeanServer.class, long.class, TimeUnit.class)
				.invoke(null, ManagementFactory.getPlatformMBeanServer(), 5, TimeUnit.SECONDS);
			System.out.println("Initialized tsdb-csf MXBeanObserver");
		}
	}
	
	
	/**
	 * Loads tsdb-csf into the current environment
	 * @param libLocation The MLet classloader pre-configured for the location of the tsdb-csf jar
	 * @param mbs The MBeanServer where the classloader will be registered
	 */
	public static void boot(final MLet libLocation, final MBeanServer mbs) {
		try {			
			mbs.registerMBean(libLocation, OBJECT_NAME);
			final ClassLoader cl = mbs.getClassLoader(OBJECT_NAME);
			System.out.println("Registered tsdb-csf ClassLoader at " + OBJECT_NAME);
			final ClassLoader CURRENT = Thread.currentThread().getContextClassLoader();
			try {
				Thread.currentThread().setContextClassLoader(cl);
				final Class<?> logConfClazz = Class.forName("com.heliosapm.opentsdb.client.logging.LoggingConfiguration", true, cl);
				logConfClazz.getDeclaredMethod("getInstance").invoke(null);
				System.out.println("Initialized tsdb-csf Logging");
				final Class<?> observerClass = Class.forName("com.heliosapm.opentsdb.client.jvmjmx.MBeanObserverSet", true, cl);
				observerClass.getDeclaredMethod("build", MBeanServer.class, long.class, TimeUnit.class)
					.invoke(null, ManagementFactory.getPlatformMBeanServer(), 5, TimeUnit.SECONDS);
				System.out.println("Initialized tsdb-csf MXBeanObserver");						
				// MBeanObserverSet build(final MBeanServer mbeanServer, final long period, final TimeUnit unit) {
//				LoggingConfiguration.getInstance(); 
			} finally {
				Thread.currentThread().setContextClassLoader(CURRENT);
			}
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}
	}

	/**
	 * Creates a new ManualLoader
	 */
	private ManualLoader() {
		/* No Op */
	}

}


/*
import javax.management.loading.MLet;
import javax.management.*;
import java.lang.management.*;
MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
System.setProperty("tsdb.http.compression.enabled", "false");
try {
    mbs.unregisterMBean(new ObjectName("com.heliosapm.opentsdb:service=TSDBCSF"));
} catch (e) {}    
tsdburl = new File("/media/sf_C_DRIVE/hprojects/tsdb-csf/csf-core/target/tsdb-csf-1.0-SNAPSHOT.jar").toURI().toURL();
String className = "com.heliosapm.opentsdb.client.boot.ManualLoader";
MLet mlet = new MLet([tsdburl] as URL[], ClassLoader.getSystemClassLoader().getParent(), false);
ClassLoader CURRENT = Thread.currentThread().getContextClassLoader();
try {
    Thread.currentThread().setContextClassLoader(mlet);
    clazz = Class.forName(className, true, mlet);
    clazz.getDeclaredMethod("boot", MLet.class, MBeanServer.class).invoke(null, mlet, mbs);
} finally {
    Thread.currentThread().setContextClassLoader(CURRENT);
}    
 
println "Done";
     
     
     // Need to set:  app ID locator, no compression, 

*/
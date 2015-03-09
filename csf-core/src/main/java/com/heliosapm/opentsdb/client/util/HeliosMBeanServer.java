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

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>Title: HeliosMBeanServer</p>
 * <p>Description: Creates a new MBeanServer using the JVM's native implementation.
 * This is intended to provide a JMX platform in environments where an older JMX spec is 
 * installed as the system's MBeanServer factory (such as some older JBoss app servers).</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.util.HeliosMBeanServer</code></p>
 */

public class HeliosMBeanServer {
	/** Instance logger */
	protected final Logger log = LogManager.getLogger(getClass());
	/** The MBeanServer's default domain */
	protected final String defaultDomain;
	/** The optional remoting URL to define the connector server to start */
	protected final JMXServiceURL remotingUrl;
	/** The MBeanServer impl */
	protected final MBeanServer mbs;
	/** The remoting server for this MBeanServer */
	protected final JMXConnectorServer remotingServer;

	/** The class name of the target MBeanServer implementation to create */
	static final String MBS_IMPL = "com.sun.jmx.mbeanserver.JmxMBeanServer";
	
	/** The NIO Buffer Pool MX Domain Type */
	public static final String NIO_BUFFER_POOL_MXBEAN_DOMAIN_TYPE = "java.nio:type=BufferPool";
	
	public static void main(String[] args) {
		try {			
			new HeliosMBeanServer("com.heliosapm", true, new JMXServiceURL("service:jmx:jmxmp://localhost:9091"));
			Thread.currentThread().join();
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
			System.exit(-1);
		}
	}
	
	
	/**
	 * Creates a new HeliosMBeanServer
	 * @param defaultDomain The default domain
	 * @param register If true, registers the MBeanServer so that it is locatable through the MBeanServerFactory
	 * @param remotingUrl The optional remoting URL to define the connector server to start 
	 */
	public HeliosMBeanServer(final String defaultDomain, final boolean register, final JMXServiceURL remotingUrl) {	
		this.defaultDomain = defaultDomain;
		this.remotingUrl = remotingUrl;
		try {
			Class<? extends MBeanServer> clazz = (Class<? extends MBeanServer>) Class.forName(MBS_IMPL);
			Constructor<? extends MBeanServer> ctor = clazz.getDeclaredConstructor(String.class, MBeanServer.class, MBeanServerDelegate.class, boolean.class);
			ctor.setAccessible(true);
			mbs = ctor.newInstance(defaultDomain, null,  new MBeanServerDelegate(), true);
			// TODO:
			// stop
			// unregister
			registerMXBeans();
			log.info("Registered MXBeans");
			remotingServer = startConnectorServer();
			log.info("Started JMXConnectorServer on [{}]", remotingUrl);
			if(register) {
				registerMBeanServer();
			}
		} catch (Exception ex) {
			throw new RuntimeException("Failed to create HeliosMBeanServer[" + defaultDomain + "]", ex);
		}
	}
	
	/**
	 * Registers all found MXBeans
	 */
	protected void registerMXBeans() {
		addMBean(ManagementFactory.getClassLoadingMXBean(), JMXHelper.objectName(ManagementFactory.CLASS_LOADING_MXBEAN_NAME));
		addMBean(ManagementFactory.getCompilationMXBean(), JMXHelper.objectName(ManagementFactory.COMPILATION_MXBEAN_NAME));
		addMBean(ManagementFactory.getMemoryMXBean(), JMXHelper.objectName(ManagementFactory.MEMORY_MXBEAN_NAME));
		addMBean(ManagementFactory.getOperatingSystemMXBean(), JMXHelper.objectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME));
		addMBean(ManagementFactory.getRuntimeMXBean(), JMXHelper.objectName(ManagementFactory.RUNTIME_MXBEAN_NAME));
		addMBean(ManagementFactory.getThreadMXBean(), JMXHelper.objectName(ManagementFactory.THREAD_MXBEAN_NAME));
		addMBean(java.util.logging.LogManager.getLoggingMXBean(), JMXHelper.objectName(java.util.logging.LogManager.LOGGING_MXBEAN_NAME));
		for(GarbageCollectorMXBean gc: ManagementFactory.getGarbageCollectorMXBeans()) {
			addMBean(gc, JMXHelper.objectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",name=" + gc.getName()));
		}
		for(MemoryPoolMXBean mem: ManagementFactory.getMemoryPoolMXBeans()) {
			addMBean(mem, JMXHelper.objectName(ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE + ",name=" + mem.getName()));
		}
		for(MemoryManagerMXBean mem: ManagementFactory.getMemoryManagerMXBeans()) {
			addMBean(mem, JMXHelper.objectName(ManagementFactory.MEMORY_MANAGER_MXBEAN_DOMAIN_TYPE + ",name=" + mem.getName()));
		}
		try {
			Class<?> bufferPoolClazz = Class.forName("java.lang.management.BufferPoolMXBean");
			Method m = ManagementFactory.class.getDeclaredMethod("getPlatformMXBeans", Class.class);
			List<Object> bufferPools = (List<Object>)m.invoke(null, bufferPoolClazz);
			m = bufferPoolClazz.getDeclaredMethod("getName");
			for(Object obj: bufferPools) {
				String name = (String)m.invoke(obj);
				addMBean(obj, JMXHelper.objectName(NIO_BUFFER_POOL_MXBEAN_DOMAIN_TYPE + ",name=" + name));
			}
		} catch (Exception ex) {/* No Op */}
		try {
			mbs.createMBean("sun.management.HotSpotDiagnostic", JMXHelper.objectName("com.sun.management:type=HotSpotDiagnostic"));
		} catch (Exception ex) {/* No Op */}
		try {
			Method m = Class.forName("sun.management.ManagementFactoryHelper").getDeclaredMethod("registerInternalMBeans", MBeanServer.class);
			m.setAccessible(true);
			m.invoke(null, mbs);
		} catch (Exception ex) {/* No Op */}	
	}
	
	/**
	 * Registers the passed object as an MBean in this MBeanServer
	 * @param mbean The object to register
	 * @param objectName The JMX ObjectName to register the object under
	 */
	protected void addMBean(final Object mbean, final ObjectName objectName) {
		try {
			if(!mbs.isRegistered(objectName)) {
				mbs.registerMBean(mbean, objectName);
			}
		} catch (InstanceAlreadyExistsException iax) {
			/* No Op */
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}
	}
	
	/**
	 * Registers the MBeanServer with the {@link MBeanServerFactory} so that it can be found therein.
	 */
	protected void registerMBeanServer() {
		try {
			Method m = MBeanServerFactory.class.getDeclaredMethod("addMBeanServer", MBeanServer.class);
			m.setAccessible(true);
			m.invoke(null, mbs);
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}
	}
	
	
	/**
	 * Starts the JMXConnectorServer for this MBeanServer if one was defined
	 * @return The JMXConnectorServer instance that was started
	 */
	protected JMXConnectorServer startConnectorServer() {
		if(remotingUrl!=null) {
			try {
				JMXConnectorServer remotingServer = JMXConnectorServerFactory.newJMXConnectorServer(remotingUrl, null, mbs);
				remotingServer.start();
				return remotingServer;
			} catch (Exception ex) {
				ex.printStackTrace(System.err);				
			}
		}
		return null;
	}
	
	
	
}


/*
import javax.management.*;
import java.lang.management.*;
import com.sun.jmx.mbeanserver.*;
import javax.management.remote.*;
import javax.management.remote.jmxmp.*;

def cs = null;

//JmxMBeanServer
mbs = ManagementFactory.getPlatformMBeanServer();
println ManagementFactory.getMemoryMXBean().getClass().getName();
delegate = new MBeanServerDelegate();
jbs = new JmxMBeanServer("com.heliosapm", null, delegate, true);
sun.management.ManagementFactoryHelper.registerInternalMBeans(jbs);
sun.management.HotSpotDiagnostic hsd = new sun.management.HotSpotDiagnostic();
jbs.registerMBean(hsd, new ObjectName("com.sun.management:type=HotSpotDiagnostic"));
mbs.queryNames(new ObjectName("java.*:*"), null).each() {

    boolean notifs = mbs.isInstanceOf(it, NotificationBroadcaster.class.getName()) ;    
    info = mbs.getMBeanInfo(it);
    descriptor = info.getDescriptor();
    iface = descriptor.getFieldValue("interfaceClassName");
    proxy = MBeanServerInvocationHandler.newProxyInstance(mbs, it, Class.forName(iface), notifs);
    jbs.registerMBean(proxy, it);
    println "Registered $it";
}

surl = new JMXServiceURL("service:jmx:jmxmp://localhost:9091");

cs = new JMXMPConnectorServer(surl, null, jbs);
try {
    cs.start();
    println "Started";
    Thread.currentThread().join();
} finally {
    if(cs!=null) try { cs.stop(); println "Stopped";} catch(e) {}
}

*/
/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2007, Helios Development Group and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package com.heliosapm.opentsdb.client.opentsdb.jvm;

import java.lang.management.ManagementFactory;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import org.cliffc.high_scale_lib.NonBlockingHashMap;

import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: MBeanObserverBuilder</p>
 * <p>Description: Fluent style builder for building MBeanObservers</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MBeanObserverBuilder</code></p>
 */

public class MBeanObserverBuilder {
	/** The target MBeanServerConnection */
	private final MBeanServerConnection mbs;
	/** The JMXConnector that will supply the target MBeanServerConnection */
	private final JMXConnector jmxConnector;
	/** The ObjectName of the target MBean to observe */
	private final ObjectName target;
	/** The provided host name to use */
	private String hostName = null;
	/** The provided app name to use */
	private String appName = null;	
	
	
	/** An agent name finder that will use the mbean server connection to discover the AgentName */
	private RemoteMBeanServerAgentNameFinder nameFinder = null;
	private Class<?> mxBeanIface = null;
	private Class<?> mBeanIface = null;
	private Class<?> observerImpl = null;
	
	/** The known and default base mbean observer classes keyed by the ObjectName */
	private static Map<ObjectName, Class<? extends BaseMBeanObserver>> KNOWN_IMPLS = new NonBlockingHashMap<ObjectName, Class<? extends BaseMBeanObserver>>();;
	
	static {
		KNOWN_IMPLS.put(Util.objectName(ManagementFactory.COMPILATION_MXBEAN_NAME), CompilationMBeanObserver.class);
	}
	
	/**
	 * Creates a new observer builder
	 * @param mbs The target MBeanServerConnection
	 * @param target The ObjectName of the target MBean to observe
	 * @return the initialized builder
	 */
	public static MBeanObserverBuilder newBuilder(final MBeanServerConnection mbs, final ObjectName target) {
		if(mbs==null) throw new IllegalArgumentException("The passed MBeanServerConnection was null");
		if(target==null) throw new IllegalArgumentException("The passed target ObjectName was null");
		return new MBeanObserverBuilder(mbs, target);
	}
	
	/**
	 * Creates a new observer builder
	 * @param jmxConnector The JMXConnector that will supply the target MBeanServerConnection
	 * @param target The ObjectName of the target MBean to observe
	 * @return the initialized builder
	 */
	public static MBeanObserverBuilder newBuilder(final JMXConnector jmxConnector, final ObjectName target) {
		if(jmxConnector==null) throw new IllegalArgumentException("The passed JMXConnector was null");
		if(target==null) throw new IllegalArgumentException("The passed target ObjectName was null");
		return new MBeanObserverBuilder(jmxConnector, target);
	}
	
	
	/**
	 * Creates a new MBeanObserverBuilder
	 * @param mbs The target MBeanServerConnection
	 * @param target The ObjectName of the target MBean to observe
	 */
	private MBeanObserverBuilder(final MBeanServerConnection mbs, final ObjectName target) {		
		this.mbs = mbs;
		jmxConnector = null;
		this.target = target;
	}
	
	/**
	 * Creates a new MBeanObserverBuilder
	 * @param mbs The JMXConnector that will supply the target MBeanServerConnection
	 * @param target The ObjectName of the target MBean to observe
	 */
	private MBeanObserverBuilder(final JMXConnector jmxConnector, final ObjectName target) {
		this.jmxConnector = jmxConnector;
		mbs = null;
		this.target = target;
	}
	

}

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

import java.lang.management.CompilationMXBean;
import java.lang.management.ManagementFactory;
import java.util.Map;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: CompilationMBeanObserver</p>
 * <p>Description: MBeanObserver for monitoring compilation times.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.CompilationMBeanObserver</code></p>
 */

public class CompilationMBeanObserver extends BaseMBeanObserver {
	/** The compilation mxbean jmx ObjectName */
	static final ObjectName OBJECT_NAME = Util.objectName(ManagementFactory.COMPILATION_MXBEAN_NAME);
	/** The Compiler MXBean proxy */
	protected final CompilationMXBean proxy;
	/** Indicates if compilation time is supported in the target MXBean */
	protected final boolean compileTimeSupported;
	/** The compilation time gauge */
	protected final Gauge<Long> compileTime;

	
	
	
	/**
	 * Creates a new CompilationMBeanObserver
	 * @param connection The delegate MBeanServerConnection or null if one will be acquired from the passed JMXConnector
	 * @param jmxConnector The JMXConnector which will supply a MBeanServerConnection, or null if one is passed directly. 
	 */
	protected CompilationMBeanObserver(final MBeanServerConnection connection, final JMXConnector jmxConnector) {
		super(connection, jmxConnector, OBJECT_NAME);
		proxy = mbs.isLocalPlatform() ? 
				ManagementFactory.getCompilationMXBean() : 
				newPlatformMXBeanProxy(ManagementFactory.COMPILATION_MXBEAN_NAME, CompilationMXBean.class);
		compileTimeSupported = proxy.isCompilationTimeMonitoringSupported();
		if(compileTimeSupported) {
			compileTime = new Gauge<Long>() {
			     @Override
					public Long getValue() {
				         return delta("compiletime", proxy.getTotalCompilationTime(), 0L);
				     }
				 };
			metrics.put("java.lang.compiler.CompileTime:" + OBJECT_NAME.getCanonicalKeyPropertyListString(), compileTime);
		} else {
			compileTime = null;
		}
	}
	 
	/**
	 * Creates a new CompilationMBeanObserver
	 * @param connection The MBeanServerConnection to the MBeanServer to observe
	 */
	public CompilationMBeanObserver(final MBeanServerConnection connection) {
		this(connection, null);
	}

	/**
	 * Creates a new CompilationMBeanObserver
	 * @param jmxConnector The JMXConnector that supplies the MBeanServerConnection to the MBeanServer to observe
	 */
	public CompilationMBeanObserver(final JMXConnector jmxConnector) {
		this((MBeanServerConnection)null, jmxConnector);
	}
	
	@Override
	public Map<String, Metric> getMetrics() {
		if(!compileTimeSupported) return EMPTY_METRIC_MAP; 
		return null;
	}

//	/**
//	 * {@inheritDoc}
//	 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.BaseMBeanObserver#doCollect()
//	 */
//	@Override
//	protected void doCollect() {
//		if(!compileTimeSupported) return;
//	}

}

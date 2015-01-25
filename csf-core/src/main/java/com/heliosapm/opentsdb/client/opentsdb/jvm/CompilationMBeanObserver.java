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
import java.util.concurrent.TimeUnit;

import javax.management.ObjectName;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer.Context;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: CompilationMBeanObserver</p>
 * <p>Description: MBeanObserver for monitoring compilation times.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.CompilationMBeanObserver</code></p>
 */

public class CompilationMBeanObserver extends BaseMBeanObserver {
	/**  */
	private static final long serialVersionUID = -2040792110405342795L;
	/** The compilation mxbean jmx ObjectName */
	static final ObjectName OBJECT_NAME = Util.objectName(ManagementFactory.COMPILATION_MXBEAN_NAME);
	/** The Compiler MXBean proxy */
	protected final CompilationMXBean proxy;
	/** Indicates if compilation time is supported in the target MXBean */
	protected final boolean compileTimeSupported;
	/** The compilation time gauge */
	protected final Gauge<Long> compileTimeGauge;
	/** The last sampled compilation time */
	protected long compileTime = 0L;
	
	CompilationMBeanObserver(final MBeanObserverBuilder builder) {
		super(builder.period(3).unit(TimeUnit.SECONDS));
		proxy = mbs.isLocalPlatform() ? 
				ManagementFactory.getCompilationMXBean() : 
				newPlatformMXBeanProxy(ManagementFactory.COMPILATION_MXBEAN_NAME, CompilationMXBean.class);
		compileTimeSupported = proxy.isCompilationTimeMonitoringSupported();
		if(compileTimeSupported) {
			compileTimeGauge = new Gauge<Long>() {
			     @Override
					public Long getValue() {
				        return compileTime;
				     }
				 };
			metrics.put("java.lang.compiler.CompileTime:" 
				 + OBJECT_NAME.getCanonicalKeyPropertyListString() 
				 + "," 
				 + getAgentNameTags()
				 , compileTimeGauge);
		} else {
			compileTimeGauge = null;
		}
	}
	
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.BaseMBeanObserver#getMetrics()
	 */
	@Override
	public Map<String, Metric> getMetrics() {
		if(!compileTimeSupported) return EMPTY_METRIC_MAP; 
		return metrics;	
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.BaseMBeanObserver#acceptData(java.util.Map)
	 */
	@Override
	protected void acceptData(final Map<ObjectName, Map<String, Object>> attrMaps) {
		Map<String, Object> attrValues = attrMaps.get(OBJECT_NAME);
		compileTime = (Long)attrValues.get("TotalCompilationTime");

	}


}

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
package com.heliosapm.opentsdb.client.opentsdb.jvm;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.JmxAttributeGauge;
import com.codahale.metrics.JvmAttributeGaugeSet;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.MetricSet;

/**
 * <p>Title: BufferPoolMetricSet</p>
 * <p>Description: OpenTSDB naming friendly rewrite of {@link com.codahale.metrics.jvm.BufferPoolMetricSet}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.BufferPoolMetricSet</code></p>
 */

public class BufferPoolMetricSet implements MetricSet {
    private static final Logger LOGGER = LoggerFactory.getLogger(BufferPoolMetricSet.class);
    private static final String[] ATTRIBUTES = { "Count", "MemoryUsed", "TotalCapacity" };
    private static final String[] NAMES = { "count", "used", "capacity" };
    private static final String[] POOLS = { "direct", "mapped" };

    private final MBeanServerConnection mBeanServer;
    private final Set<ObjectName> bufferPoolMBeans;
    
    
    //  java.io.buffers.type=BufferPool.name=direct.attr=MemoryUsed
    

    /**
     * Creates a new BufferPoolMetricSet
     * @param mBeanServer An optional MBeanServerConnection. If null, will use the platform MBeanServer
     */
    public BufferPoolMetricSet(final MBeanServerConnection mBeanServer) {
        this.mBeanServer = mBeanServer!=null ? mBeanServer: ManagementFactory.getPlatformMBeanServer();
        Set<ObjectName> tmp = null;
        try {
        	tmp = this.mBeanServer.queryNames(new ObjectName("java.nio:type=BufferPool,name=*"), null);        	        
        } catch (Exception ex) {/* No Op */}
        bufferPoolMBeans = (tmp==null || tmp.isEmpty()) ? null : Collections.unmodifiableSet(tmp);                
    }
    
    /**
     * Creates a new BufferPoolMetricSet using the platform MBeanServer
     */
    public BufferPoolMetricSet() {
    	this(ManagementFactory.getPlatformMBeanServer());
    }

    @Override
    public Map<String, Metric> getMetrics() {
    	if(bufferPoolMBeans==null) return Collections.emptyMap();
        final Map<String, Metric> gauges = new HashMap<String, Metric>();
        JvmAttributeGaugeSet gs = new JvmAttributeGaugeSet(); 
        for (ObjectName pool : bufferPoolMBeans) {
            for (int i = 0; i < ATTRIBUTES.length; i++) {
                final String attribute = ATTRIBUTES[i];
                final String poolName = pool.getKeyProperty("name");
                
                gauges.put(MetricRegistry.name("java.io.buffers", "type=BufferPool", "name=" + poolName),   // , "attr=" + attribute
                           new JmxAttributeGauge(mBeanServer, pool, attribute));
                
            }
        }
        return Collections.unmodifiableMap(gauges);
    }

}

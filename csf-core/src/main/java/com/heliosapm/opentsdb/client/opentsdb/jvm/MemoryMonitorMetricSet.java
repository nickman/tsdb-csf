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
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.Map;

import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import com.codahale.metrics.Gauge;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: MemoryMonitorMetricSet</p>
 * <p>Description: MBeanObserver for heap and non-heap memory usage</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MemoryMonitorMetricSet</code></p>
 */

public class MemoryMonitorMetricSet extends BaseMBeanObserver {
	
	/** The memory mxbean jmx ObjectName */
	static final ObjectName OBJECT_NAME = Util.objectName(ManagementFactory.MEMORY_MXBEAN_NAME);
	/** The attribute name for pending final count */
	static final String OPFC = "ObjectPendingFinalizationCount";
	/** The attribute name for heap memory usage */
	static final String HEAP = "HeapMemoryUsage";
	/** The attribute name for non-heap memory usage */
	static final String NONHEAP = "NonHeapMemoryUsage";
	
	/** The attribute names to bulk retrieve */
	static final String[] ATTR_NAMES = {HEAP, NONHEAP, OPFC};
	/** The attribute names of the composite memory types */
	static final String[] SUB_ATTR_NAMES = {"init", "used", "committed", "max", "percentUsage", "percentCapacity"};
	
	
	
	protected final Map<String, long[]> poolAttrValues;

	/**
	 * Creates a new MemoryMonitorMetricSet
	 * @param builder The observer builder
	 */
	public MemoryMonitorMetricSet(MBeanObserverBuilder builder) {
		super(builder, ATTR_NAMES);
		poolAttrValues = new HashMap<String, long[]>(3);
		for(String attrName: ATTR_NAMES) {
			if(OPFC.equals(attrName)) {
				metrics.put("java.lang.mem.finalCount:" + OBJECT_NAME.getCanonicalKeyPropertyListString() + "," + getAgentNameTags(), new Gauge<Long>() {    			
					@Override
					public Long getValue() {
						latch.countDown();
						return poolAttrValues.get(recorderKey(OBJECT_NAME, OPFC))[0];
					}
				});				
			} else {
				final String heapType = (HEAP.equals(attrName)) ? "heap" : "nonheap";
				for(int i = 0; i < SUB_ATTR_NAMES.length; i++) {
					final int index = i;
					String subAttrName = SUB_ATTR_NAMES[index];
					final String metricName = String.format("java.lang.mem.%s:%s,memtype=%s,%s", subAttrName, OBJECT_NAME.getCanonicalKeyPropertyListString(),heapType, getAgentNameTags());
					final String key = recorderKey(OBJECT_NAME, attrName);
					metrics.put(metricName, new Gauge<Long>(){
						@Override
						public Long getValue() {
							latch.countDown();
							return poolAttrValues.get(key)[index];
						}
					});
				}				
			}
		}		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.BaseMBeanObserver#acceptData(java.util.Map)
	 */
	@Override
	protected void acceptData(Map<ObjectName, Map<String, Object>> attrMaps) {
		// "HeapMemoryUsage", "NonHeapMemoryUsage", "ObjectPendingFinalizationCount"
		// "init", "used", "committed", "max, percentUsage, percentCapacity"
		Map<String, Object> map = attrMaps.get(OBJECT_NAME);
		MemoryUsage heapMem = MemoryUsage.from((CompositeData)map.get("HeapMemoryUsage"));
		poolAttrValues.put(recorderKey(OBJECT_NAME, "HeapMemoryUsage"), new long[]{
			heapMem.getInit(), heapMem.getUsed(), heapMem.getCommitted(), heapMem.getMax(),
			percent(heapMem.getUsed(), heapMem.getCommitted()),
			percent(heapMem.getUsed(), heapMem.getMax())
		});
		MemoryUsage nonHeapMem = MemoryUsage.from((CompositeData)map.get("NonHeapMemoryUsage"));
		poolAttrValues.put(recorderKey(OBJECT_NAME, "NonHeapMemoryUsage"), new long[]{
			nonHeapMem.getInit(), nonHeapMem.getUsed(), nonHeapMem.getCommitted(), nonHeapMem.getMax(),
			percent(nonHeapMem.getUsed(), nonHeapMem.getCommitted()),
			percent(nonHeapMem.getUsed(), nonHeapMem.getMax())			
		});
		poolAttrValues.put(recorderKey(OBJECT_NAME, "ObjectPendingFinalizationCount"), new long[]{((Integer)map.get("ObjectPendingFinalizationCount")).longValue()});		
	}

}

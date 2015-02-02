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
import java.util.LinkedHashMap;
import java.util.Map;

import javax.management.ObjectName;

import com.codahale.metrics.Gauge;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: GCMonitorMetricSet</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.GCMonitorMetricSet</code></p>
 */

public class GCMonitorMetricSet extends BaseMBeanObserver {
	/** The attribute name for the GC collection count */
	static final String COLLCOUNT = "CollectionCount";
	/** The attribute name for the GC collection time */
	static final String COLLTIME = "CollectionTime";
	
	/** The attribute name for the last GC info composite data set */
	static final String GCINFO = "LastGcInfo";
	
	static final String GC_EVENT_TYPE = "com.sun.management.gc.notification";
	
	/** The attribute names to bulk retrieve */
	static final String[] ATTR_NAMES = {COLLTIME, COLLCOUNT};
	
	/** The number of cores in the target JVM */
	final int cores;
	
	
	/** A map of GC values */
	protected final Map<String, long[]> gcAttrValues = new LinkedHashMap<String, long[]>(2);

	/**
	 * Creates a new GCMonitorMetricSet
	 * @param builder The MBeanObserver builder
	 */
	public GCMonitorMetricSet(final MBeanObserverBuilder builder) {
		super(builder, ATTR_NAMES);
		cores = (Integer)mbs.getAttribute(Util.objectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME), "AvailableProcessors");
		for(final ObjectName on: objectNames) {
			gcAttrValues.put(on.toString(), new long[]{0,0});
			for(int i = 0; i < ATTR_NAMES.length-1; i++) {
				final int index = i;
				final String metricName = String.format("java.lang.gc.%s:%s,%s", ATTR_NAMES[index], on.getCanonicalKeyPropertyListString(),getAgentNameTags());
				final String key = recorderKey(on);
				metrics.put(metricName, new Gauge<Long>(){
					@Override
					public Long getValue() {
						actionCounter.incr();
						final String attrKey = key + ATTR_NAMES[index]; 
						final long d = delta(attrKey, gcAttrValues.get(key)[index], 0L);
						return d;
					}
				});					
			}			
		}
		log.info("Registered [{}] metrics:\n{}", metrics.size(), metrics.keySet());
	}
	

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.BaseMBeanObserver#acceptData(java.util.Map)
	 */
	@Override
	protected void acceptData(final Map<ObjectName, Map<String, Object>> attrMaps) {
		for(ObjectName on: objectNames) {
			Map<String, Object> attrValues = attrMaps.get(on);
			if(attrValues==null) continue;
			log.info("----- [{}]  Accepted Data {}", recorderKey(on), attrValues);
			gcAttrValues.put(recorderKey(on), new long[]{
				(Long)attrValues.get(ATTR_NAMES[0]), (Long)attrValues.get(ATTR_NAMES[1])
			});
		}
	}

}

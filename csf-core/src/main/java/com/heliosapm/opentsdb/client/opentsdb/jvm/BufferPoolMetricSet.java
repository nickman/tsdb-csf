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

import java.util.HashMap;
import java.util.Map;

import javax.management.ObjectName;

import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: BufferPoolMetricSet</p>
 * <p>Description: OpenTSDB naming friendly rewrite of {@link com.codahale.metrics.jvm.BufferPoolMetricSet}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.BufferPoolMetricSet</code></p>
 */

public class BufferPoolMetricSet extends BaseMBeanObserver {
    
	public static final String OBJECT_PATTERN = "java.nio:type=BufferPool,name=*";
	
	/** The class loading mxbean jmx ObjectName */
	static final ObjectName OBJECT_NAME = Util.objectName(OBJECT_PATTERN);
	/** The attribute names to bulk retrieve */
	static final String[] ATTR_NAMES = {"Count", "MemoryUsed", "TotalCapacity"};
	
	protected final Map<ObjectName, long[]> poolAttrValues;
	protected final Map<String, Histogram> histograms;
	
	/**
	 * Creates a new histogram for the passed ObjectName and registers it in the histogram map
	 * @param on The ObjectName to associate with
	 * @return the created histogram
	 */
	protected Histogram createAndRegisterHistogram(final ObjectName on, String...attrs) {
		final Histogram hist = new Histogram(new ExponentiallyDecayingReservoir());
		final String key = recorderKey(on, attrs);
		histograms.put(key, hist);
		return hist;
	}
	

    /**
     * Creates a new BufferPoolMetricSet
     * @param builder The observer builder
     */
    public BufferPoolMetricSet(final MBeanObserverBuilder builder) {
    	super(builder, ATTR_NAMES);
    	poolAttrValues = new HashMap<ObjectName, long[]>(objectNames.size());
    	histograms = new HashMap<String, Histogram>(objectNames.size());
    	for(final ObjectName on: objectNames) {
//    		metrics.put("java.lang.bufferpools.Count:" + on.getCanonicalKeyPropertyListString() + "," + getAgentNameTags(), new Gauge<Long>() {
//    			@Override
//    			public Long getValue() {
//    				latch.countDown();
//    				return poolAttrValues.get(on)[0];
//    			}
//    		});
    		metrics.put("java.lang.bufferpools.Count:" + on.getCanonicalKeyPropertyListString() + "," + getAgentNameTags(),
    				createAndRegisterHistogram(on)
    		);
    		
    		// 
    		metrics.put("java.lang.bufferpools.MemoryUsed:" + on.getCanonicalKeyPropertyListString() + "," + getAgentNameTags(), new Gauge<Long>() {    			
    			@Override
    			public Long getValue() {
    				latch.countDown();
    				long[] vals = poolAttrValues.get(on);
    				return vals[0];
    				//return poolAttrValues.get(on)[0];
    			}
    		});
    		metrics.put("java.lang.bufferpools.TotalCapacity:" + on.getCanonicalKeyPropertyListString() + "," + getAgentNameTags(), new Gauge<Long>() {
    			@Override
    			public Long getValue() {
    				latch.countDown();
    				return poolAttrValues.get(on)[1];
    			}
    		});
    		
    	}
    }

	@Override
	protected void acceptData(final Map<ObjectName, Map<String, Object>> attrMaps) {
		for(Map.Entry<ObjectName, Map<String, Object>> entry : attrMaps.entrySet()) {
			final Map<String, Object> vals = entry.getValue();
			poolAttrValues.put(entry.getKey(), new long[]{
				(Long)vals.get(ATTR_NAMES[1]),
				(Long)vals.get(ATTR_NAMES[2])
			});
			histograms.get(recorderKey(entry.getKey())).update((Long)vals.get(ATTR_NAMES[0]));
		}
	}

}

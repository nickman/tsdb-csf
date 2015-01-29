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
import java.util.Map;

import javax.management.ObjectName;

import com.codahale.metrics.Gauge;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: ClassLoadingMBeanObserver</p>
 * <p>Description: MBeanObserver for monitoring class loading</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.ClassLoadingMBeanObserver</code></p>
 */

public class ClassLoadingMBeanObserver extends BaseMBeanObserver {

	/**  */
	private static final long serialVersionUID = -8550681991711309564L;
	/** The class loading mxbean jmx ObjectName */
	static final ObjectName OBJECT_NAME = Util.objectName(ManagementFactory.CLASS_LOADING_MXBEAN_NAME);
	/** The attribute names to bulk retrieve */
	static final String[] ATTR_NAMES = {"LoadedClassCount", "TotalLoadedClassCount", "UnloadedClassCount"};
	
	/** The loaded class count gauge */
	protected final Gauge<Integer> loadedClassCountGauge;
	/** The total number of classes ever loaded */
	protected final Gauge<Long> totalLoadedClassCountGauge;
	/** The number of classes that have been unloaded  */
	protected final Gauge<Long> unloadedClassCountGauge;
	/** The last read value for the loaded class count  */
	protected int loadedClassCount = 0;
	/** The last read value for the total number of classes ever loaded */
	protected long totalLoadedClassCount = 0;
	/** The last read value for the number of classes that have been unloaded  */
	protected long unloadedClassCount = 0;
	
	
	

	/**
	 * Creates a new ClassLoadingMBeanObserver
	 * @param builder The observer builder
	 */
	public ClassLoadingMBeanObserver(final MBeanObserverBuilder builder) {
		super(builder, ATTR_NAMES);
		loadedClassCountGauge = new Gauge<Integer>() {
			@Override
			public Integer getValue() {
				latch.countDown();
				return loadedClassCount;
			}
		};
		totalLoadedClassCountGauge = new Gauge<Long>() {
			@Override
			public Long getValue() {
				latch.countDown();
				return totalLoadedClassCount;
			}
		};
		unloadedClassCountGauge = new Gauge<Long>() {
			@Override
			public Long getValue() {
				latch.countDown();
				return unloadedClassCount;
			}
		};
		metrics.put("java.lang.classload.CurrentClasses:" + OBJECT_NAME.getCanonicalKeyPropertyListString() + "," + getAgentNameTags(), loadedClassCountGauge);
		metrics.put("java.lang.classload.TotalClasses:" + OBJECT_NAME.getCanonicalKeyPropertyListString() + "," + getAgentNameTags(), totalLoadedClassCountGauge);
		metrics.put("java.lang.classload.UnloadedClasses:" + OBJECT_NAME.getCanonicalKeyPropertyListString() + "," + getAgentNameTags(), unloadedClassCountGauge);
	}
	
	protected void acceptData(final Map<ObjectName, Map<String, Object>> attrMaps) {
		Map<String, Object> attrValues = attrMaps.get(OBJECT_NAME);
		loadedClassCount = (Integer)attrValues.get(ATTR_NAMES[0]);
		totalLoadedClassCount = (Long)attrValues.get(ATTR_NAMES[1]);
		unloadedClassCount = (Long)attrValues.get(ATTR_NAMES[2]);
	}
	
	


}

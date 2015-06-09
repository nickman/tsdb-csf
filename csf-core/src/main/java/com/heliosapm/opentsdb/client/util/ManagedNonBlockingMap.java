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

import java.util.AbstractMap;
import java.util.concurrent.atomic.AtomicLong;

import javax.management.ObjectName;

import org.cliffc.high_scale_lib.NonBlockingHashMap;
import org.cliffc.high_scale_lib.NonBlockingHashMapLong;

import com.heliosapm.utils.jmx.JMXHelper;

/**
 * <p>Title: ManagedNonBlockingMap</p>
 * <p>Description: Wraps and provides JMX instrumentation for a NonBlockingHashMapLong or NonBlockingHashMap instance</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.util.ManagedNonBlockingMap</code></p>
 */

public class ManagedNonBlockingMap implements ManagedNonBlockingMapMBean {
	/** The managed map */
	protected final AbstractMap<?, ?> map;
	/** The application designated name for this managed map */
	protected final String name;
	/** The JMX MBean ObjectName */
	protected final ObjectName objectName;
	/** The total number of reprobes */
	protected final AtomicLong totalReprobes = new AtomicLong(0L);
	/** Indicates if the map is a NonBlockingHashMapLong (true) or a NonBlockingHashMap (false) */
	protected final boolean longMap;
	
	/** The object name template */
	public static final String OBJECT_NAME_TEMPLATE = "com.heliosapm.tsdb.cache:type=%s,name=%s";
	
	
	/**
	 * Creates and registers a ManagedNonBlockingMap JMX MBean for the passed map 
	 * @param map The managed map
	 * @param name The application designated name
	 * @return The managed map instance
	 */
	public static ManagedNonBlockingMap manage(final AbstractMap<?, ?> map, final String name) {
		if(map==null) throw new IllegalArgumentException("The passed NonBlockingHashMapX was null");
		if(name==null || name.trim().isEmpty()) throw new IllegalArgumentException("The passed name was null or empty");
		if(!(map instanceof NonBlockingHashMapLong) && !(map instanceof NonBlockingHashMap)) {
			throw new IllegalArgumentException("The passed map of type [" + map.getClass().getName() + "] was not a NonBlockingHashMapLong or NonBlockingHashMap");
		}
		return new ManagedNonBlockingMap(map, name);
	}

	/**
	 * Creates a new ManagedNonBlockingHashMapLong or ManagedNonBlockingHashMap 
	 * @param map The managed map
	 * @param name The application designated name
	 */
	private ManagedNonBlockingMap(final AbstractMap<?, ?> map, final String name) {
		if(map==null) throw new IllegalArgumentException("The passed NonBlockingHashMapX was null");
		if(name==null || name.trim().isEmpty()) throw new IllegalArgumentException("The passed name was null or empty");
		this.map = map;
		this.longMap = (map instanceof NonBlockingHashMapLong);
		this.name = name.trim();
		objectName = JMXHelper.objectName(String.format(OBJECT_NAME_TEMPLATE, map.getClass().getSimpleName(), name));
		try { JMXHelper.registerMBean(this, objectName); } catch (Exception ex) {
			System.err.println("Failed to register MBean [" + objectName + "]:" + ex);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.util.ManagedNonBlockingMapMBean#getName()
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.util.ManagedNonBlockingMapMBean#getObjectName()
	 */
	@Override
	public ObjectName getObjectName() {
		return objectName;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.util.ManagedNonBlockingMapMBean#getSize()
	 */
	@Override
	public int getSize() {
		return map.size();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.util.ManagedNonBlockingMapMBean#clear()
	 */
	@Override
	public void clear() {
		map.clear();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.util.ManagedNonBlockingMapMBean#getTotalReprobeCount()
	 */
	@Override
	public long getTotalReprobeCount() {
		return totalReprobes.get();
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.util.ManagedNonBlockingMapMBean#getLastReprobeCount()
	 */
	@Override
	public long getLastReprobeCount() {
		final long reprobes = longMap ? ((NonBlockingHashMapLong)map).reprobes() : ((NonBlockingHashMap)map).reprobes(); 
		totalReprobes.addAndGet(reprobes);
		return reprobes;
	}

}

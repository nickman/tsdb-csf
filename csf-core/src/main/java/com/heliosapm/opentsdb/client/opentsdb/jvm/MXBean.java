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

import java.lang.management.*;
import java.util.Arrays;

import javax.management.ObjectName;

import static java.lang.management.ManagementFactory.*;

import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: MXBeans</p>
 * <p>Description: A collection of enums defining the values we might retrieve from MBeans and the Metric types to accept them.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBeans</code></p>
 */

public enum MXBean {
	/** The class loading MXBean */
	CLASSLOADING_MXBEAN(ClassLoadingMXBean.class, Util.objectName(CLASS_LOADING_MXBEAN_NAME), ClassLoadingAttribute.getAllAttributes()),	
	/** The compilation MXBean */
	COMPILATION_MXBEAN(CompilationMXBean.class, Util.objectName(COMPILATION_MXBEAN_NAME)),
	/** The compilation MXBean */
	GARBAGE_COLLECTOR_MXBEAN(GarbageCollectorMXBean.class, Util.objectName(GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE)),
	/** The memory MXBean */
	MEMORY_MXBEAN(MemoryMXBean.class, Util.objectName(MEMORY_MXBEAN_NAME)),
	/** The memory pool MXBean */
	MEMORY_POOL_MXBEAN(MemoryPoolMXBean.class, Util.objectName(MEMORY_POOL_MXBEAN_DOMAIN_TYPE)),
	/** The OS MXBean */
	OPERATING_SYSTEM_MXBEAN(OperatingSystemMXBean.class, Util.objectName(OPERATING_SYSTEM_MXBEAN_NAME)),
	/** The runtime MXBean */
	RUNTIME_MXBEAN(RuntimeMXBean.class, Util.objectName(RUNTIME_MXBEAN_NAME)),
	/** The runtime MXBean */
	THREAD_MXBEAN(ThreadMXBean.class, Util.objectName(THREAD_MXBEAN_NAME));
	

	public static void main(String[] args) {
		System.out.println("Attrs:" + Arrays.toString(CLASSLOADING_MXBEAN.attributeNames));
	}
	
	private MXBean(final Class<?> type, final ObjectName objectName, String...attrNames) {
		this.type = type;
		this.objectName = objectName;
		this.attributeNames = attrNames;
	}
	
	/** The MXBean interface class */
	public final Class<?> type;
	/** The object name of the target instance[s] */
	public final ObjectName objectName;
	/** the attribute names */
	private final String[] attributeNames;
	
	/**
	 * <p>Title: AttributeProvider</p>
	 * <p>Description: Defines an attribute provider that supplies details of the attributes of an MBean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.AttributeProvider</code></p>
	 */
	public static interface AttributeProvider {
		/**
		 * Returns the attribute name
		 * @return the attribute name
		 */
		public String getAttributeName();
		/**
		 * Returns the attribute type
		 * @return the attribute type
		 */
		public Class<?> getType();
		/**
		 * Indicates if the attribute type is a primitive
		 * @return true if the attribute type is a primitive, false otherwise
		 */
		public boolean isPrimitive();
		/**
		 * Returns the attribute mask
		 * @return the attribute mask
		 */
		public int getMask();
	}
	
	/**
	 * Returns the attribute names for the passed enum 
	 * @param type The enum type 
	 * @return An array of attribute names
	 */
	public static <T extends Enum<T> & AttributeProvider> String[] getAttributeNames(Class<T> type) {
		final T[] ecs = type.getEnumConstants();
		final String[] names = new String[ecs.length];
		int cnt = 0;
		for(T ec: ecs) {
			names[cnt] = ec.getAttributeName();
			cnt++;
		}
		return names;
	}
	
	
	public static enum ClassLoadingAttribute implements AttributeProvider {
		/** The currently loaded class count */
		LOADED_CLASS_COUNT("LoadedClassCount", int.class),
		/** The total loaded class count */
		TOTAL_LOADED_CLASS_COUNT("TotalLoadedClassCount", long.class),
		/** The unloaded class count */
		UNLOADED_CLASS_COUNT("UnloadedClassCount", long.class);
		
		private ClassLoadingAttribute(final String attributeName, final Class<?> type) {
			this.attributeName = attributeName;
			this.type = type;
			primitive = type.isPrimitive();
		}
		
		/** The bitmask */
		public final int bitMask = Util.pow2Index(ordinal());
		/** The currently loaded class count */
		public final String attributeName;
		/** The total loaded class count */
		public final Class<?> type;
		/** The unloaded class count */
		public final boolean primitive;
		
		/**
		 * Returns all the attribute names
		 * @return all the attribute names
		 */
		public static String[] getAllAttributes() {
			return getAttributeNames(ClassLoadingAttribute.class);
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}		
	}
}

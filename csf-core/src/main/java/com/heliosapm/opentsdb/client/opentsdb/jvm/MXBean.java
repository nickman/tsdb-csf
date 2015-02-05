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

import static java.lang.management.ManagementFactory.CLASS_LOADING_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.COMPILATION_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE;
import static java.lang.management.ManagementFactory.MEMORY_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE;
import static java.lang.management.ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.RUNTIME_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.THREAD_MXBEAN_NAME;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;

import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: MXBeans</p>
 * <p>Description: A collection of enums defining the values we might retrieve from MBeans and the Metric types to accept them.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBeans</code></p>
 */

public enum MXBean implements MXBeanManager {
	/** The class loading MXBean */
	CLASSLOADING_MXBEAN(ClassLoadingMXBean.class, Util.objectName(CLASS_LOADING_MXBEAN_NAME), ClassLoadingAttribute.class),	
	/** The compilation MXBean */
	COMPILATION_MXBEAN(CompilationMXBean.class, Util.objectName(COMPILATION_MXBEAN_NAME), CompilationAttribute.class),
	/** The compilation MXBean */
	GARBAGE_COLLECTOR_MXBEAN(GarbageCollectorMXBean.class, Util.objectName(GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE), GarbageCollectorAttribute.class),
	/** The memory MXBean */
	MEMORY_MXBEAN(MemoryMXBean.class, Util.objectName(MEMORY_MXBEAN_NAME), MemoryAttribute.class),
	/** The memory pool MXBean */
	MEMORY_POOL_MXBEAN(MemoryPoolMXBean.class, Util.objectName(MEMORY_POOL_MXBEAN_DOMAIN_TYPE), MemoryPoolAttribute.class),
	/** The OS MXBean */
	OPERATING_SYSTEM_MXBEAN(OperatingSystemMXBean.class, Util.objectName(OPERATING_SYSTEM_MXBEAN_NAME), OperatingSystemAttribute.class),
	/** The runtime MXBean */
	RUNTIME_MXBEAN(RuntimeMXBean.class, Util.objectName(RUNTIME_MXBEAN_NAME), RuntimeAttribute.class),
	/** The runtime MXBean */
	THREAD_MXBEAN(ThreadMXBean.class, Util.objectName(THREAD_MXBEAN_NAME), ThreadingAttribute.class);
	

	public static void main(String[] args) {
		System.out.println("Attrs:" + Arrays.toString(CLASSLOADING_MXBEAN.getAttributeProviders()));
	}
	
	private MXBean(final Class<?> type, final ObjectName objectName, final Class<? extends AttributeManager<?>> attributeManager) {
		this.type = type;
		this.objectName = objectName;
		this.am = attributeManager;
	}
	
	/** The MXBean interface class */
	public final Class<?> type;
	/** The object name of the target instance[s] */
	public final ObjectName objectName;
	/** the attribute names */
	private final Class<? extends AttributeManager<?>> am;
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String[] getAttributeNames() {
		return getAttributeNames(am);
	}
	
	/**
	 * {@inheritDoc}
	 */
	@SuppressWarnings("unchecked")
	public <T extends Enum<T> & AttributeProvider> T[] getAttributeProviders() {
		return (T[]) am.getEnumConstants();
	}
	
	
	/**
	 * Returns the attribute names for the passed enum 
	 * @param type The enum type 
	 * @return An array of attribute names
	 */
	public static <T extends AttributeManager<?>> String[] getAttributeNames(Class<T> type) {
		final T[] ecs = type.getEnumConstants();
		final String[] names = new String[ecs.length];
		int cnt = 0;
		for(T ec: ecs) {
			names[cnt] = ec.getAttributeName();
			cnt++;
		}
		return names;
	}
	
	
	/**
	 * <p>Title: ClassLoadingAttribute</p>
	 * <p>Description: Attribute manager for </p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.ClassLoadingAttribute</code></p>
	 */
	public static enum ClassLoadingAttribute implements AttributeManager<ClassLoadingAttribute> {
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
		public final int bitMask = POW2.get(ordinal());
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
	
	/**
	 * <p>Title: CompilationAttribute</p>
	 * <p>Description: Attribute manager for the Compilation MXBean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.CompilationAttribute</code></p>
	 */	
	public static enum CompilationAttribute implements AttributeManager<CompilationAttribute> {
		/**  */
		TOTAL_COMPILATION_TIME("TotalCompilationTime", long.class);
		
		private CompilationAttribute(final String attributeName, final Class<?> type) {
			this.attributeName = attributeName;
			this.type = type;
			primitive = type.isPrimitive();
		}
		
		/** The bitmask */
		public final int bitMask = POW2.get(ordinal());
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
			return getAttributeNames(CompilationAttribute.class);
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

	/**
	 * <p>Title: GarbageCollectorAttribute</p>
	 * <p>Description: Attribute manager for the GarbageCollector MXBean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.GarbageCollectorAttribute</code></p>
	 */	
	public static enum GarbageCollectorAttribute implements AttributeManager<GarbageCollectorAttribute> {
		/**  */
		LAST_GC_INFO("LastGcInfo", CompositeData.class),
		/**  */
		COLLECTION_COUNT("CollectionCount", long.class);

		
		private GarbageCollectorAttribute(final String attributeName, final Class<?> type) {
			this.attributeName = attributeName;
			this.type = type;
			primitive = type.isPrimitive();
		}
		
		/** The bitmask */
		public final int bitMask = POW2.get(ordinal());
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
			return getAttributeNames(GarbageCollectorAttribute.class);
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

	/**
	 * <p>Title: MemoryAttribute</p>
	 * <p>Description: Attribute manager for the Memory MXBean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.MemoryAttribute</code></p>
	 */	
	public static enum MemoryAttribute implements AttributeManager<MemoryAttribute> {
		/**  */
		HEAP_MEMORY_USAGE("HeapMemoryUsage", CompositeData.class),
		/**  */
		NON_HEAP_MEMORY_USAGE("NonHeapMemoryUsage", CompositeData.class),
		/**  */
		OBJECT_PENDING_FINALIZATION_COUNT("ObjectPendingFinalizationCount", int.class);

		
		private MemoryAttribute(final String attributeName, final Class<?> type) {
			this.attributeName = attributeName;
			this.type = type;
			primitive = type.isPrimitive();
		}
		
		/** The bitmask */
		public final int bitMask = POW2.get(ordinal());
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
			return getAttributeNames(MemoryAttribute.class);
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

	/**
	 * <p>Title: MemoryPoolAttribute</p>
	 * <p>Description: Attribute manager for the MemoryPool MXBean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.MemoryPoolAttribute</code></p>
	 */	
	public static enum MemoryPoolAttribute implements AttributeManager<MemoryPoolAttribute> {
		
		/**  */
		COLLECTION_USAGE("CollectionUsage", CompositeData.class),
		/**  */
		COLLECTION_USAGE_THRESHOLD_COUNT("CollectionUsageThresholdCount", long.class),
		/**  */
		PEAK_USAGE("PeakUsage", CompositeData.class),
		/**  */
		USAGE_THRESHOLD_COUNT("UsageThresholdCount", long.class),
		/**  */
		USAGE("Usage", CompositeData.class);

		
		private MemoryPoolAttribute(final String attributeName, final Class<?> type) {
			this.attributeName = attributeName;
			this.type = type;
			primitive = type.isPrimitive();
		}
		
		/** The bitmask */
		public final int bitMask = POW2.get(ordinal());
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
			return getAttributeNames(MemoryPoolAttribute.class);
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

	/**
	 * <p>Title: OperatingSystemAttribute</p>
	 * <p>Description: Attribute manager for the OperatingSystem MXBean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.OperatingSystemAttribute</code></p>
	 */	
	public static enum OperatingSystemAttribute implements AttributeManager<OperatingSystemAttribute> {
		/**  */
		MAX_FILE_DESCRIPTOR_COUNT("MaxFileDescriptorCount", long.class),
		/**  */
		OPEN_FILE_DESCRIPTOR_COUNT("OpenFileDescriptorCount", long.class),
		/**  */
		COMMITTED_VIRTUAL_MEMORY_SIZE("CommittedVirtualMemorySize", long.class),
		/**  */
		FREE_PHYSICAL_MEMORY_SIZE("FreePhysicalMemorySize", long.class),
		/**  */
		FREE_SWAP_SPACE_SIZE("FreeSwapSpaceSize", long.class),
		/**  */
		PROCESS_CPU_LOAD("ProcessCpuLoad", double.class),
		/**  */
		PROCESS_CPU_TIME("ProcessCpuTime", long.class),
		/**  */
		SYSTEM_CPU_LOAD("SystemCpuLoad", double.class),
		/**  */
		TOTAL_PHYSICAL_MEMORY_SIZE("TotalPhysicalMemorySize", long.class),
		/**  */
		TOTAL_SWAP_SPACE_SIZE("TotalSwapSpaceSize", long.class),
		/**  */
		SYSTEM_LOAD_AVERAGE("SystemLoadAverage", double.class);
		
		private OperatingSystemAttribute(final String attributeName, final Class<?> type) {
			this.attributeName = attributeName;
			this.type = type;
			primitive = type.isPrimitive();
		}
		
		/** The bitmask */
		public final int bitMask = POW2.get(ordinal());
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
			return getAttributeNames(OperatingSystemAttribute.class);
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

	/**
	 * <p>Title: RuntimeAttribute</p>
	 * <p>Description: Attribute manager for the Runtime MXBean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.RuntimeAttribute</code></p>
	 */	
	public static enum RuntimeAttribute implements AttributeManager<RuntimeAttribute> {
		/**  */
		UPTIME("Uptime", long.class);

		
		private RuntimeAttribute(final String attributeName, final Class<?> type) {
			this.attributeName = attributeName;
			this.type = type;
			primitive = type.isPrimitive();
		}
		
		/** The bitmask */
		public final int bitMask = POW2.get(ordinal());
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
			return getAttributeNames(RuntimeAttribute.class);
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

	/**
	 * <p>Title: ThreadingAttribute</p>
	 * <p>Description: Attribute manager for the Threading MXBean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.ThreadingAttribute</code></p>
	 */	
	public static enum ThreadingAttribute implements AttributeManager<ThreadingAttribute> {
		/**  */
		DAEMON_THREAD_COUNT("DaemonThreadCount", int.class),
		/**  */
		PEAK_THREAD_COUNT("PeakThreadCount", int.class),
		/**  */
		THREAD_COUNT("ThreadCount", int.class),
		/**  */
		TOTAL_STARTED_THREAD_COUNT("TotalStartedThreadCount", long.class);

		
		private ThreadingAttribute(final String attributeName, final Class<?> type) {
			this.attributeName = attributeName;
			this.type = type;
			primitive = type.isPrimitive();
		}
		
		/** The bitmask */
		public final int bitMask = POW2.get(ordinal());
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
			return getAttributeNames(ThreadingAttribute.class);
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

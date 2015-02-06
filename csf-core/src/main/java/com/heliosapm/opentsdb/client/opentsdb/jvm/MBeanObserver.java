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
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.management.MBeanAttributeInfo;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: MXBeans</p>
 * <p>Description: A collection of enums defining the values we might retrieve from MBeans and the Metric types to accept them.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MBeanObserver</code></p>
 */

public enum MBeanObserver implements MXBeanManager {
	/** The class loading MXBean */
	CLASSLOADING_MXBEAN(ClassLoadingMXBean.class, Util.objectName(CLASS_LOADING_MXBEAN_NAME), ClassLoadingAttribute.class),	
	/** The compilation MXBean */
	COMPILATION_MXBEAN(CompilationMXBean.class, Util.objectName(COMPILATION_MXBEAN_NAME), CompilationAttribute.class),
	/** The compilation MXBean */
	GARBAGE_COLLECTOR_MXBEAN(GarbageCollectorMXBean.class, Util.objectName(GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*"), GarbageCollectorAttribute.class),
	/** The memory MXBean */
	MEMORY_MXBEAN(MemoryMXBean.class, Util.objectName(MEMORY_MXBEAN_NAME), MemoryAttribute.class),
	/** The memory pool MXBean */
	MEMORY_POOL_MXBEAN(MemoryPoolMXBean.class, Util.objectName(MEMORY_POOL_MXBEAN_DOMAIN_TYPE + ",*"), MemoryPoolAttribute.class),
	/** The OS MXBean */
	OPERATING_SYSTEM_MXBEAN(OperatingSystemMXBean.class, Util.objectName(OPERATING_SYSTEM_MXBEAN_NAME), OperatingSystemAttribute.class),
	/** The runtime MXBean */
	RUNTIME_MXBEAN(RuntimeMXBean.class, Util.objectName(RUNTIME_MXBEAN_NAME), RuntimeAttribute.class),
	/** The runtime MXBean */
	THREAD_MXBEAN(ThreadMXBean.class, Util.objectName(THREAD_MXBEAN_NAME), ThreadingAttribute.class);
	

	public static void main(String[] args) {
		for(MBeanObserver mx: MBeanObserver.values()) {
			System.out.println(mx.name() + "  Providers:" + Arrays.toString(mx.getAttributeProviders()));
			System.out.println(mx.name() + "  Attrs:" + Arrays.toString(mx.getAttributeNames()));
		}
		
	}
	
	private MBeanObserver(final Class<?> type, final ObjectName objectName, final Class<? extends AttributeManager<?>> attributeManager) {
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
	 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MXBeanManager#getAttributeNames()
	 */
	@Override
	public String[] getAttributeNames() {
		return getAttributeNames(am);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MXBeanManager#getAttributeProviders()
	 */
	@SuppressWarnings("rawtypes")
	@Override	
	public <T extends Enum<T> & AttributeProvider> T[] getAttributeProviders() {
		return (T[]) am.getEnumConstants();
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MXBeanManager#getNameMasks()
	 */
	@Override
	public <T extends Enum<T> & AttributeProvider> Map<String, Integer> getNameMasks() {
		final T[] providers = getAttributeProviders();
		final Map<String, Integer> map = new LinkedHashMap<String, Integer>(providers.length);
		for(T provider: providers) {
			map.put(provider.getAttributeName(), provider.getMask());
		}
		return map;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MXBeanManager#getMaskFor(MBeanAttributeInfo...)
	 */
	@Override
	public int getMaskFor(final MBeanAttributeInfo...infos) {
		int mask = 0;
		final Map<String, Integer> attrMaskMap = getNameMasks();
		for(MBeanAttributeInfo info: infos) {
			Integer bmask = attrMaskMap.get(info.getName());
			if(bmask==null) continue;
			mask = mask | bmask.intValue();
		}
		return mask;
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MXBeanManager#getAttributeNames(int)
	 */
	@SuppressWarnings("rawtypes")
	@Override
	public <T extends Enum<T> & AttributeProvider> String[] getAttributeNames(final int mask) {
		final T[] providers = getAttributeProviders(mask);
		Set<String> names = new LinkedHashSet<String>(providers.length);
		for(T provider: providers) {
			names.add(provider.getAttributeName());
		}
		return names.toArray(new String[names.size()]);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MXBeanManager#getAttributeProviders(int)
	 */
	@Override
	@SuppressWarnings("rawtypes")
	public <T extends Enum<T> & AttributeProvider> T[] getAttributeProviders(int mask) {
		final T[] providers = getAttributeProviders();
		Class<T> enumType = providers[0].getDeclaringClass();
		EnumSet<T> filtered = EnumSet.noneOf(enumType);
		for(T provider: providers) {
			if(provider.isEnabledFor(mask)) {
				filtered.add(provider);
			}
		}
		return filtered.toArray((T[]) Array.newInstance(enumType, filtered.size()));		
	}
	
	
	/**
	 * Returns the attribute names for the passed enum 
	 * @param type The enum type 
	 * @return An array of attribute names
	 */
	public static <T extends AttributeManager<?>> String[] getAttributeNames(Class<T> type) {
		final T[] ecs = type.getEnumConstants();
		final Set<String> names = new LinkedHashSet<String>(ecs.length);
		for(T ec: ecs) {
			names.add(ec.getAttributeName());
		}
		return names.toArray(new String[names.size()]);
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
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (bitMask & mask)==mask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return attributeName; 
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
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (bitMask & mask)==mask;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return attributeName; 
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
		LAST_GC_INFO_COMMITTED("LastGcInfo", "committed", CompositeData.class, long.class),
		/**  */
		LAST_GC_INFO_INIT("LastGcInfo", "init", CompositeData.class, long.class),
		/**  */
		LAST_GC_INFO_MAX("LastGcInfo", "max", CompositeData.class, long.class),
		/**  */
		LAST_GC_INFO_USED("LastGcInfo", "used", CompositeData.class, long.class),		
		/**  */
		COLLECTION_COUNT("CollectionCount", long.class);

		
		private GarbageCollectorAttribute(final String attributeName, final String subAttributeName, final Class<?> type, final Class<?> transformedType) {
			this.attributeName = attributeName;
			this.type = type;
			primitive = type.isPrimitive();
			this.subAttributeName = subAttributeName==null ? "": subAttributeName;
			this.transformedType = transformedType==null ? this.type: transformedType;
		}
		
		private GarbageCollectorAttribute(final String attributeName, final Class<?> type) {
			this(attributeName, null, type, null);
		}
		
		
		/** The bitmask */
		public final int bitMask = POW2.get(ordinal());
		/** The attribute name */
		public final String attributeName;
		/** The sub-attribute name */
		public final String subAttributeName;		
		/** The type of the attribute */
		public final Class<?> type;
		/** The tranformed type which is returned to metrics */
		public final Class<?> transformedType;		
		/** Indicates if the type is primitive */
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
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (bitMask & mask)==mask;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			if(primitive) {
				return input;
			}
			final CompositeData cd = (CompositeData)input;
			return cd==null ? 0L : cd.get(subAttributeName);			
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return subAttributeName.isEmpty() ? attributeName : (attributeName + "." + subAttributeName); 
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
		HEAP_MEMORY_USAGE_COMMITTED("HeapMemoryUsage", "committed", CompositeData.class, long.class),
		/**  */
		HEAP_MEMORY_USAGE_INIT("HeapMemoryUsage", "init", CompositeData.class, long.class),
		/**  */
		HEAP_MEMORY_USAGE_MAX("HeapMemoryUsage", "max", CompositeData.class, long.class),
		/**  */
		HEAP_MEMORY_USAGE_USED("HeapMemoryUsage", "used", CompositeData.class, long.class),
		/**  */
		NON_HEAP_MEMORY_USAGE_COMMITTED("NonHeapMemoryUsage", "committed", CompositeData.class, long.class),
		/**  */
		NON_HEAP_MEMORY_USAGE_INIT("NonHeapMemoryUsage", "init", CompositeData.class, long.class),
		/**  */
		NON_HEAP_MEMORY_USAGE_MAX("NonHeapMemoryUsage", "max", CompositeData.class, long.class),
		/**  */
		NON_HEAP_MEMORY_USAGE_USED("NonHeapMemoryUsage", "used", CompositeData.class, long.class),
		/**  */
		OBJECT_PENDING_FINALIZATION_COUNT("ObjectPendingFinalizationCount", int.class);

		
		private MemoryAttribute(final String attributeName, final String subAttributeName, final Class<?> type, final Class<?> transformedType) {
			this.attributeName = attributeName;
			this.type = type;
			primitive = type.isPrimitive();
			this.subAttributeName = subAttributeName==null ? "": subAttributeName;
			this.transformedType = transformedType==null ? this.type: transformedType;
		}
		
		private MemoryAttribute(final String attributeName, final Class<?> type) {
			this(attributeName, null, type, null);
		}
		
		
		/** The bitmask */
		public final int bitMask = POW2.get(ordinal());
		/** The attribute name */
		public final String attributeName;
		/** The sub-attribute name */
		public final String subAttributeName;		
		/** The type of the attribute */
		public final Class<?> type;
		/** The tranformed type which is returned to metrics */
		public final Class<?> transformedType;		
		/** Indicates if the type is primitive */
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
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (bitMask & mask)==mask;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			if(primitive) {
				return input;
			}
			final CompositeData cd = (CompositeData)input;
			return cd==null ? 0L : cd.get(subAttributeName);			
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return subAttributeName.isEmpty() ? attributeName : (attributeName + "." + subAttributeName); 
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
		COLLECTION_USAGE_COMMITTED("CollectionUsage", "committed", CompositeData.class, long.class),
		/**  */
		COLLECTION_USAGE_INIT("CollectionUsage", "init", CompositeData.class, long.class),
		/**  */
		COLLECTION_USAGE_MAX("CollectionUsage", "max", CompositeData.class, long.class),
		/**  */
		COLLECTION_USAGE_USED("CollectionUsage", "used", CompositeData.class, long.class),
		/**  */
		COLLECTION_USAGE_THRESHOLD_COUNT("CollectionUsageThresholdCount", long.class),
		/**  */
		PEAK_USAGE_COMMITTED("PeakUsage", "committed", CompositeData.class, long.class),
		/**  */
		PEAK_USAGE_INIT("PeakUsage", "init", CompositeData.class, long.class),
		/**  */
		PEAK_USAGE_MAX("PeakUsage", "max", CompositeData.class, long.class),
		/**  */
		PEAK_USAGE_USED("PeakUsage", "used", CompositeData.class, long.class),		
		/**  */
		USAGE_THRESHOLD_COUNT("UsageThresholdCount", long.class),
		/**  */
		USAGE_COMMITTED("Usage", "committed", CompositeData.class, long.class),
		/**  */
		USAGE_INIT("Usage", "init", CompositeData.class, long.class),
		/**  */
		USAGE_MAX("Usage", "max", CompositeData.class, long.class),
		/**  */
		USAGE_USED("Usage", "used", CompositeData.class, long.class);

		
		private MemoryPoolAttribute(final String attributeName, final String subAttributeName, final Class<?> type, final Class<?> transformedType) {
			this.attributeName = attributeName;
			this.type = type;
			primitive = type.isPrimitive();
			this.subAttributeName = subAttributeName==null ? "": subAttributeName;
			this.transformedType = transformedType==null ? this.type: transformedType;
		}
		
		private MemoryPoolAttribute(final String attributeName, final Class<?> type) {
			this(attributeName, null, type, null);
		}
		
		/** The bitmask */
		public final int bitMask = POW2.get(ordinal());
		/** The attribute name */
		public final String attributeName;
		/** The sub-attribute name */
		public final String subAttributeName;		
		/** The type of the attribute */
		public final Class<?> type;
		/** The tranformed type which is returned to metrics */
		public final Class<?> transformedType;		
		/** Indicates if the type is primitive */
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
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (bitMask & mask)==mask;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			if(primitive) {
				return input;
			}
			final CompositeData cd = (CompositeData)input;
			return cd==null ? 0L : cd.get(subAttributeName);			
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return subAttributeName.isEmpty() ? attributeName : (attributeName + "." + subAttributeName); 
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
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (bitMask & mask)==mask;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return attributeName; 
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
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (bitMask & mask)==mask;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return attributeName; 
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
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (bitMask & mask)==mask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return attributeName; 
		}
		
		
	}

	
	/**
	 * <p>Title: DataAcceptor</p>
	 * <p>Description: Defines a {@link Metric} extension that accepts the data it will provide</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.DataAcceptor</code></p>
	 * @param <I> The expected type of the data to be accepted
	 */
	public interface DataAcceptor<I> extends Metric {
		/**
		 * Accepts the data to be transformed and served
		 * @param dataMap The data map containing the data to be accepted, keyed by the attribute name
		 */
		public void accept(Map<String, I> dataMap);
	}
	
	/**
	 * <p>Title: DataAcceptorGauge</p>
	 * <p>Description: A data accepting {@link Gauge} implementation</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBean.DataAcceptorGauge</code></p>
	 * @param <I> The expected type of the accepted data
	 * @param <O> The expected type of the gauge
	 */
	@SuppressWarnings("rawtypes")
	public class DataAcceptorGauge<I, O> implements Gauge<O>, DataAcceptor<I> {
		/** The attribute provider for this gauge */
		protected final AttributeProvider attributeProvider;
		/** The data to be accepted */
		protected I data = null;
		
		
		/**
		 * Creates a new DataAcceptorGauge
		 * @param attributeProvider The attribute provider that specifies the data key and transform
		 */		
		public DataAcceptorGauge(final AttributeProvider attributeProvider) {
			this.attributeProvider = attributeProvider;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.codahale.metrics.Gauge#getValue()
		 */
		@Override
		public O getValue() {
			return (O) attributeProvider.extractDataFrom(data);
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.opentsdb.jvm.MBeanObserver.DataAcceptor#accept(java.util.Map)
		 */
		@Override
		public void accept(final Map<String, I> dataMap) {
			if(dataMap==null) throw new IllegalArgumentException("The passed data map was null");
			data = dataMap.get(attributeProvider.getAttributeName());
			if(data==null) throw new RuntimeException("No data found in data map for attribute [" + attributeProvider.getAttributeName() + "]");			
		}
	}
	
	public class DataAcceptorGaugeSet<I, O> implements MetricSet {
		protected final Map<String, DataAcceptorGauge<I, O>> gauges = new HashMap<String, DataAcceptorGauge<I, O>>();
		protected final MBeanObserver observer;
		protected final RuntimeMBeanServerConnection mbs;
		
		/**
		 * Creates a new DataAcceptorGaugeSet
		 * @param observer The observer defining what is to be collected
		 */
		public DataAcceptorGaugeSet(final MBeanObserver observer, final RuntimeMBeanServerConnection mbs) {
			this.observer = observer;
			this.mbs = mbs;			
		}

		/**
		 * {@inheritDoc}
		 * @see com.codahale.metrics.MetricSet#getMetrics()
		 */
		@Override
		public Map<String, Metric> getMetrics() {
			return new TreeMap<String, Metric>(gauges);
		}
		
		
		
	}
}

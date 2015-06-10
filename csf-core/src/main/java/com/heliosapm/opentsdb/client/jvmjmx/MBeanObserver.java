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

package com.heliosapm.opentsdb.client.jvmjmx;

import static java.lang.management.ManagementFactory.CLASS_LOADING_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.COMPILATION_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE;
import static java.lang.management.ManagementFactory.MEMORY_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.MEMORY_POOL_MXBEAN_DOMAIN_TYPE;
import static java.lang.management.ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.RUNTIME_MXBEAN_NAME;
import static java.lang.management.ManagementFactory.THREAD_MXBEAN_NAME;

import java.lang.management.BufferPoolMXBean;
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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.heliosapm.opentsdb.client.jvmjmx.ObserverFactories.ObserverFactory;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.util.Util;
import com.heliosapm.utils.jmx.JMXHelper;

/**
 * <p>Title: MBeanObserver</p>
 * <p>Description: All info for all JVM provided mbeans</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver</code></p>
 */

public enum MBeanObserver implements MXBeanDescriptor, ObserverFactory {
	/** The class loading MXBean */
	CLASSLOADING_MXBEAN(ClassLoadingMXBean.class, JMXHelper.objectName(CLASS_LOADING_MXBEAN_NAME), ClassLoadingAttribute.class){
		@Override
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String... args) {
			return new ClassLoadingMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
		}
	},	
	/** The compilation MXBean */
	COMPILATION_MXBEAN(CompilationMXBean.class, JMXHelper.objectName(COMPILATION_MXBEAN_NAME), CompilationAttribute.class){
		@Override
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String... args) {
			return new CompilationMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
		}
	},
	/** The compilation MXBean */
	GARBAGE_COLLECTOR_MXBEAN(GarbageCollectorMXBean.class, JMXHelper.objectName(GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",name=*"), GarbageCollectorAttribute.class){
		@Override
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String... args) {
			return new GarbageCollectorMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
		}
	},
	/** The memory MXBean */
	MEMORY_MXBEAN(MemoryMXBean.class, JMXHelper.objectName(MEMORY_MXBEAN_NAME), MemoryAttribute.class){
		@Override
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String... args) {
			return new MemoryCollectorMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
		}
	},
	/** The memory pool MXBean */
	MEMORY_POOL_MXBEAN(MemoryPoolMXBean.class, JMXHelper.objectName(MEMORY_POOL_MXBEAN_DOMAIN_TYPE + ",name=*"), MemoryPoolAttribute.class){
		@Override
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String... args) {
			return new MemoryPoolsCollectorMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
		}
	},
	/** The OS MXBean */
	OPERATING_SYSTEM_MXBEAN(OperatingSystemMXBean.class, JMXHelper.objectName(OPERATING_SYSTEM_MXBEAN_NAME), OperatingSystemAttribute.class){
		@Override
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String... args) {
			return new OperatingSystemCollectorMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
		}
	},
	/** The runtime MXBean */
	RUNTIME_MXBEAN(RuntimeMXBean.class, JMXHelper.objectName(RUNTIME_MXBEAN_NAME), RuntimeAttribute.class){
		@Override
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String... args) {
			return null;  // FIXME
		}
	},
	/** The threading MXBean */
	THREAD_MXBEAN(ThreadMXBean.class, JMXHelper.objectName(THREAD_MXBEAN_NAME), ThreadingAttribute.class){
		@Override
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String... args) {
			return new ThreadingCollectorMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
		}
	},
	/** The NIO Buffer Pool MXBean */
	NIOBUFFER_MXBEAN(Util.loadClassByName("java.lang.management.BufferPoolMXBean", null), JMXHelper.objectName("java.nio:type=BufferPool,name=*"), BufferPoolAttribute.class){
		@Override
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String... args) {
			return null; // FIXME
		}
	},     	
	/** The Hotspot Internal Memory MBean */
	@SuppressWarnings("restriction")
	HOTSPOT_MEMORY_MBEAN(sun.management.HotspotMemoryMBean.class, JMXHelper.objectName("sun.management:type=HotspotMemory"), HotspotInternalMemoryAttribute.class){
		@Override
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String... args) {
			return GCConfiguration.getInstance(mbeanServerConn, Pattern.compile(".*"));
		}
	},
	/** The Hotspot Internal ClassLoading MBean */
	@SuppressWarnings("restriction")
	HOTSPOT_CLASSLOADING_MBEAN(sun.management.HotspotClassLoadingMBean.class, JMXHelper.objectName("sun.management:type=HotspotClassLoading"), HotspotInternalClassLoadingAttribute.class){
		@Override
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String... args) {
			return new HotSpotInternalsBaseMBeanObserver(mbeanServerConn, publishObserverMBean, tags, "ClassLoading", ".*");
		}
	},
	/** The Hotspot Internal Compilation MBean */
	@SuppressWarnings("restriction")
	HOTSPOT_COMPILATION_MBEAN(sun.management.HotspotCompilationMBean.class, JMXHelper.objectName("sun.management:type=HotspotCompilation"), HotspotInternalCompilationAttribute.class){
		@Override
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String... args) {
			return new HotSpotInternalsBaseMBeanObserver(mbeanServerConn, publishObserverMBean, tags, "Compilation", ".*");
		}
	},
	/** The Hotspot Internal Runtime MBean */
	@SuppressWarnings("restriction")
	HOTSPOT_RUNTIME_MBEAN(sun.management.HotspotRuntimeMBean.class, JMXHelper.objectName("sun.management:type=HotspotRuntime"), HotspotInternalRuntimeAttribute.class){
		@Override
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String... args) {
			return new HotSpotInternalsBaseMBeanObserver(mbeanServerConn, publishObserverMBean, tags, "Runtime", ".*");
		}
	},
	/** The Hotspot Internal Runtime MBean */
	@SuppressWarnings("restriction")
	HOTSPOT_THREADING_MBEAN(sun.management.HotspotThreadMBean.class, JMXHelper.objectName("sun.management:type=HotspotThreading"), HotspotInternalThreadingAttribute.class){
		@Override
		public BaseMBeanObserver build(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean, final String... args) {
			return new HotSpotInternalsBaseMBeanObserver(mbeanServerConn, publishObserverMBean, tags, "Threading", ".*");
		}
	};
	

	private MBeanObserver(final Class<?> type, final ObjectName objectName, final Class<? extends AttributeManager<?>> attributeManager) {
		this.type = type;
		this.objectName = objectName;
		this.am = attributeManager;
	}
	
	/** Static class logger */
	private static final Logger LOG = LogManager.getLogger(MBeanObserver.class);

	
	/** The MXBean interface class */
	public final Class<?> type;
	/** The object name of the target instance[s] */
	public final ObjectName objectName;
	/** the attribute names */
	private final Class<? extends AttributeManager<?>> am;
	
	/** The hotspot internal MBeanObservers keyed by the short name */
	public static final Map<String, MBeanObserver> hotspotMbeanObservers;
	
	public static final ObjectName PLATFORM_PATTERN = JMXHelper.objectName("java.lang:*");
	public static final ObjectName PLATFORM_NIO_PATTERN = JMXHelper.objectName("java.nio:*");
	public static final ObjectName HOTSPOT_PATTERN = JMXHelper.objectName("sun.management:*");
	
	
	/** All known ObjectNames. Any others must use a custom collector */
	public static final Map<ObjectName, MBeanObserver> ALL_OBJECT_NAMES;
	/** The default ObjectNames */
	public static final Map<ObjectName, MBeanObserver> DEFAULT_OBJECT_NAMES;
	
	static {
		
		final MBeanObserver[] values = MBeanObserver.values();
		final Map<ObjectName, MBeanObserver> tmpOns = new HashMap<ObjectName, MBeanObserver>(values.length);
		final Map<ObjectName, MBeanObserver> defOns = new HashMap<ObjectName, MBeanObserver>(values.length);
		final Map<String, MBeanObserver> hotspotBeans = new HashMap<String, MBeanObserver>();
		for(MBeanObserver mbo: values) {
			if(mbo.name().startsWith("HOTSPOT_")) {
				hotspotBeans.put(mbo.objectName.getKeyProperty("type").replace("Hotspot", "").trim().toLowerCase(), mbo);
			} else {
				defOns.put(mbo.objectName, mbo);
			}
			tmpOns.put(mbo.objectName, mbo);
		}
		hotspotMbeanObservers = Collections.unmodifiableMap(hotspotBeans);
		ALL_OBJECT_NAMES = Collections.unmodifiableMap(tmpOns);
		DEFAULT_OBJECT_NAMES = Collections.unmodifiableMap(defOns);
	}
	
	/**
	 * Creates a an array of MBeanObservers based on the passed include and exclude ObjectName filters
	 * @param includes A set of ObjectName patterns to include. Ignored if null.
	 * @param excludes A set of ObjectName patterns to exclude. Ignored if null.
	 * @return an array of matching MBeanObservers
	 */
	public static MBeanObserver[] filter(final Set<String> includes, final Set<String> excludes) {
		final EnumSet<MBeanObserver> set = EnumSet.noneOf(MBeanObserver.class);
		if(includes==null || includes.isEmpty()) {
			set.addAll(DEFAULT_OBJECT_NAMES.values());
		} else {
			for(String s: includes) {
				try {
					final ObjectName on = JMXHelper.objectName(s);
					for(Map.Entry<ObjectName, MBeanObserver> entry : ALL_OBJECT_NAMES.entrySet()) {
						if(on.apply(JMXHelper.dePatternize(entry.getKey()))) {
							set.add(entry.getValue());
						}
					}
				} catch (Exception ex) {
					LOG.warn("Ignoring invalid include patter: [{}]", s);
				}
			}
		}
		if(excludes!=null && !excludes.isEmpty()) {
			for(String s: excludes) {
				try {
					final ObjectName on = JMXHelper.objectName(s);
					for(Map.Entry<ObjectName, MBeanObserver> entry : ALL_OBJECT_NAMES.entrySet()) {
						if(on.apply(entry.getKey())) {
							set.remove(entry.getValue());
						}
					}
				} catch (Exception ex) {
					LOG.warn("Ignoring invalid include patter: [{}]", s);
				}
			}			
		}
		if(!Constants.IS_JAVA_7) {
			set.remove(NIOBUFFER_MXBEAN);
		}		
		return set.toArray(new MBeanObserver[set.size()]);
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
	
	public String[] getAttributeNames() {
		return getAttributeNames(am);
	}
	
	public <T extends Enum<T> & AttributeProvider> T[] getAttributeProviders() {
		return (T[]) am.getEnumConstants();
	}
	
	public <T extends Enum<T> & AttributeProvider> Map<String, Integer> getNameMasks() {
		final T[] providers = getAttributeProviders();
		final Map<String, Integer> map = new LinkedHashMap<String, Integer>(providers.length);
		for(T provider: providers) {
			map.put(provider.getAttributeName(), provider.getMask());
		}
		return map;
	}
	
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
	
	public <T extends Enum<T> & AttributeProvider> String[] getAttributeNames(final int mask) {
		final T[] providers = getAttributeProviders(mask);
		Set<String> names = new LinkedHashSet<String>(providers.length);
		for(T provider: providers) {
			names.add(provider.getAttributeName());
		}
		return names.toArray(new String[names.size()]);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
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
	 * Returns an array of the hotspot internal mbeanobserver mbean members
	 * @return an array of the hotspot internal mbeanobserver mbean members
	 */
	public static MBeanObserver[] getHotSpotObservers() {
		return hotspotMbeanObservers.values().toArray(new MBeanObserver[hotspotMbeanObservers.size()]);
	}
	
	/**
	 * Returns the shortnames of the Hotspot MBean names
	 * @return the shortnames of the Hotspot MBean names
	 */
	public static String[] getHotSpotMBeanShortNames() {
		return hotspotMbeanObservers.keySet().toArray(new String[hotspotMbeanObservers.size()]);
	}
	
	/**
	 * Returns the Hotspot internal MBeanObserver for the passed short name
	 * @param name The short name of the bean
	 * @return the Hotspot internal MBeanObserver or null if the name could not be matched
	 */
	public static MBeanObserver hotspotObserver(final String name) {
		if(name==null || name.trim().isEmpty()) return null;
		return hotspotMbeanObservers.get(name.trim().toLowerCase());
	}
	
	public AttributeManager<?> getAttributeManager() {
		try {
			return this.am.newInstance();
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	/**
	 * Returns the Hotspot internal MBeanObserver for the passed comma separated short names
	 * @param names The comma separated short names of the beans
	 * @return an array of matching Hotspot internal MBeanObserver
	 */
	public static MBeanObserver[] hotspotObservers(final String names) {
		final Set<MBeanObserver> beans = EnumSet.noneOf(MBeanObserver.class);
		if(names==null || names.trim().isEmpty()) return new MBeanObserver[0];
		final String[] frags = names.split(",");
		for(String name: frags) {
			MBeanObserver mbo = hotspotObserver(name);
			if(mbo!=null) {
				beans.add(mbo);
			}
		}
		return beans.toArray(new MBeanObserver[beans.size()]);
	}
	
	
	/**
	 * <p>Title: ClassLoadingAttribute</p>
	 * <p>Description: Attribute manager for the ClassLoading MXBean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.ClassLoadingAttribute</code></p>
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (mask & bitMask)==bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return attributeName; 
		}

		public static String getCounterPattern() {
			// TODO Auto-generated method stub
			return null;
		}

		public static BaseMBeanObserver getMBeanObserver(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean) {
			return new ClassLoadingMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
		}
		
	}
	
	/**
	 * <p>Title: CompilationAttribute</p>
	 * <p>Description: Attribute manager for the Compilation MXBean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.CompilationAttribute</code></p>
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (mask & bitMask)==bitMask;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return attributeName; 
		}

		public static String getCounterPattern() {
			// TODO Auto-generated method stub
			return null;
		}

		public static BaseMBeanObserver getMBeanObserver(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean) {
			return new CompilationMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
		}
		
		
		
	}

	/**
	 * <p>Title: GarbageCollectorAttribute</p>
	 * <p>Description: Attribute manager for the GarbageCollector MXBean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.GarbageCollectorAttribute</code></p>
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
		COLLECTION_COUNT("CollectionCount", long.class),
		/**  */
		COLLECTION_TIME("CollectionTime", long.class);
		
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (mask & bitMask)==bitMask;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#extractDataFrom(java.lang.Object)
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return subAttributeName.isEmpty() ? attributeName : (attributeName + "." + subAttributeName); 
		}
		
	}
	
	/**
	 * <p>Title: BufferPoolAttribute</p>
	 * <p>Description: Attribute manager for the NIO Buffer Pool MXBean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.BufferPoolAttribute</code></p>
	 */	
	public static enum BufferPoolAttribute implements AttributeManager<BufferPoolAttribute> {
		/** The number of buffers in the pool */
		BUFFER_COUNT("Count", long.class),
		/** An estimate of the memory that the Java virtual machine is using for this buffer pool */
		MEMORY_USED("MemoryUsed", long.class),
		/** An estimate of the total capacity of the buffers in this pool */
		TOTAL_CAPACITY("TotalCapacity", long.class);
		
		private BufferPoolAttribute(final String attributeName, final String subAttributeName, final Class<?> type, final Class<?> transformedType) {
			this.attributeName = attributeName;
			this.type = type;
			primitive = type.isPrimitive();
			this.subAttributeName = subAttributeName==null ? "": subAttributeName;
			this.transformedType = transformedType==null ? this.type: transformedType;
		}
		
		private BufferPoolAttribute(final String attributeName, final Class<?> type) {
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (mask & bitMask)==bitMask;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#extractDataFrom(java.lang.Object)
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getKey()
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
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.MemoryAttribute</code></p>
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (mask & bitMask)==bitMask;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#extractDataFrom(java.lang.Object)
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return subAttributeName.isEmpty() ? attributeName : (attributeName + "." + subAttributeName); 
		}

		public static String getCounterPattern() {
			// TODO Auto-generated method stub
			return null;
		}

		public static BaseMBeanObserver getMBeanObserver(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean) {
			return new MemoryCollectorMBeanObserver(mbeanServerConn, tags, publishObserverMBean);
		}

		
		
	}

	/**
	 * <p>Title: MemoryPoolAttribute</p>
	 * <p>Description: Attribute manager for the MemoryPool MXBean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.MemoryPoolAttribute</code></p>
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (mask & bitMask)==bitMask;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#extractDataFrom(java.lang.Object)
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getKey()
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
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.OperatingSystemAttribute</code></p>
	 */	
	public static enum OperatingSystemAttribute implements AttributeManager<OperatingSystemAttribute> {
		/**  */
		MAX_FILE_DESCRIPTOR_COUNT("MaxFileDescriptorCount", long.class, true),
		/**  */
		OPEN_FILE_DESCRIPTOR_COUNT("OpenFileDescriptorCount", long.class, false),
		/**  */
		COMMITTED_VIRTUAL_MEMORY_SIZE("CommittedVirtualMemorySize", long.class, false),
		/**  */
		FREE_PHYSICAL_MEMORY_SIZE("FreePhysicalMemorySize", long.class, false),
		/**  */
		FREE_SWAP_SPACE_SIZE("FreeSwapSpaceSize", long.class, false),
		/**  */
		PROCESS_CPU_LOAD("ProcessCpuLoad", double.class, false),
		/**  */
		PROCESS_CPU_TIME("ProcessCpuTime", long.class, false),
		/**  */
		SYSTEM_CPU_LOAD("SystemCpuLoad", double.class, false),
		/**  */
		TOTAL_PHYSICAL_MEMORY_SIZE("TotalPhysicalMemorySize", long.class, true),
		/**  */
		TOTAL_SWAP_SPACE_SIZE("TotalSwapSpaceSize", long.class, true),
		/**  */
		SYSTEM_LOAD_AVERAGE("SystemLoadAverage", double.class, false);
		
		private OperatingSystemAttribute(final String attributeName, final Class<?> type, final boolean oneTime) {
			this.attributeName = attributeName;
			this.type = type;
			primitive = type.isPrimitive();
			this.oneTime = oneTime;
		}
		
		/** The bitmask */
		public final int bitMask = POW2.get(ordinal());
		/** The currently loaded class count */
		public final String attributeName;
		/** The total loaded class count */
		public final Class<?> type;
		/** The unloaded class count */
		public final boolean primitive;
		/** Indicates if the attribute is a one time */
		public final boolean oneTime;
		
		/** A set of the one time attributes */
		public static final Set<OperatingSystemAttribute> ONE_TIMERS = Collections.unmodifiableSet(EnumSet.of(MAX_FILE_DESCRIPTOR_COUNT, TOTAL_PHYSICAL_MEMORY_SIZE, TOTAL_SWAP_SPACE_SIZE));
		/** A set of the non-one time attributes */
		public static final Set<OperatingSystemAttribute> NON_ONE_TIMERS = Collections.unmodifiableSet(EnumSet.complementOf(EnumSet.of(MAX_FILE_DESCRIPTOR_COUNT, TOTAL_PHYSICAL_MEMORY_SIZE, TOTAL_SWAP_SPACE_SIZE)));
		
		/** A map of OperatingSystemAttribute enum members keyed by the attribute name */
		public static final Map<String, OperatingSystemAttribute> ATTR2ENUM;
		
		static {
			final OperatingSystemAttribute[] values = values();
			final Map<String, OperatingSystemAttribute> tmp = new HashMap<String, OperatingSystemAttribute>(values.length);
			for(OperatingSystemAttribute osa: values) {
				tmp.put(osa.attributeName.toLowerCase(), osa);
			}
			ATTR2ENUM = Collections.unmodifiableMap(tmp);
		}
		
		/**
		 * Returns the OperatingSystemAttribute enum member for the passed attribute name
		 * @param attributeName The attribute name to get the OperatingSystemAttribute enum member for 
		 * @return the OperatingSystemAttribute
		 */
		public static OperatingSystemAttribute getEnum(final String attributeName) {
			if(attributeName==null || attributeName.trim().isEmpty()) throw new IllegalArgumentException("The passed attributeName was null or empty");
			OperatingSystemAttribute osa = ATTR2ENUM.get(attributeName.trim().toLowerCase());
			if(osa==null) throw new IllegalArgumentException("The passed attributeName [" + attributeName + "] was not a valid OperatingSystemAttribute attribute name");
			return osa;
		}
		
		/**
		 * Returns a set of the one timer attributes for which the attribute name is in the passed array of enabled attribute names
		 * @param enabled The enabled attribute names
		 * @return The set of enabled one timers
		 */
		public static EnumSet<OperatingSystemAttribute> getEnabledOneTimers(final String[] enabled) {
			final Set<String> attrNames = new HashSet<String>(Arrays.asList(enabled));
			EnumSet<OperatingSystemAttribute> set = EnumSet.noneOf(OperatingSystemAttribute.class);
			for(OperatingSystemAttribute osa: ONE_TIMERS) {
				if(attrNames.contains(osa.attributeName)) {
					set.add(osa);
				}
			}
			return set;
		}
		
		/**
		 * Returns a set of the non-one-timer attributes for which the attribute name is in the passed array of enabled attribute names
		 * @param enabled The enabled attribute names
		 * @return The set of enabled non-one-timers
		 */
		public static EnumSet<OperatingSystemAttribute> getEnabledNonOneTimers(final String[] enabled) {
			final Set<String> attrNames = new HashSet<String>(Arrays.asList(enabled));
			EnumSet<OperatingSystemAttribute> set = EnumSet.noneOf(OperatingSystemAttribute.class);
			for(OperatingSystemAttribute osa: NON_ONE_TIMERS) {
				if(attrNames.contains(osa.attributeName)) {
					set.add(osa);
				}
			}
			return set;
		}
		
		/**
		 * Returns an array of attribute names for the passed OperatingSystemAttribute
		 * @param attrs A collection of OperatingSystemAttributes to get the attribute names for
		 * @return an array of attribute names
		 */
		public static String[] getAttributeNameArr(final Collection<OperatingSystemAttribute> attrs) {
			if(attrs==null || attrs.isEmpty()) return new String[0];
			final String[] attrNames = new String[attrs.size()];
			int index = 0;
			for(OperatingSystemAttribute osa: attrs) {
				attrNames[index] = osa.attributeName;
				index++;
			}
			return attrNames;
		}
		
		/**
		 * Returns all the attribute names
		 * @return all the attribute names
		 */
		public static String[] getAllAttributes() {
			return getAttributeNames(OperatingSystemAttribute.class);
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (mask & bitMask)==bitMask;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getKey()
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
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.RuntimeAttribute</code></p>
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (mask & bitMask)==bitMask;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return attributeName; 
		}

		
	}
	
	public static enum HotspotInternalThreadingAttribute implements AttributeManager<HotspotInternalThreadingAttribute> {
		INTERNAL_THREADING_COUNTERS("InternalThreadingCounters", List.class);
		
		private HotspotInternalThreadingAttribute(final String attributeName, final Class<?> type) {
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (mask & bitMask)==bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return attributeName; 
		}
		
	}

	
	public static enum HotspotInternalRuntimeAttribute implements AttributeManager<HotspotInternalRuntimeAttribute> {
		INTERNAL_RUNTIME_COUNTERS("InternalRuntimeCounters", List.class);
		
		private HotspotInternalRuntimeAttribute(final String attributeName, final Class<?> type) {
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (mask & bitMask)==bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return attributeName; 
		}
		
	}
	
	public static enum HotspotInternalCompilationAttribute implements AttributeManager<HotspotInternalCompilationAttribute> {
		//COMPILER_THREAD_STATS("CompilerThreadStats", List.class)
		INTERNAL_COMPILER_COUNTERS("InternalCompilerCounters", List.class);
		
		private HotspotInternalCompilationAttribute(final String attributeName, final Class<?> type) {
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (mask & bitMask)==bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return attributeName; 
		}

	}
	
	public static enum HotspotInternalMemoryAttribute implements AttributeManager<HotspotInternalMemoryAttribute> {
		INTERNAL_MEMORY_COUNTERS("InternalMemoryCounters", List.class);
		
		private HotspotInternalMemoryAttribute(final String attributeName, final Class<?> type) {
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (mask & bitMask)==bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return attributeName; 
		}
		
	}
	
	public static enum HotspotInternalClassLoadingAttribute implements AttributeManager<HotspotInternalClassLoadingAttribute> {
		INTERNAL_CLASS_LOADING_COUNTERS("InternalClassLoadingCounters", List.class);
		
		private HotspotInternalClassLoadingAttribute(final String attributeName, final Class<?> type) {
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (mask & bitMask)==bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getKey()
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
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.MBeanObserver.ThreadingAttribute</code></p>
	 */	
	public static enum ThreadingAttribute implements AttributeManager<ThreadingAttribute> {
		/**  */
		DAEMON_THREAD_COUNT("DaemonThreadCount", int.class),
		/**  */
		PEAK_THREAD_COUNT("PeakThreadCount", int.class),
		/**  */
		THREAD_COUNT("ThreadCount", int.class),
		/**  */
		TOTAL_STARTED_THREAD_COUNT("TotalStartedThreadCount", long.class),
		/**  */
		ALL_THREAD_IDS("AllThreadIds", long[].class);
		

		
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
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getAttributeName()
		 */
		@Override
		public String getAttributeName() {
			return attributeName;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getType()
		 */
		@Override
		public Class<?> getType() {
			return type;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isPrimitive()
		 */
		@Override
		public boolean isPrimitive() {
			return primitive;
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getMask()
		 */
		@Override
		public int getMask() {
			return bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#isEnabledFor(int)
		 */
		@Override
		public boolean isEnabledFor(final int mask) {
			return (mask & bitMask)==bitMask;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#extractDataFrom(java.lang.Object)
		 */
		@Override
		public Object extractDataFrom(final Object input) {
			return input;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.AttributeProvider#getKey()
		 */
		@Override
		public String getKey() {
			return attributeName; 
		}

	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.MXBeanDescriptor#getMBeanObserver(javax.management.MBeanServerConnection, java.util.Map, boolean)
	 */
	@Override
	public BaseMBeanObserver getMBeanObserver(final MBeanServerConnection mbeanServerConn, final Map<String, String> tags, final boolean publishObserverMBean) {
		return MBeanObserverFactory.getMBeanObserver(this, mbeanServerConn, tags, publishObserverMBean);
	}

	
}

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
package com.heliosapm.opentsdb.client.classloaders;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.loading.MLet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.util.JMXHelper;
import com.heliosapm.opentsdb.client.util.URLHelper;
import com.heliosapm.opentsdb.client.util.Util;
import com.heliosapm.opentsdb.client.util.XMLHelper;

/**
 * <p>Title: ClassLoaderRepository</p>
 * <p>Description: A repository and cache for the classloaders supporting AOP instrumentation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.classloaders.ClassLoaderRepository</code></p>
 */

public class ClassLoaderRepository implements RemovalListener<Object, ClassLoader> {
	/** The singleton instance */
	private static volatile ClassLoaderRepository instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
	/** Instance logger */
	private final Logger log = LogManager.getLogger(getClass());

	/** A map of published ObjectNames keyed by the resource name */
	private final Map<String, ObjectName> mbeanRefs = new ConcurrentHashMap<String, ObjectName>();
	
	/**
	 * Acquires and returns the ClassLoaderRepository singleton instance
	 * @return the ClassLoaderRepository singleton instance
	 */
	public static ClassLoaderRepository getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new ClassLoaderRepository();
				}
			}
		}
		return instance;
	}
	
	/** The classloader cache */
	private final Cache<Object, ClassLoader> classLoaderCache = CacheBuilder.newBuilder()
			.concurrencyLevel(Constants.CORES)
			.initialCapacity(128)
			.recordStats()
			.weakValues()
			.removalListener(this)
			.build();
	
	
	/**
	 * Creates a new ClassLoaderRepository
	 */
	private ClassLoaderRepository() {}
	
	/**
	 * Loads an agent XML configuration node
	 * @param configNode The classloader config node
	 */
	public static void config(final Node configNode) {
		getInstance().loadConfig(configNode);
	}
	
	/** Empty URL array const */
	private static final URL[] EMPTY_URL_ARG = {};
	
	/**
	 * Loads an agent XML configuration node
	 * @param configNode The classloader config node
	 * <pre><resource name="[arbitrary]" type="classloader" url="[location of classes]"/></pre>
	 */
	public void loadConfig(final Node configNode) {
		if(configNode==null) return;
		for(Node resourceNode: XMLHelper.getChildNodesByName(configNode, "resource", false)) {
			final String name = XMLHelper.getAttributeByName(resourceNode, "name", "").trim();
			if(name==null || name.isEmpty()) {
				log.warn("No name defined for resource node [{}]", XMLHelper.renderNode(resourceNode));
				continue;
			}
			final String type = XMLHelper.getAttributeByName(resourceNode, "type", "").trim();
			if(type==null || type.isEmpty()) {
				log.warn("No type defined for resource node [{}]", XMLHelper.renderNode(resourceNode));
				continue;
			}
			if(!"classloader".equalsIgnoreCase(type)) {
				continue;
			}
			final String url = XMLHelper.getAttributeByName(resourceNode, "type", "").trim();
			if(url==null || url.isEmpty()) {
				log.warn("No url defined for resource node [{}]", XMLHelper.renderNode(resourceNode));
				continue;
			}
			try { 
				ClassLoader classLoader = getClassLoader(url);
				classLoaderCache.put(name, classLoader);
				log.info("Loaded Resource class loader [{}] / [{}]", name, url);
				if(XMLHelper.hasChildNodeByName(resourceNode, "objectName", false)) {
					String objName = XMLHelper.getAttributeByName(resourceNode, "objectName", null);
					if(objName==null) continue;
					try {
						final MLet mletCl = new MLet(EMPTY_URL_ARG, new WeaklyReferencedClassLoader(classLoader));						
						final ObjectName on = new ObjectName(objName.trim());
						final MBeanServer mbs = JMXHelper.getHeliosMBeanServer();
						mbs.registerMBean(mletCl, on);
						mbeanRefs.put(name, on);
					} catch (Exception ex) {
						log.warn("Failed to register class loader under ObjectName [{}]: {}", objName, ex.toString());
					}
				}
			} catch (Exception ex) {
				log.warn("Failed to create classloader for resource node [{}]", XMLHelper.renderNode(resourceNode), ex);
				continue;
			}
		}
	}
	
	/**
	 * Returns the classloader for the passed key
	 * @param key The classloader key
	 * @return the classloader
	 */
	public ClassLoader getClassLoader(final Object key) {
		if(key==null) return null;
		if("SYSTEM".equals(key)) return ClassLoader.getSystemClassLoader();
		if("SYSTEM.PARENT".equals(key)) return ClassLoader.getSystemClassLoader().getParent();
		final Object _key = WeakReferenceKey.isWeakRefRequired(key.getClass()) ? WeakReferenceKey.newInstance(key, classLoaderCache) : typeKey(key); 		
		try {
			return classLoaderCache.get(_key, new Callable<ClassLoader>(){
				@Override
				public ClassLoader call() throws Exception {
					return classLoaderFrom(_key);
				}
			});
		} catch (Exception ex) {
//			throw new RuntimeException(ex);
			return Thread.currentThread().getContextClassLoader();
		}
	}
	
	private Object typeKey(final Object key) {
		if(key instanceof CharSequence) {
			final String skey = key.toString();
			if(URLHelper.isValidURL(skey)) {
				return URLHelper.toURL(skey);
			}
			if(JMXHelper.isObjectName(skey)) {
				return JMXHelper.objectName(skey);
			}
			return skey;			
		}
		
		return key;
	}
	
	/**
	 * <p>Title: WeakReferenceKey</p>
	 * <p>Description: A cache key wrapper. We don't want to use {@link CacheBuilder#weakKeys()} on the cache because many of the keys
	 * are arbitrary non-significant values which could be enqueued at any time. On the other hand, if the key is a {@link Class}, 
	 * we don't want to keep a hard reference because this will prevent the class (and associated classloader) from being enqueued which
	 * is quite important when using dynamic aop because classes will come and go.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.aop.ClassLoaderRepository.WeakReferenceKey</code></p>
	 */
	private static class WeakReferenceKey<T> extends WeakReference<T> {
		/** The hash code of the referent */
		final int hashCode;
		/** A reference to the cache so we can remove this key from the cache if enqueued */
		final Cache<Object, ClassLoader> cache;
		
		public static boolean isWeakRefRequired(final Class<?> clazz) {
			if(clazz.equals(Class.class)) return true;
			if(ClassLoader.class.isAssignableFrom(clazz)) return true;
			// any others ?
			return false;
		}
		
		/**
		 * Creates a new WeakReferenceKey
		 * @param referent The referent we want to avoid prevention of GC on
		 * @param cache A reference to the cache so we can remove this key from the cache if enqueued
		 * @return the new WeakReferenceKey
		 */
		public static <T> WeakReferenceKey<T> newInstance(T referent, final Cache<Object, ClassLoader> cache) {
			return new WeakReferenceKey<T>(referent, cache);
		}
		
		private WeakReferenceKey(final T referent, final Cache<Object, ClassLoader> cache) {
			super(referent);
			hashCode = referent.hashCode();
			this.cache = cache;
		}
		
		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(final Object that) {
			final T t = get();
			if(this==that) {
				return true;
			}
			if(t==null) {
				cache.invalidate(this);
				return false;
			}
			return t.equals(that);
		}
		
		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			return hashCode;
		}
		
		
		
	}
	
	
	/**
	 * Attempts to derive a classloader from the passed object.
	 * @param obj The object to derive a classloader from
	 * @return a classloader
	 */
	protected static ClassLoader classLoaderFrom(Object obj) {
		if(obj==null) {
			return ClassLoader.getSystemClassLoader();
		} else if(obj instanceof ClassLoader) {
			return (ClassLoader)obj;
		} else if(obj instanceof Class) {
			return ((Class<?>)obj).getClassLoader();
		} else if(obj instanceof URL) {
			return new URLClassLoader(new URL[]{(URL)obj}); 
		} else if(URLHelper.isValidURL(obj.toString())) {
			URL url = URLHelper.toURL(obj.toString());
			return new URLClassLoader(new URL[]{url});
		} else if(obj instanceof ObjectName) {
			return getClassLoader((ObjectName)obj);
		} else if(Util.isObjectName(obj.toString())) {
			return getClassLoader(Util.objectName(obj.toString()));
		} else if(obj instanceof File) {
			File f = (File)obj;
			return new URLClassLoader(new URL[]{URLHelper.toURL(f)});
		} else if(new File(obj.toString()).canRead()) {
			File f = new File(obj.toString());
			return new URLClassLoader(new URL[]{URLHelper.toURL(f)});			
		} else {
			return obj.getClass().getClassLoader();
		}
		
	}
	
	/**
	 * Returns the classloader represented by the passed ObjectName
	 * @param on The ObjectName to resolve the classloader from
	 * @return a classloader
	 */
	protected static ClassLoader getClassLoader(ObjectName on) {
		MBeanServer server = JMXHelper.getMBeanServerFor(on);
		if(server==null) return null; //throw new RuntimeException("The passed ObjectName [" + on + "] was not found in any MBeanServer");
		try {
			return server.getClassLoaderFor(on);
		} catch (Exception ex) {
			//throw new RuntimeException("Failed to get classloader for object name [" + on + "]", ex);
			return null;
		}
	}
	


	/**
	 * {@inheritDoc}
	 * @see com.google.common.cache.RemovalListener#onRemoval(com.google.common.cache.RemovalNotification)
	 */
	@Override
	public void onRemoval(final RemovalNotification<Object, ClassLoader> notification) {		
		final Object key = notification.getKey();
		log.info("ClassLoader [{}] was GC'ed", key);
		if(key instanceof String) {
			ObjectName on = mbeanRefs.remove((String)key);
			if(on!=null) {
				try {
					JMXHelper.unregisterMBean(on);					
				} catch (Exception x) {/* No Op */}
			}
		}
		
	}

}

/**
 * Helios, OpenSource Monitoring
 * Brought to you by the Helios Development Group
 *
 * Copyright 2015, Helios Development Group and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org. 
 *
 */
package com.heliosapm.opentsdb.client.aop;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.Callable;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.util.JMXHelper;
import com.heliosapm.opentsdb.client.util.URLHelper;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: ClassLoaderRepository</p>
 * <p>Description: A repository and cache for the classloaders supporting AOP instrumentation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.ClassLoaderRepository</code></p>
 */

public class ClassLoaderRepository implements RemovalListener<Object, ClassLoader> {
	/** The singleton instance */
	private static volatile ClassLoaderRepository instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	
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
	 * Returns the classloader for the passed key
	 * @param key The classloader key
	 * @return the classloader
	 */
	public ClassLoader getClassLoader(final Object key) {
		if(key==null) return null;
		final Object _key = WeakReferenceKey.isWeakRefRequired(key.getClass()) ? WeakReferenceKey.newInstance(key, classLoaderCache) : key; 		
		try {
			return classLoaderCache.get(_key, new Callable<ClassLoader>(){
				@Override
				public ClassLoader call() throws Exception {
					return classLoaderFrom(_key);
				}
			});
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
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
		// TODO Auto-generated method stub
		
	}

}

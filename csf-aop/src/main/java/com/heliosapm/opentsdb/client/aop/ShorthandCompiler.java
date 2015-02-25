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

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Member;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javassist.CtMethod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.heliosapm.opentsdb.client.aop.naming.MetricNameCompiler;
import com.heliosapm.opentsdb.client.aop.naming.MetricNameProvider;

/**
 * <p>Title: ShorthandCompiler</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.ShorthandCompiler</code></p>
 */

public class ShorthandCompiler {
	/** The singleton instance */
	private static volatile ShorthandCompiler instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();
	/** Instance logger */
	protected final Logger log = LogManager.getLogger(getClass());
	/** The instrumentation instance to instrument with */
	protected final Instrumentation instrumentation;
	
	/**
	 * Acquires and returns the ShorthandCompiler singleton instance
	 * @return the ShorthandCompiler singleton instance
	 */
	public static ShorthandCompiler getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new ShorthandCompiler();
				}
			}
		}
		return instance;
	}

	/**
	 * Creates a new ShorthandCompiler
	 */
	private ShorthandCompiler() {
		instrumentation = TransformerManager.getInstrumentation();
	}
	
	/**
	 * Acquires a metric name provider for the passed fefines Shorthand joinpoint
	 * @param clazz The class the joinpoint is in
	 * @param member The class member the joinpoint is on
	 * @param nameTemplate The metric naming template
	 * @return The metric name provider
	 */
	public MetricNameProvider getNameProvider(final Class<?> clazz, final Member member, final String nameTemplate) {
		return MetricNameCompiler.getMetricNameProvider(clazz, member, nameTemplate);
	}
	
	/**
	 * Compiles the passed scripts
	 * @param scriptsToCompile A map of sets of parsed shorthand scripts keyed by the class the scripts are instrumenting.
	 */
	// Return a classfiletransformer
	public void compile(final Map<Class<?>, Set<ShorthandScript>> scriptsToCompile) {
		if(scriptsToCompile==null || scriptsToCompile.isEmpty()) return;
		final Map<String, Integer> classesToTransform = new HashMap<String, Integer>(scriptsToCompile.size()); 
		for(Map.Entry<Class<?>, Set<ShorthandScript>> entry: scriptsToCompile.entrySet()) {
			final Class<?> clazz = entry.getClass();
			final Set<ShorthandScript> scripts = entry.getValue();
			if(scripts.isEmpty()) continue;
			if(!instrumentation.isModifiableClass(clazz)) {
				log.warn("The class [{}] with [{}] shorthand scripts is not modifiable. Skipping.", clazz.getName(), scripts.size());
			}
			
			
			
			classesToTransform.put(b2i(clazz), System.identityHashCode(clazz.getClassLoader()));
		}
	}
	
	
	protected void instrument(final CtMethod ctMethod, final long metricId, final int measurementMask) {
		if(ctMethod==null) throw new IllegalArgumentException("The passed CtMethod was null");
		
		/*
		 * DefaultShorthandInterceptor
		 * body exec capture
		 * catch block capture
		 * finally block capture
		 */
	}
	
	/**
	 * Converts the internal format class name to the binary name format
	 * @param internalName The internal name
	 * @return the binary name
	 */
	public static String i2b(final String internalName) {
		return internalName.replace('/', '.');
	}
	
	/**
	 * Converts the binary format class name to the internal name format
	 * @param binaryName The binary name
	 * @return the internal name
	 */
	public static String b2i(final String binaryName) {
		return binaryName.replace('.', '/');
	}
	
	/**
	 * Returns the internal format name for the passed class
	 * @param clazz The class
	 * @return the internal name of the passed class
	 */
	public static String b2i(final Class<?> clazz) {
		return clazz.getName().replace('.', '/');
	}

}

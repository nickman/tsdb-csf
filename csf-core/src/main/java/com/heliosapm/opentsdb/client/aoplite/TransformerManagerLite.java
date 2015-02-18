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

package com.heliosapm.opentsdb.client.aoplite;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>Title: TransformerManager</p>
 * <p>Description: Special hack for when we want to shift around registered transformers.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.TransformerManager</code></p>
 */

public class TransformerManagerLite {
	/** Static class logger */
	private static final Logger LOG = LogManager.getLogger(TransformerManagerLite.class);
	/** The known providers */
	private static final Class<?> transformManagerClass;
	private static final Class<?> instrClass;
	private static final Field transformManagerField;
	private static final Field transformerList;
	
	/** These are the class names of transformers known to need relocating */
	private static final Set<String> KNOWN_TRANSFORMERS = new CopyOnWriteArraySet<String>(Arrays.asList("org.jboss.aop.standalone.AOPTransformer"));
	
	
	
	static {
		Class<?> tmClass = null;
		Class<?> iClass = null;
		Field tmField = null;
		Field tListField = null;
		try {
			tmClass = Class.forName("sun.instrument.TransformerManager");
			iClass = Class.forName("sun.instrument.InstrumentationImpl");
			tmField = iClass.getDeclaredField("mTransformerManager");
			tListField = tmClass.getDeclaredField("mTransformerList");
			tmField.setAccessible(true);
		} catch (Exception ex) {
			tmClass = null; iClass = null; tmField = null; tListField = null;
		}
		transformManagerClass = tmClass;
		instrClass = iClass;
		transformManagerField = tmField;
		transformerList = tListField;
	}

	/**
	 * Creates a new TransformerManager
	 */
	public TransformerManagerLite() {
	}

	
	/**
	 * Rarely needed hack to unregister class transformers and reregister with retransform enabled 
	 * @param instrumentation The optional instrumentation instance to use. 
	 */
	public static void switchTransformers(Instrumentation instrumentation) {
		if(instrClass==null) {
			LOG.warn("switchTransformers disabled. Failed to load instrumentation");
			return;
		}		
		if(instrumentation==null) {
			LOG.warn("No instrumentation instance available. Cannot switch transformers");
			return;				
		}
		try {
			final Object transformerManager = transformManagerField.get(instrumentation);
			final Object transformersArray = transformerList.get(transformerManager);
			final int tCount = Array.getLength(transformersArray);
			if(tCount==0) {
				LOG.info("No transformer found");
				return;
			}
			for(int i = 0; i < tCount; i++) {
				Object transformer = Array.get(transformersArray, i);
				if(transformer==null || !(transformer instanceof ClassFileTransformer)) continue;
				if(!KNOWN_TRANSFORMERS.contains(transformer.getClass().getName())) {
					LOG.info("Transformer {} not marked for switch", transformer.getClass().getName());
				} else {
					LOG.info("Switching Transformer {}...", transformer.getClass().getName());
					ClassFileTransformer cft = (ClassFileTransformer)transformer;
					instrumentation.removeTransformer(cft);
					try {
						instrumentation.addTransformer(cft, true);
						LOG.info("Transformer {} Switched", transformer.getClass().getName());
					} catch (Exception ex) {
						LOG.error("Failed to re-register transformer {}. Adding back to original slot.", transformer.getClass().getName());
						try {
							instrumentation.addTransformer(cft, false);
							LOG.warn("Transformer {} added back to original slot", transformer.getClass().getName());
						} catch (Exception ex2) {
							LOG.fatal("\n\t==========================\n\tFATAL ERROR.\n\tJVM MAY BE UNSTABLE\n\tFailed to re-register transformer {} back to original slot.\n\t==========================\n", transformer.getClass().getName(), ex2);
						}
					}
				}
			}
		} catch (Exception ex) {
			LOG.error("switchTransformers failed", ex);
		}
	}
	
	/**
	 * Adds a target transformer class name for switching with {@link #switchTransformers(Instrumentation)}
	 * @param className The name of the {@link ClassFileTransformer} class
	 */
	public static void addTargetTransformer(final String className) {
		KNOWN_TRANSFORMERS.add(className);
	}
	
	/**
	 * Returns an array of known {@link ClassFileTransformer} class names that {@link #switchTransformers(Instrumentation)} will attempt to switch.
	 * @return an array of {@link ClassFileTransformer} class names
	 */
	public String[] getKnownTransformers() {
		return KNOWN_TRANSFORMERS.toArray(new String[KNOWN_TRANSFORMERS.size()]);
	}
	
}

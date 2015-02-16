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

package com.heliosapm.opentsdb.client.aop;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;

import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;

/**
 * <p>Title: StaticFieldInstrumentationProvider</p>
 * <p>Description: Acquires an {@link Instrumentation} instance from a static field</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.StaticFieldInstrumentationProvider</code></p>
 * FIXME: A customized class loader might be a good idea here.
 */

public class StaticFieldInstrumentationProvider implements InstrumentationProvider {
	/** A shareable instance */
	public static final StaticFieldInstrumentationProvider INSTANCE = new StaticFieldInstrumentationProvider();

	/**
	 * Creates a new StaticFieldInstrumentationProvider
	 */
	StaticFieldInstrumentationProvider() {
		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.InstrumentationProvider#getInstrumentation()
	 */
	@Override
	public Instrumentation getInstrumentation() {
		String className = ConfigurationReader.conf(Constants.PROP_INSTR_PROV_CLASS, null);
		String fieldName = ConfigurationReader.conf(Constants.PROP_INSTR_PROV_FIELD, null);
		if(className==null) throw new RuntimeException("The configured class name was null");
		if(fieldName==null) throw new RuntimeException("The configured field name was null");
		Object instr = getFromClass(className, fieldName);
		if(instr==null) throw new RuntimeException("Instrumentation from [" + className + "." + fieldName + "] was null");
		if(!(instr instanceof Instrumentation)) throw new RuntimeException("Value in field [" + className + "." + fieldName + "] was not an Instrumentation. It was a [" + instr.getClass().getName() + "]");
		return (Instrumentation)instr;
	}
	
	/**
	 * Attempts to retrieve the value of the named static field in the named class.
	 * @param className The class name
	 * @param fieldName The field name
	 * @return the read object
	 */
	protected Object getFromClass(final String className, final String fieldName) {
		try {
			Class<?> clazz = Class.forName(className);
			Field f = clazz.getDeclaredField(fieldName);
			f.setAccessible(true);
			return f.get(null);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to get Instrumentation from [" + className + "." + fieldName + "]");
		}		
	}

}

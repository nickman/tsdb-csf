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
import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader;
import com.heliosapm.opentsdb.client.opentsdb.Constants;

/**
 * <p>Title: JMXAttributeInstrumentationProvider</p>
 * <p>Description: Acquires an {@link Instrumentation} instance from a JMX attribute</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.JMXAttributeInstrumentationProvider</code></p>
 */

public class JMXAttributeInstrumentationProvider implements InstrumentationProvider {
	/** A shareable instance */
	public static final JMXAttributeInstrumentationProvider INSTANCE = new JMXAttributeInstrumentationProvider();

	/** The default domain of the target MBeanServer */
	protected final String jmxDomain;
	/** The object name of the target MBean */
	protected final String objectName;
	/** The name of the attribute providing the Instrumentation */
	protected final String attributeName;
	
	
	/**
	 * Creates a new JMXAttributeInstrumentationProvider
	 * @param jmxDomain The default domain of the target MBeanServer
	 * @param objectName The object name of the target MBean
	 * @param attributeName The name of the attribute providing the Instrumentation
	 */
	public JMXAttributeInstrumentationProvider(final String jmxDomain, final String objectName, final String attributeName) {
		this.jmxDomain = jmxDomain;
		this.objectName = objectName;
		this.attributeName = attributeName;
	}
	
	/**
	 * Creates a new JMXAttributeInstrumentationProvider
	 */
	private JMXAttributeInstrumentationProvider() {
		jmxDomain = null;
		objectName = null;
		attributeName = null;		
	}
	
	/**
	 * Returns the provided value if not null or empty, otherwise returns the conf value of the passed property name
	 * @param providedValue The provided value
	 * @param propName The conf prop name for the override
	 * @return the configured value or null if the provider and override were null or empty
	 */
	protected String get(final String providedValue, final String propName) {
		if(providedValue==null || providedValue.trim().isEmpty()) {
			final String override = ConfigurationReader.conf(propName, null);
			return (override==null || override.trim().isEmpty()) ? null : override.trim();
		}
		return providedValue;
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.InstrumentationProvider#getInstrumentation()
	 */
	@Override
	public Instrumentation getInstrumentation() {
		String _jmxDomain = get(jmxDomain, Constants.PROP_INSTR_PROV_DOMAIN);
		String _objectName = get(objectName, Constants.PROP_INSTR_PROV_ON);
		String _attributeName = get(attributeName, Constants.PROP_INSTR_PROV_ATTR);
		if(_objectName==null) throw new RuntimeException("No ObjectName Configured");
		if(_attributeName==null) throw new RuntimeException("No AttributeName Configured");
		Object instr = null;
		try {			
			final ObjectName on = new ObjectName(_objectName);
			MBeanServer server = null;
			if(_jmxDomain==null || "DefaultDomain".equals(_jmxDomain)) {
				server = ManagementFactory.getPlatformMBeanServer();
			} else {
				for(MBeanServer mbs: MBeanServerFactory.findMBeanServer(null)) {
					final String d = mbs.getDefaultDomain();
					if(_jmxDomain.equals(d)) {
						server = mbs;
						break;
					}
				}
				throw new RuntimeException("Failed to find MBeanServer for jmxdomain [" + _jmxDomain + "]");
			}
			instr = server.getAttribute(on, _attributeName);
		} catch (Exception ex) {
			throw new RuntimeException("Failed to acquire instrumentation from [" + jmxDomain + "/" + objectName + "/" + attributeName + "]", ex);
		}
		if(instr==null) throw new RuntimeException("Instrumentation from [" + jmxDomain + "/" + objectName + "/" + attributeName + "] was null");
		if(!(instr instanceof Instrumentation)) throw new RuntimeException("Value from [" + jmxDomain + "/" + objectName + "/" + attributeName + "] was not an Instrumentation. It was a [" + instr.getClass().getName() + "]");
		return (Instrumentation)instr;
	}

}

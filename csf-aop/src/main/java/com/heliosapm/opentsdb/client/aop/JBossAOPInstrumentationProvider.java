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

/**
 * <p>Title: JBossAOPInstrumentationProvider</p>
 * <p>Description: {@link InstrumentationProvider} that uses a static field in JBossAOP to get the instrumentation instance</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.JBossAOPInstrumentationProvider</code></p>
 */

public class JBossAOPInstrumentationProvider extends StaticFieldInstrumentationProvider {
	/** A shareable instance */
	public static final JBossAOPInstrumentationProvider INSTANCE = new JBossAOPInstrumentationProvider();

	/** The class name containing the static field */
	public static final String className = "org.jboss.aop.standalone.PluggableInstrumentor";
	/** The static field containing the instrumentation */
	public static final String fieldName = "instrumentor";
	
	/**
	 * Creates a new JBossAOPInstrumentationProvider
	 */
	private JBossAOPInstrumentationProvider() {
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.InstrumentationProvider#getInstrumentation()
	 */
	@Override
	public Instrumentation getInstrumentation() {
		
		Object instr = getFromClass(className, fieldName);
		if(instr==null) throw new RuntimeException("Instrumentation from [" + className + "." + fieldName + "] was null");
		if(!(instr instanceof Instrumentation)) throw new RuntimeException("Value in field [" + className + "." + fieldName + "] was not an Instrumentation. It was a [" + instr.getClass().getName() + "]");
		return (Instrumentation)instr;
	}

}

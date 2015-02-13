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

import com.heliosapm.attachme.agent.LocalAgentInstaller;

/**
 * <p>Title: DefaultInstrumentationProvider</p>
 * <p>Description: Acquires an {@link Instrumentation} instance using the Attach API.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.DefaultInstrumentationProvider</code></p>
 */

public class DefaultInstrumentationProvider implements InstrumentationProvider {
	/** A shareable instance */
	public static final DefaultInstrumentationProvider INSTANCE = new DefaultInstrumentationProvider();
	
	/**
	 * Creates a new DefaultInstrumentationProvider
	 */
	private DefaultInstrumentationProvider() {

	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.InstrumentationProvider#getInstrumentation()
	 */
	@Override
	public Instrumentation getInstrumentation() {		
		return LocalAgentInstaller.getInstrumentation();
	}

}

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

/**
 * <p>Title: ShorthandToken</p>
 * <p>Description: Enumerates the shorthand tokens in a shorthand expression</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.ShorthandToken</code></p>
 */

public enum ShorthandToken {
	/** The index of the target class annotation indicator */
	IND_TARGETCLASS_ANNOT,				
	/** The index of the target class */
	IND_TARGETCLASS(true),				// MANDATORY
	/** The index of the inherritance indicator */
	IND_INHERRIT,
	/** The index of the target class classloader expression */
	IND_TARGETCLASS_CL,	
	/** The index of the method attributes */
	IND_ATTRS,
	/** The index of the target method annotation indicator */
	IND_METHOD_ANNOT,	
	/** The index of the target method name or expression */
	IND_METHOD(true),						// MANDATORY
	/** The index of the target method signature */
	IND_SIGNATURE,
	/** The index of the target method attributes */
	IND_METHOD_ATTRS,	
	/** The index of the method level annotation classloader expression */
	IND_METHOD_ANNOT_CL,	
	/** The index of the instrumentation options */
	IND_INSTOPTIONS,
	/** The index of the collector name */
	IND_COLLECTORNAME(true),				// MANDATORY
	/** The index of the instrumentation bit mask */
	IND_BITMASK,					
	/** The index of the collector class classloader expression */
	IND_COLLECTOR_CL,	
	/** The index of the instrumentation generated metric name */
	IND_METRICNAME;	
	
	
	private ShorthandToken() {
		mandatory = false;
	}
	
	private ShorthandToken(final boolean mandatory) {
		this.mandatory = mandatory;
	}
	
	/** Indicates if this token is mandatory */
	public final boolean mandatory;

}

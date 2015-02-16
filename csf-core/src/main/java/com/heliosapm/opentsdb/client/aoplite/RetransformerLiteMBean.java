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

/**
 * <p>Title: RetransformerLiteMBean</p>
 * <p>Description: JMX MBean interface for {@link RetransformerLite} instances</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aoplite.RetransformerLiteMBean</code></p>
 */

public interface RetransformerLiteMBean {

	/**
	 * Returns an array of the instrumented class names
	 * @return an array of the instrumented class names
	 */
	public String[] getInstrumentedClassNames();
	
	/**
	 * Returns the number of instrumented classes
	 * @return the number of instrumented classes
	 */
	public int getInstrumentedClassCount();
	
	/**
	 * Instruments the classes meetig the passed criteria
	 * @param classLoaderName An optional class loader name
	 * @param className The name of the class to instrument
	 * @param methodExpr A regular expression to match against the method names of the methods in the target class
	 */
	public void instrument(final String classLoaderName, final String className, final String methodExpr);
	
	/**
	 * Attempts to uninstrument an instrumented class
	 * @param className The name of the instrumented class to uninstrument
	 */
	public void uninstrumentClass(final String className);

}

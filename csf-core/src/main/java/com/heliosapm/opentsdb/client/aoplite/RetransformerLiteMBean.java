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

import java.util.Map;
import java.util.Set;

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
	 * Instruments the classes meeting the passed criteria
	 * @param classLoaderName An optional class loader name
	 * @param className The name of the class to instrument
	 * @param methodExpr A regular expression to match against the method names of the methods in the target class
	 * @return the number of methods instrumented at the end of this procedure
	 */
	public int instrument(final String classLoaderName, final String className, final String methodExpr);
	
	/**
	 * Instruments the classes meeting the passed criteria using the system classloader or a straight class array search (slower...)
	 * @param className The name of the class to instrument
	 * @param methodExpr A regular expression to match against the method names of the methods in the target class
	 * @return the number of methods instrumented at the end of this procedure
	 */
	public int instrument(final String className, final String methodExpr);
	
	/**
	 * Attempts to uninstrument an instrumented class
	 * @param className The name of the instrumented class to uninstrument
	 */
	public void restoreClass(final String className);
	
	/**
	 * @param pattern
	 * @param segments
	 * @param scanLimit
	 * @param matchLimit
	 * @return
	 */
	public Set<String> searchClasses(final String pattern, final int segments, final int scanLimit, final int matchLimit);
	
	/**
	 * @param classLoader
	 * @param pattern
	 * @param segments
	 * @param scanLimit
	 * @param matchLimit
	 * @return
	 */
	public Set<String> searchClasses(final String classLoader, final String pattern, final int segments, final int scanLimit, final int matchLimit);
	
	/**
	 * Returns sets of classloader object names keyed by the default domain of the MBeanServer they were found in
	 * @return a map of sets of classloader object names
	 */
	public Map<String, Set<String>> listClassLoaders();
}

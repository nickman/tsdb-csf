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
package com.heliosapm.opentsdb.client.aop.naming;

import java.lang.reflect.Member;

/**
 * <p>Title: ValueExtractor</p>
 * <p>Description: Defines a static value extractor for a given MetricNamingToken for a passed class and method</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.naming.ValueExtractor</code></p>
 */
public interface ValueExtractor {
	/**
	 * Returns a static metric name part for the passed class and method
	 * @param expression The token expression
	 * @param clazz The target class
	 * @param member The target method or constructor
	 * @param qualifiers Additional reference qualifiers such as indexes
	 * @return the static metric name part or the "%s" runtime replacement token and the Java expression that will replace it.
	 */
	public String[] getStringReplacement(CharSequence expression, Class<?> clazz, Member member, Object...qualifiers);
}
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
package com.heliosapm.opentsdb.client.jvmjmx.customx;

import java.util.Map;
import org.w3c.dom.Node;

/**
 * <p>Title: QueryDecodeInvoker</p>
 * <p>Description: Defines a QueryDecode invoker that can evaluate an XML node and produce a value from it.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.QueryDecodeInvoker</code></p>
 * @param <T> The return type of the invocation
 */

public interface QueryDecodeInvoker<T> {
	/**
	 * Invokes an XML specified query
	 * @param xmlArgs The child nodes passed as arguments
	 * @return The resulting expression
	 */
	public T invoke(Node...xmlArgs);
	
	/**
	 * Evaluates a terminal expression
	 * @param attributes A possibly empty map of the node's attributes
	 * @return the evaluated value expression
	 */
	public T eval(Map<String, String> attributes);
}

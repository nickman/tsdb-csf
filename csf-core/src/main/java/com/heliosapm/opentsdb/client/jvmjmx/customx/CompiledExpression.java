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

/**
 * <p>Title: CompiledExpression</p>
 * <p>Description: Defines a class that represents a compiled <b><code>trace</code></b> tag
 * in the custom jmx collection. Instances are passed the current collection context
 * and should invoke the context trace callback.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.CompiledExpression</code></p>
 */

public interface CompiledExpression {
	/**
	 * Traces zero, one or more metric measurements on the passed context
	 * @param ctx The in-focus collection context
	 */
	public void trace(CollectionContext ctx);
}

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

/**
 * <p>Title: CollectionScript</p>
 * <p>Description: Defines a loosely typed (usually a script) that accepts a colloection definition and extracts specific JMX collected data, 
 * returning it in a standard format for tracing.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionScript</code></p>
 */

public interface CollectionScript {
	/**
	 * Accepts a value map and a collection definition. The map is populated and returned.
	 * @param valueMap The map to populate with the collected values. The key is an ObjectName like string.
	 * @param collectionDef The collection context
	 * @param args Arbitrary arguments for the script
	 * @return the populated value map to trace
	 */
	public Map<String, Number> collect(final Map<String, Number> valueMap, final CollectionDefinition collectionDef, final Object...args);
}

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

import javax.management.ObjectName;

/**
 * <p>Title: ExpressionSet</p>
 * <p>Description: The compiled expression set that queried data is passed to in order to gather traces</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.ExpressionSet</code></p>
 */

public interface ExpressionSet {
	/**
	 * Accepts the current focus of the collection context and generates a map of values to trace Objectname styped strings
	 * @param ctx
	 * @param objectName
	 * @param attributeName
	 * @param attributeValue
	 * @return
	 */
	public Number collect(
			final CollectionContext ctx, 
			final ObjectName objectName, 
			final String attributeName, 
			final Object attributeValue			
			);
}

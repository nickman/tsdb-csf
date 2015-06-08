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
 * <p>Title: TokenResolver</p>
 * <p>Description: Defines a class that accepts a parsed token expression and a monitor context and returns the resolved value</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolver</code></p>
 */

public interface TokenResolver {
	/**
	 * Resolves the passed token instance values
	 * @param dctx The expression data context
	 * @param args The expression tokener's arguments expression
	 * @return the resolved value
	 */
	public CharSequence resolve(ExpressionDataContext dctx, CharSequence args);
}

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
package com.heliosapm.opentsdb.client.jvmjmx.custom;

import com.heliosapm.opentsdb.client.jvmjmx.custom.Tokener.IntRange;

/**
 * <p>Title: TokenResolver</p>
 * <p>Description: Defines a class that accepts a parsed token expression and a monitor context and returns the resolved value</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolver</code></p>
 */

public interface TokenResolver {
	/**
	 * Resolves the passed token instance values
	 * @param key The parsed token key
	 * @param qualifier The parsed token qualifier
	 * @param ranges The parsed token int ranges
	 * @return the resolved value
	 */
	public CharSequence resolve(String key, String qualifier, IntRange... ranges);
}

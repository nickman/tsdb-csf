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

import java.util.regex.Pattern;

/**
 * <p>Title: TokenResolvers</p>
 * <p>Description: A collection of token resolver classes</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers</code></p>
 */

public abstract class TokenResolvers {
	
	/** The token expression matcher */
	public static final Pattern TOKEN_EXPR_PATTERN = Pattern.compile("\\$(.*?)\\{(.*)\\}");

	public class AttributeTokenResolver implements TokenResolver {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolver#resolve(com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionDefinition, java.lang.CharSequence)
		 */
		@Override
		public CharSequence resolve(CollectionDefinition dctx, CharSequence args) {
			
			return null;
		}
	}

}

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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.heliosapm.utils.lang.StringHelper;

/**
 * <p>Title: TokenResolvers</p>
 * <p>Description: A collection of token resolver classes</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers</code></p>
 */

public abstract class TokenResolvers {
	
	/** The token expression matcher */
	public static final Pattern TOKEN_EXPR_PATTERN = Pattern.compile("\\$(.*?)\\{(.*?)\\}");
	
	public static String resolve(final String expression, final CollectionContext ctx) {
		final Matcher m = TOKEN_EXPR_PATTERN.matcher(expression);
		final StringBuffer b = new StringBuffer();
		while(m.find()) {
			final String tokenerName = m.group(1);
			final Tokener t = Tokener.forName(tokenerName);
			final TokenResolver tr = t.resolver;
			final String tokenerArgs = m.group(2);
			final String resolved = tr.resolve(ctx, tokenerArgs);
			m.appendReplacement(b, resolved);
		}
		if(b.length()==0) throw new RuntimeException("Failed to resolved expression [" + expression + "]");
		m.appendTail(b);
		return b.toString();
	}
	
	protected static abstract class AbstractTokenResolver implements TokenResolver {
		/** The arguments expression parser */
		public static final Pattern ARGS_PARSER = Pattern.compile("\\[(" + StringHelper.intRange.pattern() + ")\\]");
		/** The name delimeter for compound attribute names */
		public static final Pattern ATTR_NAME_DELIM = Pattern.compile("/");
		
	}

	public static class AttributeNameTokenResolver implements TokenResolver {
		/** The arguments expression parser */
		public static final Pattern ARGS_PARSER = Pattern.compile("\\[(" + StringHelper.intRange.pattern() + ")\\]");
		/** The name delimeter for compound attribute names */
		public static final Pattern ATTR_NAME_DELIM = Pattern.compile("/");
		
		public String resolveRange(final String base, final String args, final String delim, final String rangeDef) {
			if(args!=null && !args.trim().isEmpty()) {
				final Pattern p = Pattern.compile(delim);
				final String[] fragments = p.split(base); 
				final StringBuffer b = new StringBuffer();				
				final Matcher m = ARGS_PARSER.matcher(args);
				while(m.find()) {
					final StringBuilder buff = new StringBuilder();
					final String rangeStr = m.group(1);
					final int[] range = StringHelper.compileRange(rangeStr);
					for(int i = 0; i < range.length; i++) {
						buff.append(fragments[range[i]]);
					}
					m.appendReplacement(b, buff.toString());
				}
				m.appendTail(b);
				return b.toString();
			}			
			return base;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolver#resolve(com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionDefinition, java.lang.String)
		 */
		@Override
		public String resolve(final CollectionContext ctx, final String args) {
			final String attrName = ctx.name();
			if(args!=null && !args.trim().isEmpty()) {
				final String[] fragments = ATTR_NAME_DELIM.split(attrName); 
				final StringBuffer b = new StringBuffer();				
				final Matcher m = ARGS_PARSER.matcher(args);
				while(m.find()) {
					final StringBuilder buff = new StringBuilder();
					final String rangeStr = m.group(1);
					final int[] range = StringHelper.compileRange(rangeStr);
					for(int i = 0; i < range.length; i++) {
						buff.append(fragments[range[i]]);
					}
					m.appendReplacement(b, buff.toString());
				}
				m.appendTail(b);
				return b.toString();
			}			
			return attrName;
		}
	}

}

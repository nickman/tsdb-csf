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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.heliosapm.opentsdb.client.scripts.ScriptManager;
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

		/**
		 * Resolves a range of substring indexes and returns the concatenated result
		 * @param base The base string to parse
		 * @param args The expression containing range sub-expressions
		 * @param delim The base delimeter
		 * @return the resolved def
		 */
		public String resolveRange(final String base, final String args, final String delim) {
			if(args!=null && !args.trim().isEmpty()) {
				final Pattern p = "/".equals("/") ? ATTR_NAME_DELIM : Pattern.compile(delim);
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
		
		public String processTransformArgs(final String base, final Map<String, String> args) {
			String _base = base.trim();
			for(Map.Entry<String, String> entry: args.entrySet()) {
				final String directive = entry.getKey();
				final String directiveArgs = entry.getValue();
				if("script".equalsIgnoreCase(directive)) {
					final Object[] scriptArgs = directiveArgs.split(",");
					final String scriptName = (String)scriptArgs[0];
					for(int i = 1; i < scriptArgs.length; i++) {
						scriptArgs[i] = ((String)scriptArgs[i]).trim();
					}
					_base = ScriptManager.getInstance().eval(scriptName, scriptArgs).toString();
				}				
			}
			return _base;
		}		
	}

	public static class AttributeNameTokenResolver extends AbstractTokenResolver {
		/** The arguments expression parser */
		public static final Pattern ARGS_PARSER = Pattern.compile("\\[(" + StringHelper.intRange.pattern() + ")\\]");
		/** The name delimeter for compound attribute names */
		public static final Pattern ATTR_NAME_DELIM = Pattern.compile("/");
		
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolver#resolve(com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext, java.lang.String)
		 */
		@Override
		public String resolve(final CollectionContext ctx, final String args) {
			return resolveRange(ctx.name(), args, "/");
		}
	}
	
	public static class AttributeValueTokenResolver extends AbstractTokenResolver {
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolver#resolve(com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext, java.lang.String)
		 */
		@Override
		public String resolve(final CollectionContext ctx, final String args) {
			if(args!=null && !args.trim().isEmpty()) {
				//script:<script name>
				final Map<String, String> argMap = StringHelper.splitKeyValues(args, ",", ":");
			}
			final Object v = ctx.value();
			return v!=null ? v.toString() : "";
		}
	}
	
}

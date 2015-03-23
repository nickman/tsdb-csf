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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Title: Tokener</p>
 * <p>Description: Parses and resolves expression tokens</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.Tokener</code></p>
 */

public enum Tokener {
	ATTRIBUTE_KEY("ak", null),
	ATTRIBUTE_VALUE("av", null),
	OBJECTNAME_KEY("onk", null),
	OBJECTNAME_VALUE("onv", null),
	OBJECTNAME_DOMAIN("od", null),
	SCRIPT_EXEC("s", null),
	MBEAN_CLASS("mc", null),
	MBEAN_DESCRIPTION("md", null),
	DESCRIPTOR_KEY("ak", null),
	DESCRIPTOR_VALUE("av", null);

	
	public static final Map<String, Tokener> TOKEN2ENUM;
	
	static {
		Tokener[] values = Tokener.values();
		Map<String, Tokener> tmp = new HashMap<String, Tokener>(values.length);
		for(Tokener t: values) {
			tmp.put(t.token, t);
		}
		TOKEN2ENUM = Collections.unmodifiableMap(tmp);
	}
	
	private Tokener(final String token, final TokenResolver resolver) {
		this.token = token;
		this.resolver = resolver;
	}
	
	/** The target of the value extraction */
	public final String token;
	/** The token's token resolver */
	public final TokenResolver resolver;
	
	/**
	 * Decodes the passed name to a tokener.
	 * @param name Either the tokener enum name or the define token value. Arguments are trimmed and cased appropriately.
	 * @return the decoded tokener
	 */
	public static Tokener forName(final String name) {
		if(name==null || name.trim().isEmpty()) throw new IllegalArgumentException("The passed name [" + name + "] is not a valid tokener");
		Tokener tok = TOKEN2ENUM.get(name.trim().toLowerCase());
		if(tok!=null) return tok;
		try {
			return Tokener.valueOf(name.trim().toUpperCase());
		} catch (Exception ex) {
			throw new IllegalArgumentException("The passed name [" + name + "] is not a valid tokener");
		}
	}
	
	/*
$a{attrname} : the value of an attribute
$pa{attrname(n, n)} : the pattern key expression applied to an attribute valie
$ond{(n,n)}: the object name domain
$onp{(k)}: the keyed property in the object name
$s{scriptname}: eval a script against kitchen sink
$d{descriptorname}
MBean Info:
	classname
	description

	 */
	
	public static String resolve(final String fullExpression) {
		if(fullExpression==null || fullExpression.trim().isEmpty()) return null;
		final StringBuffer sb = new StringBuffer();
		final Matcher m = TOKEN_PATTERN.matcher(fullExpression.trim());
		while(m.find()) {
			ParsedToken pt = ParsedToken.getParsedToken(m.group(0));
			if(pt!=null) {
				Object resolved = pt.getToken().resolver.resolve(pt.getKey(), pt.getQual(), pt.getRanges());
				if(resolved!=null) {
					m.appendReplacement(sb, resolved.toString());
				} else {
					m.appendReplacement(sb, "");
				}
			}
		}
		m.appendTail(sb);
		return sb.toString();
	}
	
	
	/** The token splitter */
	public static final Pattern TOKEN_PATTERN = Pattern.compile("\\$(.*)?\\{(.*?)(?:\\((\\d+(?:[,-]\\d+)*)\\))?(?:\\[(.*)?\\])?\\}");
	
	public static class IntRange {
		/** A cache of IntRanges keyed by the original expression */
		private static final ConcurrentHashMap<String, IntRange> rangeCache = new ConcurrentHashMap<String, IntRange>();
		/** The int range expression */
		public static final Pattern RANGE_PATTERN = Pattern.compile("(\\d+(?:[,-]\\d+)*)");
		/** Comma splitter pattern */
		public static final Pattern COMMA_SPLIT = Pattern.compile(",");
		private static final IntRange[] EMPTY_ARR = {};
		/** The range which is an array of ints with a length of one for a single value, or a length of two for a wider range */
		private final int[] range;
		
		public static IntRange[] getRanges(final String rangeExpression) {
			if(rangeExpression==null || rangeExpression.trim().isEmpty()) return EMPTY_ARR;  
			List<IntRange> ranges = new ArrayList<IntRange>();
			for(String rexp: COMMA_SPLIT.split(rangeExpression)) {
				Matcher matcher = RANGE_PATTERN.matcher(rexp.replace(" ", ""));
				if(matcher.matches()) {
					String exp = matcher.group(1);
					IntRange range = rangeCache.get(exp);
					if(range==null) {
						synchronized(rangeCache) {
							range = rangeCache.get(exp);
							if(range==null) {
								range = new IntRange(exp);
							}
						}
					}
					ranges.add(range);
				}
			}
			return ranges.toArray(new IntRange[ranges.size()]);
		}
		
		private IntRange(final String rangeSubExpression) {
			final int index = rangeSubExpression.indexOf('-');
			if(index==-1) {
				range = new int[]{Integer.parseInt(rangeSubExpression)};
			} else {
				range = new int[]{
						Integer.parseInt(rangeSubExpression.substring(0, index)),
						Integer.parseInt(rangeSubExpression.substring(index+1))
				};
			}
		}
		
		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {			
			return "IntRange " + Arrays.toString(range);
		}
		
		
		/**
		 * Returns the range which is an array of ints with a length of one for a single value, or a length of two for a wider range
		 * @return the range array
		 */
		public int[] getRange() {
			return range;
		}
	}
	
	public static class ParsedToken {
		/** A cache of ParsedTokens keyed by the original expression */
		private static final ConcurrentHashMap<String, ParsedToken> tokenCache = new ConcurrentHashMap<String, ParsedToken>();

		/** Empty array const */
		private static final ParsedToken[] EMPTY_ARR = {};
		
		/**
		 * Parses the passed expression and returns an array of ParsedTokens in the order they appeared in the expression
		 * @param expression The expression to parse
		 * @return an array of parsed tokens
		 */
		public static ParsedToken[] getParsedTokens(final String expression) {
			if(expression==null || expression.trim().isEmpty()) return EMPTY_ARR;
			List<ParsedToken> ptokens = new ArrayList<ParsedToken>();
			final Matcher m = TOKEN_PATTERN.matcher(expression.trim());
			while(m.find()) {
				final String subExpr = m.group(0);
				ParsedToken pt = tokenCache.get(subExpr);
				if(pt==null) {
					synchronized(tokenCache) {
						pt = tokenCache.get(subExpr);
						if(pt==null) {
							pt = new ParsedToken(m);
						}
					}
				}
				ptokens.add(pt);
			}			
			if(ptokens.isEmpty()) return EMPTY_ARR;
			return ptokens.toArray(new ParsedToken[ptokens.size()]);
		}
		
		/**
		 * Parses a single token (the first found in the passed expression)
		 * @param expression The expression to parse
		 * @return the parsed token or null if no valid token could be parsed
		 */
		public static ParsedToken getParsedToken(final String expression) {
			if(expression==null || expression.trim().isEmpty()) return null;
			final Matcher m = TOKEN_PATTERN.matcher(expression.trim());
			if(m.find()) {
				final String subExpr = m.group(0);
				ParsedToken pt = tokenCache.get(subExpr);
				if(pt==null) {
					synchronized(tokenCache) {
						pt = tokenCache.get(subExpr);
						if(pt==null) {
							pt = new ParsedToken(m);
						}
					}
				}
				return pt;
			}
			return null;
		}
		
		
		/** The tokener type */
		final Tokener token;
		/** The expression key */
		final String key;
		/** The expression ranges */
		final IntRange[] ranges;
		/** The optional expression qualifier */
		final String qual;
		
		
		private ParsedToken(final Matcher matcher) {
			token = Tokener.forName(matcher.group(1));
			key = matcher.group(2).trim();
			ranges = IntRange.getRanges(matcher.group(3));
			String tmp = matcher.group(4);
			qual = tmp==null ? null : tmp.trim();			
		}


		/**
		 * Returns 
		 * @return the token
		 */
		public Tokener getToken() {
			return token;
		}


		/**
		 * Returns 
		 * @return the key
		 */
		public String getKey() {
			return key;
		}


		/**
		 * Returns 
		 * @return the ranges
		 */
		public IntRange[] getRanges() {
			return ranges;
		}


		/**
		 * Returns 
		 * @return the qual
		 */
		public String getQual() {
			return qual;
		}
		
		
		
	}
}

/*
import java.util.regex.*;
p = Pattern.compile('\\$(.*)?\\{(.*?)\\((\\d+([,-]\\d+)*)\\)\\}');  
v = '$s{foo(1,4-7)}';
m = p.matcher(v);
int cnt = 0;
while(m.find()) {    
    println "Op: ${m.group(1)}";
    println "Arg: ${m.group(2)}";
    println "Qual: ${m.group(3)}";
    cnt++;
}
if(cnt==0) println "NO MATCH";
*/

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
package com.heliosapm.opentsdb.client.aop.naming;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * <p>Title: MetricNamingToken</p>
 * <p>Description: A functional enum representing the value replaced tokens used in a metric name template expression that can be used in a formatted metric name</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.naming.MetricNamingToken</code></p>
 */
public enum MetricNamingToken {
	// =====================================================================================
	//   Static Tokens
	// =====================================================================================
	/** Represents the simple <b><i><code>Class</code></i></b> name of the class containing the instrumented method */
	$CLASS("\\$\\{class\\}", false, Extractors.CLASS),
	/** Represents the <b><i><code>Method</code></i></b> name of the instrumented method */
	$METHOD("\\$\\{method\\}", false, Extractors.METHOD),
	/** Represents the <b><i><code>Package</code></i></b> name of the class containing the instrumented method */
	$PACKAGE("\\$\\{package(?:\\[(\\d+)\\])?\\}", false, Extractors.PACKAGE),  // Example:  ${package} or ${package[2]}
	/** Represents the <b><i><code>Package</code></i></b> name of the class containing the instrumented method */
	$ANNOTATION("\\$\\{(.*?)@\\((.*?)\\)(.*?)\\}", false, Extractors.ANNOTATION),  // Example: ${@(Instrumented).version()}
	

	// =====================================================================================
	//   Runtime Tokens
	// =====================================================================================	
	/** Represents the <b><i><code>this</code></i></b> object instance */
	$THIS("\\$\\{this\\}|\\$\\{this:(.*?)\\}", true, Extractors.THIS),  // Example:  ${this}   or   ${this: $0.toString().toUpperCase()}
	/** Represents the indexed argument to a method. e.g. <b><i><code>$1</code></i></b> is the value of the first argument */
	$ARG("\\$\\{arg\\[(\\d+)\\]\\}|\\$\\{arg:(.*?)\\}", true, Extractors.ARG),  // Example:  ${arg[2]}   or ${arg:(\"\" + ($1 + $1))}
	
	// ==================
	// This is a problem when the method error-exits because there's no return value.
	//  Commenting until we can modify to replace the $_ "non-value" with some error indicator (like "error")
	// ==================
//	/** Represents the return value of the method invocation */
//	$RETURN("\\$\\{return(?:\\:(.*))?\\}", true, Extractors.RETURN),
	
	
	/** A free form naming token built using qualified Java source and Javassist tokens */
	$JAVA("\\$\\{java:(.*?)\\}", true, Extractors.JAVA);
	
	
	/**
	 * Some quicke helper tests
	 * @param args None
	 */
	public static void main(String[] args) {
		log("======== All Patterns in One ========");
		StringBuilder b = new StringBuilder();
		for(MetricNamingToken mt: MetricNamingToken.values()) {
			//b.append("(?:").append(mt.pattern.pattern().replace("\\",  "\\\\")).append(")|");
			b.append("(?:").append(mt.pattern.pattern()).append(")|");
		}
		b.deleteCharAt(b.length()-1);
		log(b.toString());
		try {
			Pattern.compile(b.toString());
		} catch (Exception ex) {
			ex.printStackTrace(System.err);
		}
	}
	
	/**
	 * Simple out formatted logger
	 * @param fmt The format of the message
	 * @param args The message arguments
	 */
	public static void log(String fmt, Object...args) {
		System.out.println(String.format(fmt, args));
	}

	
	
	
	/** A map of all tokens keyed by the pattern */
	public static final Map<Pattern, MetricNamingToken> PATTERN2ENUM;
	/** A map of runtime tokens keyed by the pattern */
	public static final Map<Pattern, MetricNamingToken> RT_PATTERN2ENUM;
	
	/** An aggregated patttern to match all tokens. */
	public static final Pattern ALL_PATTERNS;
	
	static {
		MetricNamingToken[] values = MetricNamingToken.values();
		StringBuilder b = new StringBuilder();
		Map<Pattern, MetricNamingToken> tmp = new HashMap<Pattern, MetricNamingToken>(values.length);
		Map<Pattern, MetricNamingToken> tmp2 = new HashMap<Pattern, MetricNamingToken>(values.length);
		for(MetricNamingToken jt: values) {
			b.append("(?:").append(jt.pattern.pattern()).append(")|");
			tmp.put(jt.pattern, jt);
			if(jt.runtime) {
				tmp2.put(jt.pattern, jt);
			}
		}
		b.deleteCharAt(b.length()-1);
		ALL_PATTERNS = Pattern.compile(b.toString());
		PATTERN2ENUM = Collections.unmodifiableMap(tmp);
		RT_PATTERN2ENUM = Collections.unmodifiableMap(tmp2);
	}
	
	private MetricNamingToken(String pattern, boolean runtime, ValueExtractor extractor) {
		this.pattern = Pattern.compile(pattern);
		this.runtime = runtime;
		this.extractor = extractor;
	}
	
	/** The token match pattern. Null if actual symbol exists */
	public final Pattern pattern;
	/** Indicates if the token is a runtime replacement */
	public final boolean runtime;
	/** The value extractor */
	public final ValueExtractor extractor;
	
	/**
	 * Matches the passed expression to a MetricNamingToken
	 * @param expression The expression to evaluate
	 * @return The matching MetricNamingToken
	 */
	public static MetricNamingToken matchToken(CharSequence expression) {
		if(expression==null || expression.toString().trim().isEmpty()) throw new IllegalArgumentException("The passed expression was null or empty");
		String expr = expression.toString().trim();
		for(MetricNamingToken jt: MetricNamingToken.values()) {
			if(jt.pattern.matcher(expr).matches()) return jt;
		}
		throw new RuntimeException("Failed to match a MetricNamingToken for expression [" + expression + "]");
	}
	
	
//	public static final ValueExtractor classExtractor = new ValueExtractor {
//		public String getStaticValue(Class<?> clazz, Method method) {
//			
//		}
//	};
	
	/**
	 * Determines if the expression contains runtime tokens
	 * @param expression The expression to test
	 * @return true if the expression contains runtime tokens, false otherwise
	 */
	public static boolean hasRuntimeTokens(CharSequence expression) {
		for(Pattern p: RT_PATTERN2ENUM.keySet()) {
			if(p.matcher(expression).find()) return true;
		}
		return false;
	}
	
	
	
	
	
}

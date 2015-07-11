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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>Title: AbstractCompiledExpression</p>
 * <p>Description: The base expression that compiled expression extend</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.AbstractCompiledExpression</code></p>
 */

public abstract class AbstractCompiledExpression implements CompiledExpression {
	/** Instance logger */
	private final Logger log = LogManager.getLogger(getClass());
	
	/** The splitter pattern for compound names */
	public static final Pattern COMPOUND_NAME_SPLITTER = Pattern.compile("/");
	/** The default split name array of the name is null or empty */
	protected static final String[] DEFAULT_SPLIT_NAME = {""}; 

	/**
	 * Creates a new AbstractCompiledExpression
	 */
	public AbstractCompiledExpression() {
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.jvmjmx.customx.CompiledExpression#trace(com.heliosapm.opentsdb.client.jvmjmx.customx.CollectionContext)
	 */
	@Override
	public void trace(final CollectionContext ctx) {
		try {
			ctx.trace(getMetricFQN(ctx), getValue(ctx));
		} catch (Exception ex) {
			log.error("Trace failed", ex);
		}
	}
	
	/**
	 * Performs a string replacement
	 * @param token The token to replace
	 * @param working The working string to replace in
	 * @param value The value to replace the token with
	 * @return the new working string
	 */
	protected static String subst(final String token, final String working, final Object value) {
		return working.replace(token, value==null ? "" : value.toString());
	}
	
	/**
	 * Splits a compound name and performs some basic cleanup
	 * @param name The compound name
	 * @return the fragments of the name
	 */
	protected static String[] split(final String name) {
		if(name==null || name.trim().isEmpty()) return DEFAULT_SPLIT_NAME;
		if(name.indexOf('/')==-1) return new String[]{name.trim()};
		final String[] arr = COMPOUND_NAME_SPLITTER.split(name);
		final List<String> rarr = new ArrayList<String>(arr.length);
		int x = 0;
		for(int i = 0; i < arr.length; i++) {
			final String s = arr[i];
			if(s!=null && !s.trim().isEmpty()) {
				rarr.add(s.trim());
				x++;
			}
		}
		return rarr.toArray(new String[x]);
	}
	
	
	
	/**
	 * The default value provider if no value expression is provided
	 * @param ctx The collection context
	 * @return the value to trace
	 */
	@SuppressWarnings("static-method")
	protected Object getValue(final CollectionContext ctx) {
		return ctx.value();
	}
	
	/**
	 * Returns the fully qualified metric name to be traced
	 * @param ctx The collection context
	 * @return the fully qualified metric name
	 */
	protected abstract String getMetricFQN(final CollectionContext ctx);

}

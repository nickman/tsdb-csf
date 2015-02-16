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

package com.heliosapm.opentsdb.client.aop;

/**
 * <p>Title: ShorthandParseFailureException</p>
 * <p>Description: An exception thrown when the shorthand compiler cannot parse an expression</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.ShorthandParseFailureException</code></p>
 */
public class ShorthandParseFailureException extends ShorthandException {
	/**  */
	private static final long serialVersionUID = -5855310122745557706L;

	/**
	 * Creates a new ShorthandParseFailureException
	 * @param message The failure message
	 * @param expression The expression that failed
	 * @param cause The underlying cause
	 */
	public ShorthandParseFailureException(String message, String expression, Throwable cause) {
		super(message, expression, cause);
	}
	
	/**
	 * Creates a new ShorthandParseFailureException
	 * @param message The failure message
	 * @param expression The expression that failed
	 */
	public ShorthandParseFailureException(String message, String expression) {
		super(message, expression);
	}

}

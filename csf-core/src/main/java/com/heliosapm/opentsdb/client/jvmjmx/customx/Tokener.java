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

import com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.AttributeNameTokenResolver;

/**
 * <p>Title: Tokener</p>
 * <p>Description: Enumerates the recognized scripting tokens and 
 * parses and resolves expression containing these tokens.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.Tokener</code></p>
 */

public enum Tokener {
	/** A named attribute value */
	ATTRV,
	/** A named attribute */
	ATTRN(new AttributeNameTokenResolver()),
	/** A named operation return value */
	OP,
	/** The ObjectName domain or a subscript thereof */
	OND,
	/** An ObjectName key property value */
	ONK,
	/** A named script return value */
	SCR,
	/** The MBean description or a subscript thereof */
	MBD,
	/** An MBean descriptor value */
	DESCR;
	
	private Tokener(final TokenResolver resolver) {
		this.resolver = resolver;
	}
	private Tokener() {
		this.resolver = null;
	}	
	
	
	public final TokenResolver resolver;
	
	/**
	 * Decodes the passed type to a Tokener
	 * @param name The name to decode
	 * @return the decoded Tokener
	 */
	public static Tokener forName(final String name) {
		if(name==null || name.trim().isEmpty()) throw new IllegalArgumentException("The passed name was null or empty");
		final String _name = name.trim().toUpperCase();
		try {
			return valueOf(_name);
		} catch (Exception ex) {
			throw new IllegalArgumentException("The passed name [" + name + "] is not a valid Tokener");
		}
	}

}

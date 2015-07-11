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

import javax.management.ObjectName;

import com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.AttributeNameTokenResolver;
import com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.AttributeValueTokenResolver;
import com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.DescriptorValueTokenResolver;
import com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.MBeanOperationTokenResolver;
import com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.ObjectNameDomainTokenResolver;
import com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.ObjectNameKeyPropertyTokenResolver;
import com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.ObjectNamePropertyExpansionTokenResolver;
import com.heliosapm.opentsdb.client.jvmjmx.customx.TokenResolvers.ScriptInvocationTokenResolver;


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
	ATTRV(new AttributeValueTokenResolver()),
	/** A named attribute or a subscript thereof */
	ATTRN(new AttributeNameTokenResolver()),
	/** A named operation return value */
	OP(new MBeanOperationTokenResolver()),
	/** The ObjectName domain or a subscript thereof */
	OND(new ObjectNameDomainTokenResolver()),
	/** An ObjectName key property value */
	ONK(new ObjectNameKeyPropertyTokenResolver()),
	/** Expands the full ObjectName into key value pairs, or the same as calling {@link ObjectName#getCanonicalKeyPropertyListString()} */
	ON(new ObjectNamePropertyExpansionTokenResolver()),
	/** A named script return value */
	SCR(new ScriptInvocationTokenResolver()),
	/** An MBean descriptor value */
	DESCR(new DescriptorValueTokenResolver());
	
	private Tokener(final TokenResolver resolver) {
		this.resolver = resolver;
	}
	
	/** The tokener's resolver */
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

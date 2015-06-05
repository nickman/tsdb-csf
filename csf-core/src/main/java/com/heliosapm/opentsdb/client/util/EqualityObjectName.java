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
package com.heliosapm.opentsdb.client.util;

import java.util.Hashtable;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * <p>Title: EqualityObjectName</p>
 * <p>Description: Extension of an {@link ObjectName} that overrides {@link Object#equals(Object)} to
 * declare equality to any other ObjectName that evalutes {@link ObjectName#apply(ObjectName)} as true.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.util.EqualityObjectName</code></p>
 */

public class EqualityObjectName extends ObjectName {

	/**  */
	private static final long serialVersionUID = 2793513891370153977L;

	/**
	 * Creates a new EqualityObjectName
	 * @param name A string representation of the object name.
	 * @throws MalformedObjectNameException The string passed as a parameter does not have the right format.
	 */
	public EqualityObjectName(final String name) throws MalformedObjectNameException {
		super(name);
	}

	/**
	 * Creates a new EqualityObjectName
	 * @param name Another ObjectName
	 * @throws MalformedObjectNameException Won't happen
	 */
	public EqualityObjectName(final ObjectName name) throws MalformedObjectNameException {
		super(name.toString());
	}


	/**
	 * Creates a new EqualityObjectName
	 * @param domain The domain part of the object name.
	 * @param table A hash table containing one or more key properties. The key of each entry in the table is the key of a key property in the object name. The associated value in the table is the associated value in the object name.
	 * @throws MalformedObjectNameException The domain contains an illegal character, or one of the keys or values in table contains an illegal character, or one of the values in table does not follow the rules for quoting.
	 */
	public EqualityObjectName(final String domain, final Hashtable<String, String> table) throws MalformedObjectNameException {
		super(domain, table);
	}

	/**
	 * Creates a new EqualityObjectName
	 * @param domain The domain part of the object name.
	 * @param key The attribute in the key property of the object name.
	 * @param value The value in the key property of the object name.
	 * @throws MalformedObjectNameException The domain, key, or value contains an illegal character, or value does not follow the rules for quoting.
	 */
	public EqualityObjectName(final String domain, final String key, final String value) throws MalformedObjectNameException {
		super(domain, key, value);
	}
	
	/**
	 * {@inheritDoc}
	 * @see javax.management.ObjectName#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(final Object object) {
    // same object case
    if (this == object) return true;
    // object is not an object name case
    if (!(object instanceof ObjectName)) return false;
    return apply((ObjectName)object);		
	}

}

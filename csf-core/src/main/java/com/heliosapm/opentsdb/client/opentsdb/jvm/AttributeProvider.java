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

package com.heliosapm.opentsdb.client.opentsdb.jvm;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * <p>Title: AttributeProvider</p>
 * <p>Description: Defines an attribute provider that supplies details of the attributes of an MBean</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.AttributeProvider</code></p>
 */
public interface AttributeProvider {
	public static final List<Integer> POW2 = Collections.unmodifiableList(Arrays.asList(
			new Integer[] {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144, 524288, 1048576, 2097152, 4194304, 8388608, 16777216, 33554432, 67108864, 134217728, 268435456, 536870912, 1073741824}
	));
	
	/**
	 * Returns the attribute name
	 * @return the attribute name
	 */
	public String getAttributeName();
	/**
	 * Returns the attribute type
	 * @return the attribute type
	 */
	public Class<?> getType();
	/**
	 * Indicates if the attribute type is a primitive
	 * @return true if the attribute type is a primitive, false otherwise
	 */
	public boolean isPrimitive();
	/**
	 * Returns the attribute mask
	 * @return the attribute mask
	 */
	public int getMask();
}

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

import java.util.Map;

import javax.management.MBeanAttributeInfo;

/**
 * <p>Title: MXBeanManager</p>
 * <p>Description: Provides MXBean polling assistance with respect to available attributes and their names/types.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.jvm.MXBeanManager</code></p>
 */

public interface MXBeanManager {
	/**
	 * Returns the attribute names  
	 * @return An array of attribute names
	 */
	public String[] getAttributeNames();
	
	/**
	 * Returns the attribute names enabled for the passed mask
	 * @param mask The mask to match
	 * @return An array of attribute names enabled for the passed mask
	 */
	public <T extends Enum<T> & AttributeProvider> String[] getAttributeNames(final int mask); 

	
	/**
	 * Returns all the attribute providers
	 * @return an array of all the attribute providers
	 */
	public <T extends Enum<T> & AttributeProvider> T[] getAttributeProviders();
	
	/**
	 * Returns a map of provider masks keyed by the attribute name
	 * @return a map of provider masks keyed by the attribute name
	 */
	public <T extends Enum<T> & AttributeProvider> Map<String, Integer> getNameMasks();
	
	/**
	 * Returns the bit mask of the enabled attribute names
	 * @param infos The MBeanServer provided attribute infos
	 * @return the enabled bit mask
	 */
	public int getMaskFor(MBeanAttributeInfo...infos);
	
	
	
	
}

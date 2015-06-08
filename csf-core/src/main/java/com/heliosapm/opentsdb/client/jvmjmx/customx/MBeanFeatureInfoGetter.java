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

import javax.management.MBeanFeatureInfo;
import javax.management.MBeanInfo;

/**
 * <p>Title: MBeanFeatureInfoGetter</p>
 * <p>Description: Defines a getter for MBeanFeatureInfos from an MBeanInfo for a specific feature type</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.MBeanFeatureInfoGetter</code></p>
 */

public interface MBeanFeatureInfoGetter {
	/**
	 * Returns an array of MBeanFeatureInfos for the passed MBeanInfo
	 * @param info The MBeanInfo
	 * @return the MBeanInfos features specific to this impl.
	 */
	MBeanFeatureInfo[] getFeatures(final MBeanInfo info);
}

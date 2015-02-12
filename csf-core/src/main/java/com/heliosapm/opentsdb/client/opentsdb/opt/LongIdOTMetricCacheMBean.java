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

package com.heliosapm.opentsdb.client.opentsdb.opt;

import com.heliosapm.opentsdb.client.opentsdb.OTMetric;

/**
 * <p>Title: LongIdOTMetricCacheMBean</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.LongIdOTMetricCacheMBean</code></p>
 */

public interface LongIdOTMetricCacheMBean {
	
	/**
	 * Returns the number of OTMetrics in the opt cache
	 * @return the number of OTMetrics in the opt cache
	 */
	public int getSize();
	
	/**
	 * Returns the total number of reprobes that have occured in the opt cache
	 * @return the total number of reprobes that have occured in the opt cache
	 */
	public long getReprobes();
	
	/**
	 * Verbose printout of opt cache internals, useful for debugging.
	 * @return opt cache internals
	 */
	public String getCacheInternals();
	
	/**
	 * Returns the initial size of the opt cache
	 * @return the initial size of the opt cache
	 */
	public int getInitialSize();

	/**
	 * Indicates if the opt cache compresses the space used at the cost speed,
	 * or uses more space for an approximately 10% improvement in speed. 
	 * @return the space4Speed true if space-for-speed (slower/smaller), false if speed-for-space (faster/bigger).
	 */
	public boolean isSpace4Speed();
	
	/**
	 * Returns the OTMetric for the passed ID
	 * @param id The long hash code of the OTMetric to get
	 * @return The OTMetric for the id, or null if it was not found
	 */
	public OTMetric getOTMetric(final long id);

}

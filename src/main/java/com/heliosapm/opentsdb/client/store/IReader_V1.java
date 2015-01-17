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

package com.heliosapm.opentsdb.client.store;

/**
 * <p>Title: IReader_V1</p>
 * <p>Description: Version 1 of the MetricStore header reader</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.store.IReader_V1</code></p>
 */

public interface IReader_V1 extends IStoreReader {
	/**
	 * Returns the number of metric collections stored in the MetricStore file
	 * @return the number of entries in the file
	 */
	public int getEntryCount();
	
	/**
	 * Returns the timestamp of the last time a metric collection was stored to the MetricStore file
	 * @return the timestamp of the last metric write
	 */
	public long getLastUpdated();
	

	/**
	 * Returns the MetricStore format version
	 * @return the MetricStore format version
	 */
	public int getVersion();

	/**
	 * Updates the entry count
	 * @param entryCount the new count
	 */
	public void setEntryCount(final int entryCount);

	/**
	 * Updates the last updated timestamp
	 * @param lastUpdated the last updated timestamp
	 */
	public void setLastUpdated(final long lastUpdated);
}

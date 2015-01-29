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

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * <p>Title: BaseStoreHeaderInstance</p>
 * <p>Description: Value holder for a store header instance</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.store.BaseStoreHeaderInstance</code></p>
 */

public abstract class BaseStoreHeaderInstance implements IStoreReader {
	/** The number of metric collections stored in the MetricStore file */ 
	int entryCount;
	/** The timestamp of the last time a metric collection was stored to the MetricStore file */
	long lastUpdated;
	/** The random access file */
	RandomAccessFile raf;
	/** The file channel */
	FileChannel fc;
	/** The mapped byte buffer for the header */
	ByteBuffer headerBuffer;
	/** The mapped byte buffer for the entries */
	ByteBuffer entriesBuffer;
	

	/**
	 * Creates a new BaseStoreHeaderInstance from the passed buffer
	 * @param bb The ByteBuffer to read the header from
	 */
	abstract void read(final ByteBuffer bb);
	
	/**
	 * Creates a new BaseStoreHeaderInstance
	 */
	BaseStoreHeaderInstance() {
		
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.store.IStoreReader#setRandomAccessFile(java.io.RandomAccessFile)
	 */
	@Override
	public void setRandomAccessFile(final RandomAccessFile raf) {
		this.raf = raf;		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.store.IStoreReader#setFileChannel(java.nio.channels.FileChannel)
	 */
	@Override
	public void setFileChannel(final FileChannel fc) {
		this.fc = fc;		
	}
	
	
	/**
	 * Returns the number of metric collections stored in the MetricStore file
	 * @return the number of entries in the file
	 */
	public int getEntryCount() {
		return entryCount;
	}
	
	/**
	 * Returns the timestamp of the last time a metric collection was stored to the MetricStore file
	 * @return the timestamp of the last metric write
	 */
	public long getLastUpdated() {
		return lastUpdated;
	}
	

	/**
	 * Returns the MetricStore format version
	 * @return the MetricStore format version
	 */
	public abstract int getVersion();

	/**
	 * Updates the entry count
	 * @param entryCount the new count
	 */
	public void setEntryCount(final int entryCount) {
		this.entryCount = entryCount;
	}

	/**
	 * Updates the last updated timestamp
	 * @param lastUpdated the last updated timestamp
	 */
	public void setLastUpdated(final long lastUpdated) {
		this.lastUpdated = lastUpdated;
	}
	

}

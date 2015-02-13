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

import java.nio.ByteBuffer;

/**
 * <p>Title: Reader_V1</p>
 * <p>Description: Functional enum to describe the layout of, read and write MetricStore files</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.store.Reader_V1</code></p>
 */

public class Reader_V1 extends BaseStoreHeaderInstance implements IReader_V1 {
	
	/**
	 * <p>Title: HEADER</p>
	 * <p>Description: The header layout for the V1 MetricStore format</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.store.Reader_V1.HEADER</code></p>
	 */
	public enum HEADER  {
		/** The MetricStore file magic number */
		MAGIC(0, 8),
		/** The file format version */
		VERSION(8, 4),
		/** The number of metric collection entries in the file */
		ENTRY_COUNT(12, 4),
		/** The timestamp of the last write to the file */
		LAST_UPDATED(16, 8);
		
		private HEADER(final int offset, final int length) {
			this.offset = offset;
			this.length = length;
		}
		
		/** The offset of the segment in the file */
		public final int offset;
		/** The length of the segment in the file in bytes */
		public final int length;
		
		
		
		
	}
	
	/** The file offset of the header */
	public static final int HEADER_OFFSET = 0;	
	/** The total length of the header */
	public static final int HEADER_LENGTH = HEADER.LAST_UPDATED.offset + HEADER.LAST_UPDATED.length;
	
	/** The version of this MetricStore format */
	public static final int FORMAT_VERSION = 1;
	
//	
	/**
	 * Reads the entry count from the MetricStore file, which is the number of stored metric collections.
	 * @param bb The buffer to read from
	 * @return the entry count of the MetricStore file 
	 */
	public static int readEntryCount(final ByteBuffer bb) {
		if(bb==null) throw new IllegalArgumentException("The passed buffer was null");
		if(bb.capacity() < (HEADER.ENTRY_COUNT.offset + HEADER.ENTRY_COUNT.length)) throw new RuntimeException("Reading entryCount: Passed buffer has a limit of [" + bb.capacity() + "] but we need to read [" + (HEADER.ENTRY_COUNT.offset + HEADER.ENTRY_COUNT.length) + "]");
		return bb.getInt(HEADER.ENTRY_COUNT.offset);		
	}
	
	
	
	/**
	 * Reads the last updated timestamp of the MetricStore file which is the last time a metric collection was written, 
	 * and not the last file modified time which will be ticked when metric collections are extracted from the file.
	 * @param bb The buffer to read from
	 * @return the last updated timestamp of the MetricStore
	 */
	public static long readLastUpdated(final ByteBuffer bb) {
		if(bb==null) throw new IllegalArgumentException("The passed buffer was null");
		if(bb.capacity() < (HEADER.LAST_UPDATED.offset + HEADER.LAST_UPDATED.length)) throw new RuntimeException("Reading lastUpdate: Passed buffer has a limit of [" + bb.capacity() + "] but we need to read [" + (HEADER.LAST_UPDATED.offset + HEADER.LAST_UPDATED.length) + "]");
		return bb.getLong(HEADER.LAST_UPDATED.offset);		
	}

	
	/**
	 * Creates a new Reader_V1
	 */
	Reader_V1() {
		
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.store.BaseStoreHeaderInstance#read(java.nio.ByteBuffer)
	 */
	@Override
	void read(final ByteBuffer bb) {
		this.entryCount = readEntryCount(bb);
		this.lastUpdated = readLastUpdated(bb);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.store.IReader_V1#getVersion()
	 */
	@Override
	public int getVersion() {
		return FORMAT_VERSION;
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.store.IReader_V1#setEntryCount(int)
	 */
	@Override
	public void setEntryCount(final int entryCount) {
		this.entryCount = entryCount;
	}


	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.store.IReader_V1#setLastUpdated(long)
	 */
	@Override
	public void setLastUpdated(final long lastUpdated) {
		this.lastUpdated = lastUpdated;
	}



	
	
	
}

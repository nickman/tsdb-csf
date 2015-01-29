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

package com.heliosapm.opentsdb.client.opentsdb;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.regex.Pattern;

import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: OTMetric</p>
 * <p>Description: Streamlined OpenTSDB metric representation. Just add a time stamp and a value, and go to town.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OTMetric</code></p>
 */

public class OTMetric {
	final ByteBuffer nameBuffer;
	
	static final short HAS_APP_TAG = 0;							// 1 byte
	static final short HAS_HOST_TAG = HAS_APP_TAG + 1;			// 1 byte
	static final short TOTAL_SIZE_OFFSET = HAS_HOST_TAG + 1;  	// 4 bytes
	static final short MN_SIZE_OFFSET = TOTAL_SIZE_OFFSET + 4; 	// 4 bytes
	static final short TAG_COUNT_OFFSET = MN_SIZE_OFFSET + 4;	// 4 bytes
	// ------  then metric name bytes  ----------
	static final short FTAG_SIZE_OFFSET = TAG_COUNT_OFFSET + 4;	// 4 bytes
	static final short TOTAL_SIZE = FTAG_SIZE_OFFSET +4; 
	
	static final byte ZERO_BYTE = 0;
	static final byte ONE_BYTE = 1;
	
	static final byte EQ_BYTE = "=".getBytes(Constants.UTF8)[0];
	
	static final byte[] APP_TAG_BYTES = Constants.APP_TAG.getBytes(Constants.UTF8);
	static final byte[] HOST_TAG_BYTES = Constants.HOST_TAG.getBytes(Constants.UTF8);
	
	static final Pattern COMMA_SPLITTER = Pattern.compile(",");
	
	/**
	 * Creates a new OTMetric
	 */
	OTMetric(final String flatName) {
		if(flatName==null) throw new IllegalArgumentException("The passed flat name was null");
		final String fname = flatName.replace(" ", "");
		if(fname.isEmpty()) throw new IllegalArgumentException("The passed flat name was empty");
		final int eindex = fname.indexOf('=');		
		if(eindex==-1) {
			// totally flat metric.
			final byte[] name = fname.getBytes(Constants.UTF8);
			nameBuffer = (ByteBuffer)ByteBuffer.allocateDirect(name.length + TOTAL_SIZE)
					.put(ZERO_BYTE)			// No app tag
					.put(ZERO_BYTE)			// No host tag
					.putInt(name.length)	// Length of the full name
					.putInt(name.length)	// Length of the prefix
					.putInt(ZERO_BYTE)		// Zero tags
					.put(name)
					.asReadOnlyBuffer()
					.flip();			
		} else {
			ByteBuffer buff = null;
			try {
				buff = ByteBuffer.allocateDirect(fname.getBytes(Constants.UTF8).length*2);
				final int cindex = fname.indexOf(':');
				
				if(cindex==-1) {
					// pseudo flat metric
				} else {
					final byte[] prefix = fname.substring(0, cindex).getBytes(Constants.UTF8);
					final String[] tags = COMMA_SPLITTER.split(fname.substring(cindex+1));
					buff					
						.put(ZERO_BYTE)			// App Tag, Zero for now
						.put(ZERO_BYTE)			// Host Tag, Zero for now
						.putInt(0)				// Total Length, Zero for now
						.putInt(prefix.length)	// Length of the prefix
						.putInt(tags.length)	// Tag count for now
						.put(prefix);			// The prefix bytes
					int actualTagCount = 0;
					int totalLength = prefix.length;
					byte hasAppTag = ZERO_BYTE, hasHostTag = ZERO_BYTE;
					for(String tag: tags) {
						final int eind = tag.indexOf('=');
						if(eind==-1) continue;
						final byte[] key = Util.clean(tag.substring(0, eind)).getBytes(Constants.UTF8);
						if(key.length==0) continue;
						final byte[] value = Util.clean(tag.substring(eind+1)).getBytes(Constants.UTF8);
						if(value.length==0) continue;
						if(Arrays.equals(APP_TAG_BYTES, key)) {
							hasAppTag = ONE_BYTE;
						} else if(Arrays.equals(HOST_TAG_BYTES, key)) {
							hasHostTag = ONE_BYTE;
						}
						int tagLength = key.length + value.length + 1; 
						buff.putInt(tagLength).put(key).put(EQ_BYTE).put(value);
						actualTagCount++;						
						totalLength += tagLength;
					}
					final int pos = buff.position();
					buff.rewind();
					if(hasAppTag==ONE_BYTE) buff.put(hasAppTag);
					if(hasHostTag==ONE_BYTE) buff.put(hasHostTag);
					buff.putInt(totalLength);
					buff.putInt(prefix.length);   // yeah, this is redundant, but we need to skip 4 bytes
					buff.putInt(actualTagCount);
					buff.position(pos);
				}
				nameBuffer = (ByteBuffer)ByteBuffer.allocateDirect(buff.limit()).put(buff).asReadOnlyBuffer().flip();
			} finally {
				OffHeapFIFOFile.clean(buff);
			}
		}
	}
	
	public String toString() {
		StringBuilder b = new StringBuilder(nameBuffer.getInt(TOTAL_SIZE_OFFSET));
		int nextLen = nameBuffer.getInt(MN_SIZE_OFFSET);
		final int tagCount = nameBuffer.getInt(TAG_COUNT_OFFSET);
		byte[] prefix = new byte[nextLen];
		
		nameBuffer.g
		return b.toString();
	}
	
	protected static final String readString(final ByteBuffer buff, final int offset, final int length) {
		final byte[] content = new byte[length];
		buff.get(content, offset, length);
		return new String(content, Constants.UTF8);
	}

}

// for multi-metrics, we start with a base, and append the qualifier to the domain.
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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jboss.netty.buffer.ChannelBuffer;

import com.heliosapm.opentsdb.client.name.AgentName;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: OTMetric</p>
 * <p>Description: Streamlined OpenTSDB metric representation. Just add a time stamp and a value, and go to town.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OTMetric</code></p>
 */

public class OTMetric implements Serializable {
	/** The buffer containing the OTMetric details */
	final ByteBuffer nameBuffer;
	
	static final short HAS_APP_TAG = 0;							// 1 byte
	static final short HAS_HOST_TAG = HAS_APP_TAG + 1;			// 1 byte
	static final short IS_EXT_TAG = HAS_HOST_TAG + 1;			// 1 byte
	static final short TOTAL_SIZE_OFFSET = IS_EXT_TAG + 1;  	// 4 bytes
	static final short MN_SIZE_OFFSET = TOTAL_SIZE_OFFSET + 4; 	// 4 bytes
	static final short TAG_COUNT_OFFSET = MN_SIZE_OFFSET + 4;	// 4 bytes
	// ------  then metric name bytes  ----------
	static final short FTAG_SIZE_OFFSET = TAG_COUNT_OFFSET + 4;	// 4 bytes
	static final short TOTAL_SIZE = FTAG_SIZE_OFFSET +4; 
	
	
	
	static final byte ZERO_BYTE = 1;
	static final byte ONE_BYTE = 2;
	
	/** The equals byte const */
	static final byte EQ_BYTE = "=".getBytes(Constants.UTF8)[0];
	/** Empty tag map const */
	static final Map<String, String> EMPTY_TAG_MAP = Collections.unmodifiableMap(new HashMap<String, String>(0));
	
	static final byte[] APP_TAG_BYTES = Constants.APP_TAG.getBytes(Constants.UTF8);
	static final byte[] HOST_TAG_BYTES = Constants.HOST_TAG.getBytes(Constants.UTF8);
    static final byte[] JSON_OPEN_ARR = "[".getBytes(Constants.UTF8);
    static final byte[] JSON_CLOSE_ARR = "]".getBytes(Constants.UTF8);
    static final byte[] JSON_COMMA = ",".getBytes(Constants.UTF8);
	
	
	static final Pattern COMMA_SPLITTER = Pattern.compile(",");
	static final Pattern DOT_SPLITTER = Pattern.compile("\\.");
	
	OTMetric(final String flatName) {
		this(flatName, null);
	}
	
	/**
	 * Creates a new OTMetric
	 */
	OTMetric(final String flatName, final String extension) {
		if(flatName==null) throw new IllegalArgumentException("The passed flat name was null");
		String fname = flatName.replace(" ", "");
		if(fname.isEmpty()) throw new IllegalArgumentException("The passed flat name was empty");
		final String fext = extension==null ? null : extension.trim(); 
		final boolean isext = fext!=null && !fext.isEmpty();
		int eindex = fname.indexOf('=');		
		if(eindex==-1) {
			if(isext) {
				fname = fname + "." + fext; 
			}			
			// totally flat metric.
			final byte[] name = fname.getBytes(Constants.UTF8);
			nameBuffer = (ByteBuffer)ByteBuffer.allocateDirect(name.length + TOTAL_SIZE)
					.put(ZERO_BYTE)						// No app tag
					.put(ZERO_BYTE)						// No host tag
					.put(isext ? ONE_BYTE : ZERO_BYTE)	// Ext flag
					.putInt(name.length)				// Length of the full name
					.putInt(name.length)				// Length of the prefix
					.putInt(ZERO_BYTE)					// Zero tags
					.put(name)
					.asReadOnlyBuffer()
					.flip();			
		} else {
			ByteBuffer buff = null;
			try {
				buff = ByteBuffer.allocateDirect(fname.getBytes(Constants.UTF8).length*2);
				int cindex = fname.indexOf(':');
				final boolean wasPseudo = cindex==-1; 
				if(cindex==-1) {
					// pseudo flat metric, e.g. [KitchenSink.resultCounts.op=cache-lookup.service=cacheservice]
					final String[] prefixes = DOT_SPLITTER.split(fname.substring(0, eindex));
					
					if(prefixes.length<2) {/* FIXME: */} // INVALID. We're either starting with a "=" or there is no prefix, only tags.
					
					StringBuilder b = new StringBuilder(prefixes[0]);
					for(int i = 1; i < prefixes.length-1; i++) {
						b.append(".").append(prefixes[i]);
					}
					if(isext) {
						b.append(".").append(fext);
					}
					b.append(":");
					final String[] tags = COMMA_SPLITTER.split((prefixes[prefixes.length-1] + "=" + fname.substring(eindex+1)).replace('.', ','));
					for(String tag: tags) {
						b.append(tag).append(",");
					}
					fname = b.deleteCharAt(b.length()-1).toString();	
					cindex = fname.indexOf(':');
					eindex = fname.indexOf('=');
				}
				final byte[] prefix =
						(isext && !wasPseudo) ?
								(fname.substring(0, cindex) + "." + fext).getBytes(Constants.UTF8)
								
							:
								fname.substring(0, cindex).getBytes(Constants.UTF8);
				final String[] tags = COMMA_SPLITTER.split(fname.substring(cindex+1));
				buff					
					.put(ZERO_BYTE)			// App Tag, Zero for now
					.put(ZERO_BYTE)			// Host Tag, Zero for now
					.put(isext ? ONE_BYTE : ZERO_BYTE)	// Ext flag
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
				buff.put(hasAppTag);
				buff.put(hasHostTag);
				buff.put(isext ? ONE_BYTE : ZERO_BYTE);
				buff.putInt(totalLength);
				buff.putInt(prefix.length);
				buff.putInt(actualTagCount);					
				buff.limit(pos);
				buff.position(0);				
				nameBuffer = ByteBuffer.allocateDirect(buff.limit()).put(buff).asReadOnlyBuffer();
			} finally {
				OffHeapFIFOFile.clean(buff);
			}
		}
	}
	
	public static void main(String[] args) {
		log("OTMetric Test");
		log("Creating OTM for [" + ManagementFactory.CLASS_LOADING_MXBEAN_NAME + "] (" + ManagementFactory.CLASS_LOADING_MXBEAN_NAME.getBytes(Constants.UTF8).length + ")");
		OTMetric otm = new OTMetric(ManagementFactory.CLASS_LOADING_MXBEAN_NAME);
		printDetails(otm);
		for(GarbageCollectorMXBean gc: ManagementFactory.getGarbageCollectorMXBeans()) {
			otm = new OTMetric(gc.getObjectName().toString());
			printDetails(otm);
		}
		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice");
		printDetails(otm);
		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice", "p75");
		printDetails(otm);
		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,host=AA");
		printDetails(otm);
		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,app=XX");
		printDetails(otm);
		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,host=AA,app=XX");
		printDetails(otm);
		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,host=AA,app=XX", "p75");
		printDetails(otm);


	}
	
	Object writeReplace() throws ObjectStreamException {
		return toString();
	}
	
	
	public static void printDetails(final OTMetric otm) {
		StringBuilder b = new StringBuilder("FQN:").append(otm.toString());
		b.append("\n\thasAppTag:").append(otm.hasAppTag());
		b.append("\n\thasHostTag:").append(otm.hasHostTag());
		b.append("\n\tisExtension:").append(otm.isExtension());
		b.append("\n\tPrefix:").append(otm.getMetricName());
		b.append("\n\tTagCount:").append(otm.getTagCount());
		b.append("\n\tTags:").append(otm.getTags());
		log(b);
	}
	
	public static void log(Object msg) {
		System.out.println(msg);
	}
	
	/**
	 * Clears the byte buffer.
	 * <b>Use with caution!</b>. OTMetrics that have been cleaned are toxic.
	 */
	void clean() {
		OffHeapFIFOFile.clean(nameBuffer);
	}
	
	/**
	 * Indicates if this OTMetric already has an app tag
	 * @return true if this OTMetric already has an app tag, false otherwise
	 */
	public boolean hasAppTag() {
		return nameBuffer.get(HAS_APP_TAG) == ONE_BYTE;
	}
	
	/**
	 * Indicates if this OTMetric already has a host tag
	 * @return true if this OTMetric already has a host tag, false otherwise
	 */
	public boolean hasHostTag() {
		return nameBuffer.get(HAS_HOST_TAG) == ONE_BYTE;
	}
	
	/**
	 * Indicates if this OTMetric is an extension name
	 * @return true if this OTMetric is an extension name, false otherwise
	 */
	public boolean isExtension() {
		return nameBuffer.get(IS_EXT_TAG) == ONE_BYTE;
	}
	
	/**
	 * Returns the OTMetric's metric name (aka prefix)
	 * @return the OTMetric's metric name
	 */
	public String getMetricName() {
		final byte[] bytes = new byte[nameBuffer.getInt(MN_SIZE_OFFSET)];
		final ByteBuffer buff = nameBuffer.duplicate();
		buff.position(FTAG_SIZE_OFFSET);
		buff.get(bytes);
		return new String(bytes, Constants.UTF8);
	}
	
	/**
	 * Returns the OTMetric's tag count
	 * @return the OTMetric's tag count
	 */
	public int getTagCount() {
		return nameBuffer.getInt(TAG_COUNT_OFFSET);
	}
	
	/**
	 * Returns the OTMetric's tags
	 * @return the OTMetric's tags
	 */
	public Map<String, String> getTags() {
		final int tagCount = nameBuffer.getInt(TAG_COUNT_OFFSET);
		if(tagCount==0) return EMPTY_TAG_MAP;
		final Map<String, String> tmap = new LinkedHashMap<String, String>(tagCount);
		final int mnSize = nameBuffer.getInt(MN_SIZE_OFFSET);
		final ByteBuffer buff = nameBuffer.duplicate();
		int startingOffset = TAG_COUNT_OFFSET + mnSize + 4;
		buff.position(startingOffset);
		for(int i = 0; i < tagCount; i++) {
			String tagPair = readString(buff, buff.getInt());
			int index = tagPair.indexOf('=');
			if(index==-1) {
				//log.warn("")  // FIXME
				continue;
			}
			tmap.put(tagPair.substring(0, index), tagPair.substring(index+1));
		}
		return tmap;
	}
	
	
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return toStringBuilder().toString();
	}
	
	/**
	 * Returns the formatted OTMetric name in a StringBuilder
	 * @return the metric name StringBuilder
	 */
	public StringBuilder toStringBuilder() {
		final ByteBuffer buff = nameBuffer.duplicate();		
		buff.position(3);
		int totalLength = buff.getInt();
		int prefixLength = buff.getInt();
		int tagCount = buff.getInt();
		StringBuilder b = new StringBuilder(totalLength + tagCount + 1);
		b.append(readString(buff, prefixLength)).append(":");
		System.out.println("-------------------------------------- buff pos:" + buff.position());
		for(int i = 0; i < tagCount; i++) {
			b.append(readString(buff, buff.getInt())).append(",");
		}
		if(tagCount>0) {
			b.deleteCharAt(b.length()-1);
		}
		return b;
	}
	
	/**
	 * Reads a string from the passed byte buffer starting at the current position
	 * @param buff The buffer to read from
	 * @param length The number of bytes to read 
	 * @return the read string
	 */
	protected static final String readString(final ByteBuffer buff, final int length) {
		final byte[] content = new byte[length];
		buff.get(content);
		return new String(content, Constants.UTF8);
	}
	
    /**
     * Renders this metric into the passed buffer, or a new buffer if the passed buffer is null
     * @param buff The optional buffer to render into
     * @return the rendered metric
     */
    public StringBuilder toJSON(final long timestamp, final Object value, final StringBuilder buff) {
    	final StringBuilder b;
    	if(buff==null) b = new StringBuilder();
    	else b = buff;
    	b.append("{\"metric\":\"")
    		.append(getMetricName()).append("\",")
    		.append("\"timestamp\":").append(timestamp).append(",")
    		.append("\"value\":").append(value).append(",")
    		.append("\"tags\": {");
    	final Map<String, String> tags = getTags();
    	for(Map.Entry<String, String> gtag: AgentName.getInstance().getGlobalTags().entrySet()) {
    		if(!tags.containsKey(gtag.getKey())) {
    			b.append("\"").append(gtag.getKey()).append("\":\"").append(gtag.getValue()).append("\",");
    		}
    	}

    	for(Map.Entry<String, String> tag: tags.entrySet()) {
    		b.append("\"").append(tag.getKey()).append("\":\"").append(tag.getValue()).append("\",");    		
    	}
    	return b.deleteCharAt(b.length()-1).append("}}");    	
    }
    
    /**
     * Renders the passed set of metrics
     * @param metrics The metrics to render
     * @return A buffer containing the metrics JSON rendering
     */
    public static StringBuilder setToJSON(final Set<OpenTsdbMetric> metrics) {
    	if(metrics==null || metrics.isEmpty()) return new StringBuilder("[]");
    	StringBuilder b = new StringBuilder(1024).append("[");
    	final int last = metrics.size()-1;
    	int index = 0;
    	for(OpenTsdbMetric m: metrics) {
    		m.toJSON(b);
    		if(index != last) {
    			b.append(",");
    		}
    		index++;
    	}
    	return b.append("]");
    }
    
    
    /**
     * Writes the passed set of metrics into the passed buffer
     * @param metrics The metrics to render
     * @param buffer The buffer to write to
     * @return The number of metrics written
     */
    public static int writeToBuffer(final Set<OTMetric> metrics, final ChannelBuffer buffer) {
    	if(metrics==null || metrics.isEmpty()) return 0;
    	buffer.writeBytes(JSON_OPEN_ARR);
    	final int last = metrics.size()-1;
    	int index = 0;
    	for(OTMetric m: metrics) {
//    		buffer.writeBytes(m.toJSON().toString().getBytes(Constants.UTF8));
    		if(index != last) {
    			buffer.writeBytes(JSON_COMMA);
    		}
    		index++;
    	}
    	buffer.writeBytes(JSON_CLOSE_ARR);
    	return index;
    }
	

}


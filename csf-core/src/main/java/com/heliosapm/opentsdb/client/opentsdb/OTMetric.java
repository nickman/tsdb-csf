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

import static com.heliosapm.opentsdb.client.opentsdb.Constants.UTF8;

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.jboss.netty.buffer.ChannelBuffer;

import com.google.common.hash.Funnel;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.hash.PrimitiveSink;
import com.heliosapm.opentsdb.client.name.AgentName;
import com.heliosapm.opentsdb.client.util.DynamicByteBufferBackedChannelBufferFactory;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: OTMetric</p>
 * <p>Description: Streamlined OpenTSDB metric representation. Just add a time stamp and a value, and go to town.</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OTMetric</code></p>
 * FIXME:  All calls to duplicate need to be inited with a reset.
 */

public class OTMetric implements Serializable {
	/** The buffer containing the OTMetric details */
	final ByteBuffer nameBuffer;
	/** The calculated hash code */
	final HashCode hashCode;
	
	// TODO:  put the hashCode int and long hash codes in the nameBuffer
	
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
	static final byte EQ_BYTE = "=".getBytes(UTF8)[0];
	/** Empty tag map const */
	static final Map<String, String> EMPTY_TAG_MAP = Collections.unmodifiableMap(new HashMap<String, String>(0));
	
	/** UTF8 bytes for the App tag key */
	static final byte[] APP_TAG_BYTES = Constants.APP_TAG.getBytes(UTF8);
	/** UTF8 bytes for the Host tag key */
	static final byte[] HOST_TAG_BYTES = Constants.HOST_TAG.getBytes(UTF8);
	/** UTF8 bytes for the JSON array opener */
    static final byte[] JSON_OPEN_ARR = "[".getBytes(UTF8);
    /** UTF8 bytes for the JSON array closer */
    static final byte[] JSON_CLOSE_ARR = "]".getBytes(UTF8);
    /** UTF8 bytes for a comma */
    static final byte[] JSON_COMMA = ",".getBytes(UTF8);
	
	
	/** Comma splitting pattern */
	static final Pattern COMMA_SPLITTER = Pattern.compile(",");
	/** Dot splitting pattern */
	static final Pattern DOT_SPLITTER = Pattern.compile("\\.");
	
	/** The hashing function to compute hashes for OTMetrics */
	public static final HashFunction OTMETRIC_HASH = Hashing.murmur3_128();
	

	/** The hashing funnel for OTMetrics */
	public static final Funnel<OTMetric> OTMETRIC_FUNNEL = new Funnel<OTMetric>() {
	     /**  */
		private static final long serialVersionUID = -3637425354414746924L;

		/**
		 * {@inheritDoc}
		 * @see com.google.common.hash.Funnel#funnel(java.lang.Object, com.google.common.hash.PrimitiveSink)
		 */
		@Override
		public void funnel(final OTMetric metric, final PrimitiveSink into) {
	    	 into.putString(metric.getMetricName(), UTF8)
	    	 	.putString(metric.getTags().toString(), UTF8);	    	 
	     }		
	};
	
	/**
	 * Creates a new OTMetric
	 * @param flatName The plain flat name from the Metric name
	 */
	OTMetric(final String flatName) {
		this(flatName, null);
	}
	
	/**
	 * Creates a new OTMetric
	 * @param flatName The plain flat name from the Metric name
	 * @param prefix The optional prefix which is prefixed to the flat name
	 */
	OTMetric(final String flatName, final String prefix) {
		this(flatName, prefix, null, null);
	}
	
	
	/**
	 * Creates a new OTMetric
	 * @param flatName The plain flat name from the Metric name
	 * @param prefix The optional prefix which is prefixed to the flat name
	 * @param extension The optional extension which is appended to the TSDB metric name
	 */
	OTMetric(final String flatName, final String prefix, final String extension) {
		this(flatName, prefix, extension, null);
	}
	
	/**
	 * Creates a new OTMetric
	 * @param flatName The plain flat name from the Metric name
	 * @param nprefix The optional prefix which is prefixed to the flat name
	 * @param extension The optional extension which is appended to the TSDB metric name
	 * @param extraTags The optional extra tags to add
	 */
	OTMetric(final String flatName, final String nprefix, final String extension, final Map<String, String> extraTags) {
		if(flatName==null) throw new IllegalArgumentException("The passed flat name was null");
		final String fprefix = nprefix==null ? null : nprefix.trim();
		String fname = (fprefix==null ? "" : (fprefix + ".")) + flatName.replace(" ", "");
		if(fname.isEmpty()) throw new IllegalArgumentException("The passed flat name was empty");
		final String fext = extension==null ? null : extension.trim(); 
		final boolean isext = fext!=null && !fext.isEmpty();
		int eindex = fname.indexOf('=');		
		final boolean hasTags = (extraTags!=null && !extraTags.isEmpty());
		if(eindex==-1 && !hasTags) {
			if(isext) {
				fname = fname + "." + fext; 
			}			
			// totally flat metric.
			final byte[] name = fname.getBytes(UTF8);
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
				buff = ByteBuffer.allocateDirect((fname.getBytes(UTF8).length + (hasTags ? extraTags.toString().length() : 0))*2);
				int cindex = fname.indexOf(':');
				final boolean wasPseudo = cindex==-1;				
				if(cindex==-1 && !hasTags) {
					
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
					b.deleteCharAt(b.length()-1);
//					if(isext) {
//						b.append(".").append(fext);
//					}
					fname = b.toString();
					cindex = fname.indexOf(':');
					eindex = fname.indexOf('=');
				} else {
//					if(isext) {
//						fname = fname + "." + fext;
//					}					
				}				
				final byte[] prefix =
						(isext && !wasPseudo) ?
														// + "." + fext
								(fname.substring(0, cindex) ).getBytes(UTF8)
								
							:
								(cindex==-1 ? fname : fname.substring(0, cindex)).getBytes(UTF8);
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
					final byte[] key = Util.clean(tag.substring(0, eind)).getBytes(UTF8);
					if(key.length==0) continue;
					final byte[] value = Util.clean(tag.substring(eind+1)).getBytes(UTF8);
					if(value.length==0) continue;
					if(Arrays.equals(APP_TAG_BYTES, key)) {
						hasAppTag = ONE_BYTE;
					} else if(Arrays.equals(HOST_TAG_BYTES, key)) {
						hasHostTag = ONE_BYTE;
					}
					int tagLength = key.length + value.length + TAG_OVERHEAD; 
					buff.putInt(tagLength).put(QT).put(key).put(QT).put(COLON).put(QT).put(value).put(QT);
					actualTagCount++;						
					totalLength += tagLength;
				}
				if(extraTags!=null && !extraTags.isEmpty()) {
					for(Map.Entry<String, String> entry: extraTags.entrySet()) {
						final byte[] key = Util.clean(entry.getKey()).getBytes(UTF8);
						final byte[] value = Util.clean(entry.getValue()).getBytes(UTF8);
						if(key.length==0 || value.length==0) continue;
						if(hasAppTag!=ONE_BYTE && Arrays.equals(APP_TAG_BYTES, key)) {
							hasAppTag = ONE_BYTE;
						} else if(hasHostTag!=ONE_BYTE && Arrays.equals(HOST_TAG_BYTES, key)) {
							hasHostTag = ONE_BYTE;
						}
						int tagLength = key.length + value.length + TAG_OVERHEAD;
						buff.putInt(tagLength).put(QT).put(key).put(QT).put(COLON).put(QT).put(value).put(QT);
						actualTagCount++;						
						totalLength += tagLength;
					}
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
		hashCode = OTMETRIC_HASH.hashObject(this, OTMETRIC_FUNNEL);
	}
	
	
	
	
	
	public static void main(String[] args) {
		log("OTMetric Test");
		log("Creating OTM for [" + ManagementFactory.CLASS_LOADING_MXBEAN_NAME + "] (" + ManagementFactory.CLASS_LOADING_MXBEAN_NAME.getBytes(UTF8).length + ")");
		OTMetric otm = new OTMetric(ManagementFactory.CLASS_LOADING_MXBEAN_NAME);
		printDetails(otm);
		for(GarbageCollectorMXBean gc: ManagementFactory.getGarbageCollectorMXBeans()) {
			otm = new OTMetric(gc.getObjectName().toString());
			printDetails(otm);
		}
		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice");
		printDetails(otm);
		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice", null, "p75");
		printDetails(otm);
		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,host=AA");
		printDetails(otm);
		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,app=XX");
		printDetails(otm);
		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,host=AA,app=XX");
		printDetails(otm);
		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,host=AA,app=XX", null, "p75");
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
		b.append("\n\thashCode:").append(otm.hashCode());
		b.append("\n\tlongHashCode:").append(otm.longHashCode());
		b.append("\n\tJSON:").append(otm.toJSON(System.currentTimeMillis(), 0));
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
		return new String(bytes, UTF8);
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
			final int tagLength = buff.getInt();
			String tagPair = readString(buff, tagLength);
			int index = tagPair.indexOf(':');
			if(index==-1) {
				//log.warn("")  // FIXME
				continue;
			}
			tmap.put(tagPair.substring(1, index-1), tagPair.substring(index+2, tagLength-1));
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
		b.append(readString(buff, prefixLength));
		b.append(getTags().toString());
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
		return new String(content, UTF8);
	}
	
	private static final byte[] METRIC_OPENER = "{\"metric\":\"".getBytes(UTF8);
	private static final byte[] CLOSER = "\",".getBytes(UTF8);
	private static final byte[] TS_OPENER = "\"timestamp\":".getBytes(UTF8);
	private static final byte[] COMMA = ",".getBytes(UTF8);
	private static final byte[] VALUE_OPENER = "\"value\":".getBytes(UTF8);
	private static final byte[] TAGS_OPENER = "\"tags\":{".getBytes(UTF8);
	private static final byte[] METRIC_CLOSER = "}}".getBytes(UTF8);
	private static final byte[] COLON = ":".getBytes(UTF8);
	private static final byte[] QT = "\"".getBytes(UTF8);
	
	static final int TAG_OVERHEAD = (QT.length*4) + COLON.length;
	
	private static final DynamicByteBufferBackedChannelBufferFactory factory = new DynamicByteBufferBackedChannelBufferFactory();
	
	public String toJSON(final long timestamp, final Object value) {
		final ChannelBuffer cbuff = factory.getBuffer(16);
		System.out.println("BUFF INITIAL CAP:" + cbuff.capacity());
		String s = toJSON(timestamp, value, cbuff, false).toString(UTF8);
		System.out.println("BUFF CAP:" + cbuff.capacity() + ", LIMIT:" + cbuff.writerIndex());
		return s;
	}
	
	
    /**
     * Renders this metric into the passed buffer, or a new buffer if the passed buffer is null
     * @param timestamp The timestamp to render
     * @param value The value to render
     * @param cbuff The buffer to render into
     * @param appendComma true to append a command (if we're generating an array), false otherwise
     * @return The buffer to render into
     */
    public ChannelBuffer toJSON(final long timestamp, final Object value, final ChannelBuffer cbuff, final boolean appendComma) {
    	final ByteBuffer nbuff = nameBuffer.duplicate();
    	final int tagCount = nameBuffer.getInt(TAG_COUNT_OFFSET);
    	cbuff.writeBytes(METRIC_OPENER);
    	nbuff.position(FTAG_SIZE_OFFSET);
		transfer(cbuff, nbuff, nameBuffer.getInt(MN_SIZE_OFFSET)); // metric name
		cbuff.writeBytes(CLOSER);
		cbuff.writeBytes(TS_OPENER);
		cbuff.writeBytes(Long.toString(timestamp).getBytes(UTF8));
		cbuff.writeBytes(COMMA);
		cbuff.writeBytes(VALUE_OPENER);
		cbuff.writeBytes(value.toString().getBytes(UTF8));
		cbuff.writeBytes(COMMA);
		cbuff.writeBytes(TAGS_OPENER);
		boolean tagsWritten = false;
		if(!hasAppTag()) {
			transfer(cbuff, AgentName.getInstance().getAgentNameAppTagBuffer(), AgentName.getInstance().getAgentNameAppTagBuffer().capacity());
			cbuff.writeBytes(COMMA);
			tagsWritten = true;
		}
		if(!hasHostTag()) {
			transfer(cbuff, AgentName.getInstance().getAgentNameHostTagBuffer(), AgentName.getInstance().getAgentNameHostTagBuffer().capacity());
			cbuff.writeBytes(COMMA);
			tagsWritten = true;
		}
		if(tagCount!=0) {
			for(int i = 0; i < tagCount; i++) {
				final int tagLength = nbuff.getInt();
				transfer(cbuff, nbuff, tagLength);
				cbuff.writeBytes(COMMA);
			}
			cbuff.writerIndex(cbuff.writerIndex()-1);
		} else {
			if(tagsWritten) {
				cbuff.writerIndex(cbuff.writerIndex()-1);
			}			
		}
		
		cbuff.writeBytes(METRIC_CLOSER);
		if(appendComma) {
			cbuff.writeBytes(COMMA);
		}
    	return cbuff;
    }
    
    /**
     * @param target
     * @param source
     * @param bytes
     */
    protected void transfer(final ChannelBuffer target, final ByteBuffer source, final int bytes) {
    	final int limit = source.limit();
    	try {
    		source.limit(source.position() + bytes);
    		target.writeBytes(source);
    	} finally {
    		source.limit(limit);
    	}
    }
    
  
    
//    /**
//     * Writes the passed set of metrics into the passed buffer
//     * @param metrics The metrics to render
//     * @param buffer The buffer to write to
//     * @return The number of metrics written
//     */
//    public static int writeToBuffer(final Set<OTMetric> metrics, final ChannelBuffer buffer) {
//    	if(metrics==null || metrics.isEmpty()) return 0;
//    	buffer.writeBytes(JSON_OPEN_ARR);
//    	final int last = metrics.size()-1;
//    	int index = 0;
//    	for(OTMetric m: metrics) {
////    		buffer.writeBytes(m.toJSON().toString().getBytes(UTF8));
//    		if(index != last) {
//    			buffer.writeBytes(JSON_COMMA);
//    		}
//    		index++;
//    	}
//    	buffer.writeBytes(JSON_CLOSE_ARR);
//    	return index;
//    }

	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return hashCode.hashCode();
	}
	
	/**
	 * Returns the 64 bit hash code for the OTMetric name
	 * @return the 64 bit hash code for the OTMetric name
	 */
	public long longHashCode() {
		return hashCode.padToLong();
	}
	
	/**
	 * {@inheritDoc}
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		OTMetric other = (OTMetric) obj;
		if (nameBuffer == null) {
			if (other.nameBuffer != null)
				return false;
		} else if (!nameBuffer.equals(other.nameBuffer))
			return false;
		return true;
	}
	

	
	

}


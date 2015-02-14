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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.jboss.netty.buffer.ChannelBuffer;

import com.google.common.hash.Funnel;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.hash.PrimitiveSink;
import com.heliosapm.opentsdb.client.name.AgentName;
import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder.CapturingPrimitiveSink;
import com.heliosapm.opentsdb.client.opentsdb.opt.CHMetric;
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
	
	static final short LONG_HASH_CODE = 0;						// 8 bytes
	static final short HASH_CODE = LONG_HASH_CODE + 8;			// 4 bytes
	static final short LAST_TRACE_TIME = HASH_CODE + 4;			// 8 bytes
	static final short HAS_APP_TAG = LAST_TRACE_TIME + 8;		// 1 byte
	static final short HAS_HOST_TAG = HAS_APP_TAG + 1;			// 1 byte
	static final short IS_EXT_TAG = HAS_HOST_TAG + 1;			// 1 byte
	static final short CHMETRIC_TAG = IS_EXT_TAG + 1;			// 1 byte
	static final short SUB_METRIC_TAG = CHMETRIC_TAG + 1;		// 1 byte
	static final short TOTAL_SIZE_OFFSET = SUB_METRIC_TAG + 1; 	// 4 bytes
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
	
	/** UTF8 bytes for a dot */
    static final byte[] DOT = ".".getBytes(UTF8);


	/**
	 * Creates a new OTMetric
	 * @param flatName The plain flat name from the Metric name
	 * @param nprefix The optional prefix which is prefixed to the flat name
	 * @param extension The optional extension which is appended to the TSDB metric name
	 * @param extraTags The optional extra tags to add
	 */
	OTMetric(final String flatName, final String nprefix, final String extension, final Map<String, String> extraTags) {
		if(flatName==null) throw new IllegalArgumentException("The passed flat name was null");
		final SplitFlatName sfn = OTMetric.splitFlatName(flatName);
		sfn.addTags(extraTags);
		final String fprefix = pref(nprefix);
		final String fext = suff(extension);
		final boolean isext = !"".equals(fext);
		final byte[] metricName = (fprefix + sfn.getMetricName() + fext).getBytes(UTF8);
		final Hasher hasherx = OTMETRIC_HASH.newHasher();
		PrimitiveSink hasher = new CapturingPrimitiveSink(hasherx);
		if(!"".equals(fprefix)) {
			hasher.putBytes(fprefix.getBytes(UTF8));
//			hasher.putBytes(DOT);
		}
		hasher.putBytes(sfn.getMetricName().getBytes(UTF8));
		if(isext) {
//			hasher.putBytes(DOT);
			hasher.putBytes(fext.getBytes(UTF8));
		}
		//hasher.putBytes(metricName);
		ByteBuffer buff = null;
		try {
			buff = ByteBuffer.allocateDirect((metricName.length + sfn.estimateSize())*2);
			buff	
			.putLong(0)							// the long hash code, Zero for now
			.putInt(0)							// the java hash code, Zero for now
			.putLong(0)							// the last trace time, Zero
			.put(sfn.hasAppTag() ? ONE_BYTE : ZERO_BYTE)						// App Tag
			.put(sfn.hasHostTag() ? ONE_BYTE : ZERO_BYTE)						// Host Tag
			.put(isext ? ONE_BYTE : ZERO_BYTE)	// Ext flag
			.put(ZERO_BYTE)						// CHMetric type flag
			.put(ZERO_BYTE)						// SubMetric type flag
			.putInt(0)							// Total Length, Zero for now
			.putInt(metricName.length)			// Length of the prefix
			.putInt(sfn.getTags().size())		// Tag count
			.put(metricName);					// The metric name bytes
			
			int totalLength = metricName.length;
			if(!sfn.getTags().isEmpty()) {
				for(Map.Entry<String, String> entry: sfn.getTags().entrySet()) {
					final byte[] key = entry.getKey().getBytes(UTF8);
					final byte[] value = entry.getValue().getBytes(UTF8);				
					int tagLength = key.length + value.length + TAG_OVERHEAD;
					hasher.putBytes(key);
					hasher.putBytes(value);
					buff.putInt(tagLength).put(QT).put(key).put(QT).put(COLON).put(QT).put(value).put(QT);				
					totalLength += tagLength;
				}
			}
			
			final int pos = buff.position();
			final HashCode hashCode = hasherx.hash();
			buff.putLong(LONG_HASH_CODE, hashCode.padToLong());
			buff.putInt(HASH_CODE, hashCode.hashCode());			
			buff.putInt(TOTAL_SIZE_OFFSET, totalLength);
			buff.limit(pos);
			buff.position(0);				
			nameBuffer = ByteBuffer.allocateDirect(buff.limit()).put(buff); //.asReadOnlyBuffer();
		} finally {
			OffHeapFIFOFile.clean(buff);
		}		
	}
	
	private static String suff(final String value) {
		if(value==null) return "";
		String v = value.replace(" ", "");
		if(v.isEmpty()) return "";
		return "." + v;
	}
	
	private static String pref(final String value) {
		if(value==null) return "";
		String v = value.replace(" ", "");
		if(v.isEmpty()) return "";
		return v + ".";
	}
	

	
	
	/**
	 * <p>Title: SplitFlatName</p>
	 * <p>Description: A container to hold the results of a split flat name</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.opentsdb.OTMetric.SplitFlatName</code></p>
	 */
	public static class SplitFlatName {
		/** The parsed tags */
		protected final Map<String, String> tags = new LinkedHashMap<String, String>(3);
		/** The parsed metric name */
		protected final String metricName;
		
		/**
		 * Creates a new SplitFlatName
		 * @param metricName The metric name
		 */
		SplitFlatName(final String metricName) {
			this.metricName = metricName;
		}
		
		/**
		 * Adds a tag of pre-cleaned key values
		 * @param key The tag key
		 * @param value The tag value
		 * @return this SplitFlatName
		 */
		SplitFlatName addTag(final String key, final String value) {
			tags.put(key, value);			
			return this;
		}
		
		/**
		 * Adds a map of tags which will be cleaned
		 * @param tags The map of tags to add
		 * @return this SplitFileName
		 */
		SplitFlatName addTags(final Map<String, String> tags) {
			if(tags!=null && !tags.isEmpty()) {
				for(Map.Entry<String, String> entry: tags.entrySet()) {
					String key = Util.clean(entry.getKey()).replace("'", "").replace('.', '_');
					String value = Util.clean(entry.getValue()).replace("'", "");
					if(!key.isEmpty() && !value.isEmpty()) {
						this.tags.put(key, value);
					}
				}
			}
			return this;
		}
		
		/**
		 * Indicates if the tags contain an app tag
		 * @return true if the tags contain an app tag, false otherwise
		 */
		public boolean hasAppTag() {
			return tags.containsKey(Constants.APP_TAG);
		}
		

		
		/**
		 * Indicates if the tags contain a host tag
		 * @return true if the tags contain a host tag, false otherwise
		 */
		public boolean hasHostTag() {
			return tags.containsKey(Constants.HOST_TAG);
		}
		
		
		/**
		 * Returns the parsed tags
		 * @return the parsed tags
		 */
		public Map<String, String> getTags() {
			return tags;
		}
		
		/**
		 * Returns the parsed metric name
		 * @return the parsed metric name
		 */
		public String getMetricName() {
			return metricName;
		}		
		
		/**
		 * Returns an estimate of the size in bytes of the metric name and tags herein
		 * @return an estimate of the size in bytes 
		 */
		public int estimateSize() {
			return tags.toString().getBytes(UTF8).length + metricName.getBytes(UTF8).length;
		}
		
		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return metricName + (tags.isEmpty() ? "" : tags.toString());
		}
	}
	
	/** An empty SplitFileName const */
	private static final SplitFlatName EMPTY_FLAT_NAME = new SplitFlatName("");
	/** A splitter pattern for non-single quote wrapped dots  */
	public static final Pattern DOT_NQ_SPLITTER = Pattern.compile("[\\.|:|,](?=([^']*'[^']*')*[^']*$)");
	/** A splitter pattern for string rendered maps */
	public static final Pattern MAP_FORMAT_SPLITTER = Pattern.compile("(.*?)\\{(.*?)\\}\\$");
	
	/**
	 * Parses a flat name into a metric name and tags
	 * @param flatName The flat name
	 * @return the parsed SplitFlatName containing the metric name and tags
	 */
	public static SplitFlatName splitFlatName(final String flatName) {
		if(flatName==null || flatName.trim().isEmpty()) return EMPTY_FLAT_NAME;
		final String[] frags = DOT_NQ_SPLITTER.split(flatName.replace(" ", ""));
		if(frags.length==1) return new SplitFlatName(frags[0]);
		final StringBuilder b = new StringBuilder();
		final Map<String, String> tags = new LinkedHashMap<String, String>(3);
		
		for(String s: frags) {
			if(s==null || s.isEmpty()) continue;
			final int eindex = s.indexOf('=');
			if(eindex!=-1) {
				// ====== tag
				tags.put(Util.clean(s.substring(0, eindex).replace("'", "").replace('.', '_')), Util.clean(s.substring(eindex+1).replace("'", "")));
			} else {
				// ====== metric name fragment
				b.append(Util.clean(s)).append(".");
			}
		}
		final int len = b.length();
		if(len>0) {
			b.deleteCharAt(len -1);
		}
		return new SplitFlatName(b.toString()).addTags(tags);
	}
	
	
	
	public static void main(String[] args) {
		log("OTMetric FlatName Test");
		log(splitFlatName("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,host=AA,app=XX"));
		
		log("OTMetric Test");
		log("Creating OTM for [" + ManagementFactory.CLASS_LOADING_MXBEAN_NAME + "] (" + ManagementFactory.CLASS_LOADING_MXBEAN_NAME.getBytes(UTF8).length + ")");
		OTMetric otm = new OTMetric(ManagementFactory.CLASS_LOADING_MXBEAN_NAME);
		printDetails(otm);
		for(GarbageCollectorMXBean gc: ManagementFactory.getGarbageCollectorMXBeans()) {
			otm = new OTMetric(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",name=" + gc.getName());
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
//		
		


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
		b.append("\n\tCHMetric:").append(otm.getCHMetricType());
		b.append("\n\tSubMetric:").append(otm.isSubMetric());
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
	 * Returns the CHMetric type of this OTMetric
	 * @return the CHMetric type of this OTMetric
	 */
	public CHMetric getCHMetricType() {
		return CHMetric.valueOf(nameBuffer.get(CHMETRIC_TAG)); 
	}
	
	/**
	 * Indicates if this is a sub-metric, meaning that it was created by a reporter for a Metric that generates multiple traces.
	 * @return true if this is a sub-metric, false otherwise
	 */
	public boolean isSubMetric() {
		return nameBuffer.get(SUB_METRIC_TAG)==ZERO_BYTE;
	}
	
	/**
	 * Marks this OTMetric as a sub-metric
	 * @return this OTMetric
	 */
	public OTMetric setSubMetric() {
		nameBuffer.put(SUB_METRIC_TAG, ONE_BYTE);
		return this;
	}

	/**
	 * Sets the CHMetric type for this OTMetric
	 * @param chMetric the CHMetric type for this OTMetric
	 * @return this OTMetric
	 */
	public OTMetric setCHMetricType(final CHMetric chMetric) {
		if(chMetric==null) throw new IllegalArgumentException("The passed chMetric was null");
		nameBuffer.put(CHMETRIC_TAG, chMetric.bordinal);
		return this;
	}
	
	/**
	 * Sets the CHMetric type for this OTMetric and marks it as a sub-metric
	 * @param chMetric the CHMetric type for this OTMetric
	 * @return this OTMetric
	 */
	public OTMetric setSubCHMetricType(final CHMetric chMetric) {
		if(chMetric==null) throw new IllegalArgumentException("The passed chMetric was null");
		setCHMetricType(chMetric);
		setSubMetric();
		return this;
	}
	
	
	/**
	 * Returns the last trace time as a UTC long, or 0L if a trace has never occurred
	 * @return the last trace time 
	 */
	public long getLastTraceTime() {
		return nameBuffer.getLong(LAST_TRACE_TIME);
	}
	
	/**
	 * Sets the last trace time as a UTC long
	 * @param traceTime the last trace time 
	 */
	void setTraceTime(final long traceTime) {
		nameBuffer.putLong(LAST_TRACE_TIME, traceTime);
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
		buff.position(TOTAL_SIZE_OFFSET); 
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
     * Transfers the specified number of bytes from the source byte buffer to the target channel buffer,
     * starting at the current position.
     * @param target The target channel buffer
     * @param source The source byte buffer
     * @param bytes The number of bytes to copy
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
    
	/**
	 * Traces a value for this metric
	 * @param timestamp The timestamp
	 * @param value The value
	 */
	public void trace(final long timestamp, final Object value) {
		if(value!=null) {
			setTraceTime(timestamp);
			MetricBuilder.trace(this, timestamp, value);
		}
	}
	
	/**
	 * Traces a value for the this metric using the configured clock for the timestamp
	 * @param value The value
	 */
	public void trace(final Object value) {
		if(value!=null) {
			setTraceTime(MetricBuilder.trace(this, value));			
		}
	}
	
	/**
	 * Traces a value for the this metric using the configured clock for the timestamp
	 * @param value The value
	 */
	public void trace(final long value) {
		setTraceTime(MetricBuilder.trace(this, value));		
	}
  
	/**
	 * Traces a value for the this metric using the configured clock for the timestamp
	 * @param value The value
	 */
	public void trace(final int value) {
		setTraceTime(MetricBuilder.trace(this, value));		
	}
	
	/**
	 * Traces a value for the this metric using the configured clock for the timestamp
	 * @param value The value
	 */
	public void trace(final double value) {
		setTraceTime(MetricBuilder.trace(this, value));		
	}
	
	/**
	 * Traces a value for the this metric using the configured clock for the timestamp
	 * @param value The value
	 */
	public void trace(final float value) {
		setTraceTime(MetricBuilder.trace(this, value));		
	}
	
	/**
	 * Traces a value for the this metric using the configured clock for the timestamp
	 * @param value The value
	 */
	public void trace(final short value) {
		setTraceTime(MetricBuilder.trace(this, value));		
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
		return nameBuffer.getInt(HASH_CODE);
	}
	
	/**
	 * Returns the 64 bit hash code for the OTMetric name
	 * @return the 64 bit hash code for the OTMetric name
	 */
	public long longHashCode() {
		return nameBuffer.getLong(LONG_HASH_CODE);
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
		return other.longHashCode()==longHashCode();
	}
}


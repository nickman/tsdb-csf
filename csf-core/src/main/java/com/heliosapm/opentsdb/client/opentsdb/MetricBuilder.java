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

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.ObjectName;

import org.jboss.netty.buffer.ChannelBuffer;

import com.codahale.metrics.Clock;
import com.google.common.hash.Funnel;
import com.google.common.hash.PrimitiveSink;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric.SplitFlatName;
import com.heliosapm.opentsdb.client.opentsdb.opt.CHMetric;
import com.heliosapm.opentsdb.client.opentsdb.opt.LongIdOTMetricCache;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement;
import com.heliosapm.opentsdb.client.opentsdb.opt.SubMetric;
import com.heliosapm.opentsdb.client.util.DynamicByteBufferBackedChannelBufferFactory;
import com.heliosapm.opentsdb.client.util.Util;


/**
 * <p>Title: MetricBuilder</p>
 * <p>Description: A fluent style metric builder for {@link OTMetric}s</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.MetricBuilder</code></p>
 */

public class MetricBuilder {
	/** The base metric name */
	private final String name;
	/** An optional metric name prefix */
	private String prefix = null;
	/** An optional metric name suffix */
	private String extension = null;
	/** An existing OTMetric to base a new Metric on */
	private OTMetric otMetricBase = null;
	/** Optional tags to be appended */
	private Map<String, String> tags = null;
	
	/** The parent id of the metric to build */
	private long parentId = 0L;
	/** The measurement mask of the metric to build */
	private int measurementMask = 0;
	/** The sub-metric mask of the metric to build */
	private int subMetricMask = 0;
	/** The CHMetric type of the metric to build */
	private byte chMetric = 0;
	

	
	

	/** The buffer factory for allocating buffers to stream metrics out to the tracer */
    private static final DynamicByteBufferBackedChannelBufferFactory bufferFactory = new DynamicByteBufferBackedChannelBufferFactory(128);
    
    /** A map of OTMetric groups keyed by the group name */
    private static final Map<String, Set<OTMetric>> groups = new ConcurrentHashMap<String, Set<OTMetric>>();

    private static final MetricBuffer METRIC_BUFFER = new MetricBuffer();
    
    /** The clock for generating timestamps */
    private static Clock clock = null;
    
	/** UTF8 bytes for a dot */
    static final byte[] DOT = ".".getBytes(UTF8);

    
	/** The hashing funnel to get an {@link OTMetric#longHashCode()} from a MetricBuilder */
	public static final Funnel<MetricBuilder> OTMETRIC_BUILDER_FUNNEL = new Funnel<MetricBuilder>() {
		private static final long serialVersionUID = -216399666276403583L;
		private final Charset CHARSET = Charset.defaultCharset();
		/**
		 * {@inheritDoc}
		 * @see com.google.common.hash.Funnel#funnel(java.lang.Object, com.google.common.hash.PrimitiveSink)
		 */
		@Override
		public void funnel(final MetricBuilder builder, final PrimitiveSink sink) {
			PrimitiveSink into = new CapturingPrimitiveSink(sink);
			if(builder.prefix!=null) {
				into.putBytes(builder.prefix.getBytes(UTF8));
				into.putBytes(DOT);
			}
			into.putBytes(builder.name.getBytes(UTF8));
			if(builder.extension!=null) {
				into.putBytes(DOT);
				into.putBytes(builder.extension.getBytes(UTF8));
			}
			if(builder.tags!=null && !builder.tags.isEmpty()) {
				for(Map.Entry<String, String> entry: builder.tags.entrySet()) {
					into.putBytes(entry.getKey().getBytes(UTF8));
					into.putBytes(entry.getValue().getBytes(UTF8));
				}
			} 
			System.err.println("BLD:[" + into.toString() + "]");
	     }		
	};
	
	static class CapturingPrimitiveSink implements PrimitiveSink {
		final PrimitiveSink delegate;
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		public CapturingPrimitiveSink(PrimitiveSink delegate) {
			super();
			this.delegate = delegate;
		}
		
		public String toString() {
			try { baos.flush(); } catch (Exception ex) { throw new RuntimeException(ex); }			
			return new String(baos.toByteArray(), UTF8);
		}

		/**
		 * @param b
		 * @return
		 * @see com.google.common.hash.PrimitiveSink#putByte(byte)
		 */
		public PrimitiveSink putByte(byte b) {
			return delegate.putByte(b);
		}

		/**
		 * @param bytes
		 * @return
		 * @see com.google.common.hash.PrimitiveSink#putBytes(byte[])
		 */
		public PrimitiveSink putBytes(byte[] bytes) {
			try { baos.write(bytes); } catch (Exception ex) { throw new RuntimeException(ex); }
			return delegate.putBytes(bytes);
		}

		/**
		 * @param bytes
		 * @param off
		 * @param len
		 * @return
		 * @see com.google.common.hash.PrimitiveSink#putBytes(byte[], int, int)
		 */
		public PrimitiveSink putBytes(byte[] bytes, int off, int len) {
			return delegate.putBytes(bytes, off, len);
		}

		/**
		 * @param s
		 * @return
		 * @see com.google.common.hash.PrimitiveSink#putShort(short)
		 */
		public PrimitiveSink putShort(short s) {
			return delegate.putShort(s);
		}

		/**
		 * @param i
		 * @return
		 * @see com.google.common.hash.PrimitiveSink#putInt(int)
		 */
		public PrimitiveSink putInt(int i) {
			return delegate.putInt(i);
		}

		/**
		 * @param l
		 * @return
		 * @see com.google.common.hash.PrimitiveSink#putLong(long)
		 */
		public PrimitiveSink putLong(long l) {
			return delegate.putLong(l);
		}

		/**
		 * @param f
		 * @return
		 * @see com.google.common.hash.PrimitiveSink#putFloat(float)
		 */
		public PrimitiveSink putFloat(float f) {
			return delegate.putFloat(f);
		}

		/**
		 * @param d
		 * @return
		 * @see com.google.common.hash.PrimitiveSink#putDouble(double)
		 */
		public PrimitiveSink putDouble(double d) {
			return delegate.putDouble(d);
		}

		/**
		 * @param b
		 * @return
		 * @see com.google.common.hash.PrimitiveSink#putBoolean(boolean)
		 */
		public PrimitiveSink putBoolean(boolean b) {
			return delegate.putBoolean(b);
		}

		/**
		 * @param c
		 * @return
		 * @see com.google.common.hash.PrimitiveSink#putChar(char)
		 */
		public PrimitiveSink putChar(char c) {
			return delegate.putChar(c);
		}

		/**
		 * @param charSequence
		 * @return
		 * @see com.google.common.hash.PrimitiveSink#putUnencodedChars(java.lang.CharSequence)
		 */
		public PrimitiveSink putUnencodedChars(CharSequence charSequence) {
			return delegate.putUnencodedChars(charSequence);
		}

		/**
		 * @param charSequence
		 * @param charset
		 * @return
		 * @see com.google.common.hash.PrimitiveSink#putString(java.lang.CharSequence, java.nio.charset.Charset)
		 */
		public PrimitiveSink putString(CharSequence charSequence,
				Charset charset) {
			return delegate.putString(charSequence, charset);
		}
		
	}
    
    
	
	/**
	 * Creates a new metric builder
	 * @param name The base metric name
	 * @return the metric builder
	 */
	public static MetricBuilder metric(final String name) {
		return new MetricBuilder(nvl(name, "base metric name"));
	}
	
	/**
	 * Creates a new metric builder from an {@link ObjectName}.
	 * @param objectName The {@link ObjectName} to build from
	 * @return the metric builder
	 */
	public static MetricBuilder metric(final ObjectName objectName) {
		if(objectName==null) throw new IllegalArgumentException("The passed object name was null");
		if(objectName.isDomainPattern()) throw new IllegalArgumentException("The passed object name [" + objectName + "] is a domain wildcard");
		final MetricBuilder builder = new MetricBuilder(objectName.getDomain());
		final Map<String, String> props = objectName.getKeyPropertyList();
		for(Map.Entry<String, String> entry: props.entrySet()) {
			if(objectName.isPropertyValuePattern(entry.getKey())) continue;
			builder.tag(entry.getKey(), entry.getValue());
		}
		return builder;
	}
	
	/**
	 * Creates a new metric builder from an existing {@link OTMetric}.
	 * @param otMetric The {@link OTMetric} to build from
	 * @param makeParent If true, the passed OTMetric will be made the parent of the built metric.
	 * @return the metric builder
	 */
	public static MetricBuilder metric(final OTMetric otMetric, final boolean makeParent) {
		if(otMetric==null) throw new IllegalArgumentException("The passed otMetric was null");
		MetricBuilder mb = new MetricBuilder(otMetric.getMetricName());
		if(makeParent) {
			mb.parent(otMetric.longHashCode());
		}
		mb.tags(otMetric.getTags());
		return mb;
	}
	
	/**
	 * Creates a new metric builder from an existing {@link OTMetric}.
	 * @param otMetric The {@link OTMetric} to build from
	 * @return the metric builder
	 */
	public static MetricBuilder metric(final OTMetric otMetric) {
		return metric(otMetric, false);
	}
	
	/**
	 * Creates a new MetricBuilder
	 * @param name The base metric name
	 */
	private MetricBuilder(final String name) {
		final SplitFlatName sfn = OTMetric.splitFlatName(name);
		this.name = sfn.getMetricName();
		if(!sfn.getTags().isEmpty()) {
			if(tags==null) tags = new LinkedHashMap<String, String>(3);
			this.tags.putAll(sfn.getTags());
		}		
	}
	
	/**
	 * Adds a prefix to the metric name builder
	 * @param prefix A prefix to prepend to the base metric name
	 * @return this builder
	 */
	public MetricBuilder pre(final String prefix) {
		this.prefix = nvl(prefix, "prefix");
		return this;
	}
	
	// parentId = 0L; 	measurementMask = 0; subMetricMask = 0; chMetric = 0;
	
	/**
	 * Sets the CHMetric type of the metric to build
	 * @param chMetrics The chMetric members to build the mask with
	 * @return this builder
	 */
	public MetricBuilder chMetricType(final CHMetric...chMetrics) {
		this.chMetric = CHMetric.getMaskFor(chMetrics);
		return this;
	}

	
	/**
	 * Sets the CHMetric type of the metric to build
	 * @param chMetric The chMetric byte
	 * @return this builder
	 */
	public MetricBuilder chMetricType(final byte chMetric) {
		this.chMetric = chMetric;
		return this;
	}
	
	/**
	 * Sets the sub-metric mask of the metric to build
	 * @param subMetricMask The sub-metric mask
	 * @return this builder
	 */
	public MetricBuilder subMetric(final int subMetricMask) {
		this.subMetricMask = subMetricMask;
		return this;
	}
	
	/**
	 * Sets the sub-metric mask of the metric to build
	 * @param subMetrics The sub-metrics to build a mask from
	 * @return this builder
	 */
	public MetricBuilder subMetric(final SubMetric...subMetrics) {
		this.subMetricMask = SubMetric.getMaskFor(subMetrics);
		return this;
	}

	/**
	 * Sets the measurement mask of the metric to build
	 * @param measurementMask The measurement mask
	 * @return this builder
	 */
	public MetricBuilder measurement(final int measurementMask) {
		this.measurementMask = measurementMask;
		return this;
	}

	/**
	 * Sets the measurement mask of the metric to build
	 * @param measurements The measurements to build a mask from
	 * @return this builder
	 */
	public MetricBuilder measurement(final Measurement...measurements) {
		this.measurementMask = Measurement.getMaskFor(measurements);
		return this;
	}
	
	/**
	 * Sets the parentId of the metric to build
	 * @param parentId The parent id
	 * @return this builder
	 */
	public MetricBuilder parent(final long parentId) {
		this.parentId = parentId;
		return this;
	}
	
	/**
	 * Adds a suffix to the metric name builder
	 * @param extension A suffix to append to the base metric name
	 * @return this builder
	 */
	public MetricBuilder ext(final String extension) {
		this.extension = nvl(extension, "extension");
		return this;
	}
	
	/**
	 * Adds a tag to the metric name builder
	 * @param key The tag key
	 * @param value The tag value
	 * @return this metric builder
	 */
	public MetricBuilder tag(final String key, final Object value) {
		if(tags==null) tags = new LinkedHashMap<String, String>(3);
		tags.put(nvl(key, "tag key"), nvl(value.toString(), "tag value"));
		return this;
	}
	
	/**
	 * Adds tags parsed from the passed key value pairs in the format <b><code>KEY=VALUE</code></b>. 
	 * @param keyValuePairs An array of key value pairs
	 * @return this builder
	 */
	public MetricBuilder tags(final String...keyValuePairs) {
		if(keyValuePairs!=null && keyValuePairs.length > 0) {
			final Map<String, String> map = new LinkedHashMap<String, String>(keyValuePairs.length);
			for(String s: keyValuePairs) {
				int index = s.indexOf('=');
				if(index < 1) throw new IllegalArgumentException("One or more invalid KeyValuePairs:" + Arrays.toString(keyValuePairs));
				map.put(s.substring(0, index), s.substring(index+1));
			}
			try {
				tags(map);
			} catch (Exception ex) {
				throw new IllegalArgumentException("One or more invalid KeyValuePairs:" + Arrays.toString(keyValuePairs));
			}
		}
		return this;
	}
	
	/**
	 * Adds a map of tags to the metric name builder.
	 * Ignores a null or empty map of tags
	 * @param tags A map of tag values
	 * @return this metric builder
	 */
	public MetricBuilder tags(final Map<String, String> tags) {
		if(tags==null || tags.isEmpty()) return this;
		for(Map.Entry<String, String> entry: tags.entrySet()) {
			tag(entry.getKey(), entry.getValue());
		}
		return this;
	}
	
	/**
	 * Adds a map of tags to the metric name builder.
	 * Throws an {@link IllegalArgumentException} if the map is null or empty.
	 * @param tags A map of tag values
	 * @return this metric builder
	 */
	public MetricBuilder ntags(final Map<String, String> tags) {
		if(tags==null || tags.isEmpty()) throw new IllegalArgumentException("The passed tag map was null or empty");
		for(Map.Entry<String, String> entry: tags.entrySet()) {
			tag(entry.getKey(), entry.getValue());
		}
		return this;
	}
	
	
	/**
	 * Returns the long hash code of the builder in its current state.
	 * If an OTMetric were built in the current state of this builder,
	 * it would have the same long hash code.
	 * @return the long hash code 
	 */
	public long longHashCode() {
		return OTMetric.OTMETRIC_HASH.hashObject(this, OTMETRIC_BUILDER_FUNNEL).padToLong();
	}
	
	/**
	 * Builds an OTMetric from this builder
	 * @return an OTMetric
	 */
	public OTMetric build() {
		return build(null);
	}
	
	/**
	 * Builds the OTMeric but does not add it to the OTMetricCache
	 * @return the built OTMetric
	 */
	public OTMetric buildNoCache() {
		return buildNoCache(null);
	}
	
	// parentId = 0L; 	measurementMask = 0; subMetricMask = 0; chMetric = 0;
	
	/**
	 * Builds the OTMeric but does not add it to the OTMetricCache
	 * @param groupName An optional group name that groups all the created metrics into a set 
	 * which can be retrieved after all the group's OTMetrics have been created.
	 * @return the built OTMetric
	 */
	public OTMetric buildNoCache(final String groupName) {
		final OTMetric otm = new OTMetric(name, prefix, extension, tags);
		setMasks(otm);
		if(groupName!=null) {
			Set<OTMetric> groupSet = groups.get(groupName);
			if(groupSet==null) {
				synchronized(groups) {
					groupSet = groups.get(groupName);
					if(groupSet==null) {
						groupSet = new CopyOnWriteArraySet<OTMetric>();
						groups.put(groupName, groupSet);						
					}
				}
			}
			groupSet.add(otm);
		}		
		return otm;
	}

	/**
	 * @param otm
	 */
	private void setMasks(final OTMetric otm) {
		if(parentId!=0) {
			otm.setParentMetric(parentId);
			if(measurementMask!=0) otm.setMeasurement(measurementMask);
			if(subMetricMask!=0) otm.setSubMetric(subMetricMask);
		}
		if(chMetric!=0) otm.setCHMetricType(chMetric);
	}
	
	/**
	 * Builds an OTMetric from this builder
	 * @param groupName An optional group name that groups all the created metrics into a set 
	 * which can be retrieved after all the group's OTMetrics have been created.
	 * @return an OTMetric
	 */
	public OTMetric build(final String groupName) {
		final OTMetric otMetric = OTMetricCache.getInstance().getOTMetric(name, prefix, extension, tags);
		setMasks(otMetric);
		if(groupName!=null) {
			Set<OTMetric> groupSet = groups.get(groupName);
			if(groupSet==null) {
				synchronized(groups) {
					groupSet = groups.get(groupName);
					if(groupSet==null) {
						groupSet = new CopyOnWriteArraySet<OTMetric>();
						groups.put(groupName, groupSet);						
					}
				}
			}
			groupSet.add(otMetric);
		}
		return otMetric;
	}
	
	/**
	 * Builds an opt cache backed OTMetric from this builder
	 * @return an OTMetric
	 */
	public OTMetric optBuild() {
		return optBuild(null);
	}
	
	/**
	 * Builds an opt cache backed OTMetric from this builder
	 * @param groupName An optional group name that groups all the created metrics into a set 
	 * which can be retrieved after all the group's OTMetrics have been created.
	 * @return an OTMetric
	 */
	public OTMetric optBuild(final String groupName) {
		final OTMetric otMetric = LongIdOTMetricCache.getInstance().getOTMetric(this);
		setMasks(otMetric);
		if(groupName!=null) {
			Set<OTMetric> groupSet = groups.get(groupName);
			if(groupSet==null) {
				synchronized(groups) {
					groupSet = groups.get(groupName);
					if(groupSet==null) {
						groupSet = new CopyOnWriteArraySet<OTMetric>();
						groups.put(groupName, groupSet);						
					}
				}
			}
			groupSet.add(otMetric);
//			System.err.println("Adding to group [" + groupName + "]: [" + otMetric.toString() + "]");
		}
		return otMetric;
	}
	
	
	/**
	 * Returns the named OTMetric group
	 * @param groupName The group name
	 * @param remove true to remove the group, false to leave it in the cache so it can keep accumulating
	 * @return the group set or null if the named group does not exist
	 */
	public static Set<OTMetric> getGroupSet(final String groupName, final boolean remove) {
		if(groupName==null) throw new IllegalArgumentException("The passed group name was null");
		if(remove) return groups.remove(groupName);
		return groups.get(groupName);
	}

	/**
	 * Removes and returns the named OTMetric group
	 * @param groupName The group name
	 * @return the group set or null if the named group does not exist
	 */
	public static Set<OTMetric> getGroupSet(final String groupName) {
		return getGroupSet(groupName, true);
	}
	
	/**
	 * Builds the OTMetric and traces the passed value 
	 * @param timestamp The timestamp of the metric
	 * @param value The value to trace
	 * @return the built OTMetric
	 */
	public OTMetric trace(final long timestamp, final Object value) {
		final OTMetric otm = build();
		trace(otm, timestamp, value);
		return otm;
	}
	
	/**
	 * Builds the OTMetric and traces the passed value using the configured clock for the timestamp 
	 * @param value The value to trace
	 * @return the built OTMetric
	 */
	public OTMetric trace(final Object value) {
		final OTMetric otm = build();
		trace(otm, value);
		return otm;
	}
	
	
	/**
	 * Traces a value for the passed metric
	 * @param metric The OTMetric to trace
	 * @param timestamp The timestamp
	 * @param value The value
	 */
	public static void trace(final OTMetric metric, final long timestamp, final Object value) {
		if(metric==null) throw new IllegalArgumentException("The passed metric was null");
		if(value==null) throw new IllegalArgumentException("The passed value was null");
		METRIC_BUFFER.append(metric, timestamp, value);
//		final ChannelBuffer chBuff = bufferFactory.getBuffer();
//		metric.toJSON(timestamp, value, chBuff, false);
//		OpenTsdb.getInstance().send(chBuff, 1);		
	}
	
	/**
	 * Traces a value for the passed metric using the configured clock for the timestamp
	 * @param metric The OTMetric to trace
	 * @param value The value
	 * @return The time recorded
	 */
	public static long trace(final OTMetric metric, final Object value) {
		final long time = getClock().getTime();
		// ####    FIXME
		// Temporary hack to avoid sending 2 values for the same timestamp
		// ####
		final long ctime = metric.getLastTraceTime();
		if(time==ctime) return ctime; 
		trace(metric, time, value);
		return time;
	}
	
	

	
	
	private static String nvl(final String s, final String name) {
		if(s==null) throw new IllegalArgumentException("The passed " + name + " was null");
		final String _s = Util.clean(s);
		if(_s.isEmpty()) throw new IllegalArgumentException("The passed " + name + " was empty");
		return _s;
		
	}
	
	private static Clock getClock() {
		if(clock==null) {
			if(ConfigurationReader.confBool(Constants.PROP_TIME_IN_SEC, Constants.DEFAULT_TIME_IN_SEC)) {
				clock = EpochClock.INSTANCE;		
			} else {
				clock = Clock.UserTimeClock.defaultClock();
			}
		}
		return clock;
	}
	
	static class MetricBuffer implements Runnable {
		protected final AtomicInteger counter = new AtomicInteger(0);
		protected final int sizeThreshold = 100;
		protected final long timeThreshold = 5000;
		protected ChannelBuffer metricBuffer = bufferFactory.getBuffer(4096);
		
		MetricBuffer() {
			metricBuffer.writeBytes(OTMetric.JSON_OPEN_ARR);
			Threading.getInstance().schedule(this, timeThreshold);
		}
		
		public void run() {
			try {
				synchronized(counter) {
					final int count = counter.get();
					if(count>0) {
						flush(count);
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace(System.err);
			}
		}
		
		void append(final OTMetric otm, final long timestamp, final Object value) {			
			synchronized(counter) {
				final int count = counter.incrementAndGet();
				otm.toJSON(timestamp, value, metricBuffer, true);
				if(count < sizeThreshold) {
					return;
				}
				flush(count);
			}
		}
		
		void flush(final int count) {
			try {
				metricBuffer.writerIndex(metricBuffer.writerIndex()-OTMetric.JSON_COMMA.length);
				metricBuffer.writeBytes(OTMetric.JSON_CLOSE_ARR);
//				try {
//					System.err.println(new JSONArray(metricBuffer.toString(UTF8)).toString(1));
//					System.err.println("Sending " + count + " metrics");
//				} catch (Exception ex) {}
				OpenTsdb.getInstance().send(metricBuffer, count);
				metricBuffer = bufferFactory.getBuffer(4096);
				metricBuffer.writeBytes(OTMetric.JSON_OPEN_ARR);
				counter.set(0);
			} catch (Exception ex) {
				ex.printStackTrace(System.err);
			}
		}
	}

}

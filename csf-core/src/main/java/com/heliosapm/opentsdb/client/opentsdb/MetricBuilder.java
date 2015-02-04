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

import javax.management.ObjectName;

import org.jboss.netty.buffer.ChannelBuffer;

import com.codahale.metrics.Clock;
import com.google.common.hash.Funnel;
import com.google.common.hash.PrimitiveSink;
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
	/** Optional tags to be appended */
	private Map<String, String> tags = null;
	
	/** The buffer factory for allocating buffers to stream metrics out to the tracer */
    private static final DynamicByteBufferBackedChannelBufferFactory bufferFactory = new DynamicByteBufferBackedChannelBufferFactory(128);

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
	 * Creates a new MetricBuilder
	 * @param name The base metric name
	 */
	private MetricBuilder(final String name) {
		this.name = Util.clean(name);
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
	public MetricBuilder tag(final String key, final String value) {
		if(tags==null) tags = new LinkedHashMap<String, String>(3);
		tags.put(nvl(key, "tag key"), nvl(value, "tag value"));
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
	 * Adds a map of tags to the metric name builder
	 * @param tags A map of tag values
	 * @return this metric builder
	 */
	public MetricBuilder tags(final Map<String, String> tags) {
		if(tags==null) throw new IllegalArgumentException("The passed tag map was null");
		if(!tags.isEmpty()) {
			for(Map.Entry<String, String> entry: tags.entrySet()) {
				tag(entry.getKey(), entry.getValue());
			}
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
		return OTMetricCache.getInstance().getOTMetric(name, prefix, extension, tags);
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
		final ChannelBuffer chBuff = bufferFactory.getBuffer();
		metric.toJSON(timestamp, value, chBuff, false);
		OpenTsdb.getInstance().send(chBuff, 1);		
	}
	
	/**
	 * Traces a value for the passed metric using the configured clock for the timestamp
	 * @param metric The OTMetric to trace
	 * @param value The value
	 */
	public static void trace(final OTMetric metric, final Object value) {
		trace(metric, getClock().getTime(), value);
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
				clock = EpochClock.INSTANCE;;			
			} else {
				clock = Clock.UserTimeClock.defaultClock();
			}
		}
		return clock;
	}

}

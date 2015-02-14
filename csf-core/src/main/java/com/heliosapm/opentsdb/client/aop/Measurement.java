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
package com.heliosapm.opentsdb.client.aop;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.CachedGauge;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import com.heliosapm.opentsdb.client.opentsdb.Constants;
import com.heliosapm.opentsdb.client.opentsdb.OffHeapFIFOFile;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: Measurement</p>
 * <p>Description: Functional enumeration of thread execution metrics that can be collected</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.collector.Measurement</code></p>
 */

public enum Measurement {
	/** Elapsed time in ns.  */
	ELAPSED(false, new ElapsedMeasurement()),
	/** Elapsed CPU time in ns. */
	CPU(false, new CpuMeasurement()),
	/** Elapsed User Mode CPU time in ns. */
	UCPU(false, new UserCpuMeasurement()),
	/** Number of thread waits */	
	WAIT(true, new WaitCountMeasurement()),
	/** Number of thread blocks */
	BLOCK(true, new BlockCountMeasurement()),
	/** Thread wait time in ms. */
	WAITTIME(true, new WaitTimeMeasurement()),
	/** Thread block time in ms. */
	BLOCKTIME(true, new BlockTimeMeasurement()),
	/** Concurrent threads with entry/exit block */
	CONCURRENT(false, null),  // FIXME: !!!
	/** Total invocation count */
	COUNT(false, new CountMeasurement()),
	/** Total return count */
	RETURN(false, new ReturnMeasurement()),
	/** Total exception count */
	ERROR(false, new ErrorMeasurement());
	
	private Measurement(final boolean requiresTinfo, final ThreadMetricReader reader) {
		this.mask = Util.pow2Index(ordinal());
		this.reader = reader;
		this.requiresTinfo = requiresTinfo;
	}
	
	public static void main(String[] args) {
		for(Measurement m: Measurement.values()) {
			System.out.println("Name:" + m.name() + ", Reader:" + (m.reader==null ? "<None>" : m.reader.getClass().getSimpleName()));
		}
	}
	
	/** The bit mask value for this Measurement member */
	public final int mask;
	/** The thread metric reader instance */
	public final ThreadMetricReader reader;
	/** Indicates if this measurement requires a ThreadInfo */
	public final boolean requiresTinfo;
	
	/** The mask for measurements requiring a thread info */
	public static final int TI_REQUIRED_MASK = getMaskFor(WAIT, BLOCK, WAITTIME, BLOCKTIME);
	
	/** The thread mx bean */
	public static final ThreadMXBean TMX = ManagementFactory.getThreadMXBean();

	private static final boolean CPUTIMEAVAIL = TMX.isThreadCpuTimeSupported();
	private static final boolean THREADCONTTIMEAVAIL = TMX.isThreadContentionMonitoringSupported();
	
	private static final CachedGauge<Boolean> CPUTIMEON = new CachedGauge<Boolean>(5, TimeUnit.SECONDS) {
		@Override
		protected Boolean loadValue() {
			return TMX.isThreadCpuTimeEnabled();
		}
	};
	private static final CachedGauge<Boolean> CONTENTIONON = new CachedGauge<Boolean>(5, TimeUnit.SECONDS) {
		@Override
		protected Boolean loadValue() {
			return TMX.isThreadContentionMonitoringEnabled();
		}
	};
	

	
	/** A global cache of value buffers keyed by the thread calling */
	private static final LoadingCache<Thread, LongBuffer> threadCounters = CacheBuilder.newBuilder()
			.concurrencyLevel(Constants.CORES)
			.initialCapacity(512)
			.recordStats()
			.weakKeys()
			.removalListener(new RemovalListener<Thread, LongBuffer>(){
				@Override
				public void onRemoval(RemovalNotification<Thread, LongBuffer> notification) {
					OffHeapFIFOFile.clean(notification.getValue());
				}				
			})
			.build(new CacheLoader<Thread, LongBuffer>() {
				final int size = Measurement.values().length * 8; 
	             public LongBuffer load(Thread thread) {
	                 return ByteBuffer.allocateDirect(size).asLongBuffer();
	             }
	         });
	
	/** A thread info thread local for the initial tinfo capture */
	private static final ThreadLocal<ThreadInfo> TINFO = new ThreadLocal<ThreadInfo>() {
		private final long TID = Thread.currentThread().getId();
		/**
		 * {@inheritDoc}
		 * @see java.lang.ThreadLocal#initialValue()
		 */
		@Override
		protected ThreadInfo initialValue() {			
			return TMX.getThreadInfo(TID);
		}
		
	};
	
	public static abstract class SimpleMeasurement implements ThreadMetricReader {
		/** The Measurement being measured */
		private final Measurement mez;
		/**
		 * Creates a new SimpleMeasurement
		 * @param mez The Measurement being measured
		 */
		public SimpleMeasurement(final Measurement mez) {
			this.mez = mez;
		}
		@Override
		public void pre(final LongBuffer values) {
			values.put(mez.mask, pre());
		}
		@Override
		public void post(final LongBuffer values) {
			values.put(mez.mask, post(values.get(mez.mask)));
		}		
		
		protected abstract long pre();
		
		protected abstract long post(final long pre);
	}
	
	public static class IncrementMeasurement implements ThreadMetricReader {
		/** The Measurement being measured */
		private final Measurement mez;
		/**
		 * Creates a new IncrementMeasurement
		 * @param mez The Measurement being measured
		 */
		public IncrementMeasurement(final Measurement mez) {
			this.mez = mez;
		}
		@Override
		public void pre(final LongBuffer values) {
			values.put(mez.mask, 1);
		}
		@Override
		public void post(final LongBuffer values) {
			/* No Op */
		}				
	}
	
	public static class CountMeasurement extends IncrementMeasurement {
		public CountMeasurement() {
			super(COUNT);
		}
	}
	
	public static class ReturnMeasurement extends IncrementMeasurement {
		public ReturnMeasurement() {
			super(RETURN);
		}
	}
	
	public static class ErrorMeasurement extends IncrementMeasurement {
		public ErrorMeasurement() {
			super(ERROR);
		}
	}
	
	


	public static class ElapsedMeasurement extends SimpleMeasurement {
		public ElapsedMeasurement() {
			super(ELAPSED);
		}
		@Override
		protected long pre() {
			return System.nanoTime();
		}

		@Override
		protected long post(final long pre) {			
			return System.nanoTime()-pre;
		}
	}
	
	public static class CpuMeasurement extends SimpleMeasurement {
		public CpuMeasurement() {
			super(CPU);
		}
		@Override
		protected long pre() {
			return CPUTIMEON.getValue() ? TMX.getCurrentThreadCpuTime() : -1L; 
		}

		@Override
		protected long post(final long pre) {
			return CPUTIMEON.getValue() ? TMX.getCurrentThreadCpuTime()-pre : -1L;
		}
	}
	
	public static class UserCpuMeasurement extends SimpleMeasurement {
		public UserCpuMeasurement() {
			super(UCPU);
		}
		@Override
		protected long pre() {
			return CPUTIMEON.getValue() ? TMX.getCurrentThreadUserTime() : -1L; 
		}

		@Override
		protected long post(final long pre) {
			return CPUTIMEON.getValue() ? TMX.getCurrentThreadUserTime()-pre : -1L;
		}
	}
	
	public static class WaitCountMeasurement extends SimpleMeasurement {
		public WaitCountMeasurement() {
			super(WAIT);
		}
		@Override
		protected long pre() {
			return TINFO.get().getWaitedCount();
		}

		@Override
		protected long post(final long pre) {
			return TINFO.get().getWaitedCount() - pre;
		}
	}
	
	public static class BlockCountMeasurement extends SimpleMeasurement {
		public BlockCountMeasurement() {
			super(BLOCK);
		}
		@Override
		protected long pre() {
			return TINFO.get().getBlockedCount();
		}

		@Override
		protected long post(final long pre) {
			return TINFO.get().getBlockedCount() - pre;
		}
	}
	
	public static class WaitTimeMeasurement extends SimpleMeasurement {
		public WaitTimeMeasurement() {
			super(WAITTIME);
		}
		@Override
		protected long pre() {
			return CONTENTIONON.getValue() ? TINFO.get().getWaitedTime() : -1L; 
		}

		@Override
		protected long post(final long pre) {
			return CONTENTIONON.getValue() ? TINFO.get().getWaitedTime() - pre : -1L;
		}
	}
	
	public static class BlockTimeMeasurement extends SimpleMeasurement {
		public BlockTimeMeasurement() {
			super(BLOCKTIME);
		}
		@Override
		protected long pre() {
			return CONTENTIONON.getValue() ? TINFO.get().getBlockedTime() : -1L; 
		}

		@Override
		protected long post(final long pre) {
			return CONTENTIONON.getValue() ? TINFO.get().getBlockedTime() - pre : -1L;
		}
	}
	
	
	
	/**
	 * Returns a bitmask enabled for all the passed measurement members
	 * @param ots the measurement members to get a mask for
	 * @return the mask
	 */
	public static int getMaskFor(final Measurement...ots) {
		if(ots==null || ots.length==0) return 0;
		int bitMask = 0;
		for(Measurement ot: ots) {
			if(ot==null) continue;
			bitMask = bitMask | ot.mask;
		}
		return bitMask;
	}

	/**
	 * Returns a bitmask enabled for all the passed measurement member names
	 * @param ignoreInvalids If true, ignore any invalid names, otherwise throws.
	 * @param names the measurement member names to get a mask for
	 * @return the mask
	 */
	public static int getMaskFor(final boolean ignoreInvalids, final String...names) {
		if(names==null || names.length==0) return 0;
		int bitMask = 0;
		for(int i = 0; i < names.length; i++) {
			String name = names[i];
			if((name==null || name.trim().isEmpty())) {
				if(ignoreInvalids) continue;
				throw new RuntimeException("Invalid name at index [" + i + "] in " + Arrays.toString(names));
			}
			Measurement ot = null;
			try { ot = Measurement.valueOf(name.toLowerCase().trim()); } catch (Exception ex) {}
			if(ot==null) {
				if(ignoreInvalids) continue;
				throw new RuntimeException("Invalid name at index [" + i + "] in " + Arrays.toString(names));				
			}
			bitMask = bitMask | ot.mask;
		}
		return bitMask;
	}
	
	/**
	 * Returns a bitmask enabled for all the passed measurement member names, ignoring any invalid values
	 * @param names the measurement member names to get a mask for
	 * @return the mask
	 */
	public static int getMaskFor(final String...names) {
		return getMaskFor(true, names);
	}
	
	/**
	 * Returns an array of the Measurements enabled by the passed mask 
	 * @param mask The mask
	 * @return an array of Measurements
	 */
	public static Measurement[] getEnabled(final int mask) {
		final EnumSet<Measurement> set = EnumSet.noneOf(Measurement.class);
		for(Measurement ot: values()) {
			if((mask & ot.mask) != 0) set.add(ot);
		}
		return set.toArray(new Measurement[set.size()]);
	}

	
	
}

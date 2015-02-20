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
package com.heliosapm.opentsdb.client.opentsdb.opt;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.codahale.metrics.CachedGauge;
import com.codahale.metrics.Metric;
import com.google.common.base.Objects;
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
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.Measurement</code></p>
 * TODO:  For given Measurement bitmask:
 * 		compute minimum required CHMetrics
 * 		eliminate duplicate/redundant
 */
public enum Measurement implements Measurers, ThreadMetricReader {
	/** Elapsed time in ns.  */
	ELAPSED(true, false, CHMetric.TIMER, ELAPSED_MEAS),
	/** Elapsed CPU time in ns. */
	CPU(false, false, CHMetric.TIMER, CPU_MEAS),
	/** Elapsed User Mode CPU time in ns. */
	UCPU(false, false, CHMetric.TIMER, UCPU_MEAS),
	/** The Total Elapsed (User + System Mode) CPU time in ns. */
	TCPU(false, false, CHMetric.TIMER, TCPU_MEAS),	
	/** Number of thread waits */	
	WAIT(false, true, CHMetric.COUNTER, WAIT_MEAS),
	/** Number of thread blocks */
	BLOCK(false, true, CHMetric.COUNTER, BLOCK_MEAS),
	/** Number of thread waits */	
	WAITRATE(false, true, CHMetric.METER, WAIT_MEAS),
	/** Number of thread blocks */
	BLOCKRATE(false, true, CHMetric.METER, BLOCK_MEAS),	
	/** Thread wait time in ms. */
	WAITTIME(false, true, CHMetric.TIMER, WAIT_TIME_MEAS),
	/** Thread block time in ms. */
	BLOCKTIME(false, true, CHMetric.TIMER, BLOCK_TIME_MEAS),
//	/** Concurrent threads with entry/exit block */
//	CONCURRENT(false, null),  // FIXME: !!!
	/** Total invocation count */
	INVOKE(false, false, CHMetric.COUNTER, COUNT_MEAS),
	/** Total return count */
	RETURN(true, false, CHMetric.COUNTER, RETURN_MEAS),
	/** Total exception count */
	ERROR(false, false, CHMetric.COUNTER, ERROR_MEAS),
	/** The invocation rate */
	INVOKERATE(false, false, CHMetric.METER, COUNT_MEAS),
	/** The return rate */
	RETURNRATE(true, false, CHMetric.METER, RETURN_MEAS),
	/** The exception rate */
	ERRORRATE(false, false, CHMetric.METER, ERROR_MEAS);
	
	
	
//	/** The elapsed system cpu time in microseconds */
//	SYS_CPU(seed.next(), false, true, "CPU Time (\u00b5s)", "syscpu", "CPU Thread Execution Time", new DefaultSysCpuMeasurer(0), DataStruct.getInstance(Primitive.LONG, 3, Long.MAX_VALUE, Long.MIN_VALUE, -1L), "Min", "Max", "Avg"),
//	/** The elapsed user mode cpu time in microseconds */
//	USER_CPU(seed.next(), false, true, "CPU Time (\u00b5s)", "usercpu", "CPU Thread Execution Time In User Mode", new DefaultUserCpuMeasurer(1), DataStruct.getInstance(Primitive.LONG, 3, Long.MAX_VALUE, Long.MIN_VALUE, -1L), "Min", "Max", "Avg"),
	
	
	private Measurement(final boolean isDefault, final boolean requiresTinfo, final CHMetric chMetric, final ThreadMetricReader reader) {
		this.mask = Util.pow2Index(ordinal());
		this.isDefault = isDefault;
		this.chMetric = chMetric;
		this.reader = reader;
		this.requiresTinfo = requiresTinfo;
	}
	
	/** Indicates if this is a default measurement */
	public final boolean isDefault;
	/** The bit mask value for this Measurement member */
	public final int mask;
	/** The CodaHale metric type allocated to track this measurement */
	public final CHMetric chMetric;
	/** The thread metric reader instance */
	public final ThreadMetricReader reader;
	/** Indicates if this measurement requires a ThreadInfo */
	public final boolean requiresTinfo;
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.opt.ThreadMetricReader#pre(long[], int)
	 */
	@Override
	public void pre(long[] values, int index) {
		reader.pre(values, index);
	}
	
	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.opentsdb.opt.ThreadMetricReader#post(long[], int)
	 */
	@Override
	public void post(long[] values, int index) {
		post(values, index);
	}
	
	/** The number of header longs in the long array value buffer */
	public static final int VALUEBUFFER_HEADER_SIZE = 2;
	
	/** An empty Measurement array const */
	public static final Measurement[] EMPTY_MEASUREMENT_ARR = {};
	/** A comma splitter pattern */
	public static final Pattern COMMA_SPLITTER = Pattern.compile(",");
	
	/** The thread mx bean */
	public static final ThreadMXBean TMX = ManagementFactory.getThreadMXBean();

	/** The mask for measurements requiring a thread info */
	public static final int TI_REQUIRED_MASK = getMaskFor(WAIT, BLOCK, WAITTIME, BLOCKTIME);
	
	/** Indicates if thread cpu time is supported */
	public static final boolean CPUTIMEAVAIL = TMX.isThreadCpuTimeSupported();
	/** Indicates if thread contention monitoring is supported */
	public static final boolean THREADCONTTIMEAVAIL = TMX.isThreadContentionMonitoringSupported();
	/** The mask for all measurements */
	public static final int ALL_MASK = getMaskFor(Measurement.values());
	/** The mask for disabled measurements if thread cpu time is not supported */
	public static final int CPU_CONDITIONAL_MASK = getMaskFor(CPU, UCPU);
	/** The mask for disabled measurements if thread contention monitoring is not supported */
	public static final int CONT_CONDITIONAL_MASK = getMaskFor(WAITTIME, BLOCKTIME);
	/** The mask for all possibly disabled measurements */
	public static final int ALL_CONDITIONAL_MASK = (CPU_CONDITIONAL_MASK | CONT_CONDITIONAL_MASK);
	/** The actual disabled measurement mask */
	public static final int DISABLED_MASK;
	/** The maximum actual enabled measurements mask */
	public static final int ACTUAL_ALL_MASK;
	/** The default measurements mask */
	public static final int DEFAULT_MASK;
	/** Maps the member bitmask to the member */
	public static final Map<Integer, Measurement> MASK2ENUM;
	
	
	static {
		int disabledMask = 0;
		if(!THREADCONTTIMEAVAIL) disabledMask = (disabledMask | CONT_CONDITIONAL_MASK);
		if(!CPUTIMEAVAIL) disabledMask = (disabledMask | CPU_CONDITIONAL_MASK);
		DISABLED_MASK = disabledMask;
		ACTUAL_ALL_MASK = (ALL_MASK & ~DISABLED_MASK);
		int dm = 0;
		final Measurement[] values = values();
		Map<Integer, Measurement> tmp = new HashMap<Integer, Measurement>(values.length);
		for(Measurement m: values) {
			tmp.put(m.mask, m);
			if(m.isDefault) dm = (dm | m.mask);
		}
		DEFAULT_MASK = dm;
		MASK2ENUM = Collections.unmodifiableMap(tmp);
	}
	
	
	/** A cached gauge that periodically tests if thread cpu time is enabled */
	public static final CachedGauge<Boolean> CPUTIMEON = new CachedGauge<Boolean>(5, TimeUnit.SECONDS) {
		@Override
		protected Boolean loadValue() {
			return TMX.isThreadCpuTimeEnabled();
		}
	};
	/** A cached gauge that periodically tests if thread contention monitoring is enabled */
	public static final CachedGauge<Boolean> CONTENTIONON = new CachedGauge<Boolean>(5, TimeUnit.SECONDS) {
		@Override
		protected Boolean loadValue() {
			return TMX.isThreadContentionMonitoringEnabled();
		}
	};
	
	
	
	/** A global cache of value buffers keyed by the thread calling 
	 * The cache retrieves the thread buffer for the current thread, creating a new one if necessary.
	 * 
	 * */
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
			.build(new CacheLoader<Thread, LongBuffer>(){
				@Override
				public LongBuffer load(Thread key) throws Exception {
					return ByteBuffer.allocateDirect((((getEnabled(ACTUAL_ALL_MASK).length*2) + 1) * 8)).asLongBuffer();
				}
			});
	
//	/**
//	 * Gets the thread buffer from cache or creates a new one.
//	 * The returned buffer contains the mask in the first slot (as a long) plus
//	 * enough slots to contain two sets of the measurement values for all the enabled measurements.
//	 * @param mask The measurement mask enabled for this call
//	 * @return The counter buffer
//	 */
//	private static LongBuffer allocate(final int mask) {
//		return ByteBuffer.allocateDirect(size).asLongBuffer();
////		try {
////			return threadCounters.get(Thread.currentThread(), new Callable<LongBuffer>(){
////				@Override
////				public LongBuffer call() throws Exception {
////					final int size = (getEnabled(mask).length + 1) * 8;
////					final LongBuffer buff = ByteBuffer.allocateDirect(size).asLongBuffer();
////					buff.put(0, mask);
////					return buff;
////				}
////			});
////		} catch (Exception ex) {
////			// should never happend
////			throw new RuntimeException("Failed to allocate thread counter buffer", ex);
////		}
//	}
	
	
	
	/**
	 * Allocates a long array for the passed measurement mask, minus the unsupported measurements, plus one for the mask.
	 * @param otMetricId The ID of the parent OTMetric the values are being collected for
	 * @param mask The measurement bit mask
	 * @return the allocated array
	 */
	public static long[] allocate(final long otMetricId, final int mask) {
		final long[] alloc = new long[getEnabled(mask & ~DISABLED_MASK).length + 2];
		alloc[0] = mask;
		alloc[1] = otMetricId;
		return alloc;
	}
	
	/**
	 * Decodes the passed expression into an array of Measurements.
	 * Is expected to be a comma separated list of values where each value 
	 * can be either the name of a Measurement member or a Measurement mask.
	 * Normally there would only be one mask, but multiple are supported. 
	 * The expression can contain both representations. 
	 * @param throwIfInvalid If true, will throw an exception if a value cannot be decoded
	 * @param expr The expression to decode
	 * @return an array of represented Measurement members.
	 */
	public static Measurement[] decode(final boolean throwIfInvalid, final CharSequence expr) {
		if(expr==null) return EMPTY_MEASUREMENT_ARR;
		final String sexpr = expr.toString().trim().toUpperCase();
		if(sexpr.isEmpty()) return EMPTY_MEASUREMENT_ARR;
		final EnumSet<Measurement> set = EnumSet.noneOf(Measurement.class);
		final String[] exprFields = COMMA_SPLITTER.split(sexpr);
		for(String s: exprFields) {
			if(isMeasurementName(s)) {
				set.add(valueOf(s.trim()));
				continue;
			}
			try {
				int mask = new Double(s).intValue();
				Measurement m = MASK2ENUM.get(mask);
				if(m!=null) {
					set.add(m);
					continue;
				} else {
					if(!throwIfInvalid) continue;
				}
			} catch (Exception ex) {
				if(!throwIfInvalid) continue;
			}
			throw new RuntimeException("Failed to decode Measurement value [" + s + "]");
		}
		return set.toArray(new Measurement[set.size()]);
	}
	
	/**
	 * Decodes the passed expression into an array of Measurements, ignoring any non-decoded values.
	 * Is expected to be a comma separated list of values where each value 
	 * can be either the name of a Measurement member or a Measurement mask.
	 * Normally there would only be one mask, but multiple are supported. 
	 * The expression can contain both representations. 
	 * @param expr The expression to decode
	 * @return an array of represented Measurement members.
	 */
	public static Measurement[] decode(final CharSequence expr) {
		return decode(false, expr);
	}
	
	/**
	 * Decodes the passed expression into a bitmask representing enabled Measurements, ignoring any non-decoded values.
	 * Is expected to be a comma separated list of values where each value 
	 * can be either the name of a Measurement member or a Measurement mask.
	 * Normally there would only be one mask, but multiple are supported. 
	 * The expression can contain both representations. 
	 * @param expr The expression to decode
	 * @return the bit mask of the enabled Measurement members
	 */
	public static int decodeToMask(final CharSequence expr) {
		return getMaskFor(decode(expr));
	}
	
	/**
	 * Decodes the passed expression into a bitmask representing enabled Measurements
	 * Is expected to be a comma separated list of values where each value 
	 * can be either the name of a Measurement member or a Measurement mask.
	 * Normally there would only be one mask, but multiple are supported. 
	 * The expression can contain both representations. 
	 * @param throwIfInvalid If true, will throw an exception if a value cannot be decoded
	 * @param expr The expression to decode
	 * @return the bit mask of the enabled Measurement members
	 */
	public static int decodeToMask(final boolean throwIfInvalid, final CharSequence expr) {
		return getMaskFor(decode(throwIfInvalid, expr));
	}
	
	
	
	/**
	 * Indicates if the passed symbol is a valid measurement member name.
	 * The passed value is trimmed and upper-cased.
	 * @param symbol The symbol to test
	 * @return true if the passed symbol is a valid measurement member name, false otherwise
	 */
	public static boolean isMeasurementName(final CharSequence symbol) {
		if(symbol==null) return false;
		final String name = symbol.toString().trim().toLowerCase();
		if(name.isEmpty()) return false;
		try {
			valueOf(name);
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	
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
	
	/**
	 * Returns an array of the unique CHMetrics required to capture measurements for the passed measurement bit mask
	 * @param mask The bit mask defining which measurements are to be captured
	 * @return an array of CHMetrics
	 */
	public static CHMetric[] getRequiredCHMetrics(final int mask) {
		final Set<CHMetric> set = EnumSet.noneOf(CHMetric.class);
		for(Measurement m: getEnabled(mask)) {
			set.add(m.chMetric);
		}
		return set.toArray(new CHMetric[set.size()]);
	}
	
	/**
	 * Returns a map of Metric instances keyed by the Measurement that will write to the metric 
	 * within a map keyed by the CHMetric type of the created Metric instances 
	 * @param mask The bit mask defining which Measurements are enabled
	 * @return the map of metrics
	 */
	public static Map<CHMetric, Map<Measurement, Metric>> getRequiredCHMetricInstances(final int mask) {
		final Map<CHMetric, Map<Measurement, Metric>> map = new EnumMap<CHMetric, Map<Measurement, Metric>>(CHMetric.class);
		for(CHMetric chm: getRequiredCHMetrics(mask)) {			
			final Map<Measurement, Metric> instances = new EnumMap<Measurement, Metric>(Measurement.class);
			map.put(chm, instances);			
		}
		for(Measurement m: getEnabled(mask)) {
			map.get(m.chMetric).put(m, m.chMetric.createNewMetric());			
		}
		return map;
	}
	
	/**
	 * Returns a map of the default Metric instances keyed by the Measurement that will write to the metric 
	 * within a map keyed by the CHMetric type of the created Metric instances 
	 * @return the map of metrics
	 */
	public static Map<CHMetric, Map<Measurement, Metric>> getDefaultCHMetricInstances() {
		return getRequiredCHMetricInstances(DEFAULT_MASK);
	}
	
	/**
	 * Called at the entry of a measured block
	 * @param buffer The value buffer
	 */
	public static final void enter(final long[] buffer) {
		final int mask = (int)buffer[0];
		int index = VALUEBUFFER_HEADER_SIZE;
		final boolean ti = (mask & ~TI_REQUIRED_MASK) != mask; 
		try {
			if(ti) {
				TINFO.get();
			}
			for(Measurement m: getEnabled(mask)) {
				m.reader.pre(buffer, index);
				index++;
			}
		} finally {
			if(ti) TINFO.remove();
		}
	}
	
	/**
	 * Called at the normal exit of a measured block
	 * @param buffer The value buffer
	 */
	public static final void exit(final long[] buffer) {
		final int mask = (int)buffer[0];
		int index = VALUEBUFFER_HEADER_SIZE;
		final boolean ti = (mask & ~TI_REQUIRED_MASK) != mask;
		try {
			for(Measurement m: getEnabled((mask & ~ERROR.mask))) {
				m.reader.post(buffer, index);
				index++;
			}
		} finally {
			if(ti) TINFO.remove();
		}
	}
	
	/**
	 * Called on a thrown exception in a measured block
	 * @param buffer The value buffer
	 * // TODO: In some cases, we want to resolve all the measurements, 
	 * but mostly we probably just want the error count since 
	 * the metric values are probably invalid.
	 */
	public static final void errorExit(final long[] buffer) {
		final int mask = (int)buffer[0];
		int index = 1;
		for(Measurement m: getEnabled((mask & ~RETURN.mask))) {  
			m.reader.post(buffer, index);
			index++;
		}
	}
	
	public static void log(final Object fmt, final Objects.ToStringHelper...args) {
		System.out.println(String.format(fmt.toString(), args));
	}
	
	public static void main(String[] args) {
		for(Measurement m: Measurement.values()) {
//			System.out.println(m);
//			System.out.println("Name:" + m.name() + ", Reader:" + (m.reader==null ? "<None>" : m.reader.getClass().getSimpleName()) + ", CHMetric:" + m.chMetric.name());
		}
//		for(Measurement m: Measurement.values()) {
//			if(m.isDefault) {
//				System.out.println(String.format("\t\t\t\t\t<option value=\"%s\" selected=\"selected\" >%s</option>", m.mask, m.name()));
//			}
//		}
//		for(Measurement m: Measurement.values()) {
//			if(!m.isDefault) {
//				System.out.println(String.format("\t\t\t\t\t<option value=\"%s\">%s</option>", m.mask, m.name()));
//			}
//		}
		
		final Map<CHMetric, Map<Measurement, Metric>> map = getDefaultCHMetricInstances();
		int subMetricMask = 0;
		for(Map.Entry<CHMetric, Map<Measurement, Metric>> entry: map.entrySet()) {
			final CHMetric chm = entry.getKey();
			log("CHMetric: %s", chm);
			for(Map.Entry<Measurement, Metric> inst: entry.getValue().entrySet()) {
				final Measurement m = inst.getKey();
				final Metric metric = inst.getValue();
				log("\tInstance:   for: %s  in: %s", m.name(), metric.getClass().getSimpleName());
				for(SubMetric sm: m.chMetric.defaultSubMetrics) {
					subMetricMask = subMetricMask | sm.mask;
					log("\t\tSubMetric: %s", sm);
				}
			}
		}
		log("SubMetricMask: %s  --->  %s", subMetricMask, Arrays.toString(SubMetric.getEnabled(subMetricMask)));
//		TMX.setThreadCpuTimeEnabled(false);
//		TMX.setThreadContentionMonitoringEnabled(false);
//		final CountDownLatch latch = new CountDownLatch(1);
//		final Thread sleepThread = new Thread() {
//			public void run() {
//				dropLatchAndSleep(1347L, latch);
//			}
//		};
//		
//		final long[] alloc = allocate(ACTUAL_ALL_MASK);
//		final Random r = new Random(System.currentTimeMillis());
//		enter(alloc);
//		
//		long t = 0;
//		for(int i = 0; i < 10000; i++) {
//			if(i%2==0) t+= r.nextLong();
//			else t-= r.nextLong();
//		}
//		log("Random T:%s", t);
//		sleepThread.start();
//		try { latch.await(); } catch (Exception ex) {}	
//		dropLatchAndSleep(200, latch);
//		try { Thread.sleep(200); } catch (Exception ex) {}		
////		try { Thread.currentThread().join(500); } catch (Exception ex) {}
//		exit(alloc);
//		log(renderAlloc(alloc));		
	}
	
	private static synchronized void dropLatchAndSleep(final long time, final CountDownLatch latch) {
		try { latch.countDown();  Thread.currentThread().join(time); } catch (Exception ex) {}
	}
	
	public static String renderAlloc(final long[] alloc) {
		final int mask = (int)alloc[0];
		log("Alloc: %s", Arrays.toString(alloc));
		final Measurement[] mez = getEnabled((mask));
		if(mez.length==0) return "No Measurements";
		StringBuilder b = new StringBuilder("=== Measurements:");
		int index = 1;
		for(Measurement m: mez) {  
			if(alloc[index]!=-1) {
				b.append("\n\t").append(m.name()).append(":").append(alloc[index]);
			}
			index++;
		}
		b.append("\n");
		return b.toString();

	}
	
	public static void log(final Object fmt, final Object...args) {
		System.out.println(String.format(fmt.toString(), args));
	}
	
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
		public void pre(final long[] values, final int index) {			
			values[index] = pre();
		}
		@Override
		public void post(final long[] values, final int index) {
			values[index] = post(values[index]);
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
		public void pre(final long[] values, final int index) {
			values[index]++;
		}
		@Override
		public void post(final long[] values, final int index) {
			/* No Op */
		}				
	}
	
	public static class CountMeasurement extends IncrementMeasurement {
		public CountMeasurement() {
			super(INVOKE);
		}
	}
	
	public static class ReturnMeasurement extends IncrementMeasurement {
		public ReturnMeasurement() {
			super(RETURN);
		}
		@Override
		public void post(final long[] values, final int index) {
			values[index]++;
		}
		@Override
		public void pre(final long[] values, final int index) {
			/* No Op */
		}				
		
	}
	
	public static class ErrorMeasurement extends IncrementMeasurement {
		public ErrorMeasurement() {
			super(ERROR);
		}
		@Override
		public void post(final long[] values, final int index) {
			values[index]++;
		}
		@Override
		public void pre(final long[] values, final int index) {
			/* No Op */
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
	
	public static class TotalCpuMeasurement extends SimpleMeasurement {
		public TotalCpuMeasurement() {
			super(TCPU);
		}
		@Override
		protected long pre() {
			return CPUTIMEON.getValue() ? (TMX.getCurrentThreadCpuTime() + TMX.getCurrentThreadUserTime()) : -1L; 
		}

		@Override
		protected long post(final long pre) {
			return CPUTIMEON.getValue() ? (TMX.getCurrentThreadCpuTime() + TMX.getCurrentThreadUserTime())-pre : -1L;
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

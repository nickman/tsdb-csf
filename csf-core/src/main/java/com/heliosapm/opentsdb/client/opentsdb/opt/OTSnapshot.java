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

import java.util.Arrays;
import java.util.EnumSet;

import com.codahale.metrics.Snapshot;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: OTSnapshot</p>
 * <p>Description: Enumerates the available submetrics for a Snapshot</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.OTSnapshot</code></p>
 */

public enum OTSnapshot  implements SnapshotReader {
	/** the lowest value in the snapshot. */
	min(){@Override	public double get(final Snapshot snap) { return snap.getMin(); }},
	/** the highest value in the snapshot. */
	max(){@Override	public double get(final Snapshot snap) { return snap.getMax(); }},
	/** the arithmetic mean of the values in the snapshot */
	mean(){@Override	public double get(final Snapshot snap) { return snap.getMean(); }},
	/** the standard deviation of the values in the snapshot. */
	stddev(){@Override	public double get(final Snapshot snap) { return snap.getStdDev(); }},
	/** the median value in the distribution. */
	median(){@Override	public double get(final Snapshot snap) { return snap.getMedian(); }},
	/** The value at the 75th percentile in the distribution. */
	p75(){@Override	public double get(final Snapshot snap) { return snap.get75thPercentile(); }},
	/** The value at the 95th percentile in the distribution. */
	p95(){@Override	public double get(final Snapshot snap) { return snap.get95thPercentile(); }},
	/** The value at the 98th percentile in the distribution. */
	p98(){@Override	public double get(final Snapshot snap) { return snap.get98thPercentile(); }},
	/** The value at the 99th percentile in the distribution. */
	p99(){@Override	public double get(final Snapshot snap) { return snap.get99thPercentile(); }},
	/** The value at the 99.9th percentile in the distribution. */
	p999(){@Override public double get(final Snapshot snap) { return snap.get999thPercentile(); }};
	
	private OTSnapshot() {
		mask = Util.pow2Index(ordinal());
	}
	
	/** The bit mask value for this OTSnapshot member */
	public final int mask;
	
	/** The bit mask for all OTSnapshots */
	public static final int ALL = getMaskFor(values());
	
	/**
	 * Returns a bitmask enabled for all the passed snapshot members
	 * @param ots the snapshot members to get a mask for
	 * @return the mask
	 */
	public static int getMaskFor(final OTSnapshot...ots) {
		if(ots==null || ots.length==0) return 0;
		int bitMask = 0;
		for(OTSnapshot ot: ots) {
			if(ot==null) continue;
			bitMask = bitMask | ot.mask;
		}
		return bitMask;
	}

	/**
	 * Returns a bitmask enabled for all the passed snapshot member names
	 * @param ignoreInvalids If true, ignore any invalid names, otherwise throws.
	 * @param names the snapshot member names to get a mask for
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
			OTSnapshot ot = null;
			try { ot = OTSnapshot.valueOf(name.toLowerCase().trim()); } catch (Exception ex) {}
			if(ot==null) {
				if(ignoreInvalids) continue;
				throw new RuntimeException("Invalid name at index [" + i + "] in " + Arrays.toString(names));				
			}
			bitMask = bitMask | ot.mask;
		}
		return bitMask;
	}
	
	/**
	 * Returns a bitmask enabled for all the passed snapshot member names, ignoring any invalid values
	 * @param names the snapshot member names to get a mask for
	 * @return the mask
	 */
	public static int getMaskFor(final String...names) {
		return getMaskFor(true, names);
	}
	
	/**
	 * Returns an array of the OTSnapshots enabled by the passed mask 
	 * @param mask The mask
	 * @return an array of OTSnapshots
	 */
	public static OTSnapshot[] getEnabled(final int mask) {
		final EnumSet<OTSnapshot> set = EnumSet.noneOf(OTSnapshot.class);
		for(OTSnapshot ot: values()) {
			if((mask & ot.mask) != 0) set.add(ot);
		}
		return set.toArray(new OTSnapshot[set.size()]);
	}
	
}

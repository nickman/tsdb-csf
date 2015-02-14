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

import com.codahale.metrics.Metered;
import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: OTMetered</p>
 * <p>Description: Enumerates the available submetrics for a Metered</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.OTMetered</code></p>
 */

public enum OTMetered implements MeteredReader {
	/** The number of events which have been marked */
	mcount(){@Override public double get(final Metered metered) { return metered.getCount(); }},
	/** The mean rate at which events have occurred since the meter was created */
	mean_rate(){@Override public double get(final Metered metered) { return metered.getMeanRate(); }},
	/** The one-minute exponentially-weighted moving average rate at which events have occurred since the meter was created */
	m1(){@Override public double get(final Metered metered) { return metered.getOneMinuteRate(); }},
	/** The five-minute exponentially-weighted moving average rate at which events have occurred since the meter was created */
	m5(){@Override public double get(final Metered metered) { return metered.getFiveMinuteRate(); }},
	/** The fifteen-minute exponentially-weighted moving average rate at which events have occurred since the meter was created */
	m15(){@Override public double get(final Metered metered) { return metered.getFifteenMinuteRate(); }};
	
	
	private OTMetered() {
		this.mask = Util.pow2Index(ordinal());
	}
	
	/** The bit mask value for this OTMetered member */
	public final int mask;
	
	/** The bit mask for all OTMetereds */
	public static final int ALL = getMaskFor(values());
	
	/**
	 * Returns a bitmask enabled for all the passed metered members
	 * @param ots the metered members to get a mask for
	 * @return the mask
	 */
	public static int getMaskFor(final OTMetered...ots) {
		if(ots==null || ots.length==0) return 0;
		int bitMask = 0;
		for(OTMetered ot: ots) {
			if(ot==null) continue;
			bitMask = bitMask | ot.mask;
		}
		return bitMask;
	}

	/**
	 * Returns a bitmask enabled for all the passed metered member names
	 * @param ignoreInvalids If true, ignore any invalid names, otherwise throws.
	 * @param names the metered member names to get a mask for
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
			OTMetered ot = null;
			try { ot = OTMetered.valueOf(name.toLowerCase().trim()); } catch (Exception ex) {}
			if(ot==null) {
				if(ignoreInvalids) continue;
				throw new RuntimeException("Invalid name at index [" + i + "] in " + Arrays.toString(names));				
			}
			bitMask = bitMask | ot.mask;
		}
		return bitMask;
	}
	
	/**
	 * Returns a bitmask enabled for all the passed metered member names, ignoring any invalid values
	 * @param names the metered member names to get a mask for
	 * @return the mask
	 */
	public static int getMaskFor(final String...names) {
		return getMaskFor(true, names);
	}
	
	/**
	 * Returns an array of the OTMetereds enabled by the passed mask 
	 * @param mask The mask
	 * @return an array of OTMetereds
	 */
	public static OTMetered[] getEnabled(final int mask) {
		final EnumSet<OTMetered> set = EnumSet.noneOf(OTMetered.class);
		for(OTMetered ot: values()) {
			if((mask & ot.mask) != 0) set.add(ot);
		}
		return set.toArray(new OTMetered[set.size()]);
	}
	
}

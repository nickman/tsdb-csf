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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Snapshot;

/**
 * <p>Title: CachedSnapshot</p>
 * <p>Description: A snapshot instance cast as a metric</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.CachedSnapshot</code></p>
 */

public class CachedSnapshot implements Metric {
	/** the snapshot delegate */
	private final Snapshot delegate;
	/** the source histogram's count at create time */
	private final double hcount;
	
	
	/**Creates a new CachedSnapshot
	 * @param histogram The histogram to get the snapshot from
	 * @return the cached snapshot
	 */
	public static CachedSnapshot cs(final Histogram histogram) {
		return new CachedSnapshot(histogram.getSnapshot(), histogram.getCount());
	}
	
	/**
	 * Creates a new CachedSnapshot
	 * @param delegate the snapshot delegate
	 */
	private CachedSnapshot(final Snapshot delegate, final long hcount) {
		this.delegate = delegate;
		this.hcount = hcount;
	}
	
	/**
	 * @return
	 * @see com.codahale.metrics.Histogram#getCount()
	 */
	public double getCount() {
		return hcount;
	}


	/**
	 * @param quantile
	 * @return
	 * @see com.codahale.metrics.Snapshot#getValue(double)
	 */
	public double getValue(double quantile) {
		return delegate.getValue(quantile);
	}

	/**
	 * @return
	 * @see com.codahale.metrics.Snapshot#getValues()
	 */
	public long[] getValues() {
		return delegate.getValues();
	}

	/**
	 * @return
	 * @see com.codahale.metrics.Snapshot#getMedian()
	 */
	public double getMedian() {
		return delegate.getMedian();
	}

	/**
	 * @return
	 * @see com.codahale.metrics.Snapshot#get75thPercentile()
	 */
	public double get75thPercentile() {
		return delegate.get75thPercentile();
	}

	/**
	 * @return
	 * @see com.codahale.metrics.Snapshot#get95thPercentile()
	 */
	public double get95thPercentile() {
		return delegate.get95thPercentile();
	}

	/**
	 * @return
	 * @see com.codahale.metrics.Snapshot#get98thPercentile()
	 */
	public double get98thPercentile() {
		return delegate.get98thPercentile();
	}

	/**
	 * @return
	 * @see com.codahale.metrics.Snapshot#get99thPercentile()
	 */
	public double get99thPercentile() {
		return delegate.get99thPercentile();
	}

	/**
	 * @return
	 * @see com.codahale.metrics.Snapshot#get999thPercentile()
	 */
	public double get999thPercentile() {
		return delegate.get999thPercentile();
	}

	/**
	 * @return
	 * @see com.codahale.metrics.Snapshot#getMax()
	 */
	public long getMax() {
		return delegate.getMax();
	}

	/**
	 * @return
	 * @see com.codahale.metrics.Snapshot#getMean()
	 */
	public double getMean() {
		return delegate.getMean();
	}

	/**
	 * @return
	 * @see com.codahale.metrics.Snapshot#getMin()
	 */
	public long getMin() {
		return delegate.getMin();
	}

	/**
	 * @return
	 * @see com.codahale.metrics.Snapshot#getStdDev()
	 */
	public double getStdDev() {
		return delegate.getStdDev();
	}


}

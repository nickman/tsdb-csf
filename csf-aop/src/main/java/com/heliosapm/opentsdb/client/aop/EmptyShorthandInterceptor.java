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

import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement;

/**
 * <p>Title: EmptyShorthandInterceptor</p>
 * <p>Description: An empty {@link ShorthandInterceptor} implementation</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.aop.EmptyShorthandInterceptor</code></p>
 */

public class EmptyShorthandInterceptor implements ShorthandInterceptor {

	/**
	 * Creates a new EmptyShorthandInterceptor
	 */
	public EmptyShorthandInterceptor() {

	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandInterceptor#enter(int, long)
	 */
	@Override
	public long[] enter(final int mask, final long parentMetricId) {
		return Measurement.enter(mask, parentMetricId);
	}

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.opentsdb.client.aop.ShorthandInterceptor#exit(long[])
	 */
	@Override
	public void exit(final long[] entryState) {
		Measurement.exit(entryState);
		// enqueue state buffer
	}

}

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

import java.util.concurrent.TimeUnit;

import com.codahale.metrics.Clock.UserTimeClock;

/**
 * <p>Title: EpochClock</p>
 * <p>Description: An extention of {@link UserTimeClock} that returns the Unix Epoch time for {@link #getTime()}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.EpochClock</code></p>
 */

public class EpochClock extends UserTimeClock {
	/** A shareable instance */
	public static final EpochClock INSTANCE = new EpochClock();
	
	private EpochClock() {}
	
	/**
	 * {@inheritDoc}
	 * @see com.codahale.metrics.Clock#getTime()
	 */
	@Override
	public long getTime() {		
		return TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
	}
}

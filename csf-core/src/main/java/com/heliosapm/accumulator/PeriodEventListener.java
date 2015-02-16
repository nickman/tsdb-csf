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
package com.heliosapm.accumulator;


/**
 * <p>Title: PeriodEventListener</p>
 * <p>Description: Defines a listener that will be notified of period switch events</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.accumulator.PeriodEventListener</code></p>
 */
public interface PeriodEventListener {
	/**
	 * Callback when a period ends and new period begins
	 * @param newStartTime The start time in ms of the new [current] period
	 * @param newEndTime The end time in ms of the new [current] period
	 * @param priorStartTime The start time in ms of the prior period
	 * @param priorEndTime The end time in ms of the prior period
	 */
	public void onNewPeriod(long newStartTime, long newEndTime ,long priorStartTime, long priorEndTime);
}

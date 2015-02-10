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

package com.heliosapm.opentsdb.client.jvmjmx;

import java.lang.management.MemoryUsage;

import com.heliosapm.opentsdb.client.util.Util;

/**
 * <p>Title: MUsage</p>
 * <p>Description: Enuemerates the mem usage keys</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.GarbageCollectorMBeanObserver.MUsage</code></p>
 */
public enum MUsage implements MemoryUsageReader {
	/** Initial allocation */
	init{
		@Override
		public long get(final MemoryUsage memoryUsage) {
			return memoryUsage.getInit();
		}
	},
	/** Currently used */
	used{
		@Override
		public long get(final MemoryUsage memoryUsage) {
			return memoryUsage.getUsed();
		}
	},
	/** Currently committed */
	committed{
		@Override
		public long get(final MemoryUsage memoryUsage) {
			return memoryUsage.getCommitted();
		}
	},
	/** Maximum committed */
	max{
		@Override
		public long get(final MemoryUsage memoryUsage) {
			return memoryUsage.getMax();
		}
	},
	/** Percent capacity (used as a % of Max) */
	pctUsed{
		@Override
		public long get(final MemoryUsage memoryUsage) {
			return Util.percent(memoryUsage.getUsed(), memoryUsage.getMax());
		}
	},	
	/** Percent capacity (committed as a % of Max) */
	pctCapacity{
		@Override
		public long get(final MemoryUsage memoryUsage) {
			return Util.percent(memoryUsage.getCommitted(), memoryUsage.getMax());
		}
	};
	
	
	private static final MUsage[] ONE_TIMES = new MUsage[]{init, max};
	private static final MUsage[] NON_ONE_TIMES = new MUsage[]{used, committed, pctUsed, pctCapacity};
	
	public static MUsage[] getNonOneTimes() {
		return NON_ONE_TIMES.clone();
	}
	
	public static MUsage[] getOneTimes() {
		return ONE_TIMES.clone();
	}
	
	
}

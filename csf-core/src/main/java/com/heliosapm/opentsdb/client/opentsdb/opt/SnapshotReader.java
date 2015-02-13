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

import com.codahale.metrics.Snapshot;

/**
 * <p>Title: SnapshotReader</p>
 * <p>Description: Reads a specific value from a snapshot</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.SnapshotReader</code></p>
 */
public interface SnapshotReader {
	/**
	 * Returns the appropriate value from the snapshot
	 * @param snap The snapshot to read from
	 * @return the read value
	 */
	public double get(Snapshot snap);
}

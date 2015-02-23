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

import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement.BlockCountMeasurement;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement.BlockTimeMeasurement;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement.ConcurrencyMeasurement;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement.CountMeasurement;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement.CpuMeasurement;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement.ElapsedMeasurement;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement.ErrorMeasurement;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement.ReturnMeasurement;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement.TotalCpuMeasurement;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement.UserCpuMeasurement;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement.WaitCountMeasurement;
import com.heliosapm.opentsdb.client.opentsdb.opt.Measurement.WaitTimeMeasurement;

/**
 * <p>Title: Measurers</p>
 * <p>Description: Static shared measurer instances</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.Measurers</code></p>
 */

/**
 * <p>Title: Measurers</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.opt.Measurers</code></p>
 */
public interface Measurers {
	/**  */
	public static final ElapsedMeasurement ELAPSED_MEAS = new ElapsedMeasurement();
	/**  */
	public static final CpuMeasurement CPU_MEAS = new CpuMeasurement();
	/**  */
	public static final UserCpuMeasurement UCPU_MEAS = new UserCpuMeasurement();
	/**  */
	public static final TotalCpuMeasurement TCPU_MEAS = new TotalCpuMeasurement();
	/**  */
	public static final WaitCountMeasurement WAIT_MEAS = new WaitCountMeasurement();
	/**  */
	public static final BlockCountMeasurement BLOCK_MEAS = new BlockCountMeasurement();
	/**  */
	public static final WaitTimeMeasurement WAIT_TIME_MEAS = new WaitTimeMeasurement();
	/**  */
	public static final BlockTimeMeasurement BLOCK_TIME_MEAS = new BlockTimeMeasurement();
	/**  */
	public static final CountMeasurement COUNT_MEAS = new CountMeasurement();
	/**  */
	public static final ReturnMeasurement RETURN_MEAS = new ReturnMeasurement();
	/**  */
	public static final ErrorMeasurement ERROR_MEAS = new ErrorMeasurement();
	/**  */
	public static final ConcurrencyMeasurement CONCURRENT_MEAS = new ConcurrencyMeasurement();

}

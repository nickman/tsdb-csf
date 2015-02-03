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

package com.heliosapm.opentsdb.client;

import org.junit.Assert;
import org.junit.Test;

import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;


/**
 * <p>Title: OTMetricTest</p>
 * <p>Description: Unit tests around metric naming and hashing</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.OTMetricTest</code></p>
 */

public class OTMetricTest extends BaseTest {

	/**
	 * Tests some basic builder ops
	 */
	@Test
	public void testBuilder() {
		OTMetric otm1  = testBuild(MetricBuilder.metric("resultCounts").pre("KitchenSink").ext("ext").tag("op", "cache-lookup").tag("service", "cache-service"), 
				"KitchenSink.resultCounts.ext{op=cache-lookup, service=cache-service}");
		OTMetric otm2  = testBuild(MetricBuilder.metric("resultCounts").pre("KitchenSink").ext("ext").tag("op", "cache-lookup").tag("service", "cache-service"), 
				"KitchenSink.resultCounts.ext{op=cache-lookup, service=cache-service}");
		compare(otm1, otm2);
		
		
	}
	
	/**
	 * OTMetric builder test
	 * @param builder The pre-loaded builder
	 * @param expectedName The expected name to render
	 * @return the built OTMetric
	 */
	protected OTMetric testBuild(final MetricBuilder builder, final String expectedName) {
		long mbLongHashCode = builder.longHashCode();
		OTMetric otm = builder.build();
		log("OTMetric:" + otm.toString());
		Assert.assertEquals("The rendered string is unexpected", expectedName, otm.toString());
		Assert.assertEquals("The long hashcodes do not match", mbLongHashCode, otm.longHashCode());
		return otm;
	}
	
	protected void compare(final OTMetric otm1, final OTMetric otm2) {
		Assert.assertEquals("The names are not the same", otm1.toString(), otm2.toString());
		Assert.assertEquals("The long hash codes are not the same", otm1.longHashCode(), otm2.longHashCode());
		Assert.assertEquals("The java hash codes are not the same", otm1.hashCode(), otm2.hashCode());
		Assert.assertTrue("The instances are not the same", otm1==otm2); 
	}
	
	
//	log("Creating OTM for [" + ManagementFactory.CLASS_LOADING_MXBEAN_NAME + "] (" + ManagementFactory.CLASS_LOADING_MXBEAN_NAME.getBytes(UTF8).length + ")");
//	OTMetric otm = new OTMetric(ManagementFactory.CLASS_LOADING_MXBEAN_NAME);
//	printDetails(otm);
//	for(GarbageCollectorMXBean gc: ManagementFactory.getGarbageCollectorMXBeans()) {
//		otm = new OTMetric(gc.getObjectName().toString());
//		printDetails(otm);
//	}
//	otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice");
//	printDetails(otm);
//	otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice", "p75");
//	printDetails(otm);
//	otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,host=AA");
//	printDetails(otm);
//	otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,app=XX");
//	printDetails(otm);
//	otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,host=AA,app=XX");
//	printDetails(otm);
//	otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,host=AA,app=XX", "p75");
//	printDetails(otm);
//	
//	
//
//
//}
//
//Object writeReplace() throws ObjectStreamException {
//	return toString();
//}
//
//
//public static void printDetails(final OTMetric otm) {
//	StringBuilder b = new StringBuilder("FQN:").append(otm.toString());
//	b.append("\n\thasAppTag:").append(otm.hasAppTag());
//	b.append("\n\thasHostTag:").append(otm.hasHostTag());
//	b.append("\n\tisExtension:").append(otm.isExtension());
//	b.append("\n\tPrefix:").append(otm.getMetricName());
//	b.append("\n\tTagCount:").append(otm.getTagCount());
//	b.append("\n\tTags:").append(otm.getTags());
//	b.append("\n\thashCode:").append(otm.hashCode());
//	b.append("\n\tlongHashCode:").append(otm.longHashCode());
//	b.append("\n\tJSON:").append(otm.toJSON(System.currentTimeMillis(), 0));
//	log(b);
//}

}

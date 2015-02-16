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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;


/**
 * <p>Title: OTMetricTest</p>
 * <p>Description: Unit tests around metric naming and hashing</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.OTMetricTest</code></p>
 */
@RunWith(JUnit4.class)
public class OTMetricTest extends BaseTest {

	static final Set<String[]> flatNameTests = new LinkedHashSet<String[]>(Arrays.asList(
			new String[][] {
					// All flat, dot separated
					new String[]{"KitchenSink.resultCounts.op=cache-lookup.service=cacheservice.host=AA.app=XX", 
							     "KitchenSink.resultCounts{op=cache-lookup, service=cacheservice, host=AA, app=XX}"},
				    // All flat, quoted dot tag value
					new String[]{"KitchenSink.resultCounts.op=cache-lookup.service=cacheservice.host='AA.com'.app=XX", 
				     "KitchenSink.resultCounts{op=cache-lookup, service=cacheservice, host=AA.com, app=XX}"},
				     // Add in some random spaces
						new String[]{"KitchenSink. resultCounts.op=cache-l ookup.service= cacheservice.host= 'AA.com'.app=XX", 
				     "KitchenSink.resultCounts{op=cache-lookup, service=cacheservice, host=AA.com, app=XX}"},
				     // just a dot name
						new String[]{"KitchenSink.resultCounts", "KitchenSink.resultCounts"}, 

			}
			//new String[]{}
	)); 
	
	/**
	 * Basic flat name tests
	 */
	@Test
	public void testFlatNames() {
		for(String[] flatNameTest: flatNameTests) {
			log("\n\tFlatNameTest:\n\t============\n\t" + flatNameTest[1] + "\n\t" + OTMetric.splitFlatName(flatNameTest[0]).toString() + "\n\t==========");
			Assert.assertEquals("Flat name does not match", flatNameTest[1], OTMetric.splitFlatName(flatNameTest[0]).toString());
		}
	}
	
	@Test
	public void longTestFlatNames() {
		for(int i = 0; i < 10000; i++) {
			for(String[] flatNameTest: flatNameTests) {
				log("\n\tFlatNameTest:\n\t============\n\t" + flatNameTest[1] + "\n\t" + OTMetric.splitFlatName(flatNameTest[0]).toString() + "\n\t==========");
				Assert.assertEquals("Flat name does not match", flatNameTest[1], OTMetric.splitFlatName(flatNameTest[0]).toString());
			}
			try { Thread.sleep(500); } catch (Exception ex) {}
		}
	}
	
	
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
		// ==============================
		otm1  = testBuild(MetricBuilder.metric("KitchenSink.resultCounts.op=cache-lookup.service=cache-service"), 
				"KitchenSink.resultCounts{op=cache-lookup, service=cache-service}");
		otm2  = testBuild(MetricBuilder.metric("resultCounts").pre("KitchenSink").tag("op", "cache-lookup").tag("service", "cache-service"), 
				"KitchenSink.resultCounts{op=cache-lookup, service=cache-service}");
		compare(otm1, otm2);
		

		
		
//		log("Creating OTM for [" + ManagementFactory.CLASS_LOADING_MXBEAN_NAME + "] (" + ManagementFactory.CLASS_LOADING_MXBEAN_NAME.getBytes(UTF8).length + ")");
//		OTMetric otm = new OTMetric(ManagementFactory.CLASS_LOADING_MXBEAN_NAME);
//		printDetails(otm);
//		for(GarbageCollectorMXBean gc: ManagementFactory.getGarbageCollectorMXBeans()) {
//			otm = new OTMetric(gc.getObjectName().toString());
//			printDetails(otm);
//		}
//		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice");
//		printDetails(otm);
//		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice", null, "p75");
//		printDetails(otm);
//		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,host=AA");
//		printDetails(otm);
//		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,app=XX");
//		printDetails(otm);
//		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,host=AA,app=XX");
//		printDetails(otm);
//		otm = new OTMetric("KitchenSink.resultCounts.op=cache-lookup.service=cacheservice,host=AA,app=XX", null, "p75");
//		printDetails(otm);
		
		
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
	
	/**
	 * Compares two OTMetrics that should have the same longHashCode, same fully qualified name
	 * and therefore be the same instance.
	 * @param otm1 The first OTMetric
	 * @param otm2 The second OTMetric
	 */
	protected void compare(final OTMetric otm1, final OTMetric otm2) {
		Assert.assertNotNull("OTM1 was null", otm1);
		Assert.assertNotNull("OTM2 was null", otm2);
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

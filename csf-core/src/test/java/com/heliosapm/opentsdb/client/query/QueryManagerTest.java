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
package com.heliosapm.opentsdb.client.query;

import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

import javax.management.ObjectName;
import javax.management.QueryExp;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.heliosapm.opentsdb.client.BaseTest;
import com.heliosapm.opentsdb.client.jvmjmx.customx.QueryManager;
import com.heliosapm.utils.jmx.JMXHelper;
import com.heliosapm.utils.url.URLHelper;
import com.heliosapm.utils.xml.XMLHelper;

/**
 * <p>Title: QueryManagerTest</p>
 * <p>Description: QueryManager tests</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.query.QueryManagerTest</code></p>
 */

public class QueryManagerTest extends BaseTest {
	private static final Set<ObjectName> testMBeans = new HashSet<ObjectName>();	
	/** The timestamp when this test started */
	public static final long NOW = System.currentTimeMillis();
	/** A year in ms. */
	private static final long AYEARINMS = 1000L * 60L * 60L * 24L * 365L;
	
	/** The person object domain */
	public static final String PEOPLE_DOMAIN = "all.the.peoples";
	
	/** The pattern for all the peoples */
	public static final ObjectName PEOPLE_PATTERN = JMXHelper.objectName(PEOPLE_DOMAIN + ":*");
	
	/** The template for a person's objectName */
	public static final String PERSON_TEMPLATE = PEOPLE_DOMAIN + ":lastName=%s,firstName=%s";
	
	
	/**
	 * Creates a new QueryManagerTest
	 */
	public QueryManagerTest() {

	}
	
	/**
	 * Creates and registers the QueryManager test MBeans
	 * @throws Exception throw on any error
	 */
	@BeforeClass
	public static void loadTestMBeans() throws Exception {
		testMBeans.clear();
		testMBeans.add(MBeans.newPerson("Smith", "John", 32, 46534.23, true, NOW).getObjectName());
		testMBeans.add(MBeans.newPerson("Smith", "Becky", 28, 46000.00, false, NOW-AYEARINMS).getObjectName());
		testMBeans.add(MBeans.newPerson("Bundy", "Al", 59, 23000.00, true, NOW).getObjectName());
		testMBeans.add(MBeans.newPerson("Simpson", "Homer", 42, 60000.00, true, NOW).getObjectName());
		log("Test MBeans Loaded");
	}
	
	/**
	 * Loads the test queries from the XML file
	 * @throws Exception thrown on any error
	 */
	@BeforeClass
	public static void loadTestQueries() throws Exception {
		final URL url = QueryManagerTest.class.getClassLoader().getResource("configs/jmxcollect/querymanagertests.xml");			
		QueryManager.getInstance().load(XMLHelper.parseXML(URLHelper.getTextFromURL(url)).getDocumentElement());
	}
	
	/**
	 * Clears the compiled test queries from the QueryManager
	 * @throws Exception thrown on any error
	 */
	@AfterClass
	public static void clearTestQueries() throws Exception {
		QueryManager.getInstance().clear();
	}
	
	
	/**
	 * Unregisters the QueryManager test MBeans
	 * @throws Exception throw on any error
	 */
	@AfterClass
	public static void unloadTestBeans() throws Exception {
		for(ObjectName on: testMBeans) {
			JMXHelper.unregisterMBean(on);
		}
		testMBeans.clear();
		log("Test Unloaded");
	}
	
	/**
	 * Tests to make sure the expected number of peoples and queries were loaded
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testLoaded() throws Exception {
		Assert.assertEquals("The number of test MBeans", 4, JMXHelper.query(PEOPLE_PATTERN).length);
//		Assert.assertEquals("The number of test queries", 3, QueryManager.getInstance().getQuerySize());
		Assert.assertEquals("The number of test value exps", 0, QueryManager.getInstance().getValueSize());
	}
	
	/**
	 * Tests the <b><code>And</code></b>, <b><code>Gt</code></b> and <b><code>Eq</code></b> expressions 
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testAndGtEq() throws Exception {
		QueryExp qe = QueryManager.getInstance().getQuery("SmithsOlderThan30");
		Assert.assertNotNull("The query expression", qe);
		log("\tQuery Expression: [%s]", qe);
		ObjectName[] ons = JMXHelper.query(PEOPLE_PATTERN, qe);
		Assert.assertEquals("The length of the result array", 1, ons.length);
		ObjectName on = ons[0];
		Assert.assertEquals("The firstName property", "John", on.getKeyProperty("firstName"));
		Assert.assertEquals("The lastName property", "Smith", on.getKeyProperty("lastName"));
	}
	
	/**
	 * Tests the <b><code>AnySubstring</code></b> expression 
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testAnySubstring() throws Exception {
		final int XMATCHES = 2;
		QueryExp qe = QueryManager.getInstance().getQuery("LastNamesWithIt");
		Assert.assertNotNull("The query expression", qe);
		log("\tQuery Expression: [%s]", qe);
		ObjectName[] ons = JMXHelper.query(PEOPLE_PATTERN, qe);
		Assert.assertEquals("The length of the result array", XMATCHES, ons.length);
		TreeMap<String, ObjectName> tm = new TreeMap<String, ObjectName>();
		for(ObjectName on: testMBeans) {
			if(!"Smith".equals(on.getKeyProperty("lastName"))) continue;
			tm.put(on.getKeyProperty("firstName"), on);
		}
		Assert.assertArrayEquals("Keyset match", new String[]{"Becky", "John"}, tm.keySet().toArray());
		Assert.assertArrayEquals("Values match", new ObjectName[]{
				JMXHelper.objectName(String.format(PERSON_TEMPLATE, "Smith", "Becky")), 
				JMXHelper.objectName(String.format(PERSON_TEMPLATE, "Smith", "John"))
				}, 
			tm.values().toArray());
	}
	
	/**
	 * Tests the <b><code>Between</code></b> expression 
	 * @throws Exception thrown on any error
	 */
	@Test
	public void testBetween() throws Exception {
		final int XMATCHES = 2;
		QueryExp qe = QueryManager.getInstance().getQuery("AgesBetween27and33");
		Assert.assertNotNull("The query expression", qe);
		log("\tQuery Expression: [%s]", qe);
		ObjectName[] ons = JMXHelper.query(PEOPLE_PATTERN, qe);
		Assert.assertEquals("The length of the result array", XMATCHES, ons.length);
		TreeMap<String, ObjectName> tm = new TreeMap<String, ObjectName>();
		for(ObjectName on: testMBeans) {
			if(!"Smith".equals(on.getKeyProperty("lastName"))) continue;
			tm.put(on.getKeyProperty("firstName"), on);
		}
		Assert.assertArrayEquals("Keyset match", new String[]{"Becky", "John"}, tm.keySet().toArray());
		Assert.assertArrayEquals("Values match", new ObjectName[]{
				JMXHelper.objectName(String.format(PERSON_TEMPLATE, "Smith", "Becky")), 
				JMXHelper.objectName(String.format(PERSON_TEMPLATE, "Smith", "John"))
				}, 
			tm.values().toArray());
	}

}

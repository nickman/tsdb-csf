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
package com.heliosapm.opentsdb.client.jvmjmx.customx;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.QueryExp;
import javax.management.ValueExp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

import com.heliosapm.opentsdb.client.logging.LoggingConfiguration;
import com.heliosapm.utils.xml.XMLHelper;



/**
 * <p>Title: QueryManager</p>
 * <p>Description: Named JMX Query Repository</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.QueryManager</code></p>
 */

public class QueryManager {
	/** The singleton instance */
	private static volatile QueryManager instance = null;
	/** The singleton instance ctor lock */
	private static final Object lock = new Object();

	/** Instance logger */
	private final Logger log = LogManager.getLogger(getClass());
	/** The query cache */
	private final Map<String, QueryExp> queryExpCache = new ConcurrentHashMap<String, QueryExp>();
	/** The named value exp cache */
	private final Map<String, ValueExp> valueExpCache = new ConcurrentHashMap<String, ValueExp>();

	/** The name of a query expr collection xml node */
	public static final String QUERY_COLLECTION_NAME = "queryexps";
	
	public static void main(String[] args) {
		//LoggingConfiguration.getInstance();		
		
		final QueryManager qm = QueryManager.getInstance();
		final Logger LOG = qm.log;
		LOG.info("QueryManager Test");
		final String resource = "./src/test/resources/configs/jmxcollect/jmxcollect.xml";
		final Node rootNode = XMLHelper.parseXML(new File(resource)).getDocumentElement();
		LOG.info("Parsed resource");
		final Node qnode = XMLHelper.getChildNodeByName(rootNode, "queryexps");
		final int cnt = qm.load(qnode);
		LOG.info("Loaded [{}] exp objects", cnt);
	}
	
	/**
	 * Acquires the QueryManager singleton instance
	 * @return the QueryManager singleton instance
	 */
	public static QueryManager getInstance() {
		if(instance==null) {
			synchronized(lock) {
				if(instance==null) {
					instance = new QueryManager(); 
				}
			}
		}
		return instance;
	}
	
	/**
	 * Creates a new QueryManager
	 */
	private QueryManager() {
		log.info("Created JMX QueryManager");
	}
	
	
	/**
	 * Clears the entire query cache
	 */
	public void clear() {
		queryExpCache.clear();
		valueExpCache.clear();
	}
	
	/**
	 * Clears the query cache
	 */
	public void clearQueries() {
		queryExpCache.clear();
	}

	/**
	 * Clears the value cache
	 */
	public void clearValues() {
		valueExpCache.clear();
	}

	/**
	 * Returns the number of queries in the query cache
	 * @return the size of the query cache
	 */
	public int getQuerySize() {
		return queryExpCache.size();
	}
	
	/**
	 * Returns the number of values in the value cache
	 * @return the size of the value cache
	 */
	public int getValueSize() {
		return valueExpCache.size();
	}
	
	
	/**
	 * Returns the QueryExp for the passed ID
	 * @param id The query exp id
	 * @return the QueryExp or null if one was not found
	 */
	public QueryExp getQuery(final String id) {
		if(id==null || id.trim().isEmpty()) throw new IllegalArgumentException("The passed query id was null or empty");
		return queryExpCache.get(id.trim().toLowerCase());
	}
	
	/**
	 * Returns the ValueExp for the passed ID
	 * @param id The value exp id
	 * @return the ValueExp or null if one was not found
	 */
	public ValueExp getValue(final String id) {
		if(id==null || id.trim().isEmpty()) throw new IllegalArgumentException("The passed query id was null or empty");
		return valueExpCache.get(id.trim().toLowerCase());
	}
	
	
	/**
	 * Loads new query and value exp definitions from the passed XML node
	 * @param xmlNode The XML node containing one or more query/value expression
	 * @return the number of loaded objects
	 */
	public int load(final Node xmlNode) {
		int qcnt = 0, vcnt = 0;
		final String nodeName = xmlNode.getNodeName();
		final List<Node> toProcess = new ArrayList<Node>();
		
		if(QUERY_COLLECTION_NAME.equalsIgnoreCase(nodeName)) {			
				toProcess.addAll(XMLHelper.getElementChildNodes(xmlNode));
		} else if(QueryDecode.isQueryDecode(xmlNode.getNodeName())) {
			toProcess.add(xmlNode);
		} else {
			log.warn("QueryExp load XML node name not recognized: [{}]", nodeName);
		}
		if(!toProcess.isEmpty()) {
			for(Node node: toProcess) {
				//log.info("Processing node [{}]", XMLHelper.getStringFromNode(node));
				final String id = XMLHelper.getAttributeValueByName(node, "id");
				if(id==null || id.trim().isEmpty()) continue;
				QueryDecode q = QueryDecode.getNodeType(node);
				log.info("Processing Node Type: [{}]", q);
				if(q==null) continue;
				if(q.isTerminal()) {
					final ValueExp v = (ValueExp)q.eval(XMLHelper.getAttributeMap(node));
					log.info("Compiled ValueExp: [{} / {}]", v.getClass().getSimpleName(), v);
					valueExpCache.put(id, v);					
					vcnt++;
				} else {
					final QueryExp query = (QueryExp)q.invoke(XMLHelper.getElementChildNodes(node).toArray(new Node[0]));
					log.info("Compiled QueryExp: [{} / {}]", query.getClass().getSimpleName(), query);
					queryExpCache.put(id, query);
					qcnt++;
				}
			}
		}
		log.info("Loaded [{}] QueryExps and [{}] ValueExps into cache", qcnt, vcnt);
		return qcnt + vcnt;
	}
	
	
	
}

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
package com.heliosapm.opentsdb.client.jvmjmx.custom;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanFeatureInfo;
import javax.management.ObjectName;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Node;

import com.heliosapm.opentsdb.client.opentsdb.MetricBuilder;
import com.heliosapm.opentsdb.client.opentsdb.OTMetric;
import com.heliosapm.opentsdb.client.opentsdb.jvm.RuntimeMBeanServerConnection;
import com.heliosapm.opentsdb.client.util.XMLHelper;

/**
 * <p>Title: AttributeTracer</p>
 * <p>Description: Extracts the target attribute names and values and traces them to OpenTSDB</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.AttributeTracer</code></p>
 */

public class AttributeTracer {
	/** Instance logger */
	protected static final Logger log = LogManager.getLogger(AttributeTracer.class);
	
	final RuntimeMBeanServerConnection mbs;
	final boolean numericsOnly;
	final boolean dynamic;
	final Pattern attrIncludePattern;
	final Pattern attrExcludePattern;
	final String metricBuilderExpr;
	final ExpressionDataContext dctx;
	final Map<ObjectName, Map<String, OTMetric>> otMetricMap;
	final Set<String> knownMatches = new HashSet<String>();
	/**
	 * Creates a new AttributeTracer
	 * @param tracerNode the trace configuration node
	 * @param dctx The parent monitor's data context
	 */
	public AttributeTracer(final Node tracerNode, final ExpressionDataContext dctx) {
		this.dctx = dctx;
		this.mbs = dctx.focusedMBeanServer();
		// =================== Attr Conf			
		String attrInclude = XMLHelper.getAttributeByName(tracerNode, "include", ".*");
		String attrExclude = XMLHelper.getAttributeByName(tracerNode, "exclude", null);
		numericsOnly = XMLHelper.getAttributeByName(tracerNode, "numericsonly", true);
		dynamic = XMLHelper.getAttributeByName(tracerNode, "dynamic", false);
		attrIncludePattern = Pattern.compile(attrInclude);
		attrExcludePattern = attrExclude==null ? null : Pattern.compile(attrExclude);
		otMetricMap = dynamic ? null : new HashMap<ObjectName, Map<String, OTMetric>>();
		metricBuilderExpr = XMLHelper.getNodeTextValue(tracerNode);
		if(!dynamic) initOTMetrics();
	}
	
	/**
	 * Initializes the OTMetric map if this tracer is not dynamic.
	 */
	void initOTMetrics() {
		for(Map.Entry<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>> entry: dctx.metaData().entrySet()) {
			final ObjectName on = entry.getKey();
			for(Map.Entry<String, MBeanFeatureInfo> attrEntry: entry.getValue().get(MBeanFeature.ATTRIBUTE).entrySet()) {
				final String attrName = attrEntry.getKey();
				if(!matchesAttr(attrName)) continue;
				final MBeanAttributeInfo ainfo = (MBeanAttributeInfo)attrEntry.getValue(); 
				if(numericsOnly && DefaultMonitor.isNumeric(ainfo.getType(), on, mbs)) continue;
				final OTMetric otm =  MetricBuilder.metric(Tokener.resolve(
						dctx.focus(mbs, on, attrName, null), metricBuilderExpr)).build();
				Map<String, OTMetric> mmap = otMetricMap.get(on);
				if(mmap==null) {
					mmap  = new HashMap<String, OTMetric>();
					otMetricMap.put(on, mmap);
				}
				mmap.put(attrName, otm);				
			}
		}
	}
	
	void initMeta() {
		final Map<ObjectName, Map<MBeanFeature, Map<String, MBeanFeatureInfo>>> meta = dctx.metaData();
		
	}
	
	/**
	 * Determines if the passed name matches the attribute include/exclude patterns
	 * @param name The name to test
	 * @return true for match, false otherwise
	 */
	boolean matchesAttr(final String name) {
		if(name==null || name.trim().isEmpty()) return false;
		if(knownMatches.contains(name)) return true;
		final boolean match = attrIncludePattern.matcher(name).matches()
				&& (attrExcludePattern!=null && !attrExcludePattern.matcher(name).matches());
		if(match) knownMatches.add(name);
		return match;
	}

	
	
	/**
	 * Traces the attributes for the MBean registered with the passed ObjectName
	 * @param objectName The ObjectName of the MBean to trace values for
	 */
	public void trace(final ObjectName objectName) {
		if(objectName==null || objectName.isPattern()) return;
		final Map<String, Object> attrValues = dctx.attributeValues().get(objectName);
		if(!dynamic && otMetricMap.containsKey(objectName)) {			
			for(Map.Entry<String, OTMetric> mentry: otMetricMap.get(objectName).entrySet()) {
				mentry.getValue().trace(attrValues.get(mentry.getKey()));
			}
			return;
		}
		for(Map.Entry<String, Object> entry: attrValues.entrySet()) {
			final Object value = entry.getValue();
			if(numericsOnly && DefaultMonitor.isNumber(value)) continue;
			MetricBuilder.metric(Tokener.resolve(
					dctx.focus(mbs, objectName, entry.getKey(), value), metricBuilderExpr))
					.build()
					.trace(value);
		}		
	}
	
	

}

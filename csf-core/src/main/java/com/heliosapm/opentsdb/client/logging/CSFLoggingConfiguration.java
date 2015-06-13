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
package com.heliosapm.opentsdb.client.logging;

import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;

/**
 * <p>Title: CSFLoggingConfiguration</p>
 * <p>Description: </p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.logging.CSFLoggingConfiguration</code></p>
 */

public class CSFLoggingConfiguration extends ConfigurationFactory {

	/**
	 * Creates a new CSFLoggingConfiguration
	 */
	public CSFLoggingConfiguration() {

	}

	/**
	 * {@inheritDoc}
	 * @see org.apache.logging.log4j.core.config.ConfigurationFactory#getSupportedTypes()
	 */
	@Override
	protected String[] getSupportedTypes() {
		return new String[] {".xml", "*"};
	}

	/**
	 * {@inheritDoc}
	 * @see org.apache.logging.log4j.core.config.ConfigurationFactory#getConfiguration(org.apache.logging.log4j.core.config.ConfigurationSource)
	 */
	@Override
	public Configuration getConfiguration(final ConfigurationSource source) {
		try {
			return new XmlConfiguration(new ConfigurationSource(getClass().getClassLoader().getResourceAsStream("log4j/tsdb-csf-log4j2.xml")));
		} catch (Exception ex) {
			return new XmlConfiguration(source);
		}
	}

}

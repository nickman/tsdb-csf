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
package com.heliosapm.opentsdb.client.opentsdb;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Level;


/**
 * <p>Title: ConfigurationReader</p>
 * <p>Description: Utiluty methods to read the configuration</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.opentsdb.ConfigurationReader</code></p>
 */

public class ConfigurationReader {

	/**
	 * Returns the configured string value for the passed property name.
	 * The value is first looked up in {@link System#getProperty(String)}.
	 * If not found, the key is modified to all upper case and replaced all <b></code>.</code></b> characters with <b></code>_</code></b>
	 * and the resulting key is used to look up the environmental variable using {@link System#getenv(String)}.
	 * If a value is still not found, the supplied dedault value is returned as a trimmed string.
	 * @param propName The system property configuration key
	 * @param defaultValue The default value
	 * @return the configured value, or the default value, or null if the conf is not found and the supplied default is null
	 */
	public static String conf(final String propName, final Object defaultValue) {
		String value = System.getProperty(propName);
		if(value==null) {
			value = System.getenv(propName.toUpperCase().replace('.', '_'));
		}
		if(value==null) {
			return defaultValue==null ? null : defaultValue.toString().trim();
		}
		return value.trim();
	}
	
	/**
	 * Returns the String array configuration value for the passed config key.
	 * The value is expected to comma separated values.
	 * All members of the array are trimmed before being returned, and null or empty
	 * items are discarded.
	 * See {@link #conf(String, Object)} for the processing rules.
	 * @param propName The system property configuration key
	 * @param defaultValue The default value
	 * @return the configuration value
	 */
	public static String[] confStrArr(final String propName, final String[] defaultValue) {
		String[] arr = null;
		String value = System.getProperty(propName);
		if(value==null) {
			value = System.getenv(propName.toUpperCase().replace('.', '_'));
		}
		if(value==null) {
			arr = defaultValue==null ? null : defaultValue;
		} else {
			arr = value.split(",");
		}
		final List<String> arrList = new ArrayList<String>(arr.length);
		for(int i = 0; i < arr.length; i++) {
			if(arr[i]!=null && !arr[i].trim().isEmpty()) {
				arrList.add(arr[i].trim());
			}						
		}
		return arrList.toArray(new String[0]);
	}
	
	/**
	 * Returns the boolean configuration value for the passed config key.
	 * See {@link #conf(String, Object)} for the processing rules.
	 * @param propName The system property configuration key
	 * @param defaultValue The default value
	 * @return the configuration value
	 */
	public static boolean confBool(final String propName, final boolean defaultValue) {
		String value = conf(propName, defaultValue);
		return value.equals("true");
	}
	
	/**
	 * Returns the enum configuration value for the passed config key and enum type
	 * See {@link #conf(String, Object)} for the processing rules.
	 * @param enumType The enum type expected
	 * @param propName The system property configuration key
	 * @param defaultValue The default value
	 * @return the configuration value
	 */
	public static <T extends Enum<T>> T confEnum(final Class<T> enumType,  final String propName, final T defaultValue) {
		String value = conf(propName, null);
		if(value==null) return defaultValue;
		return decodeEnum(enumType, value.trim(), defaultValue);
	}
	
	/**
	 * Decodes the passed value into an enum member from the passed enum type
	 * @param enumType The enum type to decode with
	 * @param propValue The value to decode
	 * @param defaultValue The default enum member returned if the value cannot be decoded
	 * @return the decoded enum member of the default value if the value cannot be decoded
	 */
	public static <T extends Enum<T>> T decodeEnum(final Class<T> enumType,  final String propValue, final T defaultValue) {
		for(T en: enumType.getEnumConstants()) {
			if(en.name().equalsIgnoreCase(propValue)) return en;
		}
		return defaultValue;
	}
	
	/**
	 * Returns the int configuration value for the passed config key.
	 * See {@link #conf(String, Object)} for the processing rules.
	 * @param propName The system property configuration key
	 * @param defaultValue The default value
	 * @return the configuration value
	 */
	public static int confInt(final String propName, final int defaultValue) {
		String value = conf(propName, defaultValue);
		try {
			return new Double(value).intValue();
		} catch (Exception ex) {
			return defaultValue;
		}
	}
	
	/**
	 * Returns the long configuration value for the passed config key.
	 * See {@link #conf(String, Object)} for the processing rules.
	 * @param propName The system property configuration key
	 * @param defaultValue The default value
	 * @return the configuration value
	 */
	public static long confLong(final String propName, final long defaultValue) {
		String value = conf(propName, defaultValue);
		try {
			return new Double(value).longValue();
		} catch (Exception ex) {
			return defaultValue;
		}
	}
	
	
	/**
	 * Returns the URI configuration value for the passed config key.
	 * See {@link #conf(String, Object)} for the processing rules.
	 * @param propName The system property configuration key
	 * @param defaultValue The default value
	 * @return the configuration value
	 */
	public static URI confURI(final String propName, final String defaultValue) {
		String value = conf(propName, defaultValue);
		try {
			return new URI(value);
		} catch (Exception ex) {
			try {
				if(defaultValue==null || defaultValue.trim().isEmpty()) return null;
				return new URI(defaultValue);
			} catch (Exception ex2) {
				System.err.println("Failed to convert the supplied value [" + defaultValue + "] to a URI. Programmer Error.");
				throw new RuntimeException("Failed to convert the supplied value [" + defaultValue + "] to a URI. Programmer Error.", ex2);
			}
		}		
	}
	
	/**
	 * Returns the logging level configuration value for the passed config key.
	 * See {@link #conf(String, Object)} for the processing rules.
	 * @param propName The system property configuration key
	 * @param defaultValue The default value
	 * @return the configuration value
	 */
	public static Level confLevel(final String propName, final Level defaultValue) {
		String value = conf(propName, defaultValue.name());
		try {
			Level lev = Level.getLevel(value.trim().toUpperCase());
			return lev;
		} catch (Exception x) {/* No Op */}
		return defaultValue;
	}
	
	
	
}

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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MBeanFeatureInfo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.heliosapm.opentsdb.client.jvmjmx.custom.MBeanFeature.DescriptorFeatureInfo;
import com.heliosapm.opentsdb.client.jvmjmx.custom.Tokener.IntRange;
import com.heliosapm.opentsdb.client.scripts.ScriptType;

/**
 * <p>Title: TokenResolvers</p>
 * <p>Description: Static token resolver definitions</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers</code></p>
 */

public class TokenResolvers {

//	ATTRIBUTE_KEYT("akt", null),
//	ATTRIBUTE_KEYR("akr", null),
//	ATTRIBUTE_VALUE("av", null),
//	OBJECTNAME_KEYT("onkt", null),
//	OBJECTNAME_KEYR("onkr", null),

//	OBJECTNAME_VALUE("onv", null),
//	OBJECTNAME_DOMAIN("od", null),
//	SCRIPT_EXEC("s", null),
//	MBEAN_CLASS("mc", null),
//	MBEAN_DESCRIPTION("md", null),
//	DESCRIPTOR_KEY("ak", null),
//	DESCRIPTOR_VALUE("av", null);
	
	/** Dot splitter pattern */
	public static final Pattern DOT_SPLITTER = Pattern.compile("\\.");

	
	public static abstract class AbstractTokenResolver implements TokenResolver {
		/** Instance logger */
		protected final Logger log = LogManager.getLogger(getClass());

		/**
		 * Resolves the passed token instance values, returning a blank if an exception is thrown.
		 * @param dctx The expression data context
		 * @param key The parsed token key
		 * @param qualifier The parsed token qualifier
		 * @param ranges The parsed token int ranges
		 * @return the resolved value
		 */
		public abstract CharSequence doResolve(final ExpressionDataContext dctx, final String key, final String qualifier, final IntRange... ranges);

		/**
		 * The default value to return if an exception is thrown during the execution of {@link #doResolve(ExpressionDataContext, String, String, IntRange...)}
		 * @return the default value to return
		 */
		public CharSequence getDefaultValue() {
			return "";
		}
		
		/**
		 * Extracts and formats the first found matching key
		 * @param keys The keyset to match against
		 * @param matchPattern The regex pattern to match
		 * @param delim The delimeter between the joined fragments
		 * @return the formatted first match or null if no match was made
		 */
		public String buildFirstMatch(final Set<String> keys, final String matchPattern, final String delim) {
			final Pattern p = Pattern.compile(matchPattern);
			for(String key: keys) {
				final Matcher m = p.matcher(key);
				if(m.matches()) {					
					final int gcount = m.groupCount();
					if(gcount==0) return key;
					final StringBuilder b = new StringBuilder();
					final String d = (delim==null) ? "" : delim.trim();
					for(int i = 1; i <= gcount; i++) {
						b.append(m.group(i)).append(d);
					}
					for(int i = 0; i < d.length(); i++) {
						b.deleteCharAt(b.length()-1);
					}
					return b.toString();
				}
			}
			return null;
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolver#resolve(com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext, java.lang.String, java.lang.String, com.heliosapm.opentsdb.client.jvmjmx.custom.Tokener.IntRange[])
		 */
		@Override
		public CharSequence resolve(final ExpressionDataContext dctx, final String key, final String qualifier, final IntRange... ranges) {
			try {
				return doResolve(dctx, key, qualifier, ranges);
			} catch (Exception ex) {
				log.error("Failed to resolve", ex);
				return "";
			}
		}
	}
	
	/**
	 * <p>Title: KeyValuePairTokenResolver</p>
	 * <p>Description: Base resolver to extract tags from the focused attribute keys that match the regex in the passed key and the corresponding value.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.KeyValuePairTokenResolver</code></p>
	 */
	public abstract static class KeyValuePairTokenResolver extends AbstractTokenResolver {
		/** true to generate tags in the data context, false otherwise */
		private final boolean genTags;
		/** true to return the extracted tags, false otherwise */
		private final boolean returnTags;
		/**
		 * Creates a new KeyValuePairTokenResolver
		 * @param genTags true to generate tags in the data context, false otherwise
		 * @param returnTags true to return the extracted tags, false otherwise 
		 */
		public KeyValuePairTokenResolver(final boolean genTags, final boolean returnTags) {
			this.genTags = genTags;
			this.returnTags = returnTags;
		}

		/**
		 * Returns the map the tags should be extracted from
		 * @param dctx The data context
		 * @return the map
		 */
		public abstract Map<String, Object> getTargetMap(final ExpressionDataContext dctx);


		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.AbstractTokenResolver#doResolve(com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext, java.lang.String, java.lang.String, com.heliosapm.opentsdb.client.jvmjmx.custom.Tokener.IntRange[])
		 */
		@Override
		public CharSequence doResolve(final ExpressionDataContext dctx, final String key, final String qualifier, final IntRange... ranges) {
			final Map<String, Object> targetMap = getTargetMap(dctx);
			final Set<String> attrNames = targetMap.keySet();
			final Pattern attributePattern = Pattern.compile(key);
			final String delim = (qualifier==null || qualifier.trim().isEmpty()) ? "" : qualifier.trim();
			final StringBuilder ret = new StringBuilder();
			for(String attrName: attrNames) {				
				Matcher m = attributePattern.matcher(attrName);
				if(m.matches()) {
					final int gcount = m.groupCount();
					final String value = targetMap.get(attrName).toString();
					if(gcount==0) return attrName;
					StringBuilder b = new StringBuilder();
					for(int i = 1; i <= m.groupCount(); i++) {
						b.append(m.group(i));
						b.append(delim);
					}
					for(int i = 0; i < delim.length(); i++) {
						b.deleteCharAt(b.length()-1);
					}
					if(genTags) {
						dctx.tag(b.toString(), value);
					}
					if(returnTags) {
						ret.append(b.toString()).append("=").append(value).append(",");
					}					
				}
			}
			if(ret.length()>0) ret.deleteCharAt(ret.length()-1);
			return ret.toString();
		}
		
	}
	
	
	/**
	 * <p>Title: AttributeKeyTokenResolver</p>
	 * <p>Description: Extracts tags from the focused attribute keys that match the regex in the passed key and the corresponding value.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.AttributeKeyTokenResolver</code></p>
	 */
	public static class AttributeKeyTokenResolver extends KeyValuePairTokenResolver {
		/**
		 * Creates a new AttributeKeyTokenResolver
		 * @param genTags true to generate tags in the data context, false otherwise
		 * @param returnTags true to return the extracted tags, false otherwise 
		 */
		public AttributeKeyTokenResolver(final boolean genTags, final boolean returnTags) {
			super(genTags, returnTags);
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.KeyValuePairTokenResolver#getTargetMap(com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext)
		 */
		@Override
		public Map<String, Object> getTargetMap(final ExpressionDataContext dctx) {			
			return dctx.attributeValues().get(dctx.focusedObjectName());
		}		
	}
	
	/**
	 * <p>Title: DescriptorKeyTokenResolver</p>
	 * <p>Description: Extracts tags from the focused descriptor keys that match the regex in the passed key and the corresponding value.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.DescriptorKeyTokenResolver</code></p>
	 */
	public static class DescriptorKeyTokenResolver extends KeyValuePairTokenResolver {
		/**
		 * Creates a new DescriptorKeyTokenResolver
		 * @param genTags true to generate tags in the data context, false otherwise
		 * @param returnTags true to return the extracted tags, false otherwise 
		 */
		public DescriptorKeyTokenResolver(final boolean genTags, final boolean returnTags) {
			super(genTags, returnTags);
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.KeyValuePairTokenResolver#getTargetMap(com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext)
		 */
		@Override
		public Map<String, Object> getTargetMap(final ExpressionDataContext dctx) {			
			Map<String, MBeanFeatureInfo> features =  dctx.metaData().get(dctx.focusedObjectName()).get(MBeanFeature.DESCRIPTOR);
			Map<String, Object> values = new HashMap<String, Object>(features.size());
			for(Map.Entry<String, MBeanFeatureInfo> entry: features.entrySet()) {
				DescriptorFeatureInfo dfi = (DescriptorFeatureInfo)entry.getValue();
				values.put(dfi.getName(), dfi.getValue());
			}
			return values;
		}		
	}
	
	
	/**
	 * <p>Title: ObjectNameKeyTokenResolver</p>
	 * <p>Description: Extracts tags from the focused ObjectName that match the regex in the passed key and the corresponding value.</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.ObjectNameKeyTokenResolver</code></p>
	 */
	public static class ObjectNameKeyTokenResolver extends KeyValuePairTokenResolver {
		/**
		 * Creates a new ObjectNameKeyTokenResolver
		 * @param genTags true to generate tags in the data context, false otherwise
		 * @param returnTags true to return the extracted tags, false otherwise 
		 */
		public ObjectNameKeyTokenResolver(final boolean genTags, final boolean returnTags) {
			super(genTags, returnTags);
		}

		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.KeyValuePairTokenResolver#getTargetMap(com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext)
		 */
		@Override
		public Map<String, Object> getTargetMap(final ExpressionDataContext dctx) {
//			final Hashtable<String, String> props = dctx.focusedObjectName().getKeyPropertyList();
//			final Map<String, Object> map = new HashMap<String, Object>(props);
//			for(Map.Entry<String, String> e: props.entrySet()) {
//				map.put(e.getKey(), e.getValue());
//			}
//			return map;
			return new HashMap<String, Object>(dctx.focusedObjectName().getKeyPropertyList());
		}		
	}
	
	public abstract static class MapValueTokenResolver extends AbstractTokenResolver {
		/**
		 * Returns the map the tags should be extracted from
		 * @param dctx The data context
		 * @return the map
		 */
		public abstract Map<String, Object> getTargetMap(final ExpressionDataContext dctx);
		
	}
	
	/**
	 * <p>Title: AttributeValueTokenResolver</p>
	 * <p>Description: Resolver to extract an attribute value for an mbean where the attribute name is the key</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.AttributeValueTokenResolver</code></p>
	 */
	public static class AttributeValueTokenResolver extends MapValueTokenResolver {
		@Override
		public CharSequence doResolve(final ExpressionDataContext dctx, final String key, final String qualifier, final IntRange... ranges) {
			return getTargetMap(dctx).get(key).toString();
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.MapValueTokenResolver#getTargetMap(com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext)
		 */
		@Override
		public Map<String, Object> getTargetMap(final ExpressionDataContext dctx) {			
			return dctx.attributeValues().get(dctx.focusedObjectName());
		}
	}
	
	/**
	 * <p>Title: DescriptorValueTokenResolver</p>
	 * <p>Description: Resolver to extract a descriptor value for an mbean where the descriptor name is the key</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.DescriptorValueTokenResolver</code></p>
	 */
	public static class DescriptorValueTokenResolver extends MapValueTokenResolver {
		@Override
		public CharSequence doResolve(final ExpressionDataContext dctx, final String key, final String qualifier, final IntRange... ranges) {
			return getTargetMap(dctx).get(key).toString();
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.MapValueTokenResolver#getTargetMap(com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext)
		 */
		@Override
		public Map<String, Object> getTargetMap(final ExpressionDataContext dctx) {			
			Map<String, MBeanFeatureInfo> features =  dctx.metaData().get(dctx.focusedObjectName()).get(MBeanFeature.DESCRIPTOR);
			Map<String, Object> values = new HashMap<String, Object>(features.size());
			for(Map.Entry<String, MBeanFeatureInfo> entry: features.entrySet()) {
				DescriptorFeatureInfo dfi = (DescriptorFeatureInfo)entry.getValue();
				values.put(dfi.getName(), dfi.getValue());
			}
			return values;			
		}
	}
	
	
	/**
	 * <p>Title: ObjectNameValueTokenResolver</p>
	 * <p>Description: Resolver to extract an objectName property value for an ObjectName</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.ObjectNameValueTokenResolver</code></p>
	 */
	public static class ObjectNameValueTokenResolver extends MapValueTokenResolver {
		@Override
		public CharSequence doResolve(final ExpressionDataContext dctx, final String key, final String qualifier, final IntRange... ranges) {
			return getTargetMap(dctx).get(key).toString();
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.MapValueTokenResolver#getTargetMap(com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext)
		 */
		@Override
		public Map<String, Object> getTargetMap(final ExpressionDataContext dctx) {			
			return new HashMap<String, Object>(dctx.focusedObjectName().getKeyPropertyList());
		}
	}
	
	/**
	 * <p>Title: StringFragmentTokenResolver</p>
	 * <p>Description: Resolver that returns a string value, or joined fragments of it</p>
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.StringFragmentTokenResolver</code></p>
	 */
	public abstract static class StringFragmentTokenResolver extends AbstractTokenResolver {
		
		/**
		 * Returns the base string value
		 * @param dctx The data comtext
		 * @return the base string value
		 */
		public abstract String getStringValue(final ExpressionDataContext dctx);
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.AbstractTokenResolver#doResolve(com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext, java.lang.String, java.lang.String, com.heliosapm.opentsdb.client.jvmjmx.custom.Tokener.IntRange[])
		 */
		@Override
		public CharSequence doResolve(final ExpressionDataContext dctx, final String key, final String qualifier, final IntRange... ranges) {
			final String domain = getStringValue(dctx);
			if(ranges!=null && ranges.length>0) {
				final String[] frags = DOT_SPLITTER.split(domain);
				final String delim = (qualifier==null ? "" : qualifier);
				final int[] flatRange = IntRange.flattenRanges(ranges);
				final StringBuilder b = new StringBuilder();
				for(int i = 0; i < flatRange.length; i++) {
					try {
						b.append(frags[flatRange[i]]).append(delim);
					} catch (Exception x) {/* No Op */}
				}
				if(b.length() > delim.length()) {
					for(int i = 0; i < delim.length(); i++) {
						b.deleteCharAt(b.length()-1);
					}
				}
				return b.toString();
			} else {
				if(key!=null) {
					return buildFirstMatch(new HashSet<String>(Arrays.asList(domain)), key, (qualifier==null ? "" : qualifier));
				}
				return domain;
			}
		}
	}
	
	
	
	/**
	 * <p>Title: ObjectNameDomainTokenResolver</p>
	 * <p>Description: Resolver that returns the focused ObjectName domain, or joined fragments of it</p>
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.ObjectNameDomainTokenResolver</code></p>
	 */
	public static class ObjectNameDomainTokenResolver extends StringFragmentTokenResolver {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.StringFragmentTokenResolver#getStringValue(com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext)
		 */
		@Override
		public String getStringValue(ExpressionDataContext dctx) {			
			return dctx.focusedObjectName().getDomain();
		}
	}

	/**
	 * <p>Title: MBeanClassNameTokenResolver</p>
	 * <p>Description: Resolver that returns the focused MBean's class name, or joined fragments of it</p>
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.MBeanClassNameTokenResolver</code></p>
	 */
	public static class MBeanClassNameTokenResolver extends StringFragmentTokenResolver {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.StringFragmentTokenResolver#getStringValue(com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext)
		 */
		@Override
		public String getStringValue(ExpressionDataContext dctx) {			
			return dctx.focusedMBeanServer().getMBeanInfo(dctx.focusedObjectName()).getClassName();
		}
	}
	
	/**
	 * <p>Title: MBeanDescriptionTokenResolver</p>
	 * <p>Description: Resolver that returns the focused MBean's description, or joined fragments of it</p>
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.MBeanDescriptionTokenResolver</code></p>
	 */
	public static class MBeanDescriptionTokenResolver extends StringFragmentTokenResolver {
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.StringFragmentTokenResolver#getStringValue(com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext)
		 */
		@Override
		public String getStringValue(ExpressionDataContext dctx) {			
			return dctx.focusedMBeanServer().getMBeanInfo(dctx.focusedObjectName()).getDescription();
		}
	}
	
	
	
	/**
	 * <p>Title: ScriptTokenResolver</p>
	 * <p>Description: Resolves a value by executing the named script</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.ScriptTokenResolver</code></p>
	 */
	public static class ScriptTokenResolver extends AbstractTokenResolver {
		private static final Pattern SLASH_SPLIT = Pattern.compile("/");
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.opentsdb.client.jvmjmx.custom.TokenResolvers.AbstractTokenResolver#doResolve(com.heliosapm.opentsdb.client.jvmjmx.custom.ExpressionDataContext, java.lang.String, java.lang.String, com.heliosapm.opentsdb.client.jvmjmx.custom.Tokener.IntRange[])
		 */
		@Override
		public CharSequence doResolve(final ExpressionDataContext dctx, final String key, final String qualifier, final IntRange... ranges) {
			final ScriptType st = ScriptType.forName(key);
			if(qualifier==null || qualifier.trim().isEmpty()) {
				throw new RuntimeException("No script name passed in qualifier");
			}
			String[] quals = SLASH_SPLIT.split(qualifier.replace(" ", ""));
			Object returnValue = null;
			switch(quals.length) {
				case 1:
					returnValue = st.invoke(quals[0], null, null, new HashMap<String, Object>(Collections.singletonMap("dctx", dctx)));
					break;
				case 2:
					returnValue = st.invoke(quals[0], null, quals[1], new HashMap<String, Object>(Collections.singletonMap("dctx", dctx)));
					break;
				default:
					returnValue = st.invoke(quals[0], quals[1], quals[2], new HashMap<String, Object>(Collections.singletonMap("dctx", dctx)));
			}
			return returnValue==null ? "" : returnValue.toString();
		}
	}
	
}

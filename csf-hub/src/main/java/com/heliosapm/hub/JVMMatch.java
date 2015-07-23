/**
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
 */
package com.heliosapm.hub;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.w3c.dom.Node;

import com.heliosapm.hub.jvmmatchers.AgentPropertyMatcher;
import com.heliosapm.hub.jvmmatchers.DisplayNameMatcher;
import com.heliosapm.hub.jvmmatchers.SystemPropertyMatcher;
import com.heliosapm.shorthand.attach.vm.VirtualMachine;
import com.heliosapm.utils.xml.XMLHelper;

/**
 * <p>Title: JVMMatch</p>
 * <p>Description: A functional enumeration of implemented {@link JVMMatcher}s</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.hub.JVMMatch</code></p>
 */

public enum JVMMatch implements JVMMatcher {
	/** Matches against the VM's display name */
	DISPLAYNAME(DisplayNameMatcher.INSTANCE),
	/** Matches for presence or value match against the VM's agent properties */
	AGENTPROP(AgentPropertyMatcher.INSTANCE),
	/** Matches for presence or value match against the VM's system properties */
	SYSPROP(SystemPropertyMatcher.INSTANCE);
	
	private JVMMatch(final JVMMatcher matcher) {
		this.matcher = matcher;
	}
	
	/**
	 * <p>Title: JMatch</p>
	 * <p>Description: Container class for an XML defined jvm matcher</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.hub.JVMMatch.JMatch</code></p>
	 */
	public static class JMatch implements JVMMatcher {
		//<mountjvm match="GroovyStarter" matcher="displayName" matchkey="">
		final String match;
		final JVMMatcher matcher;
		final String matchKey;
		final Node jvmMatcherNode;
		final Set<String> mountPoints = new HashSet<String>();
		final LinkedHashSet<AppIdFinder> appIdFinders = new LinkedHashSet<AppIdFinder>(); 
		final Node platformNode;
			

		
		private static final JMatch[] EMPTY_ARR = {};
		
		private static final Map<String, AppIdFinder> appIdFinderCache = new ConcurrentHashMap<String, AppIdFinder>(); 
		
		/**
		 * Returns an array of JMatches for each mountjvm element found in the passed root config node
		 * @param rootConfigNode The hub root config node
		 * @return a possibly zero length array of JMatches
		 */
		public static JMatch[] fromNode(final Node rootConfigNode) {
			if(rootConfigNode==null) return EMPTY_ARR;
			final List<Node> matches = XMLHelper.getChildNodesByName(rootConfigNode, "mountjvm", false);
			if(matches.isEmpty()) return EMPTY_ARR;
			final List<JMatch> jmatches = new ArrayList<JMatch>(matches.size());
			for(Node n: matches) {
				jmatches.add(new JMatch(n));
			}
			return jmatches.toArray(new JMatch[0]);
			
		}
		
		/**
		 * Creates a new JMatch
		 * @param jvmMatcherNode The jvm matcher config node
		 */
		public JMatch(final Node jvmMatcherNode) {
			if(jvmMatcherNode==null) throw new IllegalArgumentException("The passed node was null");
			match = XMLHelper.getAttributeByName(jvmMatcherNode, "match", null);
			matchKey = XMLHelper.getAttributeByName(jvmMatcherNode, "matchkey", null);
			matcher = JVMMatch.forName(XMLHelper.getAttributeByName(jvmMatcherNode, "matcher", null));
			this.jvmMatcherNode = jvmMatcherNode;
			for(Node mp: XMLHelper.getChildNodesByName(jvmMatcherNode, "mountpoint", false)) {
				final String s = XMLHelper.getNodeTextValue(mp, "").trim();
				if(!s.isEmpty()) {
					mountPoints.add(XMLHelper.getNodeTextValue(mp));
				}
			}
			final Node finderNode = XMLHelper.getChildNodeByName(jvmMatcherNode, "appidfinders", false);
			if(finderNode!=null) configureFinders(finderNode);
			if(appIdFinders.isEmpty()) throw new IllegalStateException("JMatch created with no AppId finders");
			platformNode = XMLHelper.getChildNodeByName(jvmMatcherNode, "platform-mbeanobserver");
		}
		
		/**
		 * @param finderNode
		 */
		private void configureFinders(final Node finderNode) {
			final String content = XMLHelper.getNodeTextValue(finderNode, "").trim();
			for(String s: content.split(",")) {
				try {
					final String _className = s.trim();
					AppIdFinder finder = appIdFinderCache.get(_className);
					if(finder==null) {
						synchronized(appIdFinderCache) {
							finder = appIdFinderCache.get(_className);
							if(finder==null) {
								@SuppressWarnings("unchecked")
								Class<AppIdFinder> clazz = (Class<AppIdFinder>) Class.forName(_className);
								finder = clazz.newInstance();
								appIdFinderCache.put(_className, finder);								
							}
						}
					}					
					appIdFinders.add(finder);
				} catch (Exception ex) {					
					ex.printStackTrace(System.err);
				}
			}
			
		}
		
		/**
		 * Returns the configured app id finders
		 * @return an array of app id finders
		 */
		public AppIdFinder[] getAppIdFinders() {
			return appIdFinders.toArray(new AppIdFinder[appIdFinders.size()]);
		}
		
		
		/**
		 * Returns the configured mountpoints in an array
		 * @return an array of mountpoint patterns
		 */
		public String[] getMountPoints() {
			return mountPoints.toArray(new String[mountPoints.size()]);
		}
		
		/**
		 * {@inheritDoc}
		 * @see com.heliosapm.hub.JVMMatcher#match(java.lang.String, com.heliosapm.shorthand.attach.vm.VirtualMachine, java.lang.String, java.lang.String)
		 */
		@Override
		public boolean match(final String displayName, final VirtualMachine vm, final String match, final String key) {
			return matcher.match(displayName, vm, match, key);
		}
		
		
		/**
		 * Returns the match value
		 * @return the match
		 */
		public String getMatch() {
			return match;
		}

		/**
		 * Returns the matcher instance
		 * @return the matcher
		 */
		public JVMMatcher getMatcher() {
			return matcher;
		}

		/**
		 * Returns the match key
		 * @return the matchKey
		 */
		public String getMatchKey() {
			return matchKey;
		}

		/**
		 * Returns the jvmmount node
		 * @return the jvmmount node
		 */
		public Node getNode() {
			return jvmMatcherNode;
		}

		/**
		 * Returns the platform MBean collection configuration node
		 * @return the platformNode
		 */
		public Node getPlatformNode() {
			return platformNode;
		}
		
	}
	
	/**
	 * Returns the JVMMatch for the passed name which is trimmed and uppercased
	 * @param name The name to decode
	 * @return the named JVMMatch
	 */
	public static JVMMatcher forName(final String name) {
		if(name==null || name.trim().isEmpty()) throw new IllegalArgumentException("The passed name was null or empty");
		final String _name = name.trim().toUpperCase();
		try {
			return valueOf(_name);
		} catch (Exception ex) {
			throw new IllegalArgumentException("The passed name [" + name + "] is not a valid JVMMatch");
		}
	}
	
	
	
	/** This enum member's matcher */
	public final JVMMatcher matcher;

	/**
	 * {@inheritDoc}
	 * @see com.heliosapm.hub.JVMMatcher#match(java.lang.String, com.heliosapm.shorthand.attach.vm.VirtualMachine, java.lang.String, java.lang.String)
	 */
	@Override
	public boolean match(final String displayName, final VirtualMachine vm, final String match, final String key) {
		return matcher.match(displayName, vm, match, key);
	}
}

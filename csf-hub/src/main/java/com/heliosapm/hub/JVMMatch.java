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
import java.util.List;

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
		final List<Node> childNodes;
		
		private static final JMatch[] EMPTY_ARR = {};
		
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
			childNodes = XMLHelper.getElementChildNodes(jvmMatcherNode);
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
		 * Returns the child nodes in the jvmmatch element
		 * @return the childNodes
		 */
		public Node[] getChildNodes() {
			return childNodes.toArray(new Node[0]);
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

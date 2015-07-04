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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.management.Descriptor;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanFeatureInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;

/**
 * <p>Title: MBeanFeature</p>
 * <p>Description: Enumerates the {@link javax.management.MBeanFeatureInfo}s of an MBean's {@link javax.management.MBeanInfo}</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.customx.MBeanFeature</code></p>
 */

public enum MBeanFeature implements MBeanFeatureInfoGetter {
	/** Enum for MBean Attributes */
	ATTRIBUTE{@Override public MBeanFeatureInfo[] getFeatures(final MBeanInfo info) {return info.getAttributes();}},
	/** Enum for MBean Constructors */
	CONSTRUCTOR{@Override public MBeanFeatureInfo[] getFeatures(final MBeanInfo info) {return info.getConstructors();}},
	/** Enum for MBean Notifications */
	NOTIFICATION{@Override public MBeanFeatureInfo[] getFeatures(final MBeanInfo info) {return info.getNotifications();}},
	/** Enum for MBean Operations */
	OPERATION{@Override public MBeanFeatureInfo[] getFeatures(final MBeanInfo info) {return info.getOperations();}},
	/** Enum for MBean Parameters */
	PARAMETER{@Override public MBeanFeatureInfo[] getFeatures(final MBeanInfo info) {return DescriptorFeatureInfo.EMPTY_ARR;}},
	/** Enum for MBean Feature Info Descriptors */
	DESCRIPTOR{@Override public MBeanFeatureInfo[] getFeatures(final MBeanInfo info) {return DescriptorFeatureInfo.build(info);}};
	
	/**
	 * Returns an array of the features needed for collection
	 * @return an array of features
	 */
	public static MBeanFeature[] getCollectionFeatures() {
		return new MBeanFeature[]{ATTRIBUTE, DESCRIPTOR};
	}
	
	/**
	 * <p>Title: DescriptorFeatureInfo</p>
	 * <p>Description: A synthetic {@link MBeanFeatureInfo} implementation to wrap an MBean's descriptors</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.jvmjmx.custom.MBeanFeature.DescriptorFeatureInfo</code></p>
	 */
	public static class DescriptorFeatureInfo extends MBeanFeatureInfo {
		/**  */
		private static final long serialVersionUID = -2703557695845743834L;

		/** An empty arr const */
		public static final DescriptorFeatureInfo[] EMPTY_ARR = {};
		
		/** The source of the descriptor */
		public final MBeanFeature descriptorType;
		
		/**
		 * Generates an array of DescriptorFeatureInfos from the passed MBeanInfo
		 * @param mbeanInfo The mbean info to generate from
		 * @return the array of DescriptorFeatureInfos
		 */
		public static DescriptorFeatureInfo[] build(final MBeanInfo mbeanInfo) {
			if(mbeanInfo==null) return EMPTY_ARR;
			Set<DescriptorFeatureInfo> set = new HashSet<DescriptorFeatureInfo>();
			Collections.addAll(set, build(DESCRIPTOR, mbeanInfo.getDescriptor(), mbeanInfo.getDescription()));
			for(MBeanAttributeInfo info: mbeanInfo.getAttributes()) {
				Collections.addAll(set, build(ATTRIBUTE, info.getDescriptor(), info.getDescription()));
			}
			for(MBeanOperationInfo info: mbeanInfo.getOperations()) {
				Collections.addAll(set, build(OPERATION, info.getDescriptor(), info.getDescription()));
			}
			for(MBeanNotificationInfo info: mbeanInfo.getNotifications()) {
				Collections.addAll(set, build(NOTIFICATION, info.getDescriptor(), info.getDescription()));
			}			
			return set.toArray(new DescriptorFeatureInfo[set.size()]);
		}
		
		/**
		 * Generates an array of DescriptorFeatureInfos from the passed descriptor
		 * @param descriptorType Indicates the source of the descriptor
		 * @param descriptor The descriptor
		 * @param mbeanDescription The description prefix
		 * @return the array of DescriptorFeatureInfos
		 */
		public static DescriptorFeatureInfo[] build(final MBeanFeature descriptorType, final Descriptor descriptor, final String mbeanDescription) {
			if(descriptor==null) return EMPTY_ARR;
			Set<DescriptorFeatureInfo> set = new HashSet<DescriptorFeatureInfo>(descriptor.getFieldNames().length);
			for(String key: descriptor.getFieldNames()) {
				final String descr = mbeanDescription==null ?
					("DescriptorFeatureInfo for " + key) :
					(mbeanDescription + " Descriptor for " + key);
				set.add(new DescriptorFeatureInfo(descriptorType, key, descr, descriptor));
			}
			return set.toArray(new DescriptorFeatureInfo[set.size()]);
		}
		
		/**
		 * Creates a new DescriptorFeatureInfo
		 * @param descriptorType Indicates the source of the descriptor
		 * @param name The descriptor key
		 * @param description The descriptor description
		 * @param descriptor The underlying descriptor
		 */
		public DescriptorFeatureInfo(final MBeanFeature descriptorType, final String name, final String description, final Descriptor descriptor) {
			super(name, description, descriptor);
			this.descriptorType = descriptorType;
		}
		
		/**
		 * Returns the descriptor's value for this instance's field name
		 * @return the value
		 */
		public Object getValue() {
			return getDescriptor().getFieldValue(name);
		}
		
		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			StringBuilder b = new StringBuilder("DescriptorFeatureInfo [");
			b.append("\n\tName:").append(name)
			.append("\n\tValue:").append(getValue());
			return b.append("\n]").toString();
		}

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result
					+ ((getDescriptor() == null) ? 0 : getDescriptor().hashCode());
			result = prime
					* result
					+ ((descriptorType == null) ? 0 : descriptorType.hashCode());
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			return result;
		}

		/**
		 * {@inheritDoc}
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (!super.equals(obj))
				return false;
			if (getClass() != obj.getClass())
				return false;
			DescriptorFeatureInfo other = (DescriptorFeatureInfo) obj;
			if (getDescriptor() == null) {
				if (other.getDescriptor() != null)
					return false;
			} else if (!getDescriptor().equals(other.getDescriptor()))
				return false;
			if (descriptorType != other.descriptorType)
				return false;
			if (name == null) {
				if (other.name != null)
					return false;
			} else if (!name.equals(other.name))
				return false;
			return true;
		}
		
		
		
		
		
	}
}

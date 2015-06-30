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

import javax.management.ObjectName;

import com.heliosapm.utils.jmx.JMXHelper;

/**
 * <p>Title: MBeans</p>
 * <p>Description: A bunch of MBean implementations to test the QueryManager</p> 
 * <p>Company: Helios Development Group LLC</p>
 * @author Whitehead (nwhitehead AT heliosdev DOT org)
 * <p><code>com.heliosapm.opentsdb.client.query.MBeans</code></p>
 */

public abstract class MBeans {
	
	/**
	 * Creates a new Person and registers their MBean
	 * @param lastName The last name
	 * @param firstName The first name
	 * @param age The age in years
	 * @param salary The salary
	 * @param active The active flag
	 * @param lastActive The last active date as a long UTC
	 * @return the created person
	 */
	public static PersonMBean newPerson(String lastName, String firstName, int age, double salary, boolean active, long lastActive) {
		return new Person(lastName, firstName, age, salary, active, lastActive);
	}


	/**
	 * <p>Title: PersonMBean</p>
	 * <p>Description: Defines the Person MBean interface</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.query.MBeans.PersonMBean</code></p>
	 */
	public static interface PersonMBean {
		/**
		 * Returns the person's JMX ObjectName
		 * @return the person's JMX ObjectName
		 */
		public ObjectName getObjectName();
		/**
		 * Returns the last name
		 * @return the lastName
		 */
		public String getLastName();
		/**
		 * Returns the first name
		 * @return the firstName
		 */
		public String getFirstName();
		/**
		 * Returns the ageb
		 * @return the age
		 */
		public int getAge();
		/**
		 * Returns the salary
		 * @return the salary
		 */
		public double getSalary();
		/**
		 * Returns the active state
		 * @return the active
		 */
		public boolean isActive();
		/**
		 * Returns the last active date as a long UTC
		 * @return the lastActive
		 */
		public long getLastActive();
	}
	
	/**
	 * <p>Title: Person</p>
	 * <p>Description: The Person bean</p> 
	 * <p>Company: Helios Development Group LLC</p>
	 * @author Whitehead (nwhitehead AT heliosdev DOT org)
	 * <p><code>com.heliosapm.opentsdb.client.query.MBeans.Person</code></p>
	 */
	public static class Person implements PersonMBean {
		private String lastName;
		private String firstName;
		private int age;
		private double salary;
		private boolean active;
		private long lastActive;
		private ObjectName objectName = null;
		
		/**
		 * Creates a new Person
		 * @param lastName The last name
		 * @param firstName The first name
		 * @param age The age in years
		 * @param salary The salary
		 * @param active The active flag
		 * @param lastActive The last active date as a long UTC
		 */
		private Person(String lastName, String firstName, int age, double salary, boolean active, long lastActive) {			
			this.lastName = lastName;
			this.firstName = firstName;
			this.age = age;
			this.salary = salary;
			this.active = active;
			this.lastActive = lastActive;
			objectName = JMXHelper.objectName(new StringBuilder("all.the.peoples:lastName=").append(lastName).append(",firstName=").append(firstName));
			JMXHelper.registerMBean(objectName, this);
		}
		
		/**
		 * Returns the person's JMX ObjectName
		 * @return the person's JMX ObjectName
		 */
		public ObjectName getObjectName() {
			return objectName;
		}
		
		/**
		 * Returns the last name
		 * @return the lastName
		 */
		public String getLastName() {
			return lastName;
		}
		/**
		 * Returns the first name
		 * @return the firstName
		 */
		public String getFirstName() {
			return firstName;
		}
		/**
		 * Returns the ageb
		 * @return the age
		 */
		public int getAge() {
			return age;
		}
		/**
		 * Returns the salary
		 * @return the salary
		 */
		public double getSalary() {
			return salary;
		}
		/**
		 * Returns the active state
		 * @return the active
		 */
		public boolean isActive() {
			return active;
		}
		/**
		 * Returns the last active date as a long UTC
		 * @return the lastActive
		 */
		public long getLastActive() {
			return lastActive;
		}
		
		
		
	}
	
	private MBeans() {}

}

/*
 * Copyright 2010, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.seam;

import org.jboss.seam.ScopeType;

/**
 * Replacement class for Seam's {@link org.jboss.seam.Component}.
 * Tests that use the {@link SeamAutowire} class will use this class instead of
 * Seam's one to request components.
 *
 * @author Carlos Munoz <a href="mailto:camunoz@redhat.com">camunoz@redhat.com</a>
 */
public class AutowireComponent
{
   private static SeamAutowire instance;

   public static void setInstance(SeamAutowire instance)
   {
      AutowireComponent.instance = instance;
   }

   public static Object getInstance(String name, boolean create, boolean allowAutocreation)
   {
      return SeamAutowire.instance().getComponent(name);
   }

   public static Object getInstance(String name, ScopeType scope, boolean create, boolean allowAutocreation)
   {
      return SeamAutowire.instance().getComponent(name);
   }

   public static Object getInstance(Class<?> clazz, ScopeType scopeType)
   {
      return SeamAutowire.instance().autowire(clazz);
   }

   private static Object getInstance(Class<?> clazz)
   {
      return SeamAutowire.instance().autowire(clazz);
   }

   // Seam 2.2.0
   public static Object getInstance(String name, boolean create, boolean allowAutoCreation, Object result)
   {
      return SeamAutowire.instance().getComponent(name);
   }

   // Seam 2.2.2
   public static Object getInstance(String name, boolean create, boolean allowAutoCreation, Object result, ScopeType scopeType)
   {
      return SeamAutowire.instance().getComponent(name);
   }

}

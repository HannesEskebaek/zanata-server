/*
 * Copyright 2012, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.zanata.webtrans.client.ui.table.cell;

import java.util.Set;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.ui.UIObject;

/**
 * 
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
 * 
 * @param <W> the column type
 **/
public class StaticWidgetCell<W extends UIObject> extends AbstractCell<W>
{

   public StaticWidgetCell()
   {
      super();
   }

   public StaticWidgetCell(Set<String> consumedEvents)
   {
      super(consumedEvents);
   }

   public StaticWidgetCell(String... consumedEvents)
   {
      super(consumedEvents);
   }

   @Override
   public void render(Context context, W value, SafeHtmlBuilder sb)
   {
      sb.appendHtmlConstant(value.getElement().getString());
   }
}

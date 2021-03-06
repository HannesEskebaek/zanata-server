package org.zanata.action;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.AutoCreate;
import org.jboss.seam.annotations.Logger;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.log.Log;

@Name("breadcrumbs")
@AutoCreate
@Scope(ScopeType.PAGE)
public class Breadcrumbs implements Serializable
{
   private static final long serialVersionUID = 1L;
   
   @Logger
   Log log;

   private List<Breadcrumb> locations;


   public List<Breadcrumb> getLocations()
   {
      if (locations == null)
      {
         locations = new ArrayList<Breadcrumb>();
      }
      return locations;
   }

   public void clear()
   {
      getLocations().clear();
   }

   public Breadcrumb addLocation(String location, String display)
   {
      Breadcrumb bc = new Breadcrumb(location, display);
      getLocations().add(bc);
      return bc;
   }

   public Breadcrumb addLocation(String location, String display, int index)
   {
      Breadcrumb bc = new Breadcrumb(location, display);
      getLocations().add(index, bc);
      return bc;
   }
   
   public static class Breadcrumb implements Serializable
   {
      private static final long serialVersionUID = 1L;
      private String location;
      private String display;
      private Map<String, String> params = new LinkedHashMap<String, String>();
      
      private Breadcrumb(String location, String display)
      {
         this.location = location;
         this.display = display;
      }

      public String getLocation()
      {
         return location;
      }

      public String getDisplay()
      {
         return display;
      }

      public Breadcrumb param(String name, String value)
      {
         params.put(name, value);
         return this;
      }

      public Map<String, String> getParams()
      {
         return params;
      }
   }

}

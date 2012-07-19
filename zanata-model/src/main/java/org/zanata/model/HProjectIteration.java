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
package org.zanata.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MapKey;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.Where;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.FieldBridge;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.validator.NotNull;
import org.jboss.seam.annotations.security.Restrict;
import org.zanata.common.EntityStatus;
import org.zanata.hibernate.search.GroupSearchBridge;
import org.zanata.model.type.EntityStatusType;
import org.zanata.rest.dto.ProjectIteration;

import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 
 * @see ProjectIteration
 * 
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@TypeDef(name = "entityStatus", typeClass = EntityStatusType.class)
@Restrict
@Indexed
@Setter
@NoArgsConstructor
@ToString(callSuper = true, of = {"project"})
public class HProjectIteration extends SlugEntityBase
{

   private static final long serialVersionUID = 182037127575991478L;
   private HIterationProject project;

   private HProjectIteration parent;
   private List<HProjectIteration> children;

   private Map<String, HDocument> documents;
   private Map<String, HDocument> allDocuments;

   private boolean overrideLocales = false;
   private Set<HLocale> customizedLocales;

   public boolean getOverrideLocales()
   {
      return this.overrideLocales;
   }

   @ManyToOne
   @NotNull
   // TODO PERF @NaturalId(mutable=false) for better criteria caching
   @NaturalId
   @Field
   @FieldBridge(impl = GroupSearchBridge.class)
   public HIterationProject getProject()
   {
      return project;
   }

   @ManyToMany
   @JoinTable(name = "HProjectIteration_Locale", joinColumns = @JoinColumn(name = "projectIterationId"), inverseJoinColumns = @JoinColumn(name = "localeId"))
   public Set<HLocale> getCustomizedLocales()
   {
      if (customizedLocales == null)
         customizedLocales = new HashSet<HLocale>();
      return customizedLocales;
   }

   @OneToMany(mappedBy = "parent")
   public List<HProjectIteration> getChildren()
   {
      return children;
   }

   @ManyToOne
   @JoinColumn(name = "parentId")
   public HProjectIteration getParent()
   {
      return parent;
   }

   @OneToMany(mappedBy = "projectIteration", cascade = CascadeType.ALL)
   @MapKey(name = "docId")
   @Where(clause = "obsolete=0")
   // TODO add an index for path, name
   @OrderBy("path, name")
   public Map<String, HDocument> getDocuments()
   {
      if (documents == null)
         documents = new HashMap<String, HDocument>();
      return documents;
   }

   @OneToMany(mappedBy = "projectIteration", cascade = CascadeType.ALL)
   @MapKey(name = "docId")
   // even obsolete documents
   public Map<String, HDocument> getAllDocuments()
   {
      if (allDocuments == null)
         allDocuments = new HashMap<String, HDocument>();
      return allDocuments;
   }
   
   public boolean isWritable()
   {
      return (project.getStatus().equals(EntityStatus.ACTIVE) && getStatus().equals(EntityStatus.ACTIVE));
   }


}

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
package org.zanata.webtrans.server.rpc;

import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import net.customware.gwt.dispatch.server.ExecutionContext;
import net.customware.gwt.dispatch.shared.ActionException;

import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.contexts.Contexts;
import org.jboss.seam.security.management.JpaIdentityStore;
import org.jboss.seam.web.ServletContexts;
import org.zanata.common.EntityStatus;
import org.zanata.dao.ProjectDAO;
import org.zanata.dao.ProjectIterationDAO;
import org.zanata.model.HAccount;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.security.ZanataIdentity;
import org.zanata.service.GravatarService;
import org.zanata.service.LocaleService;
import org.zanata.webtrans.server.ActionHandlerFor;
import org.zanata.webtrans.server.TranslationWorkspace;
import org.zanata.webtrans.server.TranslationWorkspaceManager;
import org.zanata.webtrans.shared.auth.EditorClientId;
import org.zanata.webtrans.shared.auth.Identity;
import org.zanata.webtrans.shared.model.Person;
import org.zanata.webtrans.shared.model.PersonId;
import org.zanata.webtrans.shared.model.UserWorkspaceContext;
import org.zanata.webtrans.shared.model.WorkspaceId;
import org.zanata.webtrans.shared.rpc.ActivateWorkspaceAction;
import org.zanata.webtrans.shared.rpc.ActivateWorkspaceResult;
import org.zanata.webtrans.shared.rpc.EnterWorkspace;

@Name("webtrans.gwt.ActivateWorkspaceHandler")
@Scope(ScopeType.STATELESS)
@ActionHandlerFor(ActivateWorkspaceAction.class)
@Slf4j
public class ActivateWorkspaceHandler extends AbstractActionHandler<ActivateWorkspaceAction, ActivateWorkspaceResult>
{
   @In
   private TranslationWorkspaceManager translationWorkspaceManager;

   @In
   private GravatarService gravatarServiceImpl;

   @In
   private ProjectDAO projectDAO;
   
   @In
   private ProjectIterationDAO projectIterationDAO;

   @In
   private LocaleService localeServiceImpl;
   
   private static long nextEditorClientIdNum = 0;

   @Synchronized
   private static long generateEditorClientNum()
   {
      return nextEditorClientIdNum++;
   }

   @Override
   public ActivateWorkspaceResult execute(ActivateWorkspaceAction action, ExecutionContext context) throws ActionException
   {
      ZanataIdentity.instance().checkLoggedIn();
      Person person = retrievePerson();

      WorkspaceId workspaceId = action.getWorkspaceId();
      TranslationWorkspace workspace = translationWorkspaceManager.getOrRegisterWorkspace(workspaceId);
      String httpSessionId = ServletContexts.instance().getRequest().getSession().getId();
      EditorClientId editorClientId = new EditorClientId(httpSessionId, generateEditorClientNum());
      workspace.addEditorClient(httpSessionId, editorClientId, person.getId());
      // Send EnterWorkspace event to clients
      EnterWorkspace event = new EnterWorkspace(editorClientId, person);
      workspace.publish(event);
      
      HLocale locale = localeServiceImpl.getByLocaleId(workspaceId.getLocaleId());
      HProject project = projectDAO.getBySlug(workspaceId.getProjectIterationId().getProjectSlug());
      HProjectIteration projectIteration = projectIterationDAO.getBySlug(workspaceId.getProjectIterationId().getProjectSlug(), workspaceId.getProjectIterationId().getIterationSlug());
      
      boolean isProjectActive = isProjectIterationActive(project.getStatus(), projectIteration.getStatus());
      boolean hasWriteAccess = hasPermission(project, locale);
      
      Identity identity = new Identity(editorClientId, person);
      UserWorkspaceContext userWorkspaceContext = new UserWorkspaceContext(workspace.getWorkspaceContext(), isProjectActive, hasWriteAccess);
      return new ActivateWorkspaceResult(userWorkspaceContext, identity);
   }
   
   private boolean hasPermission(HProject project, HLocale locale)
   {
      return ZanataIdentity.instance().hasPermission("modify-translation", project, locale);
   }
   
   private boolean isProjectIterationActive(EntityStatus projectStatus, EntityStatus iterStatus)
   {
      return (projectStatus.equals(EntityStatus.ACTIVE) && iterStatus.equals(EntityStatus.ACTIVE));
   }
   
   @Override
   public void rollback(ActivateWorkspaceAction action, ActivateWorkspaceResult result, ExecutionContext context) throws ActionException
   {
   }

   private Person retrievePerson()
   {
      HAccount authenticatedAccount = (HAccount) Contexts.getSessionContext().get(JpaIdentityStore.AUTHENTICATED_USER);
      return new Person(new PersonId(authenticatedAccount.getUsername()), authenticatedAccount.getPerson().getName(), gravatarServiceImpl.getUserImageUrl(16, authenticatedAccount.getPerson().getEmail()));
   }

}
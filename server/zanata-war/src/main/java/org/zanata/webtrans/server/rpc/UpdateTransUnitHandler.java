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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import javax.annotation.Nullable;

import net.customware.gwt.dispatch.server.ExecutionContext;
import net.customware.gwt.dispatch.shared.ActionException;

import org.hibernate.Session;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Logger;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.contexts.Contexts;
import org.jboss.seam.log.Log;
import org.jboss.seam.log.Logging;
import org.jboss.seam.security.management.JpaIdentityStore;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.dao.ProjectDAO;
import org.zanata.dao.ProjectIterationDAO;
import org.zanata.dao.TextFlowTargetHistoryDAO;
import org.zanata.exception.ZanataServiceException;
import org.zanata.model.HAccount;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.rest.service.ResourceUtils;
import org.zanata.security.ZanataIdentity;
import org.zanata.service.LocaleService;
import org.zanata.webtrans.server.ActionHandlerFor;
import org.zanata.webtrans.server.TranslationWorkspace;
import org.zanata.webtrans.server.TranslationWorkspaceManager;
import org.zanata.webtrans.shared.model.DocumentId;
import org.zanata.webtrans.shared.model.TransUnit;
import org.zanata.webtrans.shared.rpc.TransUnitUpdated;
import org.zanata.webtrans.shared.rpc.UpdateTransUnit;
import org.zanata.webtrans.shared.rpc.UpdateTransUnitResult;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

@Name("webtrans.gwt.UpdateTransUnitHandler")
@Scope(ScopeType.STATELESS)
@ActionHandlerFor(UpdateTransUnit.class)
public class UpdateTransUnitHandler extends AbstractActionHandler<UpdateTransUnit, UpdateTransUnitResult>
{
   // security actions (to be implemented)
   // private static final String ACTION_ADD_TRANSLATION = "add-translation";
   private static final String ACTION_MODIFY_TRANSLATION = "modify-translation";
   // private static final String ACTION_REMOVE_TRANSLATION =
   // "remove-translation";
   // private static final String ACTION_APPROVE_TRANSLATION =
   // "approve-translation";

   @Logger
   Log log;

   @In
   private ResourceUtils resourceUtils;

   @In
   Session session;

   @In
   ZanataIdentity identity;
   
   @In(value = JpaIdentityStore.AUTHENTICATED_USER, scope = ScopeType.SESSION)
   HAccount authenticatedAccount;

   @In
   ProjectDAO projectDAO;

   @In
   ProjectIterationDAO projectIterationDAO;

   @In
   private TextFlowTargetHistoryDAO textFlowTargetHistoryDAO;

   @In
   TranslationWorkspaceManager translationWorkspaceManager;

   @In
   private LocaleService localeServiceImpl;

   // NB SimpleDateFormat is not thread safe! (we could use a ThreadLocal)
   private SimpleDateFormat simpleDateFormat = new SimpleDateFormat();

   /**
    * Used by Seam
    */
   public UpdateTransUnitHandler()
   {
   }

   /**
    * Used for tests
    */
   public UpdateTransUnitHandler(
         Session session,
         ZanataIdentity identity,
         ProjectDAO projectDAO,
         ProjectIterationDAO projectIterationDAO,
         TextFlowTargetHistoryDAO textFlowTargetHistoryDAO,
         TranslationWorkspaceManager translationWorkspaceManager,
         LocaleService localeServiceImpl,
         HAccount authenticatedAccount)
   {
      this.log = Logging.getLog(UpdateTransUnitHandler.class);
      this.session = session;
      this.identity = identity;
      this.projectDAO = projectDAO;
      this.projectIterationDAO = projectIterationDAO;
      this.textFlowTargetHistoryDAO = textFlowTargetHistoryDAO;
      this.translationWorkspaceManager = translationWorkspaceManager;
      this.localeServiceImpl = localeServiceImpl;
      this.authenticatedAccount = authenticatedAccount;
   }

   @Override
   public UpdateTransUnitResult execute(UpdateTransUnit action, ExecutionContext context) throws ActionException
   {
      identity.checkLoggedIn();
      log.debug("Updating TransUnit {0}: locale {1}, state {2}, content '{3}'", action.getTransUnitId(), action.getWorkspaceId().getLocaleId(), action.getContentState(), action.getContents());

      TranslationWorkspace workspace = translationWorkspaceManager.getOrRegisterWorkspace(action.getWorkspaceId());

      if (workspace.getWorkspaceContext().isReadOnly())
      {
         throw new ActionException("Project or version is read-only");
      }

      HTextFlow hTextFlow = (HTextFlow) session.get(HTextFlow.class, action.getTransUnitId().getValue());
      HProject hProject = projectDAO.getBySlug( action.getWorkspaceId().getProjectIterationId().getProjectSlug() );
      LocaleId locale = action.getWorkspaceId().getLocaleId();
      
      HLocale hLocale;
      try
      {
         hLocale = localeServiceImpl.validateLocaleByProjectIteration(action.getWorkspaceId().getLocaleId(), action.getWorkspaceId().getProjectIterationId().getProjectSlug(), action.getWorkspaceId().getProjectIterationId().getIterationSlug());
      }
      catch (ZanataServiceException e)
      {
         throw new ActionException(e.getMessage());
      }

      identity.checkPermission(ACTION_MODIFY_TRANSLATION, hLocale, hProject);

      HTextFlowTarget target = hTextFlow.getTargets().get(hLocale);

      if (action.isRedo())
      {
         if (target == null)
         {
            throw new ActionException("Redo Failure due to empty string.");
         }
         if (!target.getVersionNum().equals(action.getVerNum()))
         {
            if (!target.getLastModifiedBy().getAccount().getUsername().equals(authenticatedAccount.getUsername()) || textFlowTargetHistoryDAO.findConflictInHistory(target, action.getVerNum(), authenticatedAccount.getUsername()))
            {
               throw new ActionException("Find conflict, Redo Failure.");
            }
         }
      }

      boolean targetChanged = false;

      ContentState prevStatus;
      if (target != null)
      {
         prevStatus = target.getState();
      }
      else
      {
         prevStatus = ContentState.New;
         target = new HTextFlowTarget(hTextFlow, hLocale);
         target.setVersionNum(0); // this will be incremented when content is
                                  // set (below)
         hTextFlow.getTargets().put(hLocale, target);
         targetChanged = true;
      }

      Collection<String> emptyContents = Collections2.filter(action.getContents(), new Predicate<String>()
      {
         @Override
         public boolean apply(@Nullable String input)
         {
            return Strings.isNullOrEmpty(input);
         }
      });

      if (action.getContentState() == ContentState.New && emptyContents.isEmpty())
      {
         log.error("invalid ContentState New for TransUnit {0} with content '{1}', assuming NeedReview", action.getTransUnitId(), action.getContents());
         target.setState(ContentState.NeedReview);
      }
      else if (action.getContentState() == ContentState.Approved && emptyContents.size() > 0)
      {
         log.error("invalid ContentState {0} for empty TransUnit {1}, assuming New", action.getContentState(), action.getTransUnitId());
         target.setState(ContentState.New);
      }
      else
      {
         target.setState(action.getContentState());
         if (prevStatus != action.getContentState())
         {
            targetChanged = true;
         }
      }

      ArrayList<String> contents = Lists.newArrayList(target.getContents());
      UpdateTransUnit previous = new UpdateTransUnit(action.getTransUnitId(), contents, prevStatus);

      if (!action.getContents().equals(target.getContents()))
      {
         target.setContents(action.getContents());
         targetChanged = true;
      }

      if (targetChanged)
      {
         target.setVersionNum(target.getVersionNum() + 1);
         target.setTextFlowRevision(hTextFlow.getRevision());
         log.debug("last modified by :" + authenticatedAccount.getPerson().getName());
         target.setLastModifiedBy(authenticatedAccount.getPerson());
      }

      session.flush();

      int wordCount = hTextFlow.getWordCount().intValue();
      // @formatter:off
      
      String msgContext = null;
      if(hTextFlow.getPotEntryData() != null) 
      {
         msgContext = hTextFlow.getPotEntryData().getContext();
      }

      ArrayList<String> sourceContents = GwtRpcUtil.getSourceContents(hTextFlow);
      TransUnit tu = new TransUnit(
            action.getTransUnitId(), 
            hTextFlow.getResId(),
            locale, 
            hTextFlow.isPlural(),
            sourceContents,
            CommentsUtil.toString(hTextFlow.getComment()),
            action.getContents(), 
            target.getState(),
            authenticatedAccount.getPerson().getName(),
            simpleDateFormat.format(new Date()), msgContext, hTextFlow.getPos());
      // @formatter:on
      TransUnitUpdated event = new TransUnitUpdated(new DocumentId(hTextFlow.getDocument().getId()), wordCount, prevStatus, tu, identity.getCredentials().getUsername());

      workspace.publish(event);

      UpdateTransUnitResult result = new UpdateTransUnitResult(true);
      result.setPrevious(previous);
      result.setCurrentVersionNum(target.getVersionNum());

      return result;
   }

   @Override
   public void rollback(UpdateTransUnit action, UpdateTransUnitResult result, ExecutionContext context) throws ActionException
   {
      ZanataIdentity.instance().checkLoggedIn();
      log.debug("revert TransUnit {0}: locale {1}, state {2}, content '{3}'", action.getTransUnitId(), action.getWorkspaceId().getLocaleId(), action.getContentState(), action.getContents());

      HTextFlow hTextFlow = (HTextFlow) session.get(HTextFlow.class, action.getTransUnitId().getValue());
      LocaleId locale = action.getWorkspaceId().getLocaleId();
      HLocale hLocale;
      try
      {
         hLocale = localeServiceImpl.validateLocaleByProjectIteration(action.getWorkspaceId().getLocaleId(), action.getWorkspaceId().getProjectIterationId().getProjectSlug(), action.getWorkspaceId().getProjectIterationId().getIterationSlug());
      }
      catch (ZanataServiceException e)
      {
         throw new ActionException(e.getMessage());
      }

      HProject hProject = hTextFlow.getDocument().getProjectIteration().getProject();
      identity.checkPermission(ACTION_MODIFY_TRANSLATION, hLocale, hProject);
      HAccount authenticatedAccount = (HAccount) Contexts.getSessionContext().get(JpaIdentityStore.AUTHENTICATED_USER);

      HTextFlowTarget target = hTextFlow.getTargets().get(hLocale);

      if (target == null)
      {
         throw new ActionException("Undo Failure due to empty string.");
      }

      if (!target.getVersionNum().equals(result.getCurrentVersionNum()))
      {
         if (!target.getLastModifiedBy().getAccount().getUsername().equals(authenticatedAccount.getUsername()) || textFlowTargetHistoryDAO.findConflictInHistory(target, result.getCurrentVersionNum(), authenticatedAccount.getUsername()))
         {
            throw new ActionException("Find conflict, Undo Failure.");
         }
      }

      ContentState prevStatus = target.getState();

      if (!result.getPrevious().getContents().equals(target.getContents()))
      {
         target.setState(result.getPrevious().getContentState());
         target.setContents(result.getPrevious().getContents());
         target.setVersionNum(target.getVersionNum() + 1);
         target.setLastModifiedBy(authenticatedAccount.getPerson());
      }

      session.flush();

      int wordCount = hTextFlow.getWordCount().intValue();
      String msgContext = null;
      if (hTextFlow.getPotEntryData() != null)
      {
         msgContext = hTextFlow.getPotEntryData().getContext();
      }

      int nPlurals = resourceUtils.getNumPlurals(hTextFlow.getDocument(), hLocale);
      ArrayList<String> sourceContents = GwtRpcUtil.getSourceContents(hTextFlow);
      ArrayList<String> targetContents = GwtRpcUtil.getTargetContentsWithPadding(hTextFlow, target, nPlurals);
      // @formatter:off
      TransUnit tu = new TransUnit(
            action.getTransUnitId(), 
            hTextFlow.getResId(),
            locale, 
            hTextFlow.isPlural(),
            sourceContents,
            CommentsUtil.toString(hTextFlow.getComment()),
            targetContents, 
            target.getState(),
            target.getLastModifiedBy().getName(),
            simpleDateFormat.format(target.getLastChanged()), msgContext, hTextFlow.getPos());
      // @formatter:on
      TransUnitUpdated event = new TransUnitUpdated(new DocumentId(hTextFlow.getDocument().getId()), wordCount, prevStatus, tu, ZanataIdentity.instance().getCredentials().getUsername());

      TranslationWorkspace workspace = translationWorkspaceManager.getOrRegisterWorkspace(action.getWorkspaceId());
      workspace.publish(event);
   }

}
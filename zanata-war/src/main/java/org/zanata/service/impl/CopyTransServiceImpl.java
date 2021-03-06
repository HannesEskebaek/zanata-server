/*
 * Copyright 2010, Red Hat, Inc. and individual contributors
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
package org.zanata.service.impl;

import static org.zanata.common.ContentState.*;
import static org.zanata.model.HCopyTransOptions.ConditionRuleAction.DOWNGRADE_TO_FUZZY;
import static org.zanata.model.HCopyTransOptions.ConditionRuleAction.REJECT;

import java.util.List;

import javax.persistence.EntityManager;

import org.hibernate.HibernateException;
import org.hibernate.ScrollableResults;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Logger;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Observer;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.log.Log;
import org.jboss.seam.util.Work;
import org.zanata.common.ContentState;
import org.zanata.dao.DatabaseConstants;
import org.zanata.dao.DocumentDAO;
import org.zanata.dao.ProjectDAO;
import org.zanata.dao.TextFlowTargetDAO;
import org.zanata.model.HCopyTransOptions;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HSimpleComment;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.process.CopyTransProcessHandle;
import org.zanata.rest.service.TranslatedDocResourceService;
import org.zanata.service.CopyTransService;
import org.zanata.service.LocaleService;

//TODO unit test suite for this class

@Name("copyTransServiceImpl")
@Scope(ScopeType.STATELESS)
public class CopyTransServiceImpl implements CopyTransService
{
   @In
   private EntityManager entityManager;

   @In
   private LocaleService localeServiceImpl;
   
   @In
   private TextFlowTargetDAO textFlowTargetDAO;

   @In
   private DocumentDAO documentDAO;

   @In
   private ProjectDAO projectDAO;

   @In(required = false, scope = ScopeType.EVENT)
   private CopyTransProcessHandle asynchronousProcessHandle;
   
   @Logger
   Log log;


   /**
    * Internal helper class to keep track of copy trans matches.
    */
   private class CopyTransMatch
   {
      private CopyTransMatch(HTextFlowTarget matchingTarget, ContentState targetState)
      {
         this.matchingTarget = matchingTarget;
         this.targetState = targetState;
      }

      HTextFlowTarget matchingTarget;
      ContentState targetState;
   }

   @Observer(TranslatedDocResourceService.EVENT_COPY_TRANS)
   public void runCopyTrans(Long docId, String project, String iterationSlug)
   {
      HDocument document = documentDAO.findById(docId, true);
      log.info("copyTrans start: document \"{0}\"", document.getDocId());
      List<HLocale> localelist = localeServiceImpl.getSupportedLangugeByProjectIteration(project, iterationSlug);

      // TODO iterate over document's textflows, then call copyTransForTextFlow(textFlow, localeList)
      // refer patch from https://bugzilla.redhat.com/show_bug.cgi?id=746899
      for (HLocale locale : localelist)
      {
         copyTransForLocale(document, locale);
      }
      log.info("copyTrans finished: document \"{0}\"", document.getDocId());
   }

   private String createComment(HTextFlowTarget target)
   {
      String author;
      HDocument document = target.getTextFlow().getDocument();
      String projectname = document.getProjectIteration().getProject().getName();
      String version = document.getProjectIteration().getSlug();
      String documentid = document.getDocId();
      if (target.getLastModifiedBy() != null)
      {
         author = ", author " + target.getLastModifiedBy().getName();
      }
      else
      {
         author = "";
      }

      return "translation auto-copied from project " + projectname + ", version " + version + ", document " + documentid + author;
   }

   @Override
   public void copyTransForLocale(HDocument document, HLocale locale)
   {
      this.copyTransForLocale(document, locale, new HCopyTransOptions());
   }

   public void copyTransForLocale(final HDocument document, final HLocale locale, final HCopyTransOptions options)
   {
      try
      {
         new Work<Void>() {
            @Override
            protected Void work() throws Exception
            {
               int copyCount = 0;

               // Determine the state of the copies for each pass
               boolean checkContext = true,
                       checkProject = true,
                       checkDocument = true;

               // First pass, very conservative
               // Every result will match context, document, and project
               copyCount += copyTransPass(document, locale, checkContext, checkProject, checkDocument, options);

               // Next passes, more relaxed and only needed when the options call for it
               if( options.getDocIdMismatchAction() != REJECT )
               {
                  // Relax doc Id restriction
                  checkDocument = false;
                  // Every result will match context, and project
                  // Assuming Phase 1 ran, results will have non-matching doc Ids
                  copyCount += copyTransPass(document, locale, checkContext, checkProject, checkDocument, options);
               }
               if( options.getProjectMismatchAction() != REJECT )
               {
                  // Relax project restriction
                  checkProject = false;
                  // Every result will match context
                  // Assuming above phases, results will have non-matching project
                  // Assuming above phase: either doc Id didn't match, or the user explicitly rejected non-matching documents
                  copyCount += copyTransPass(document, locale, checkContext, checkProject, checkDocument, options);
               }
               if( options.getContextMismatchAction() != REJECT )
               {
                  // Relax context restriction
                  checkContext = false;
                  // Assuming above phases:
                  // Context does not match
                  // either doc Id didn't match, or the user explicitly rejected non-matching documents
                  // and either Project didn't match, or the user explicitly rejected non-matching projects
                  copyCount += copyTransPass(document, locale, checkContext, checkProject, checkDocument, options);
               }
               if( options.getContextMismatchAction() != REJECT )
               {
                  // Relax context restriction
                  checkContext = false;
                  // Assuming above phases:
                  // Context does not match
                  // either doc Id didn't match, or the user explicitly rejected non-matching documents
                  // and either Project didn't match, or the user explicitly rejected non-matching projects
                  copyCount += copyTransPass(document, locale, checkContext, checkProject, checkDocument, options);
               }

               log.info("copyTrans: {0} {1} translations for document \"{2}{3}\" ", copyCount, locale.getLocaleId(), document.getPath(), document.getName());

               return null;
            }
         }.workInTransaction();
      }
      catch (Exception e)
      {
         log.warn("exception during copy trans", e);
      }
   }

   private int copyTransPass( HDocument document, HLocale locale, boolean checkContext, boolean checkProject, boolean checkDocument,
                              HCopyTransOptions options)
   {
      ScrollableResults results = null;

      int copyCount = 0;
      try
      {
         boolean requireTranslationReview = document.getProjectIteration().getRequireTranslationReview();
         results = textFlowTargetDAO.findMatchingTranslations(document, locale, checkContext, checkDocument, checkProject, requireTranslationReview);
         copyCount = 0;

         while( results.next() )
         {
            // HTextFlowTarget matchingTarget = (HTextFlowTarget)results.get(0);
            HTextFlowTarget matchingTarget = textFlowTargetDAO.findById((Long) results.get(1), false);

            HTextFlow originalTf = (HTextFlow) results.get(0);
            HTextFlowTarget hTarget = textFlowTargetDAO.getOrCreateTarget(originalTf, locale);
            HProjectIteration matchingTargetProjectIteration = matchingTarget.getTextFlow().getDocument().getProjectIteration();
            ContentState copyState = determineContentState(
                  originalTf.getResId().equals(matchingTarget.getTextFlow().getResId()),
                  originalTf.getDocument().getProjectIteration().getProject().getId().equals(matchingTargetProjectIteration.getProject().getId()),
                  originalTf.getDocument().getDocId().equals( matchingTarget.getTextFlow().getDocument().getDocId() ),
                  options, requireTranslationReview, matchingTarget.getState());
            
            if( shouldOverwrite(hTarget, copyState) )
            {

               // NB we don't touch creationDate
               hTarget.setTextFlowRevision(originalTf.getRevision());
               hTarget.setLastChanged(matchingTarget.getLastChanged());
               hTarget.setLastModifiedBy(matchingTarget.getLastModifiedBy());
               hTarget.setTranslator(matchingTarget.getTranslator());
               // TODO rhbz953734 - will need a new copyTran option for review state
               if (copyState == ContentState.Approved)
               {
                  hTarget.setReviewer(matchingTarget.getReviewer());
               }
               hTarget.setContents(matchingTarget.getContents());
               hTarget.setState(copyState);
               HSimpleComment hcomment = hTarget.getComment();
               if (hcomment == null)
               {
                  hcomment = new HSimpleComment();
                  hTarget.setComment(hcomment);
               }
               hcomment.setComment(createComment(matchingTarget));
               ++copyCount;

               // manually flush
               if( copyCount % DatabaseConstants.BATCH_SIZE == 0 )
               {
                  entityManager.flush();
                  entityManager.clear();
               }
            }
         }

         // a final flush
         if( copyCount % DatabaseConstants.BATCH_SIZE != 0 )
         {
            entityManager.flush();
            entityManager.clear();
         }
      }
      catch (HibernateException e)
      {
         log.error("Copy trans error", e);
      }
      finally
      {
         if( results != null )
         {
            results.close();
         }
      }
      return copyCount;
   }

   private ContentState determineContentState(boolean contextMatches, boolean projectMatches, boolean docIdMatches,
                                              HCopyTransOptions options, boolean requireTranslationReview, ContentState matchingTargetState)
   {
      if (requireTranslationReview && matchingTargetState.isApproved() && projectMatches && contextMatches && docIdMatches)
      {
         return Approved;
      }
      ContentState state = Translated;
      state = getExpectedContentState(contextMatches, options.getContextMismatchAction(), state);
      state = getExpectedContentState(projectMatches, options.getProjectMismatchAction(), state);
      state = getExpectedContentState(docIdMatches, options.getDocIdMismatchAction(), state);
      return state;
   }

   public ContentState getExpectedContentState( boolean match, HCopyTransOptions.ConditionRuleAction action,
                                                 ContentState currentState )
   {
      if( currentState == null )
      {
         return null;
      }
      else if( !match )
      {
         if( action == DOWNGRADE_TO_FUZZY )
         {
            return NeedReview;
         }
         else if( action == REJECT )
         {
            return null;
         }
      }
      return currentState;
   }

   @Override
   public void copyTransForDocument(HDocument document)
   {
      // Set the max progress only if it hasn't been set yet
      if( asynchronousProcessHandle != null && !asynchronousProcessHandle.isMaxProgressSet() )
      {
         List<HLocale> localeList =
               localeServiceImpl.getSupportedLangugeByProjectIteration(document.getProjectIteration().getProject().getSlug(),
                     document.getProjectIteration().getSlug());

         asynchronousProcessHandle.setMaxProgress(localeList.size());
      }

      HCopyTransOptions copyTransOpts = null;
      // Use process handle options
      if( asynchronousProcessHandle != null )
      {
         copyTransOpts = asynchronousProcessHandle.getOptions();
      }
      // use project level options
      if( copyTransOpts == null )
      {
         // NB: Need to reload the options from the db
         copyTransOpts = projectDAO.findById( document.getProjectIteration().getProject().getId(), false )
                                   .getDefaultCopyTransOpts();
      }
      // use the global default options
      if( copyTransOpts == null )
      {
         copyTransOpts = new HCopyTransOptions();
      }

      this.copyTransForDocument(document, copyTransOpts, asynchronousProcessHandle);
   }

   @Override
   public void copyTransForIteration(HProjectIteration iteration)
   {
      if( asynchronousProcessHandle != null )
      {
         List<HLocale> localeList =
               localeServiceImpl.getSupportedLangugeByProjectIteration(iteration.getProject().getSlug(),
                     iteration.getSlug());

         asynchronousProcessHandle.setMaxProgress( iteration.getDocuments().size() * localeList.size() );
         asynchronousProcessHandle.setCurrentProgress(0);
      }

      // TODO RunnableProcess handle may not be null
      for( HDocument doc : iteration.getDocuments().values() )
      {
         if( asynchronousProcessHandle.shouldStop() )
         {
            return;
         }
         this.copyTransForDocument(doc, asynchronousProcessHandle.getOptions(), asynchronousProcessHandle);
      }
   }

   /**
    * NB: The handle's options will be ignored. This is a convenience method to have the logic
    * in a single place.
    * @see CopyTransServiceImpl#copyTransForDocument(org.zanata.model.HDocument)
    */
   private void copyTransForDocument(HDocument document, HCopyTransOptions options, CopyTransProcessHandle processHandle)
   {
      log.info("copyTrans start: document \"{0}\"", document.getDocId());
      List<HLocale> localeList =
            localeServiceImpl.getSupportedLangugeByProjectIteration(document.getProjectIteration().getProject().getSlug(),
                  document.getProjectIteration().getSlug());

      for (HLocale locale : localeList)
      {
         if( processHandle != null && processHandle.shouldStop() )
         {
            return;
         }
         copyTransForLocale(document, locale, options, processHandle);
      }

      if( processHandle != null )
      {
         processHandle.setDocumentsProcessed( processHandle.getDocumentsProcessed() + 1 );
      }
      log.info("copyTrans finished: document \"{0}\"", document.getDocId());
   }

   /**
    * @see CopyTransServiceImpl#copyTransForLocale(org.zanata.model.HDocument, org.zanata.model.HLocale, HCopyTransOptions, org.zanata.process.CopyTransProcessHandle)
    */
   private void copyTransForLocale(HDocument document, HLocale locale, HCopyTransOptions options, CopyTransProcessHandle procHandle)
   {
      this.copyTransForLocale(document, locale, options);

      if( procHandle != null )
      {
         procHandle.incrementProgress(1);
      }
   }

   /**
    * Indicates if a Copy Trans found match should overwrite the currently stored one based on their states.
    */
   private static boolean shouldOverwrite(HTextFlowTarget currentlyStored, ContentState matchState)
   {
      if( currentlyStored != null )
      {
         if( currentlyStored.getState().isRejectedOrFuzzy() && matchState.isTranslated())
         {
            return true; // If it's fuzzy, replace only with approved ones
         }
         else if (currentlyStored.getState() == ContentState.Translated && matchState.isApproved())
         {
            return true; // If it's Translated and found an Approved one
         }
         else if( currentlyStored.getState() == New )
         {
            return true; // If it's new, replace always
         }
         else
         {
            return false;
         }
      }
      return true;
   }
}

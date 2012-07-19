/*
 * Copyright 2012, Red Hat, Inc. and individual contributors as indicated by the
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
package org.zanata.webtrans.client.presenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.customware.gwt.presenter.client.EventBus;
import net.customware.gwt.presenter.client.widget.WidgetDisplay;
import net.customware.gwt.presenter.client.widget.WidgetPresenter;

import org.zanata.webtrans.client.events.NotificationEvent;
import org.zanata.webtrans.client.events.NotificationEvent.Severity;
import org.zanata.webtrans.client.events.KeyShortcutEvent;
import org.zanata.webtrans.client.events.KeyShortcutEventHandler;
import org.zanata.webtrans.client.events.TransUnitUpdatedEvent;
import org.zanata.webtrans.client.events.TransUnitUpdatedEventHandler;
import org.zanata.webtrans.client.events.WorkspaceContextUpdateEvent;
import org.zanata.webtrans.client.events.WorkspaceContextUpdateEventHandler;
import org.zanata.webtrans.client.history.History;
import org.zanata.webtrans.client.history.HistoryToken;
import org.zanata.webtrans.client.history.Window.Location;
import org.zanata.webtrans.client.keys.KeyShortcut;
import org.zanata.webtrans.client.keys.ShortcutContext;
import org.zanata.webtrans.client.resources.WebTransMessages;
import org.zanata.webtrans.client.rpc.CachingDispatchAsync;
import org.zanata.webtrans.client.ui.SearchResultsDocumentTable;
import org.zanata.webtrans.shared.model.TransUnit;
import org.zanata.webtrans.shared.model.TransUnitId;
import org.zanata.webtrans.shared.model.TransUnitUpdateInfo;
import org.zanata.webtrans.shared.model.TransUnitUpdatePreview;
import org.zanata.webtrans.shared.model.UserWorkspaceContext;
import org.zanata.webtrans.shared.rpc.GetProjectTransUnitLists;
import org.zanata.webtrans.shared.rpc.GetProjectTransUnitListsResult;
import org.zanata.webtrans.shared.rpc.PreviewReplaceText;
import org.zanata.webtrans.shared.rpc.PreviewReplaceTextResult;
import org.zanata.webtrans.shared.rpc.ReplaceText;
import org.zanata.webtrans.shared.rpc.RevertTransUnitUpdates;
import org.zanata.webtrans.shared.rpc.UpdateTransUnitResult;

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.cell.client.ActionCell.Delegate;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.HasChangeHandlers;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.MultiSelectionModel;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.gwt.view.client.SelectionChangeEvent.Handler;
import com.google.gwt.view.client.SelectionModel;
import com.google.inject.Inject;

/**
 * View for project-wide search and replace within textflow targets
 * 
 * @author David Mason, <a href="mailto:damason@redhat.com">damason@redhat.com</a>
 */
public class SearchResultsPresenter extends WidgetPresenter<SearchResultsPresenter.Display>
{

   private static final int MAX_VISIBLE_REPLACEMENT_MESSAGES = 5;
   private static final int TRUNCATED_TARGET_LENGTH = 24;

   public interface Display extends WidgetDisplay
   {
      HasText getSearchResponseLabel();

      /**
       * Set the string that will be highlighted in target content. Set to null
       * or empty string to disable highlight
       * 
       * @param highlightString
       */
      void setHighlightString(String highlightString);

      HasValue<String> getFilterTextBox();

      void focusFilterTextBox();

      HasClickHandlers getSearchButton();

      HasValue<String> getReplacementTextBox();

      void focusReplacementTextBox();

      HasValue<Boolean> getCaseSensitiveChk();

      HasValue<Boolean> getSelectAllChk();

      HasChangeHandlers getSearchFieldSelector();

      static String SEARCH_FIELD_TARGET = "target";
      static String SEARCH_FIELD_SOURCE = "source";
      static String SEARCH_FIELD_BOTH = "both";

      String getSelectedSearchField();

      void setSearching(boolean searching);

      HasClickHandlers getReplaceAllButton();

      void setReplaceAllButtonEnabled(boolean enabled);

      void setReplaceAllButtonVisible(boolean visible);

      HasValue<Boolean> getRequirePreviewChk();

      void setRequirePreview(boolean required);

      void clearAll();

      /**
       * Shows a message in the replacement region with an associated 'undo'
       * button
       * 
       * @param message
       * @param undoButtonHandler
       * @see #clearReplacementMessages()
       */
      void addReplacementMessage(String message, ClickHandler undoButtonHandler);

      /**
       * @see #addReplacementMessage(String, ClickHandler)
       */
      void clearReplacementMessages();

      /**
       * Add a document header and table to the display, with no row action
       * buttons.
       * 
       * @param docName document title to show
       * @param viewDocClickHandler handler for 'view in editor' link
       * @param searchDocClickHandler handler for 'search in editor' link
       * @param selectionModel
       * @param selectAllHandler
       * @return the created table
       * 
       * @see SearchResultsDocumentTable#SearchResultsDocumentTable(SelectionModel, ValueChangeHandler, WebTransMessages)
       */
      ListDataProvider<TransUnitReplaceInfo> addDocument(String docName,
            ClickHandler viewDocClickHandler,
            ClickHandler searchDocClickHandler,
            MultiSelectionModel<TransUnitReplaceInfo> selectionModel,
            ValueChangeHandler<Boolean> selectAllHandler);

      /**
       * Add a document header and table to the display, with action buttons per
       * row.
       * 
       * @return the created table
       * 
       * @see #addDocument(String, ClickHandler, ClickHandler, SelectionModel, ValueChangeHandler)
       * @see SearchResultsDocumentTable#SearchResultsDocumentTable(Delegate, Delegate, Delegate, SelectionModel, ValueChangeHandler, WebTransMessages, org.zanata.webtrans.client.resources.Resources)
       */
      ListDataProvider<TransUnitReplaceInfo> addDocument(
            String docName,
            ClickHandler viewDocClickHandler,
            ClickHandler searchDocClickHandler,
            MultiSelectionModel<TransUnitReplaceInfo> selectionModel,
            ValueChangeHandler<Boolean> selectAllHandler,
            Delegate<TransUnitReplaceInfo> previewDelegate,
            Delegate<TransUnitReplaceInfo> replaceDelegate,
            Delegate<TransUnitReplaceInfo> undoDelegate);

      /**
       * Required to avoid instantiating a component that calls client-only code in the presenter
       * 
       * @return a new selection model for use with table
       */
      MultiSelectionModel<TransUnitReplaceInfo> createMultiSelectionModel();
   }

   private final WebTransMessages messages;
   private final CachingDispatchAsync dispatcher;
   private final KeyShortcutPresenter keyShortcutPresenter;
   private final Location windowLocation;
   private final History history;
   private final UserWorkspaceContext userWorkspaceContext;

   private AsyncCallback<GetProjectTransUnitListsResult> projectSearchCallback;
   private Delegate<TransUnitReplaceInfo> previewButtonDelegate;
   private Delegate<TransUnitReplaceInfo> replaceButtonDelegate;
   private Delegate<TransUnitReplaceInfo> undoButtonDelegate;
   private Handler selectionChangeHandler;

   /**
    * Model objects for tables in display. Changes to these are reflected in the
    * view.
    */
   private Map<Long, ListDataProvider<TransUnitReplaceInfo>> documentDataProviders;

   /**
    * Selection model objects for tables in display. Used to determine which
    * transunits are selected
    */
   private Map<Long, MultiSelectionModel<TransUnitReplaceInfo>> documentSelectionModels;

   private Map<TransUnitId, TransUnitReplaceInfo> allReplaceInfos;

   /**
    * most recent history state that was responded to
    */
   private HistoryToken currentHistoryState = null;

   private List<ReplacementEventInfo> replacementEvents;

   private Map<Long, String> docPaths;

   private boolean autoPreview = true;
   private boolean showRowActionButtons = false;

   @Inject
   public SearchResultsPresenter(Display display, EventBus eventBus,
         CachingDispatchAsync dispatcher,
         History history,
         final WebTransMessages webTransMessages,
         final UserWorkspaceContext userWorkspaceContext,
         final KeyShortcutPresenter keyShortcutPresenter,
         Location windowLocation)
   {
      super(display, eventBus);
      messages = webTransMessages;
      this.history = history;
      this.dispatcher = dispatcher;
      this.userWorkspaceContext = userWorkspaceContext;
      this.keyShortcutPresenter = keyShortcutPresenter;
      this.windowLocation = windowLocation;
   }

   @Override
   protected void onBind()
   {
      projectSearchCallback = buildProjectSearchCallback();
      selectionChangeHandler = buildSelectionChangeHandler();
      documentDataProviders = new HashMap<Long, ListDataProvider<TransUnitReplaceInfo>>();
      documentSelectionModels = new HashMap<Long, MultiSelectionModel<TransUnitReplaceInfo>>();
      allReplaceInfos = new HashMap<TransUnitId, TransUnitReplaceInfo>();
      docPaths = new HashMap<Long, String>();
      setUiForNothingSelected();
      display.setReplaceAllButtonVisible(!userWorkspaceContext.hasReadOnlyAccess());

      registerHandler(display.getSearchButton().addClickHandler(new ClickHandler()
      {
         @Override
         public void onClick(ClickEvent event)
         {
            updateSearch();
         }
      }));

      registerHandler(display.getReplacementTextBox().addValueChangeHandler(new ValueChangeHandler<String>()
      {

         @Override
         public void onValueChange(ValueChangeEvent<String> event)
         {
            HistoryToken token = history.getHistoryToken();
            if (!event.getValue().equals(token.getProjectSearchReplacement()))
            {
               token.setProjectSearchReplacement(event.getValue());
               history.newItem(token);
            }
         }
      }));

      registerHandler(display.getSelectAllChk().addValueChangeHandler(new ValueChangeHandler<Boolean>()
      {

         @Override
         public void onValueChange(ValueChangeEvent<Boolean> event)
         {
            if (event.getValue())
            {
               selectAllTextFlows();
            }
            else
            {
               for (MultiSelectionModel<TransUnitReplaceInfo> selectionModel : documentSelectionModels.values())
               {
                  selectionModel.clear();
               }
            }
         }
      }));

      registerHandler(display.getRequirePreviewChk().addValueChangeHandler(new ValueChangeHandler<Boolean>()
      {

         @Override
         public void onValueChange(ValueChangeEvent<Boolean> event)
         {
            display.setRequirePreview(event.getValue());
            for (ListDataProvider<TransUnitReplaceInfo> provider : documentDataProviders.values())
            {
               provider.refresh();
            }
            refreshReplaceAllButton();
         }
      }));

      registerHandler(display.getReplaceAllButton().addClickHandler(new ClickHandler()
      {

         @Override
         public void onClick(ClickEvent event)
         {
            replaceSelected();
         }
      }));

      history.addValueChangeHandler(new ValueChangeHandler<String>()
      {

         @Override
         public void onValueChange(ValueChangeEvent<String> event)
         {
            processHistoryToken(HistoryToken.fromTokenString(event.getValue()));
         }
      });

      registerHandler(eventBus.addHandler(TransUnitUpdatedEvent.getType(), new TransUnitUpdatedEventHandler()
      {

         @Override
         public void onTransUnitUpdated(final TransUnitUpdatedEvent event)
         {
            TransUnitUpdateInfo updateInfo = event.getUpdateInfo();
            TransUnitReplaceInfo replaceInfo = allReplaceInfos.get(updateInfo.getTransUnit().getId());
            if (replaceInfo == null)
            {
               Log.debug("no matching TU in document for TU update, id: " + updateInfo.getTransUnit().getId().getId());
               return;
            }
            Log.debug("found matching TU for TU update, id: " + updateInfo.getTransUnit().getId().getId());


            if (replaceInfo.getReplaceState() == ReplacementState.Replaced && replaceInfo.getTransUnit().getVerNum() != updateInfo.getTransUnit().getVerNum())
            {
               // can't undo after additional update
               setReplaceState(replaceInfo, ReplacementState.NotReplaced);
               replaceInfo.setReplaceInfo(null);
               replaceInfo.setPreviewState(PreviewState.NotFetched);
               replaceInfo.setPreview(null);

               MultiSelectionModel<TransUnitReplaceInfo> selectionModel = documentSelectionModels.get(updateInfo.getDocumentId().getId());
               if (selectionModel == null)
               {
                  Log.error("missing selection model for document, id: " + updateInfo.getDocumentId().getId());
               }
               else
               {
                  // clear selection to avoid accidental inclusion in batch operations
                  selectionModel.setSelected(replaceInfo, false);
               }
            }
            replaceInfo.setTransUnit(updateInfo.getTransUnit());
            refreshInfoDisplay(replaceInfo);
         }
      }));

      registerHandler(eventBus.addHandler(WorkspaceContextUpdateEvent.getType(), new WorkspaceContextUpdateEventHandler()
      {
         @Override
         public void onWorkspaceContextUpdated(WorkspaceContextUpdateEvent event)
         {
            userWorkspaceContext.setProjectActive(event.isProjectActive());
            
            display.setReplaceAllButtonVisible(!userWorkspaceContext.hasReadOnlyAccess());

            for (TransUnitReplaceInfo info : allReplaceInfos.values())
            {
               if (userWorkspaceContext.hasReadOnlyAccess())
               {
                  setReplaceState(info, ReplacementState.NotAllowed);
               }
               else if (info.getReplaceInfo() == null)
               {
                  setReplaceState(info, ReplacementState.NotReplaced);
               }
               else
               {
                  setReplaceState(info, ReplacementState.Replaced);
               }
               refreshInfoDisplay(info);
            }
         }
      }));

      keyShortcutPresenter.registerKeyShortcut(new KeyShortcut(
            KeyShortcut.SHIFT_ALT_KEYS, 'A',
            ShortcutContext.ProjectWideSearch,
            messages.selectAllTextFlowsKeyShortcut(),
            new KeyShortcutEventHandler()
      {
         @Override
         public void onKeyShortcut(KeyShortcutEvent event)
         {
            selectAllTextFlows();
         }
      }));

      keyShortcutPresenter.registerKeyShortcut(new KeyShortcut(
            KeyShortcut.ALT_KEY, 'P',
            ShortcutContext.ProjectWideSearch,
            messages.focusSearchPhraseKeyShortcut(),
            new KeyShortcutEventHandler()
      {
         @Override
         public void onKeyShortcut(KeyShortcutEvent event)
         {
            display.focusFilterTextBox();
         }
      }));

      keyShortcutPresenter.registerKeyShortcut(new KeyShortcut(
            KeyShortcut.ALT_KEY, 'C',
            ShortcutContext.ProjectWideSearch,
            messages.focusReplacementPhraseKeyShortcut(),
            new KeyShortcutEventHandler()
      {
         @Override
         public void onKeyShortcut(KeyShortcutEvent event)
         {
            display.focusReplacementTextBox();
         }
      }));

      keyShortcutPresenter.registerKeyShortcut(new KeyShortcut(
            KeyShortcut.ALT_KEY, 'R',
            ShortcutContext.ProjectWideSearch,
            messages.replaceSelectedKeyShortcut(),
            new KeyShortcutEventHandler()
      {
         @Override
         public void onKeyShortcut(KeyShortcutEvent event)
         {
            replaceSelected();
         }
      }));

      //TODO Alt+R for replace, Alt+C to focus replace field

      keyShortcutPresenter.registerKeyShortcut(new KeyShortcut(
            KeyShortcut.ALT_KEY, 'W',
            ShortcutContext.ProjectWideSearch,
            messages.toggleRowActionButtons(),
            new KeyShortcutEventHandler()
      {
         @Override
         public void onKeyShortcut(KeyShortcutEvent event)
         {
            showRowActionButtons = !showRowActionButtons;
         }
      }));

      // TODO register key shortcuts:
      // Alt+Z undo last operation

      // detect currently focused document (if any)
      // Alt+A select current doc
      // Alt+V view current doc in editor
      // Shift+Alt+V search current doc in editor
   }

   private void showDocInEditor(String doc, boolean runSearch)
   {
      HistoryToken token = HistoryToken.fromTokenString(history.getToken());
      token.setDocumentPath(doc);
      token.setView(MainView.Editor);
      if (runSearch)
      {
         token.setSearchText(token.getProjectSearchText());
      }
      else
      {
         token.setSearchText("");
      }
      history.newItem(token);
   }

   @Override
   protected void onUnbind()
   {
   }

   @Override
   public void onRevealDisplay()
   {
      keyShortcutPresenter.setContextActive(ShortcutContext.ProjectWideSearch, true);
      display.focusReplacementTextBox();
   }

   public void concealDisplay()
   {
      keyShortcutPresenter.setContextActive(ShortcutContext.ProjectWideSearch, false);
   }

   private AsyncCallback<GetProjectTransUnitListsResult> buildProjectSearchCallback()
   {
      return new AsyncCallback<GetProjectTransUnitListsResult>()
      {

         @Override
         public void onFailure(Throwable caught)
         {
            Log.error("[SearchResultsPresenter] failed project-wide search request: " + caught.getMessage());
            eventBus.fireEvent(new NotificationEvent(Severity.Error, messages.searchFailed()));
            display.clearAll();
            display.getSearchResponseLabel().setText(messages.searchFailed());
         }

         @Override
         public void onSuccess(GetProjectTransUnitListsResult result)
         {
            int textFlowCount = displaySearchResults(result);
            if (result.getDocumentIds().size() == 0)
            {
               // TODO add case sensitivity and scope
               display.getSearchResponseLabel().setText(messages.searchForPhraseReturnedNoResults(result.getSearchAction().getSearchString()));
            }
            else
            {
               // TODO add case sensitivity and scope
               display.getSearchResponseLabel().setText(messages.showingResultsForProjectWideSearch(result.getSearchAction().getSearchString(), textFlowCount, result.getDocumentIds().size()));
            }
            display.setSearching(false);
         }

      };
   }

   private Delegate<TransUnitReplaceInfo> ensureReplaceButtonDelegate()
   {
      if (replaceButtonDelegate == null)
      {
         replaceButtonDelegate = buildReplaceButtonDelegate();
      }
      return replaceButtonDelegate;
   }

   private Delegate<TransUnitReplaceInfo> buildReplaceButtonDelegate()
   {
      return new Delegate<TransUnitReplaceInfo>()
      {

         @Override
         public void execute(TransUnitReplaceInfo info)
         {
            fireReplaceTextEvent(Collections.singletonList(info));
         }

      };
   }

   private Delegate<TransUnitReplaceInfo> ensureUndoButtonDelegate()
   {
      if (undoButtonDelegate == null)
      {
         undoButtonDelegate = buildUndoButtonDelegate();
      }
      return undoButtonDelegate;
   }

   private Delegate<TransUnitReplaceInfo> buildUndoButtonDelegate()
   {
      return new Delegate<TransUnitReplaceInfo>()
      {
         @Override
         public void execute(final TransUnitReplaceInfo info)
         {
            fireUndoEvent(Collections.singletonList(info.getReplaceInfo()));
         }
      };
   }

   private Delegate<TransUnitReplaceInfo> ensurePreviewButtonDelegate()
   {
      if (previewButtonDelegate == null)
      {
         previewButtonDelegate = buildPreviewButtonDelegate();
      }
      return previewButtonDelegate;
   }

   private Delegate<TransUnitReplaceInfo> buildPreviewButtonDelegate()
   {
      return new Delegate<TransUnitReplaceInfo>()
      {

         @Override
         public void execute(TransUnitReplaceInfo info)
         {
            switch (info.getPreviewState())
            {
            case NotAllowed:
               break;
            case Show:
               info.setPreviewState(PreviewState.Hide);
               refreshInfoDisplay(info);
               break;
            default:
               firePreviewEvent(Collections.singletonList(info));
               break;
            }
         }

      };
   }

   /**
    * @return
    */
   private Handler buildSelectionChangeHandler()
   {
      return new Handler()
      {

         @Override
         public void onSelectionChange(SelectionChangeEvent event)
         {
            int selectedFlows = countSelectedFlows();

            if (autoPreview)
            {
               previewSelected(true, true);
            }

            if (selectedFlows == 0)
            {
               setUiForNothingSelected();
            }
            else
            {
               refreshReplaceAllButton();
            }
         }
      };
   }

   /**
    * 
    * @param skipEmptyNotification true to silently ignore the request when no rows are selected
    */
   private void previewSelected(boolean skipEmptyNotification, boolean hideNonSelectedPreviews)
   {
      List<TransUnitReplaceInfo> selected = getAllSelected();
      if (!skipEmptyNotification || !selected.isEmpty())
      {
         firePreviewEvent(selected);
      }

      if (hideNonSelectedPreviews)
      {
         for (TransUnitReplaceInfo info : allReplaceInfos.values())
         {
            if (info.getPreviewState() == PreviewState.Show && !selected.contains(info))
            {
               info.setPreviewState(PreviewState.Hide);
            }
         }
      }
   }


   /**
    * Fire a {@link PreviewReplaceText} event for the given {@link TransUnit}s
    * using parameters from the current history state. This will also update the
    * state and refresh the table to show 'previewing' indicator.
    * 
    * If toPreview is empty, this is a no-op.
    * 
    * @param toPreview
    */
   private void firePreviewEvent(List<TransUnitReplaceInfo> toPreview)
   {
      if (toPreview.isEmpty())
      {
         eventBus.fireEvent(new NotificationEvent(Severity.Warning, messages.noTextFlowsSelected()));
         return;
      }
      final String replacement = currentHistoryState.getProjectSearchReplacement();
      // prevent failed requests for empty replacement
      if (replacement.isEmpty())
      {
         eventBus.fireEvent(new NotificationEvent(Severity.Warning, messages.noReplacementPhraseEntered()));
         return;
      }

      List<TransUnit> transUnits = new ArrayList<TransUnit>();
      for (TransUnitReplaceInfo replaceInfo : toPreview)
      {
         switch (replaceInfo.getPreviewState())
         {
         case NotFetched:
            transUnits.add(replaceInfo.getTransUnit());
            replaceInfo.setPreviewState(PreviewState.Fetching);
            refreshInfoDisplay(replaceInfo);
            break;
         case Hide:
            replaceInfo.setPreviewState(PreviewState.Show);
            refreshInfoDisplay(replaceInfo);
            break;
         }
      }

      if (transUnits.isEmpty())
      {
         // could notify user, doesn't seem worthwhile
         return;
      }

      final String searchText = currentHistoryState.getProjectSearchText();
      boolean caseSensitive = currentHistoryState.getProjectSearchCaseSensitive();
      ReplaceText action = new ReplaceText(transUnits, searchText, replacement, caseSensitive);
      PreviewReplaceText previewAction = new PreviewReplaceText(action);
      dispatcher.execute(previewAction, new AsyncCallback<PreviewReplaceTextResult>()
      {

         @Override
         public void onFailure(Throwable e)
         {
            Log.error("[SearchResultsPresenter] Preview replace text failure " + e, e);
            eventBus.fireEvent(new NotificationEvent(Severity.Error, messages.previewFailed()));
            // may want to change TU state from 'previewing' (possibly error state)
         }

         @Override
         public void onSuccess(final PreviewReplaceTextResult result)
         {
            for (TransUnitUpdatePreview preview : result.getPreviews())
            {
               TransUnitReplaceInfo replaceInfo = allReplaceInfos.get(preview.getId());
               if (replaceInfo == null)
               {
                  Log.error("no replace info found for previewed text flow");
               }
               else
               {
                  Log.debug("setting preview state for preview id: " + preview.getId());
                  replaceInfo.setPreview(preview);
                  replaceInfo.setPreviewState(PreviewState.Show);
                  refreshInfoDisplay(replaceInfo);
               }
            }
            refreshReplaceAllButton();
            eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.fetchedPreview()));
         }

      });
   }

   private void replaceSelected()
   {
      if (replaceSelectedAllowed())
      {
         fireReplaceTextEvent(getAllSelected());
      }
      else
      {
         eventBus.fireEvent(new NotificationEvent(Severity.Warning, messages.previewRequiredBeforeReplace()));
      }
   }

   private List<TransUnitReplaceInfo> getAllSelected()
   {
      List<TransUnitReplaceInfo> selected = new ArrayList<TransUnitReplaceInfo>();
      for (MultiSelectionModel<TransUnitReplaceInfo> sel : documentSelectionModels.values())
      {
         selected.addAll(sel.getSelectedSet());
      }
      return selected;
   }

   /**
    * Fire a {@link ReplaceText} event for the given {@link TransUnit}s using
    * parameters from the current history state. This will also update the state
    * and refresh the table to show 'replacing' indicator.
    * 
    * An event will not be fired if toReplace is empty or contains no text flows
    * that are eligible for a replace operation.
    * 
    * @param toReplace list of TransUnits to replace
    */
   private void fireReplaceTextEvent(List<TransUnitReplaceInfo> toReplace)
   {
      if (userWorkspaceContext.hasReadOnlyAccess())
      {
         eventBus.fireEvent(new NotificationEvent(Severity.Warning, messages.cannotReplaceInReadOnlyMode()));
         return;
      }

      if (toReplace.isEmpty())
      {
         eventBus.fireEvent(new NotificationEvent(Severity.Warning, messages.noTextFlowsSelected()));
         return;
      }
      List<TransUnit> transUnits = new ArrayList<TransUnit>();
      for (TransUnitReplaceInfo info : toReplace)
      {
         switch (info.getReplaceState())
         {
         case NotReplaced:
            transUnits.add(info.getTransUnit());
            setReplaceState(info, ReplacementState.Replacing);
            info.setPreviewState(PreviewState.Hide);
            refreshInfoDisplay(info);
            break;
         }
      }

      if (transUnits.isEmpty())
      {
         eventBus.fireEvent(new NotificationEvent(Severity.Warning, messages.noReplacementsToMake()));
         return;
      }

      final String searchText = currentHistoryState.getProjectSearchText();
      final String replacement = currentHistoryState.getProjectSearchReplacement();
      boolean caseSensitive = currentHistoryState.getProjectSearchCaseSensitive();
      ReplaceText action = new ReplaceText(transUnits, searchText, replacement, caseSensitive);
      dispatcher.execute(action, new AsyncCallback<UpdateTransUnitResult>()
      {

         @Override
         public void onFailure(Throwable e)
         {
            Log.error("[SearchResultsPresenter] Replace text failure " + e, e);
            eventBus.fireEvent(new NotificationEvent(Severity.Error, messages.replaceTextFailure()));
            // may want to change state from 'replacing' (possibly add error state)
         }

         @Override
         public void onSuccess(final UpdateTransUnitResult result)
         {
            final List<TransUnitUpdateInfo> updateInfoList = processSuccessfulReplacements(result.getUpdateInfoList());
            eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.replacedTextSuccess()));

            String message;
            if (updateInfoList.size() == 1)
            {
               TransUnitUpdateInfo info = updateInfoList.get(0);
               String text = info.getTransUnit().getTargets().get(0);
               String truncatedText = text.substring(0, (text.length() <= TRUNCATED_TARGET_LENGTH ? text.length() : TRUNCATED_TARGET_LENGTH));
               int oneBasedRowIndex = info.getTransUnit().getRowIndex()+1;
               String docName = docPaths.get(info.getDocumentId().getId());
               message = messages.replacedTextInOneTextFlow(searchText, replacement, docName, oneBasedRowIndex, truncatedText);
            }
            else
            {
               message = messages.replacedTextInMultipleTextFlows(searchText, replacement, updateInfoList.size());
            }
            final ReplacementEventInfo replacementInfo = new ReplacementEventInfo(message);
            ClickHandler handler = new ClickHandler()
            {
               @Override
               public void onClick(ClickEvent event)
               {
                  fireUndoEvent(updateInfoList);
                  ensureReplacementEvents().remove(replacementInfo);
                  refreshReplacementEventInfoList();
               }
            };
            replacementInfo.setHandler(handler);
            ensureReplacementEvents().add(replacementInfo);
            refreshReplacementEventInfoList();
         }
      });
   }

   /**
    * Fire a {@link RevertTransUnitUpdates} event to request undoing of the
    * given updates.
    * 
    * @param updateInfoList updates that are to be reverted
    */
   private void fireUndoEvent(List<TransUnitUpdateInfo> updateInfoList)
   {
      if (userWorkspaceContext.hasReadOnlyAccess())
      {
         eventBus.fireEvent(new NotificationEvent(Severity.Warning, messages.cannotUndoInReadOnlyMode()));
         return;
      }

      // TODO only fire undo for flows that are undoable?
      // rpc method should cope with this anyway, so no big deal

      eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.undoInProgress()));
      RevertTransUnitUpdates action = new RevertTransUnitUpdates();
      for (TransUnitUpdateInfo updateInfo : updateInfoList)
      {
         action.addUpdateToRevert(updateInfo);
         TransUnitReplaceInfo replaceInfo = allReplaceInfos.get(updateInfo.getTransUnit().getId());
         // may be null if another search has been performed since the replacement
         if (replaceInfo != null)
         {
            setReplaceState(replaceInfo, ReplacementState.Undoing);
            refreshInfoDisplay(replaceInfo);
         }
      }
      dispatcher.execute(action, new AsyncCallback<UpdateTransUnitResult>()
      {

         @Override
         public void onFailure(Throwable caught)
         {
            eventBus.fireEvent(new NotificationEvent(Severity.Error, messages.undoReplacementFailure()));
         }

         @Override
         public void onSuccess(UpdateTransUnitResult result)
         {
            eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.undoSuccess()));
            for (TransUnitUpdateInfo info : result.getUpdateInfoList())
            {
               TransUnitReplaceInfo replaceInfo = allReplaceInfos.get(info.getTransUnit().getId());
               setReplaceState(replaceInfo, ReplacementState.NotReplaced);
               if (replaceInfo.getPreview() == null)
               {
                  replaceInfo.setPreviewState(PreviewState.NotFetched);
               }
               else
               {
                  MultiSelectionModel<TransUnitReplaceInfo> selectionModel = documentSelectionModels.get(replaceInfo.getDocId());
                  if (selectionModel != null && selectionModel.isSelected(replaceInfo))
                  {
                     replaceInfo.setPreviewState(PreviewState.Show);
                  }
                  else
                  {
                     replaceInfo.setPreviewState(PreviewState.Hide);
                  }
               }
               refreshInfoDisplay(replaceInfo);
            }
            refreshReplaceAllButton();
            // update model with new values?
         }
      });
   }

   /**
    * Update data providers and refresh display for successful replacements.
    * 
    * @param updateInfoList info on replacements. If any of these are not
    *           successful, they are ignored.
    * @return the number of updates that are
    *         {@link TransUnitUpdateInfo#isSuccess()}
    */
   private List<TransUnitUpdateInfo> processSuccessfulReplacements(final List<TransUnitUpdateInfo> updateInfoList)
   {
      List<TransUnitUpdateInfo> successfulReplacements = new ArrayList<TransUnitUpdateInfo>();
      for (TransUnitUpdateInfo updateInfo : updateInfoList)
      {
         if (updateInfo.isSuccess())
         {
            successfulReplacements.add(updateInfo);
            TransUnitReplaceInfo replaceInfo = allReplaceInfos.get(updateInfo.getTransUnit().getId());
            if (replaceInfo != null)
            {
               replaceInfo.setReplaceInfo(updateInfo);
               ReplacementState replaceState = ReplacementState.Replaced;
               setReplaceState(replaceInfo, replaceState);
               // this should be done when the TU update event comes in
               // anyway may want to remove this
               replaceInfo.setTransUnit(updateInfo.getTransUnit());
               refreshInfoDisplay(replaceInfo);
            }
         }
         // individual failure behaviour not yet defined
      }
      return successfulReplacements;
   }

   /**
    * Sets the info item to its current index in the containing data
    * provider to force the provider to recognize that it has changed.
    * 
    * @param info
    */
   private void refreshInfoDisplay(TransUnitReplaceInfo info)
   {
      ListDataProvider<TransUnitReplaceInfo> dataProvider = documentDataProviders.get(info.getDocId());
      if (dataProvider != null)
      {
         List<TransUnitReplaceInfo> list = dataProvider.getList();
         try
         {
            list.set(list.indexOf(info), info);
         }
         catch (IndexOutOfBoundsException e)
         {
            Log.error("failed to re-set info object in its dataprovider", e);
         }
      }
   }

   /**
    * Show search results as documents in the display. This will replace any
    * existing results being displayed.
    * 
    * @param result results to display
    * @return the number of text flows that were displayed
    */
   private int displaySearchResults(GetProjectTransUnitListsResult result)
   {
      clearAllExistingData();
      int totalTransUnits = 0;
      for (Long docId : result.getDocumentIds())
      {
         docPaths.put(docId, result.getDocPath(docId));
         List<TransUnit> transUnits = result.getUnits(docId);
         totalTransUnits += transUnits.size();
         displayDocumentResults(docId, result.getDocPath(docId), transUnits);
      }
      return totalTransUnits;
   }

   /**
    * Display header and all results for a single document.
    * 
    * @param docId
    * @param docPathName
    * @param transUnits
    */
   private void displayDocumentResults(Long docId, final String docPathName, List<TransUnit> transUnits)
   {
      ListDataProvider<TransUnitReplaceInfo> dataProvider;
      final MultiSelectionModel<TransUnitReplaceInfo> selectionModel = display.createMultiSelectionModel();
      documentSelectionModels.put(docId, selectionModel);
      ClickHandler showDocHandler = showDocClickHandler(docPathName, false);
      ClickHandler searchDocHandler = showDocClickHandler(docPathName, true);
      ValueChangeHandler<Boolean> selectDocHandler = selectAllHandler(docId, selectionModel);
      if (showRowActionButtons)
      {
         dataProvider = display.addDocument(docPathName, showDocHandler, searchDocHandler, selectionModel, selectDocHandler,
               ensurePreviewButtonDelegate(), ensureReplaceButtonDelegate(), ensureUndoButtonDelegate());
      }
      else
      {
         dataProvider = display.addDocument(docPathName, showDocHandler, searchDocHandler, selectionModel, selectDocHandler);
      }
      documentDataProviders.put(docId, dataProvider);

      selectionModel.addSelectionChangeHandler(selectionChangeHandler);

      List<TransUnitReplaceInfo> data = dataProvider.getList();
      for (TransUnit tu : transUnits)
      {
         TransUnitReplaceInfo info = new TransUnitReplaceInfo(docId, tu);
         // default state is NotReplaced, this call triggers read-only check
         setReplaceState(info, ReplacementState.NotReplaced);
         data.add(info);
         allReplaceInfos.put(tu.getId(), info);
      }
      Collections.sort(data, TransUnitReplaceInfo.getRowComparator());
   }

   /**
    * Build a click handler to show a document in the editor.
    * 
    * @see #showDocInEditor(String, boolean)
    */
   private ClickHandler showDocClickHandler(final String docPathName, final boolean runSearch)
   {
      ClickHandler showDocClickHandler = new ClickHandler()
      {
         @Override
         public void onClick(ClickEvent event)
         {
            showDocInEditor(docPathName, runSearch);
         }
      };
      return showDocClickHandler;
   }

   /**
    * Build a handler to select and de-select all text flows in a document
    * 
    * @param selectionModel
    * @param dataProvider
    * @return the new handler
    */
   private ValueChangeHandler<Boolean> selectAllHandler(final Long docId, final MultiSelectionModel<TransUnitReplaceInfo> selectionModel)
   {
      return new ValueChangeHandler<Boolean>()
      {
         @Override
         public void onValueChange(ValueChangeEvent<Boolean> event)
         {
            if (event.getValue())
            {
               ListDataProvider<TransUnitReplaceInfo> dataProvider = documentDataProviders.get(docId);
               if (dataProvider != null)
               {
                  for (TransUnitReplaceInfo info : dataProvider.getList())
                  {
                     selectionModel.setSelected(info, true);
                  }
               }
            }
            else
            {
               selectionModel.clear();
            }
         }
      };
   }

   private void processHistoryToken(HistoryToken token)
   {
      if (currentHistoryState == null)
         currentHistoryState = new HistoryToken(); // default values


      boolean caseSensitivityChanged = token.getProjectSearchCaseSensitive() != currentHistoryState.getProjectSearchCaseSensitive();
      boolean searchTextChanged = !token.getProjectSearchText().equals(currentHistoryState.getProjectSearchText());
      boolean searchFieldsChanged = token.isProjectSearchInSource() != currentHistoryState.isProjectSearchInSource();
      searchFieldsChanged |= token.isProjectSearchInTarget() != currentHistoryState.isProjectSearchInTarget();
      if (caseSensitivityChanged || searchTextChanged || searchFieldsChanged)
      {
         display.setHighlightString(token.getProjectSearchText());
         display.getFilterTextBox().setValue(token.getProjectSearchText(), false);
         display.getCaseSensitiveChk().setValue(token.getProjectSearchCaseSensitive(), false);
         // TODO set selection in source/target selector

         clearAllExistingData();

         if (!token.getProjectSearchText().isEmpty())
         {
            display.setSearching(true);
            GetProjectTransUnitLists action = new GetProjectTransUnitLists(token.getProjectSearchText(),
                  token.isProjectSearchInSource(),
                  token.isProjectSearchInTarget(),
                  token.getProjectSearchCaseSensitive(),
                  windowLocation.getQueryDocuments());
            dispatcher.execute(action, projectSearchCallback);
         }
      }

      boolean replacementTextChanged = !token.getProjectSearchReplacement().equals(currentHistoryState.getProjectSearchReplacement());
      if (replacementTextChanged)
      {
         display.getReplacementTextBox().setValue(token.getProjectSearchReplacement(), true);
         for (TransUnitReplaceInfo info : allReplaceInfos.values())
         {
            info.setPreview(null);
            info.setPreviewState(PreviewState.NotFetched);
            refreshInfoDisplay(info);
         }
         refreshReplaceAllButton();
      }

      currentHistoryState = token;

      // uses currentHistoryState so must execute after token is updated.
      if (replacementTextChanged && autoPreview)
      {
         previewSelected(true, false);
      }
   }

   /**
    * Clear all data providers, selection models, replace infos, and removes all
    * documents from the display
    */
   private void clearAllExistingData()
   {
      documentDataProviders.clear();
      documentSelectionModels.clear();
      allReplaceInfos.clear();
      display.clearAll();
      setUiForNothingSelected();
   }

   private void setUiForNothingSelected()
   {
      display.setReplaceAllButtonEnabled(false);
   }

   private void refreshReplaceAllButton()
   {
      display.setReplaceAllButtonEnabled(replaceSelectedAllowed());
   }

   /**
    * Checks that something is selected and that if previews are required, all
    * selected rows have previews
    * 
    * @return true if conditions are met to replace selected
    */
   private boolean replaceSelectedAllowed()
   {
      boolean requirePreview = display.getRequirePreviewChk().getValue();
      boolean canReplace = countSelectedFlows() != 0 && (!requirePreview || allSelectedHavePreview());
      return canReplace;
   }

   /**
    * @return false if any selected text flows do not have an available preview.
    *         true if no text flows are selected or all have previews.
    */
   private boolean allSelectedHavePreview()
   {
      for (MultiSelectionModel<TransUnitReplaceInfo> model : documentSelectionModels.values())
      {
         for (TransUnitReplaceInfo info : model.getSelectedSet())
         {
            switch (info.getPreviewState())
            {
            case NotFetched:
            case Fetching:
               return false;
            }
         }
      }
      return true;
   }

   private int countSelectedFlows()
   {
      int selectedFlows = 0;
      for (MultiSelectionModel<TransUnitReplaceInfo> model : documentSelectionModels.values())
      {
         selectedFlows += model.getSelectedSet().size();
      }
      return selectedFlows;
   }

   /**
    * Set the replace state for a {@link TransUnitReplaceInfo}, adjusting to
    * {@link ReplacementState#NotAllowed} if the workspace is read-only.
    * 
    * @param replaceInfo
    * @param replaceState to set, ignored if workspace is read-only
    */
   private void setReplaceState(TransUnitReplaceInfo replaceInfo, ReplacementState replaceState)
   {
      if (userWorkspaceContext.hasReadOnlyAccess())
      {
         replaceInfo.setReplaceState(ReplacementState.NotAllowed);
      }
      else
      {
         replaceInfo.setReplaceState(replaceState);
      }
   }

   private void selectAllTextFlows()
   {
      for (Entry<Long, ListDataProvider<TransUnitReplaceInfo>> en : documentDataProviders.entrySet())
      {
         MultiSelectionModel<TransUnitReplaceInfo> selectionModel = documentSelectionModels.get(en.getKey());
         if (selectionModel != null)
         {
            for (TransUnitReplaceInfo tu : en.getValue().getList())
            {
               selectionModel.setSelected(tu, true);
            }
         }
      }
   }

   private List<ReplacementEventInfo> ensureReplacementEvents()
   {
      if (replacementEvents == null)
      {
         replacementEvents = new ArrayList<ReplacementEventInfo>();
      }
      return replacementEvents;
   }

   private void refreshReplacementEventInfoList()
   {
      display.clearReplacementMessages();
      List<ReplacementEventInfo> events = ensureReplacementEvents();
      while (events.size() > MAX_VISIBLE_REPLACEMENT_MESSAGES)
      {
         events.remove(0);
      }
      for (ReplacementEventInfo info : events)
      {
         display.addReplacementMessage(info.getMessage(), info.getHandler());
      }
   }

   private void updateSearch()
   {
      boolean changed = false;
      HistoryToken token = history.getHistoryToken();

      Boolean caseSensitive = display.getCaseSensitiveChk().getValue();
      if (caseSensitive != token.getProjectSearchCaseSensitive())
      {
         token.setProjectSearchCaseSensitive(caseSensitive);
         changed = true;
      }

      String searchPhrase = display.getFilterTextBox().getValue();
      if (!searchPhrase.equals(token.getProjectSearchText()))
      {
         token.setProjectSearchText(searchPhrase);
         changed = true;
      }

      String selected = display.getSelectedSearchField();
      boolean searchSource = selected.equals(Display.SEARCH_FIELD_SOURCE) || selected.equals(Display.SEARCH_FIELD_BOTH);
      boolean searchTarget = selected.equals(Display.SEARCH_FIELD_TARGET) || selected.equals(Display.SEARCH_FIELD_BOTH);
      if (searchSource != token.isProjectSearchInSource())
      {
         token.setProjectSearchInSource(searchSource);
         changed = true;
      }
      if (searchTarget != token.isProjectSearchInTarget())
      {
         token.setProjectSearchInTarget(searchTarget);
         changed = true;
      }

      if (changed)
      {
         history.newItem(token);
      }
   }

   private class ReplacementEventInfo
   {
      private String message;
      private ClickHandler handler;

      public ReplacementEventInfo(String message)
      {
         this.message = message;
      }

      public String getMessage()
      {
         return message;
      }

      public ClickHandler getHandler()
      {
         return handler;
      }

      public void setHandler(ClickHandler handler)
      {
         this.handler = handler;
      }

   }
}

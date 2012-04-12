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
package org.zanata.webtrans.client.editor.table;

import static org.zanata.webtrans.client.editor.table.TableConstants.MAX_PAGE_ROW;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Command;
import net.customware.gwt.presenter.client.EventBus;
import net.customware.gwt.presenter.client.widget.WidgetDisplay;
import net.customware.gwt.presenter.client.widget.WidgetPresenter;

import org.zanata.common.EditState;
import org.zanata.webtrans.client.action.UndoableTransUnitUpdateAction;
import org.zanata.webtrans.client.action.UndoableTransUnitUpdateHandler;
import org.zanata.webtrans.client.editor.HasPageNavigation;
import org.zanata.webtrans.client.events.DocumentSelectionEvent;
import org.zanata.webtrans.client.events.DocumentSelectionHandler;
import org.zanata.webtrans.client.events.FilterViewEvent;
import org.zanata.webtrans.client.events.FilterViewEventHandler;
import org.zanata.webtrans.client.events.FindMessageEvent;
import org.zanata.webtrans.client.events.FindMessageHandler;
import org.zanata.webtrans.client.events.NavTransUnitEvent;
import org.zanata.webtrans.client.events.NavTransUnitEvent.NavigationType;
import org.zanata.webtrans.client.events.NavTransUnitHandler;
import org.zanata.webtrans.client.events.NotificationEvent;
import org.zanata.webtrans.client.events.NotificationEvent.Severity;
import org.zanata.webtrans.client.events.OpenEditorEvent;
import org.zanata.webtrans.client.events.OpenEditorEventHandler;
import org.zanata.webtrans.client.events.RedoFailureEvent;
import org.zanata.webtrans.client.events.RequestValidationEvent;
import org.zanata.webtrans.client.events.TransUnitEditEvent;
import org.zanata.webtrans.client.events.TransUnitEditEventHandler;
import org.zanata.webtrans.client.events.TransUnitSelectionEvent;
import org.zanata.webtrans.client.events.TransUnitUpdatedEvent;
import org.zanata.webtrans.client.events.TransUnitUpdatedEventHandler;
import org.zanata.webtrans.client.events.UndoAddEvent;
import org.zanata.webtrans.client.events.UndoFailureEvent;
import org.zanata.webtrans.client.events.UndoRedoFinishEvent;
import org.zanata.webtrans.client.events.UserConfigChangeEvent;
import org.zanata.webtrans.client.events.WorkspaceContextUpdateEvent;
import org.zanata.webtrans.client.events.WorkspaceContextUpdateEventHandler;
import org.zanata.webtrans.client.presenter.SourceContentsPresenter;
import org.zanata.webtrans.client.presenter.UserConfigHolder;
import org.zanata.webtrans.client.resources.TableEditorMessages;
import org.zanata.webtrans.client.rpc.CachingDispatchAsync;
import org.zanata.webtrans.client.ui.FilterViewConfirmationPanel;
import org.zanata.webtrans.shared.auth.AuthenticationError;
import org.zanata.webtrans.shared.auth.AuthorizationError;
import org.zanata.webtrans.shared.auth.Identity;
import org.zanata.webtrans.shared.model.DocumentId;
import org.zanata.webtrans.shared.model.TransUnit;
import org.zanata.webtrans.shared.model.TransUnitId;
import org.zanata.webtrans.shared.model.WorkspaceContext;
import org.zanata.webtrans.shared.rpc.EditingTranslationAction;
import org.zanata.webtrans.shared.rpc.EditingTranslationResult;
import org.zanata.webtrans.shared.rpc.GetTransUnitList;
import org.zanata.webtrans.shared.rpc.GetTransUnitListResult;
import org.zanata.webtrans.shared.rpc.GetTransUnitsNavigation;
import org.zanata.webtrans.shared.rpc.GetTransUnitsNavigationResult;
import org.zanata.webtrans.shared.rpc.UpdateTransUnit;
import org.zanata.webtrans.shared.rpc.UpdateTransUnitResult;

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.HasSelectionHandlers;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.gen2.table.client.TableModel;
import com.google.gwt.gen2.table.client.TableModel.Callback;
import com.google.gwt.gen2.table.client.TableModelHelper.Request;
import com.google.gwt.gen2.table.client.TableModelHelper.SerializableResponse;
import com.google.gwt.gen2.table.event.client.HasPageChangeHandlers;
import com.google.gwt.gen2.table.event.client.HasPageCountChangeHandlers;
import com.google.gwt.gen2.table.event.client.PageChangeHandler;
import com.google.gwt.gen2.table.event.client.PageCountChangeHandler;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.inject.Inject;

public class TableEditorPresenter extends WidgetPresenter<TableEditorPresenter.Display> implements HasPageNavigation
{
   public interface Display extends WidgetDisplay, HasPageNavigation
   {
      HasSelectionHandlers<TransUnit> getSelectionHandlers();

      HasPageChangeHandlers getPageChangeHandlers();

      HasPageCountChangeHandlers getPageCountChangeHandlers();

      RedirectingCachedTableModel<TransUnit> getTableModel();

      void setTableModelHandler(TableModelHandler<TransUnit> hadler);

      void reloadPage();

      void setPageSize(int size);

      void gotoRow(int row, boolean andEdit);

      TransUnit getTransUnitValue(int row);

      InlineTargetCellEditor getTargetCellEditor();

      List<TransUnit> getRowValues();

      boolean isFirstPage();

      boolean isLastPage();

      int getCurrentPage();

      int getPageSize();

      void setFindMessage(String findMessage);

      void startProcessing();

      void stopProcessing();

      /**
       * @return The index of the 'selected' row on the currently displayed
       *         page, or 0 if no row is selected
       */
      int getSelectedRowNumber();

      void setTransUnitDetails(TransUnit selectedTransUnit);

      boolean isProcessing();
   }

   private DocumentId documentId;

   // private TransFilterPresenter.Display transFilterDisplay;

   private final CachingDispatchAsync dispatcher;
   private final Identity identity;
   private TransUnit selectedTransUnit;
   private TransUnitId targetTransUnitId;
   // private int lastRowNum;
   private List<Long> transIdNextNewFuzzyCache = new ArrayList<Long>();
   private List<Long> transIdPrevNewFuzzyCache = new ArrayList<Long>();

   private List<Long> transIdNextFuzzyCache = new ArrayList<Long>();
   private List<Long> transIdPrevFuzzyCache = new ArrayList<Long>();

   private List<Long> transIdNextNewCache = new ArrayList<Long>();
   private List<Long> transIdPrevNewCache = new ArrayList<Long>();

   private int curRowIndex;
   private int curPage;

   private String findMessage;

   private final TableEditorMessages messages;

   private UndoableTransUnitUpdateAction inProcessing;

   private final FilterViewConfirmationPanel filterViewConfirmationPanel = new FilterViewConfirmationPanel();

   private final WorkspaceContext workspaceContext;

   private final SourceContentsPresenter sourceContentsPresenter;
   private TargetContentsPresenter targetContentsPresenter;
   private UserConfigHolder configHolder;
   private Scheduler scheduler;

   private boolean filterTranslated, filterNeedReview, filterUntranslated;

   private final UndoableTransUnitUpdateHandler undoableTransUnitUpdateHandler = new UndoableTransUnitUpdateHandler()
   {
      @Override
      public void undo(final UndoableTransUnitUpdateAction action)
      {
         action.setUndo(true);
         action.setRedo(false);
         inProcessing = action;
         if (selectedTransUnit != null)
         {
            Log.info("cancel edit");
            display.getTargetCellEditor().cancelEdit();
         }
         eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.notifySaving()));
         dispatcher.rollback(action.getAction(), action.getResult(), new AsyncCallback<Void>()
         {
            @Override
            public void onFailure(Throwable e)
            {
               Log.error("UpdateTransUnit failure " + e, e);
               eventBus.fireEvent(new NotificationEvent(Severity.Error, messages.notifyUpdateFailed(e.getLocalizedMessage())));
               inProcessing = null;
               // put back the old cell value
               display.getTableModel().clearCache();
               display.reloadPage();
               eventBus.fireEvent(new UndoFailureEvent(action));
            }

            @Override
            public void onSuccess(Void result)
            {
               eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.notifyUpdateSaved()));
            }
         });
      }

      @Override
      public void redo(final UndoableTransUnitUpdateAction action)
      {
         action.setRedo(true);
         action.setUndo(false);
         inProcessing = action;
         final UpdateTransUnit updateTransUnit = action.getAction();
         updateTransUnit.setRedo(true);
         updateTransUnit.setVerNum(action.getResult().getCurrentVersionNum());
         eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.notifySaving()));
         dispatcher.execute(updateTransUnit, new AsyncCallback<UpdateTransUnitResult>()
         {
            @Override
            public void onFailure(Throwable e)
            {
               Log.error("redo failure " + e, e);
               eventBus.fireEvent(new NotificationEvent(Severity.Error, messages.notifyUpdateFailed(e.getLocalizedMessage())));
               inProcessing = null;
               // put back the old cell value
               display.getTableModel().clearCache();
               display.reloadPage();
               eventBus.fireEvent(new RedoFailureEvent(action));
            }

            @Override
            public void onSuccess(UpdateTransUnitResult result)
            {
               eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.notifyUpdateSaved()));
            }
         });
      }
   };

   @Inject
   public TableEditorPresenter(final Display display, final EventBus eventBus, final CachingDispatchAsync dispatcher, final Identity identity, final TableEditorMessages messages, final WorkspaceContext workspaceContext, final SourceContentsPresenter sourceContentsPresenter, TargetContentsPresenter targetContentsPresenter, UserConfigHolder configHolder, Scheduler scheduler)
   {
      super(display, eventBus);
      this.dispatcher = dispatcher;
      this.identity = identity;
      this.messages = messages;
      this.workspaceContext = workspaceContext;
      this.sourceContentsPresenter = sourceContentsPresenter;
      this.targetContentsPresenter = targetContentsPresenter;
      this.configHolder = configHolder;
      this.scheduler = scheduler;
   }

   private void clearCacheList()
   {
      if (!transIdNextNewFuzzyCache.isEmpty())
         transIdNextNewFuzzyCache.clear();
      if (!transIdPrevNewFuzzyCache.isEmpty())
         transIdPrevNewFuzzyCache.clear();

      if (!transIdNextNewCache.isEmpty())
         transIdNextNewCache.clear();
      if (!transIdPrevNewCache.isEmpty())
         transIdPrevNewCache.clear();

      if (!transIdNextFuzzyCache.isEmpty())
         transIdNextFuzzyCache.clear();
      if (!transIdPrevFuzzyCache.isEmpty())
         transIdPrevFuzzyCache.clear();
   }

   /**
    * Clear all current transUnit list and re-query from server. Force to run
    * requestRows@TableModelHandler
    */
   private void initialiseTransUnitList()
   {
      display.getTableModel().clearCache();
      display.getTableModel().setRowCount(TableModel.UNKNOWN_ROW_COUNT);
      display.gotoPage(0, true);
   }

   @Override
   protected void onBind()
   {
      display.setTableModelHandler(tableModelHandler);
      display.setPageSize(TableConstants.PAGE_SIZE);
//      if (workspaceContext.isReadOnly())
//      {
//         display.getTargetCellEditor().setShowOperationButtons(false);
//      }

      registerHandler(filterViewConfirmationPanel.getSaveChangesAndFilterButton().addClickHandler(new ClickHandler()
      {
         @Override
         public void onClick(ClickEvent event)
         {
            Log.info("Save changes and filter");
            filterViewConfirmationPanel.updateFilter(filterTranslated, filterNeedReview, filterUntranslated);
            filterViewConfirmationPanel.hide();

            display.getTargetCellEditor().savePendingChange(true);
            if (selectedTransUnit != null)
            {
               targetTransUnitId = selectedTransUnit.getId();
            }
            initialiseTransUnitList();
         }
      }));

      registerHandler(filterViewConfirmationPanel.getDiscardChangesAndFilterButton().addClickHandler(new ClickHandler()
      {
         @Override
         public void onClick(ClickEvent event)
         {
            Log.info("Discard changes and filter");
            filterViewConfirmationPanel.updateFilter(filterTranslated, filterNeedReview, filterUntranslated);
            filterViewConfirmationPanel.hide();

            display.getTargetCellEditor().cancelEdit();
            if (selectedTransUnit != null)
            {
               targetTransUnitId = selectedTransUnit.getId();
            }
            initialiseTransUnitList();
         }
      }));

      registerHandler(filterViewConfirmationPanel.getCancelFilterButton().addClickHandler(new ClickHandler()
      {
         @Override
         public void onClick(ClickEvent event)
         {
            Log.info("Cancel filter");
            eventBus.fireEvent(new FilterViewEvent(filterViewConfirmationPanel.isFilterTranslated(), filterViewConfirmationPanel.isFilterNeedReview(), filterViewConfirmationPanel.isFilterUntranslated(), true));
            filterViewConfirmationPanel.hide();
         }
      }));

      registerHandler(eventBus.addHandler(FilterViewEvent.getType(), new FilterViewEventHandler()
      {
         @Override
         public void onFilterView(FilterViewEvent event)
         {
            if (!event.isCancelFilter())
            {
               filterTranslated = event.isFilterTranslated();
               filterNeedReview = event.isFilterNeedReview();
               filterUntranslated = event.isFilterUntranslated();

               if (display.getTargetCellEditor().isOpened() && display.getTargetCellEditor().isEditing())
               {
                  filterViewConfirmationPanel.center();
               }
               else
               {
                  filterViewConfirmationPanel.updateFilter(filterTranslated, filterNeedReview, filterUntranslated);
                  if (selectedTransUnit != null)
                  {
                     targetTransUnitId = selectedTransUnit.getId();
                  }
                  initialiseTransUnitList();
               }
            }
         }
      }));

      registerHandler(display.getSelectionHandlers().addSelectionHandler(new SelectionHandler<TransUnit>()
      {
         @Override
         public void onSelection(SelectionEvent<TransUnit> event)
         {
            if (event.getSelectedItem() != null)
            {
               display.getTargetCellEditor().savePendingChange(true);
               selectTransUnit(event.getSelectedItem());
            }
         }
      }));

      registerHandler(eventBus.addHandler(DocumentSelectionEvent.getType(), new DocumentSelectionHandler()
      {
         @Override
         public void onDocumentSelected(DocumentSelectionEvent event)
         {
            loadDocument(event.getDocumentId());
         }
      }));

      registerHandler(eventBus.addHandler(FindMessageEvent.getType(), new FindMessageHandler()
      {

         @Override
         public void onFindMessage(FindMessageEvent event)
         {
            Log.info("Find Message Event: " + event.getMessage());
            display.getTargetCellEditor().savePendingChange(true);
            if (selectedTransUnit != null)
            {
               Log.info("cancelling selection");
               display.getTargetCellEditor().clearSelection();
            }
            display.startProcessing();
            findMessage = event.getMessage();
            display.setFindMessage(findMessage);
            if (selectedTransUnit != null)
            {
               targetTransUnitId = selectedTransUnit.getId();
            }
            initialiseTransUnitList();
         }

      }));

      registerHandler(eventBus.addHandler(TransUnitUpdatedEvent.getType(), new TransUnitUpdatedEventHandler()
      {
         @Override
         public void onTransUnitUpdated(TransUnitUpdatedEvent event)
         {
            if (documentId != null && documentId.equals(event.getDocumentId()))
            {
               // Clear the cache
               clearCacheList();
               if (selectedTransUnit != null && selectedTransUnit.getId().equals(event.getTransUnit().getId()))
               {
                  Log.info("selected TU updated; clear selection");
                  display.getTargetCellEditor().cancelEdit();
                  eventBus.fireEvent(new RequestValidationEvent());
               }

               Integer rowIndex = getRowIndex(event.getTransUnit());
               // - add TU index to model
               if (rowIndex != null)
               {
                  Log.info("onTransUnitUpdated - update row:" + rowIndex);
                  display.getTableModel().setRowValueOverride(rowIndex, event.getTransUnit());

                  if (inProcessing != null)
                  {
                     if (inProcessing.getAction().getTransUnitId().equals(event.getTransUnit().getId()))
                     {
                        tableModelHandler.gotoRow(rowIndex, true);
                        eventBus.fireEvent(new UndoRedoFinishEvent(inProcessing));
                        inProcessing = null;
                     }
                  }
               }
               else
               {
                  if (inProcessing != null)
                  {
                     display.getTableModel().clearCache();
                     display.getTargetCellEditor().cancelEdit();
                     if (inProcessing.getAction().getTransUnitId().equals(event.getTransUnit().getId()))
                     {
                        int pageNum = inProcessing.getCurrentPage();
                        int rowNum = inProcessing.getRowNum();
                        rowIndex = pageNum * display.getPageSize() + rowNum;
                        tableModelHandler.gotoRow(rowIndex, true);
                        eventBus.fireEvent(new UndoRedoFinishEvent(inProcessing));
                        inProcessing = null;
                     }
                  }
               }
               if (!display.isProcessing() && event.getUsername().equals(identity.getPerson().getId().toString()) && selectedTransUnit != null && selectedTransUnit.getId().equals(event.getTransUnit().getId()))
               {
                  tableModelHandler.gotoRow(curRowIndex, true);
               }
            }
         }
      }));

      registerHandler(eventBus.addHandler(TransUnitEditEvent.getType(), new TransUnitEditEventHandler()
      {
         @Override
         public void onTransUnitEdit(TransUnitEditEvent event)
         {
            if (documentId != null && documentId.equals(event.getDocumentId()))
            {
               if (selectedTransUnit != null && selectedTransUnit.getId().equals(event.getTransUnitId()))
               {
                  // handle change in current selection
                  if (!event.getSessionId().equals(identity.getSessionId()))
                     eventBus.fireEvent(new NotificationEvent(Severity.Warning, messages.notifyInEdit()));
               }
            }
         }
      }));

      registerHandler(eventBus.addHandler(NavTransUnitEvent.getType(), new NavTransUnitHandler()
      {
         @Override
         public void onNavTransUnit(NavTransUnitEvent event)
         {
            if (selectedTransUnit != null)
            {
               // int step = event.getStep();
               // Send message to server to stop editing current selection
               // stopEditing(selectedTransUnit);

               // If goto Next or Prev Fuzzy/New Trans Unit
               if (event.getRowType() == NavigationType.PrevEntry)
               {
                  targetContentsPresenter.saveAsApprovedAndMovePrevious();
               }

               if (event.getRowType() == NavigationType.NextEntry)
               {
                  targetContentsPresenter.saveAsApprovedAndMoveNext();
               }

               if (event.getRowType() == NavigationType.PrevState)
               {
                  targetContentsPresenter.moveToNextState(NavigationType.PrevEntry);
               }

               if (event.getRowType() == NavigationType.NextState)
               {
                  targetContentsPresenter.moveToNextState(NavigationType.NextEntry);
               }

               if (event.getRowType() == NavigationType.FirstEntry)
               {
                  targetContentsPresenter.saveAndMoveRow(NavigationType.FirstEntry);
               }

               if (event.getRowType() == NavigationType.LastEntry)
               {
                  targetContentsPresenter.saveAndMoveRow(NavigationType.LastEntry);
               }

            }
         }
      }));

      registerHandler(eventBus.addHandler(OpenEditorEvent.getType(), new OpenEditorEventHandler()
      {
         @Override
         public void onOpenEditor(OpenEditorEvent event)
         {
            tableModelHandler.gotoRowInCurrentPage(event.getRowNum(), true);
         }
      }));

      registerHandler(eventBus.addHandler(WorkspaceContextUpdateEvent.getType(), new WorkspaceContextUpdateEventHandler()
      {
         @Override
         public void onWorkspaceContextUpdated(WorkspaceContextUpdateEvent event)
         {
            boolean readOnly = event.isReadOnly();
            workspaceContext.setReadOnly(readOnly);
            configHolder.setDisplayButtons(false);
            eventBus.fireEvent(new UserConfigChangeEvent());
            display.getTargetCellEditor().setReadOnly(readOnly);

            if (readOnly)
            {
               eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.notifyReadOnlyWorkspace()));
            }
            else
            {
               eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.notifyEditableWorkspace()));
            }
         }
      }));

      display.gotoFirstPage();

      History.fireCurrentHistoryState();
   }

   public boolean isFiltering()
   {
      return (findMessage != null && !findMessage.isEmpty()) || (filterViewConfirmationPanel.isFilterTranslated() || filterViewConfirmationPanel.isFilterNeedReview() || filterViewConfirmationPanel.isFilterUntranslated());
   }

   public Integer getRowIndex(TransUnit tu)
   {
      if (!isFiltering())
      {
         return tu.getRowIndex();
      }
      else
      {
         TransUnitId transUnitId = tu.getId();
         int n = 0;
         for (TransUnit transUnit : display.getRowValues())
         {
            if (transUnitId.equals(transUnit.getId()))
            {
               int row = n + (curPage * display.getPageSize());
               return row;
            }
            n++;
         }
      }
      return null;
   }

   private final TableModelHandler<TransUnit> tableModelHandler = new TableModelHandler<TransUnit>()
   {

      @Override
      public void requestRows(final Request request, final Callback<TransUnit> callback)
      {
         int numRows = request.getNumRows();
         int startRow = request.getStartRow();
         Log.info("Table requesting " + numRows + " starting from " + startRow);

         if (documentId == null)
         {
            callback.onFailure(new RuntimeException("No DocumentId"));
            return;
         }

         display.startProcessing();

         dispatcher.execute(new GetTransUnitList(documentId, startRow, numRows, findMessage, filterViewConfirmationPanel.isFilterTranslated(), filterViewConfirmationPanel.isFilterNeedReview(), filterViewConfirmationPanel.isFilterUntranslated(), targetTransUnitId), new AsyncCallback<GetTransUnitListResult>()
         {

            @Override
            public void onSuccess(GetTransUnitListResult result)
            {
               targetContentsPresenter.initWidgets(display.getPageSize());
               sourceContentsPresenter.initWidgets(display.getPageSize());
               SerializableResponse<TransUnit> response = new SerializableResponse<TransUnit>(result.getUnits());
               Log.info("Got " + result.getUnits().size() + " rows back of " + result.getTotalCount() + " available");
               callback.onRowsReady(request, response);
               display.getTableModel().setRowCount(result.getTotalCount());
               display.stopProcessing();

               int gotoRow = curRowIndex;

               if (result.getUnits().size() > 0)
               {
                  if (result.getGotoRow() != -1)
                  {
                     gotoRow = result.getGotoRow();
                  }
                  tableModelHandler.gotoRow(gotoRow, false);
               }
            }

            @Override
            public void onFailure(Throwable caught)
            {
               if (caught instanceof AuthenticationError)
               {
                  eventBus.fireEvent(new NotificationEvent(Severity.Error, messages.notifyNotLoggedIn()));
               }
               else if (caught instanceof AuthorizationError)
               {
                  eventBus.fireEvent(new NotificationEvent(Severity.Error, messages.notifyLoadFailed()));
               }
               else
               {
                  Log.error("GetTransUnits failure " + caught, caught);
                  eventBus.fireEvent(new NotificationEvent(Severity.Error, messages.notifyLoadFailed()));
               }
               display.stopProcessing();
            }
         });
         targetTransUnitId = null;
      }

      @Override
      public boolean onSetRowValue(int row, TransUnit rowValue)
      {
         final UpdateTransUnit updateTransUnit = new UpdateTransUnit(rowValue.getId(), rowValue.getTargets(), rowValue.getStatus());
         eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.notifySaving()));
         dispatcher.execute(updateTransUnit, new AsyncCallback<UpdateTransUnitResult>()
         {
            @Override
            public void onFailure(Throwable e)
            {
               Log.error("UpdateTransUnit failure " + e, e);
               eventBus.fireEvent(new NotificationEvent(Severity.Error, messages.notifyUpdateFailed(e.getLocalizedMessage())));

               display.getTableModel().clearCache();
               display.reloadPage();
            }

            @Override
            public void onSuccess(UpdateTransUnitResult result)
            {
               eventBus.fireEvent(new NotificationEvent(Severity.Info, messages.notifyUpdateSaved()));
               UndoableTransUnitUpdateAction undoAction = new UndoableTransUnitUpdateAction(updateTransUnit, result, curRowIndex, curPage);
               undoAction.setHandler(undoableTransUnitUpdateHandler);
               eventBus.fireEvent(new UndoAddEvent(undoAction));
            }
         });
         stopEditing(rowValue);
         return true;
      }

      public void onCancel(TransUnit rowValue)
      {
         // stopEditing(rowValue);
      }

      @Override
      public void updatePageAndRowIndex()
      {
         curPage = display.getCurrentPage();
         curRowIndex = curPage * display.getPageSize() + display.getSelectedRowNumber();
         Log.info("Current Row Index:" + curRowIndex + " Current page:" + curPage);
      }

      @Override
      public void gotoNextRow(boolean andEdit)
      {
         updatePageAndRowIndex();
         int newRowIndex = curRowIndex + 1;
         if (newRowIndex < display.getTableModel().getRowCount())
         {
            gotoRow(newRowIndex, andEdit);
         }
      }

      @Override
      public void gotoPrevRow(boolean andEdit)
      {
         updatePageAndRowIndex();
         int newRowIndex = curRowIndex - 1;
         if (newRowIndex >= 0)
         {
            gotoRow(newRowIndex, andEdit);
         }
      }

      @Override
      public void gotoFirstRow()
      {
         updatePageAndRowIndex();
         gotoRow(0, true);
      }

      @Override
      public void gotoLastRow()
      {
         updatePageAndRowIndex();
         gotoRow(display.getTableModel().getRowCount() - 1, true);
      }

      @Override
      public void nextFuzzyNewIndex()
      {
         updatePageAndRowIndex();
         if (curRowIndex < display.getTableModel().getRowCount())
            gotoNextState(true, true);
      }

      @Override
      public void prevFuzzyNewIndex()
      {
         updatePageAndRowIndex();
         if (curRowIndex > 0)
            gotoPrevState(true, true);
      }

      @Override
      public void nextFuzzyIndex()
      {
         updatePageAndRowIndex();
         if (curRowIndex < display.getTableModel().getRowCount())
            gotoNextState(false, true);
      }

      @Override
      public void prevFuzzyIndex()
      {
         updatePageAndRowIndex();
         if (curRowIndex > 0)
            gotoPrevState(false, true);
      }

      @Override
      public void nextNewIndex()
      {
         updatePageAndRowIndex();
         if (curRowIndex < display.getTableModel().getRowCount())
            gotoNextState(true, false);
      }

      @Override
      public void prevNewIndex()
      {
         updatePageAndRowIndex();
         if (curRowIndex > 0)
            gotoPrevState(true, false);
      }

      @Override
      public void gotoRow(int rowIndex, boolean andEdit)
      {
         curPage = display.getCurrentPage();
         int prevPage = curPage;
         int pageNum = rowIndex / (MAX_PAGE_ROW + 1);
         int rowNum = rowIndex % (MAX_PAGE_ROW + 1);
         if (pageNum != prevPage)
         {
            display.gotoPage(pageNum, false);
         }
         display.gotoRow(rowNum, andEdit);
         selectTransUnit(display.getTransUnitValue(rowNum));

         if (pageNum != prevPage)
         {
            display.getTargetCellEditor().cancelEdit();
         }
      }

      @Override
      public void gotoRowInCurrentPage(int rowNum, boolean andEdit)
      {
         display.gotoRow(rowNum, andEdit);
         selectTransUnit(display.getTransUnitValue(rowNum));
      }
   };

   private void stopEditing(TransUnit rowValue)
   {
      dispatcher.execute(new EditingTranslationAction(rowValue.getId(), EditState.StopEditing), new AsyncCallback<EditingTranslationResult>()
      {
         @Override
         public void onSuccess(EditingTranslationResult result)
         {
            // eventBus.fireEvent(new NotificationEvent(Severity.Warning,
            // "TransUnit Editing is finished"));
         }

         @Override
         public void onFailure(Throwable caught)
         {
            Log.error("EditingTranslationAction failure " + caught, caught);
            eventBus.fireEvent(new NotificationEvent(Severity.Error, messages.notifyStopFailed()));
         }

      });
   }

   boolean isReqComplete = true;

   private void cacheNextState(final NavigationCacheCallback callBack, final List<Long> cacheList, final boolean isNewState, final boolean isFuzzyState)
   {
      isReqComplete = false;
      dispatcher.execute(new GetTransUnitsNavigation(selectedTransUnit.getId().getId(), 3, false, findMessage, isNewState, isFuzzyState), new AsyncCallback<GetTransUnitsNavigationResult>()
      {
         @Override
         public void onSuccess(GetTransUnitsNavigationResult result)
         {
            isReqComplete = true;
            if (!result.getUnits().isEmpty())
            {
               for (Long offset : result.getUnits())
               {
                  cacheList.add(offset + curRowIndex);
               }
               callBack.next(isNewState, isFuzzyState);
            }
         }

         @Override
         public void onFailure(Throwable caught)
         {
            Log.error("GetTransUnitsStates failure " + caught, caught);
         }
      });
   }

   private void cachePrevState(final NavigationCacheCallback callBack, final List<Long> cacheList, final boolean isNewState, final boolean isFuzzyState)
   {
      isReqComplete = false;
      dispatcher.execute(new GetTransUnitsNavigation(selectedTransUnit.getId().getId(), 3, true, findMessage, isNewState, isFuzzyState), new AsyncCallback<GetTransUnitsNavigationResult>()
      {
         @Override
         public void onSuccess(GetTransUnitsNavigationResult result)
         {
            isReqComplete = true;
            if (!result.getUnits().isEmpty())
            {
               for (Long offset : result.getUnits())
               {
                  cacheList.add(curRowIndex - offset);
               }
               callBack.prev(isNewState, isFuzzyState);
            }
         }

         @Override
         public void onFailure(Throwable caught)
         {
            Log.error("GetTransUnitsStates failure " + caught, caught);
         }
      });
   }

   NavigationCacheCallback cacheCallback = new NavigationCacheCallback()
   {
      @Override
      public void next(boolean isNewState, boolean isFuzzyState)
      {
         gotoNextState(isNewState, isFuzzyState);
      }

      @Override
      public void prev(boolean isNewState, boolean isFuzzyState)
      {
         gotoPrevState(isNewState, isFuzzyState);
      }

   };

   private void gotoNextState(boolean isNewState, boolean isFuzzyState)
   {
      if (isNewState && isFuzzyState)
      {
         Log.info("go to Next Fuzzy Or Untranslated State");
         transIdPrevNewFuzzyCache.clear();
         gotoNextState(transIdNextNewFuzzyCache, true, true);
      }
      else if (isNewState)
      {
         Log.info("go to Next Untranslated State");
         transIdPrevNewCache.clear();
         gotoNextState(transIdNextNewCache, true, false);
      }
      else if (isFuzzyState)
      {
         Log.info("go to Next Fuzzy State");
         transIdPrevFuzzyCache.clear();
         gotoNextState(transIdNextFuzzyCache, false, true);
      }
   }

   private void gotoPrevState(boolean isNewState, boolean isFuzzyState)
   {
      if (isNewState && isFuzzyState)
      {
         Log.info("go to Prev Fuzzy Or Untranslated State");
         // Clean the cache for Next Fuzzy to avoid issues about cache is
         // obsolete
         transIdNextNewFuzzyCache.clear();
         gotoPrevState(transIdPrevNewFuzzyCache, true, true);
      }
      else if (isNewState)
      {
         Log.info("go to Prev Untranslated State");
         // Clean the cache for Next Fuzzy to avoid issues about cache is
         // obsolete
         transIdNextNewCache.clear();
         gotoPrevState(transIdPrevNewCache, true, false);
      }
      else if (isFuzzyState)
      {
         Log.info("go to Prev Fuzzy State");
         // Clean the cache for Next Fuzzy to avoid issues about cache is
         // obsolete
         transIdNextFuzzyCache.clear();
         gotoPrevState(transIdPrevFuzzyCache, false, true);
      }
   }

   private void gotoPrevState(List<Long> transIdPrevCache, boolean isNewState, boolean isFuzzyState)
   {
      // If the catch of row is empty and request is complete, generate
      // one
      if (transIdPrevCache.isEmpty())
      {
         if (isReqComplete)
            cachePrevState(cacheCallback, transIdPrevCache, isNewState, isFuzzyState);
      }
      else
      {
         int size = transIdPrevCache.size();
         int offset = transIdPrevCache.get(size - 1).intValue();
         if (curRowIndex > offset)
         {
            for (int i = 0; i < size; i++)
            {
               int newRowIndex = transIdPrevCache.get(i).intValue();
               if (curRowIndex > newRowIndex)
               {
                  display.getTargetCellEditor().cancelEdit();
                  tableModelHandler.gotoRow(newRowIndex, true);
                  break;
               }
            }
         }
         else
         {
            transIdPrevCache.clear();
            cachePrevState(cacheCallback, transIdPrevCache, isNewState, isFuzzyState);
         }
      }
   }

   private void gotoNextState(List<Long> transIdNextCache, boolean isNewState, boolean isFuzzyState)
   {
      // If the cache of next is empty, generate one
      if (transIdNextCache.isEmpty())
      {
         if (isReqComplete)
            cacheNextState(cacheCallback, transIdNextCache, isNewState, isFuzzyState);
      }
      else
      {
         int size = transIdNextCache.size();
         int offset = transIdNextCache.get(size - 1).intValue();
         if (curRowIndex < offset)
         {
            for (int i = 0; i < size; i++)
            {
               int newRowIndex = transIdNextCache.get(i).intValue();
               if (curRowIndex < newRowIndex)
               {
                  display.getTargetCellEditor().cancelEdit();
                  tableModelHandler.gotoRow(newRowIndex, true);
                  break;
               }
            }
         }
         else
         {
            transIdNextCache.clear();
            cacheNextState(cacheCallback, transIdNextCache, isNewState, isFuzzyState);
         }
      }
   }

   public TransUnit getSelectedTransUnit()
   {
      return selectedTransUnit;
   }

   @Override
   protected void onUnbind()
   {
   }

   @Override
   public void onRevealDisplay()
   {
   }

   @Override
   public void gotoFirstPage()
   {
      display.gotoFirstPage();
   }

   @Override
   public void gotoLastPage()
   {
      display.gotoLastPage();
   }

   @Override
   public void gotoNextPage()
   {
      display.gotoNextPage();
   }

   @Override
   public void gotoPage(int page, boolean forced)
   {
      display.gotoPage(page, forced);
   }

   @Override
   public void gotoPreviousPage()
   {
      display.gotoPreviousPage();
   }

   public void addPageChangeHandler(PageChangeHandler handler)
   {
      display.getPageChangeHandlers().addPageChangeHandler(handler);
   }

   public void addPageCountChangeHandler(PageCountChangeHandler handler)
   {
      display.getPageCountChangeHandlers().addPageCountChangeHandler(handler);
   }

   public DocumentId getDocumentId()
   {
      return documentId;
   }

   /**
    * Selects the given TransUnit and fires associated TU Selection event
    * 
    * @param transUnit the new TO to select
    */
   public void selectTransUnit(final TransUnit transUnit)
   {
      //we want to make sure select transunit always happen first
      scheduler.scheduleEntry(new Command()
      {
         @Override
         public void execute()
         {
            tableModelHandler.updatePageAndRowIndex();

            display.setTransUnitDetails(transUnit);

            if (selectedTransUnit == null || !transUnit.getId().equals(selectedTransUnit.getId()))
            {
               selectedTransUnit = transUnit;
               Log.info("SelectedTransUnit: " + selectedTransUnit.getId());
               // Clean the cache when we click the new entry
               clearCacheList();

               eventBus.fireEvent(new TransUnitSelectionEvent(selectedTransUnit));
               sourceContentsPresenter.setSelectedSource(display.getSelectedRowNumber());
               display.getTargetCellEditor().savePendingChange(true);

               display.gotoRow(display.getSelectedRowNumber(), true);
            }
         }
      });

   }

   public void gotoCurrentRow()
   {
      tableModelHandler.gotoRow(curRowIndex, true);
   }

   public void gotoPrevRow(boolean andEdit)
   {
      tableModelHandler.gotoPrevRow(andEdit);

   }

   public void gotoNextRow(boolean andEdit)
   {
      tableModelHandler.gotoNextRow(andEdit);
   }

   public int getSelectedRowIndex()
   {
      return curRowIndex;
   }
   /**
    * Load a document into the editor
    * 
    * @param selectDocId id of the document to select
    */
   private void loadDocument(DocumentId selectDocId)
   {
      if (!selectDocId.equals(documentId))
      {
         display.startProcessing();
         documentId = selectDocId;
         initialiseTransUnitList();
      }
   }
}

package org.zanata.webtrans.client.view;

import java.util.List;

import org.zanata.webtrans.client.presenter.TransMemoryPresenter;
import org.zanata.webtrans.client.resources.Resources;
import org.zanata.webtrans.client.resources.UiMessages;
import org.zanata.webtrans.client.ui.EnumListBox;
import org.zanata.webtrans.client.ui.SearchTypeRenderer;
import org.zanata.webtrans.client.ui.table.column.CopyButtonColumn;
import org.zanata.webtrans.client.ui.table.column.DetailsColumn;
import org.zanata.webtrans.client.ui.table.column.SimilarityColumn;
import org.zanata.webtrans.client.ui.table.column.TransMemorySourceColumn;
import org.zanata.webtrans.client.ui.table.column.TransMemoryTargetColumn;
import org.zanata.webtrans.shared.model.TransMemoryResultItem;
import org.zanata.webtrans.shared.rpc.HasSearchType.SearchType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.BlurEvent;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.ValueListBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.DefaultSelectionEventManager;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.NoSelectionModel;
import com.google.inject.Inject;

public class TransMemoryView extends Composite implements TransMemoryPresenter.Display
{

   private static TransMemoryViewUiBinder uiBinder = GWT.create(TransMemoryViewUiBinder.class);

   interface TransMemoryViewUiBinder extends UiBinder<Widget, TransMemoryView>
   {
   }

   @UiField
   TextBox tmTextBox;

   @UiField
   Button searchButton;

   @UiField
   Label headerLabel;

   @UiField(provided = true)
   ValueListBox<SearchType> searchType;

   @UiField
   Button clearButton;

   @UiField
   ScrollPanel scrollPanel;

   CellTable<TransMemoryResultItem> tmTable;

   private boolean isFocused;

   private UiMessages messages;
   private ListDataProvider<TransMemoryResultItem> dataProvider;

   private TransMemorySourceColumn sourceColumn;
   private TransMemoryTargetColumn targetColumn;
   private CopyButtonColumn<TransMemoryResultItem> copyColumn;
   private DetailsColumn<TransMemoryResultItem> detailsColumn;

   @Inject
   public TransMemoryView(final UiMessages messages, SearchTypeRenderer searchTypeRenderer, final Resources resources)
   {
      this.messages = messages;

      sourceColumn = new TransMemorySourceColumn();
      targetColumn = new TransMemoryTargetColumn();
      copyColumn = new CopyButtonColumn<TransMemoryResultItem>();
      detailsColumn = new DetailsColumn<TransMemoryResultItem>(resources);

      searchType = new EnumListBox<SearchType>(SearchType.class, searchTypeRenderer);
      initWidget(uiBinder.createAndBindUi(this));

      headerLabel.setText(messages.translationMemoryHeading());
      clearButton.setText(messages.clearButtonLabel());
      searchButton.setText(messages.searchButtonLabel());
   }

   @Override
   public void setQueries(List<String> queries)
   {
      sourceColumn.setQueries(queries);
   }

   @UiHandler("tmTextBox")
   void onTmTextBoxKeyUp(KeyUpEvent event)
   {
      if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER)
      {
         searchButton.click();
      }
   }

   @UiHandler("tmTextBox")
   public void onTmTextBoxFocus(FocusEvent event)
   {
      isFocused = true;
   }

   @UiHandler("tmTextBox")
   public void onTmTextBoxBlur(BlurEvent event)
   {
      isFocused = false;
   }

   @Override
   public HasClickHandlers getClearButton()
   {
      return clearButton;
   }

   @Override
   public HasClickHandlers getSearchButton()
   {
      return searchButton;
   }

   @Override
   public HasValue<SearchType> getSearchType()
   {
      return searchType;
   }

   public TextBox getTmTextBox()
   {
      return tmTextBox;
   }

   @Override
   public Widget asWidget()
   {
      return this;
   }

   @Override
   public void startProcessing()
   {
      // TODO show waiting indicator
   }

   @Override
   public void stopProcessing()
   {
      // TODO hide waiting indicator
      // TODO add test for start and stop processing at appropriate times
   }

   @Override
   public void setPageSize(int size)
   {
      tmTable.setPageSize(size);
   }

   @Override
   public boolean isFocused()
   {
      return isFocused;
   }

   @Override
   public Column<TransMemoryResultItem, ImageResource> getDetailsColumn()
   {
      return detailsColumn;
   }

   @Override
   public Column<TransMemoryResultItem, String> getCopyColumn()
   {
      return copyColumn;
   }

   @Override
   public void setDataProvider(ListDataProvider<TransMemoryResultItem> dataProvider)
   {
      this.dataProvider = dataProvider;
      renderTable();
   }

   private void renderTable()
   {
      tmTable = new CellTable<TransMemoryResultItem>();
      tmTable.addStyleName("tmTable");
      tmTable.addStyleName("southTable");
      tmTable.addColumn(sourceColumn, messages.sourceLabel());
      tmTable.addColumn(targetColumn, messages.targetLabel());
      tmTable.addColumn(copyColumn);
      tmTable.addColumn(new SimilarityColumn<TransMemoryResultItem>(), messages.similarityLabel());
      tmTable.addColumn(detailsColumn, messages.detailsLabel());

      final NoSelectionModel<TransMemoryResultItem> selectionModel = new NoSelectionModel<TransMemoryResultItem>();
      final DefaultSelectionEventManager<TransMemoryResultItem> manager = DefaultSelectionEventManager.createBlacklistManager(0, 1, 2);
      tmTable.setSelectionModel(selectionModel, manager);

      tmTable.setKeyboardSelectionPolicy(KeyboardSelectionPolicy.DISABLED);

      dataProvider.addDataDisplay(tmTable);

      scrollPanel.clear();
      scrollPanel.add(tmTable);
   }
}

package org.zanata.webtrans.client.ui;

import org.zanata.common.ContentState;
import org.zanata.common.TranslationStats;
import org.zanata.webtrans.client.editor.HasTranslationStats;
import org.zanata.webtrans.client.resources.WebTransMessages;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.LayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class TransUnitCountBar extends Composite implements HasTranslationStats
{

   private static TransUnitCountBarUiBinder uiBinder = GWT.create(TransUnitCountBarUiBinder.class);

   final TooltipPopupPanel tooltipPanel = new TooltipPopupPanel(true);

   interface TransUnitCountBarUiBinder extends UiBinder<Widget, TransUnitCountBar>
   {
   }

   LabelFormat labelFormat = LabelFormat.DEFAULT_FORMAT;

   @UiField
   LayoutPanel layoutPanel;

   @UiField
   FlowPanel approvedPanel, needReviewPanel, untranslatedPanel, undefinedPanel;

   @UiField
   Label label;

   private final TranslationStats stats = new TranslationStats();

   private final WebTransMessages messages;

   private int totalWidth = 100;

   private boolean isGraph = false;

   private boolean statsByWords = false;


   @Inject
   public TransUnitCountBar(WebTransMessages messages, boolean statsByWords)
   {
      this.messages = messages;
      this.statsByWords = statsByWords;
      initWidget(uiBinder.createAndBindUi(this));
      initLayoutPanelHandler();
   }

   private void initLayoutPanelHandler()
   {
      layoutPanel.addHandler(new MouseOverHandler()
      {
         @Override
         public void onMouseOver(MouseOverEvent event)
         {
            tooltipPanel.showRelativeTo(layoutPanel);
         }
      }, MouseOverEvent.getType());

      layoutPanel.addHandler(new MouseOutHandler()
      {
         @Override
         public void onMouseOut(MouseOutEvent event)
         {
            tooltipPanel.hide(true);
         }
      }, MouseOutEvent.getType());

      layoutPanel.sinkEvents(Event.ONMOUSEOVER | Event.ONMOUSEOUT);
   }

   public TransUnitCountBar(WebTransMessages messages, boolean isGraph, boolean statsByWords)
   {
      this.isGraph = isGraph;
      this.messages = messages;
      this.statsByWords = statsByWords;

      labelFormat = LabelFormat.PERCENT_COMPLETE;
   }

   private void setupLayoutPanel(double undefinedLeft, double undefinedWidth, double approvedLeft, double approvedWidth, double needReviewLeft, double needReviewWidth, double untranslatedLeft, double untranslatedWidth)
   {
      layoutPanel.forceLayout();
      layoutPanel.setWidgetLeftWidth(undefinedPanel, undefinedLeft, Unit.PX, undefinedWidth, Unit.PX);
      layoutPanel.setWidgetLeftWidth(approvedPanel, approvedLeft, Unit.PX, approvedWidth, Unit.PX);
      layoutPanel.setWidgetLeftWidth(needReviewPanel, needReviewLeft, Unit.PX, needReviewWidth, Unit.PX);
      layoutPanel.setWidgetLeftWidth(untranslatedPanel, untranslatedLeft, Unit.PX, untranslatedWidth, Unit.PX);
   }

   public void refresh()
   {
      int approved, needReview, untranslated, total;
      if (statsByWords)
      {
         approved = getWordsApproved();
         needReview = getWordsNeedReview();
         untranslated = getWordsUntranslated();
         total = getWordsTotal();
      }
      else
      {
         approved = getUnitApproved();
         needReview = getUnitNeedReview();
         untranslated = getUnitUntranslated();
         total = getUnitTotal();
      }
      int width = getOffsetWidth();
      if (total == 0)
      {
         undefinedPanel.clear();
         undefinedPanel.add(new Label(messages.noContent()));
         setupLayoutPanel(0.0, width, 0.0, 0, 0.0, 0, 0.0, 0);
         label.setText("");
      }
      else
      {
         int completePx = approved * 100 / total * width / totalWidth;
         int inProgressPx = needReview * 100 / total * width / totalWidth;
         int unfinishedPx = untranslated * 100 / total * width / totalWidth;

         setupLayoutPanel(0.0, 0, 0.0, completePx, completePx, inProgressPx, completePx + inProgressPx, unfinishedPx);
         setLabelText();
      }

      int duration = isGraph ? 0 : 1000;
      refreshDisplay(duration);
   }

   private void setLabelText()
   {
      switch (labelFormat)
      {
      case PERCENT_COMPLETE_HRS:
         label.setText(messages.statusBarPercentageHrs(stats.getApprovedPercent(statsByWords), stats.getRemainingWordsHours()));
         break;
      case PERCENT_COMPLETE:
         label.setText(messages.statusBarLabelPercentage(stats.getApprovedPercent(statsByWords)));
         break;
      default:
         label.setText("error: " + labelFormat.name());
      }
   }

   public int getApprovedPercent()
   {
      return stats.getApprovedPercent(statsByWords);
   }

   private void refreshDisplay(int duration)
   {
      tooltipPanel.refreshData(this);
      if (duration == 0)
         layoutPanel.forceLayout();
      else
         layoutPanel.animate(duration);
   }

   public int getWordsTotal()
   {
      return getWordsApproved() + getWordsNeedReview() + getWordsUntranslated();
   }

   public int getWordsApproved()
   {
      return stats.getWordCount().get(ContentState.Approved);
   }

   public int getWordsNeedReview()
   {
      return stats.getWordCount().get(ContentState.NeedReview);
   }

   public int getWordsUntranslated()
   {
      return stats.getWordCount().get(ContentState.New);
   }

   public int getUnitTotal()
   {
      return getUnitApproved() + getUnitNeedReview() + getUnitUntranslated();
   }

   public int getUnitApproved()
   {
      return stats.getUnitCount().get(ContentState.Approved);
   }

   public int getUnitNeedReview()
   {
      return stats.getUnitCount().get(ContentState.NeedReview);
   }

   public int getUnitUntranslated()
   {
      return stats.getUnitCount().get(ContentState.New);
   }

   @Override
   public void setStats(TranslationStats stats)
   {
      this.stats.set(stats);
      refresh();
   }

   public void onMouseOver(Element target)
   {
      tooltipPanel.showRelativeTo(target);
   }

   public void onMouseOut()
   {
      tooltipPanel.hide(true);
   }

   @Override
   public int getOffsetWidth()
   {
      int offsetWidth = super.getOffsetWidth();
      return offsetWidth == 0 ? 115 : offsetWidth;
   }
}

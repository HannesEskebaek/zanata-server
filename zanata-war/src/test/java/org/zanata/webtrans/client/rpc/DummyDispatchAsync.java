package org.zanata.webtrans.client.rpc;

import net.customware.gwt.dispatch.shared.Action;
import net.customware.gwt.dispatch.shared.Result;

import org.zanata.webtrans.shared.auth.AuthorizationError;
import org.zanata.webtrans.shared.rpc.AbstractWorkspaceAction;
import org.zanata.webtrans.shared.rpc.ActivateWorkspaceAction;
import org.zanata.webtrans.shared.rpc.ActivateWorkspaceResult;
import org.zanata.webtrans.shared.rpc.GetDocumentList;
import org.zanata.webtrans.shared.rpc.GetDocumentListResult;
import org.zanata.webtrans.shared.rpc.GetGlossary;
import org.zanata.webtrans.shared.rpc.GetGlossaryDetailsAction;
import org.zanata.webtrans.shared.rpc.GetGlossaryDetailsResult;
import org.zanata.webtrans.shared.rpc.GetGlossaryResult;
import org.zanata.webtrans.shared.rpc.GetStatusCount;
import org.zanata.webtrans.shared.rpc.GetStatusCountResult;
import org.zanata.webtrans.shared.rpc.GetTransMemoryDetailsAction;
import org.zanata.webtrans.shared.rpc.GetTransUnitList;
import org.zanata.webtrans.shared.rpc.GetTransUnitListResult;
import org.zanata.webtrans.shared.rpc.GetTranslationMemory;
import org.zanata.webtrans.shared.rpc.GetTranslationMemoryResult;
import org.zanata.webtrans.shared.rpc.GetTranslatorList;
import org.zanata.webtrans.shared.rpc.GetTranslatorListResult;
import org.zanata.webtrans.shared.rpc.PublishWorkspaceChatAction;
import org.zanata.webtrans.shared.rpc.PublishWorkspaceChatResult;
import org.zanata.webtrans.shared.rpc.TransMemoryDetailsList;
import org.zanata.webtrans.shared.rpc.UpdateTransUnit;
import org.zanata.webtrans.shared.rpc.UpdateTransUnitResult;

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.rpc.AsyncCallback;

public class DummyDispatchAsync extends SeamDispatchAsync
{
   public DummyDispatchAsync()
   {
      Log.info("DummyDispatchAsync()");
   }

   @SuppressWarnings("unchecked")
   @Override
   public <A extends Action<R>, R extends Result> void execute(A action, AsyncCallback<R> callback)
   {

      if (action instanceof AbstractWorkspaceAction<?>)
      {
         if (this.userWorkspaceContext == null || this.identity == null)
         {
            callback.onFailure(new AuthorizationError("Dispatcher not initialized for WorkspaceActions"));
            return;
         }
         AbstractWorkspaceAction<?> wsAction = (AbstractWorkspaceAction<?>) action;
         wsAction.setWorkspaceId(this.userWorkspaceContext.getWorkspaceContext().getWorkspaceId());
         wsAction.setEditorClientId(this.identity.getEditorClientId());
      }

      if (action instanceof GetTransUnitList)
      {
         GetTransUnitList gtuAction = (GetTransUnitList) action;
         AsyncCallback<GetTransUnitListResult> gtuCallback = (AsyncCallback<GetTransUnitListResult>) callback;
         Scheduler.get().scheduleDeferred(new DummyGetTransUnitCommand(gtuAction, gtuCallback));
      }
      else if (action instanceof GetDocumentList)
      {
         final GetDocumentList gdlAction = (GetDocumentList) action;
         AsyncCallback<GetDocumentListResult> gdlCallback = (AsyncCallback<GetDocumentListResult>) callback;
         Scheduler.get().scheduleDeferred(new DummyGetDocsListCommand(gdlAction, gdlCallback));
      }
      else if (action instanceof ActivateWorkspaceAction)
      {
         final ActivateWorkspaceAction gwcAction = (ActivateWorkspaceAction) action;
         AsyncCallback<ActivateWorkspaceResult> gwcCallback = (AsyncCallback<ActivateWorkspaceResult>) callback;
         Scheduler.get().scheduleDeferred(new DummyActivateWorkspaceCommand(gwcAction, gwcCallback));
      }
      else if (action instanceof GetTranslatorList)
      {
         final GetTranslatorList _action = (GetTranslatorList) action;
         AsyncCallback<GetTranslatorListResult> _callback = (AsyncCallback<GetTranslatorListResult>) callback;
         Scheduler.get().scheduleDeferred(new DummyGetTranslatorListCommand(_action, _callback));
      }
      else if (action instanceof GetStatusCount)
      {
         final GetStatusCount _action = (GetStatusCount) action;
         AsyncCallback<GetStatusCountResult> _callback = (AsyncCallback<GetStatusCountResult>) callback;
         Scheduler.get().scheduleDeferred(new DummyGetStatusCountCommand(_action, _callback));
      }
      else if (action instanceof GetTranslationMemory)
      {
         final GetTranslationMemory _action = (GetTranslationMemory) action;
         AsyncCallback<GetTranslationMemoryResult> _callback = (AsyncCallback<GetTranslationMemoryResult>) callback;
         Scheduler.get().scheduleDeferred(new DummyGetTranslationMemoryCommand(_action, _callback));
      }
      else if (action instanceof GetGlossary)
      {
         final GetGlossary _action = (GetGlossary) action;
         AsyncCallback<GetGlossaryResult> _callback = (AsyncCallback<GetGlossaryResult>) callback;
         Scheduler.get().scheduleDeferred(new DummyGetGlossaryCommand(_action, _callback));
      }
      else if (action instanceof UpdateTransUnit)
      {
         final UpdateTransUnit _action = (UpdateTransUnit) action;
         AsyncCallback<UpdateTransUnitResult> _callback = (AsyncCallback<UpdateTransUnitResult>) callback;
         Scheduler.get().scheduleDeferred(new DummyUpdateTransUnitCommand(_action, _callback));
      }
      else if (action instanceof GetTransMemoryDetailsAction)
      {
         final GetTransMemoryDetailsAction _action = (GetTransMemoryDetailsAction) action;
         AsyncCallback<TransMemoryDetailsList> _callback = (AsyncCallback<TransMemoryDetailsList>) callback;
         Scheduler.get().scheduleDeferred(new DummyGetTransMemoryDetailsCommand(_action, _callback));
      }
      else if (action instanceof GetGlossaryDetailsAction)
      {
         final GetGlossaryDetailsAction _action = (GetGlossaryDetailsAction) action;
         AsyncCallback<GetGlossaryDetailsResult> _callback = (AsyncCallback<GetGlossaryDetailsResult>) callback;
         Scheduler.get().scheduleDeferred(new DummyGetGlossaryDetailsCommand(_action, _callback));
      }

      else if (action instanceof PublishWorkspaceChatAction)
      {
         final PublishWorkspaceChatAction _action = (PublishWorkspaceChatAction) action;
         AsyncCallback<PublishWorkspaceChatResult> _callback = (AsyncCallback<PublishWorkspaceChatResult>) callback;
         Scheduler.get().scheduleDeferred(new DummyPublishWorkspaceChatCommand(_action, _callback));
      }
      else
      {
         Log.warn("DummyDispatchAsync: ignoring action of " + action.getClass());
         // callback.onFailure(new RuntimeException());
      }
   }

}

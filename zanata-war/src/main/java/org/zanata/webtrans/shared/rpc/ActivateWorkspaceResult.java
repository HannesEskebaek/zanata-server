package org.zanata.webtrans.shared.rpc;

import net.customware.gwt.dispatch.shared.Result;

import org.zanata.webtrans.shared.auth.Identity;
import org.zanata.webtrans.shared.model.UserWorkspaceContext;


public class ActivateWorkspaceResult implements Result
{

   private static final long serialVersionUID = 1L;

   private UserWorkspaceContext userWorkspaceContext;
   private Identity identity;

   @SuppressWarnings("unused")
   private ActivateWorkspaceResult()
   {
   }

   public ActivateWorkspaceResult(UserWorkspaceContext userWorkspaceContext, Identity identity)
   {
      this.userWorkspaceContext = userWorkspaceContext;
      this.identity = identity;
   }

   public UserWorkspaceContext getUserWorkspaceContext()
   {
      return userWorkspaceContext;
   }

   public Identity getIdentity()
   {
      return identity;
   }
}

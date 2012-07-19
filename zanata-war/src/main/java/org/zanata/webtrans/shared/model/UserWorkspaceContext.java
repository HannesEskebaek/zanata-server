package org.zanata.webtrans.shared.model;

import com.google.gwt.user.client.rpc.IsSerializable;

public class UserWorkspaceContext implements IsSerializable
{
   private boolean isProjectActive;
   private boolean hasWriteAccess;

   private WorkspaceContext workspaceContext;

   // for GWT
   @SuppressWarnings("unused")
   private UserWorkspaceContext()
   {
   }

   public UserWorkspaceContext(WorkspaceContext workspaceContext, boolean isProjectActive, boolean hasWriteAccess)
   {
      this.workspaceContext = workspaceContext;
      this.isProjectActive = isProjectActive;
      this.hasWriteAccess = hasWriteAccess;
   }

   public boolean isProjectActive()
   {
      return isProjectActive;
   }

   public void setProjectActive(boolean isProjectActive)
   {
      this.isProjectActive = isProjectActive;
   }

   public boolean hasWriteAccess()
   {
      return hasWriteAccess;
   }

   public void setHasWriteAccess(boolean hasWriteAccess)
   {
      this.hasWriteAccess = hasWriteAccess;
   }

   public WorkspaceContext getWorkspaceContext()
   {
      return workspaceContext;
   }
   
   public boolean hasReadOnlyAccess()
   {
      return (!isProjectActive() || !hasWriteAccess());
   }
}

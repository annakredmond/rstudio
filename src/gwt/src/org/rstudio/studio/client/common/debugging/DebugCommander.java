/*
 * DebugCommander.java
 *
 * Copyright (C) 2009-12 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */

package org.rstudio.studio.client.common.debugging;

import java.util.ArrayList;

import org.rstudio.core.client.DebugFilePosition;
import org.rstudio.core.client.FilePosition;
import org.rstudio.core.client.command.CommandBinder;
import org.rstudio.core.client.command.Handler;
import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.common.debugging.events.BreakpointsSavedEvent;
import org.rstudio.studio.client.common.debugging.model.Breakpoint;
import org.rstudio.studio.client.common.debugging.model.TopLevelLineData;
import org.rstudio.studio.client.common.filetypes.FileTypeRegistry;
import org.rstudio.studio.client.common.filetypes.events.OpenSourceFileEvent;
import org.rstudio.studio.client.common.filetypes.events.OpenSourceFileEvent.NavigationMethod;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.Void;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.views.console.events.SendToConsoleEvent;
import org.rstudio.studio.client.workbench.views.environment.events.DebugModeChangedEvent;
import org.rstudio.studio.client.workbench.views.environment.events.DebugModeChangedEvent.DebugMode;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DebugCommander
{
   public interface Binder
      extends CommandBinder<Commands, DebugCommander> {}

   @Inject
   public DebugCommander(
         Binder binder,
         Commands commands,
         EventBus eventBus,
         BreakpointManager breakpointManager,
         DebuggingServerOperations debugServer)
   {
      eventBus_ = eventBus;
      debugServer_ = debugServer;
      breakpointManager_ = breakpointManager;
      
      binder.bind(commands, this);

      debugStepCallback_ = new ServerRequestCallback<TopLevelLineData>()
      {
         @Override
         public void onResponseReceived(TopLevelLineData lineData)
         {
            debugStep_ = lineData.getStep();
            if (lineData.getState() == TopLevelLineData.STATE_INJECTION_SITE)
            {
               // When the server pauses for breakpoint injection, have the
               // breakpoint manager inject breakpoints into the function just
               // evaluated. Regardless of the result, the breakpoint manager 
               // will emit a BreakpointsSavedEvent, which we'll use as a cue
               // to continue execution.
               waitingForBreakpointInject_ = 
                     breakpointManager_.injectBreakpointsDuringSource(
                        debugFile_, 
                        lineData.getLineNumber(), 
                        lineData.getEndLineNumber());
               
               if (!waitingForBreakpointInject_)
               {
                  continueTopLevelDebugSession();
               }
            }
            else
            {
               FileSystemItem sourceFile = FileSystemItem.createFile(debugFile_);
               DebugFilePosition position = DebugFilePosition.create(
                     lineData.getLineNumber(), 
                     lineData.getEndLineNumber(), 
                     lineData.getCharacterNumber(), 
                     lineData.getEndCharacterNumber());
               eventBus_.fireEvent(new OpenSourceFileEvent(sourceFile,
                                      (FilePosition) position.cast(),
                                      FileTypeRegistry.R,
                                      lineData.getFinished() ?
                                            NavigationMethod.DebugEnd :
                                            NavigationMethod.DebugStep));
            }
            if (lineData.getFinished())
            {
               leaveDebugMode();
            }
         }
         
         @Override
         public void onError(ServerError error)
         {
         }   
      };
      
      eventBus_.addHandler(
            BreakpointsSavedEvent.TYPE, 
            new BreakpointsSavedEvent.Handler()
      {
         @Override
         public void onBreakpointsSaved(BreakpointsSavedEvent event)
         {
            if (waitingForBreakpointInject_)
            {
               waitingForBreakpointInject_ = false;
               continueTopLevelDebugSession();
            }
         }         
      });

   }

   // Command and event handlers ----------------------------------------------

   @Handler
   void onDebugContinue()
   {
      if (debugMode_ == DebugMode.Function)
      {
         eventBus_.fireEvent(new SendToConsoleEvent("c", true, true));
      }
      else if (debugMode_ == DebugMode.TopLevel)
      {
         executeDebugStep(STEP_RUN);
      }
   }
   
   @Handler
   void onDebugStop()
   {
      if (debugMode_ == DebugMode.Function)
      {
         eventBus_.fireEvent(new SendToConsoleEvent("Q", true, true));
      }
      else if (debugMode_ == DebugMode.TopLevel)
      {
         executeDebugStep(STEP_STOP);
      }      
   }

   @Handler
   void onDebugStep()
   {
      if (debugMode_ == DebugMode.Function)
      {
         eventBus_.fireEvent(new SendToConsoleEvent("n", true, true));
      }
      else if (debugMode_ == DebugMode.TopLevel)
      {
         executeDebugStep(STEP_SINGLE);
      }
   }

   // Public methods ----------------------------------------------------------

   public void beginTopLevelDebugSession(String filename)
   {
      debugStep_ = 1;
      debugFile_ = filename;
      enterDebugMode(DebugMode.TopLevel);
      executeDebugStep(STEP_RUN);
   }
   
   public void continueTopLevelDebugSession()
   {
      executeDebugStep(debugStepMode_);
   }
   
   public void enterDebugMode(DebugMode mode)
   {
      // when entering function debug context, save the current top-level debug
      // mode so we can restore it later 
      if (mode == DebugMode.Function)
      {
         topDebugMode_ = debugMode_;
      }
      debugMode_ = mode;
      eventBus_.fireEvent(new DebugModeChangedEvent(debugMode_));
   }
   
   public void leaveDebugMode()
   {
      // when leaving function debug context, restore the top-level debug mode
      if (debugMode_ == DebugMode.Function)
      {
         eventBus_.fireEvent(new DebugModeChangedEvent(topDebugMode_));
         debugMode_ = topDebugMode_;
      }
      else
      {
         eventBus_.fireEvent(new DebugModeChangedEvent(DebugMode.Normal));
         debugMode_ = DebugMode.Normal;
         topDebugMode_ = DebugMode.Normal;
      }
   }
   
   public DebugMode getDebugMode()
   {
      return debugMode_;
   }

   public void sourceForDebugging(final String fileName)
   {
      // Find all the breakpoints in the requested file
      ArrayList<Breakpoint> breakpoints = breakpointManager_.getBreakpointsInFile(fileName);
      debugServer_.sourceForDebugging(fileName, breakpoints, 
            new ServerRequestCallback<Void>()
      {
         @Override
         public void onResponseReceived(Void v)
         {
            beginTopLevelDebugSession(fileName);
         }

         @Override
         public void onError(ServerError error)
         {
         }         
      });
   }
   
   // Private methods ---------------------------------------------------------

   private void executeDebugStep(int stepMode)
   {
      debugStepMode_ = stepMode;
      debugServer_.executeDebugSource(
            debugFile_, 
            debugStep_, 
            stepMode, 
            debugStepCallback_);
   }

   // These values are understood by the server; if you change them, you'll need
   // to update the server's understanding in SessionBreakpoints.R. 
   private static final int STEP_SINGLE = 0;
   private static final int STEP_RUN = 1;
   private static final int STEP_STOP = 2;

   private final DebuggingServerOperations debugServer_;
   private final ServerRequestCallback<TopLevelLineData> debugStepCallback_;
   private final EventBus eventBus_;
   private final BreakpointManager breakpointManager_;
   
   private DebugMode debugMode_ = DebugMode.Normal;
   private DebugMode topDebugMode_ = DebugMode.Normal;
   private int debugStep_ = 1;
   private int debugStepMode_ = STEP_SINGLE;
   private String debugFile_ = "";
   private boolean waitingForBreakpointInject_ = false;
}

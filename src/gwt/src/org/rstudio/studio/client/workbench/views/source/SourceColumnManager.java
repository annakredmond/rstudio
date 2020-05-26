/*
 * SourceColumnManager.java
 *
 * Copyright (C) 2020 by RStudio, PBC
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
package org.rstudio.studio.client.workbench.views.source;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.FocusEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Event.NativePreviewEvent;
import com.google.gwt.user.client.Event.NativePreviewHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.*;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.rstudio.core.client.*;
import org.rstudio.core.client.command.AppCommand;
import org.rstudio.core.client.command.CommandBinder;
import org.rstudio.core.client.command.Handler;
import org.rstudio.core.client.command.KeyCombination;
import org.rstudio.core.client.command.KeyboardShortcut;
import org.rstudio.core.client.command.KeySequence;
import org.rstudio.core.client.command.ShortcutManager;
import org.rstudio.core.client.dom.WindowEx;
import org.rstudio.core.client.events.*;
import org.rstudio.core.client.files.FileSystemItem;
import org.rstudio.core.client.js.JsObject;
import org.rstudio.core.client.js.JsUtil;
import org.rstudio.core.client.theme.DocTabSelectionEvent;
import org.rstudio.core.client.widget.Operation;
import org.rstudio.core.client.widget.OperationWithInput;
import org.rstudio.core.client.widget.ProgressIndicator;
import org.rstudio.core.client.widget.ProgressOperationWithInput;
import org.rstudio.studio.client.RStudioGinjector;
import org.rstudio.studio.client.application.ApplicationAction;
import org.rstudio.studio.client.application.ApplicationUtils;
import org.rstudio.studio.client.application.AriaLiveService;
import org.rstudio.studio.client.application.Desktop;
import org.rstudio.studio.client.application.events.AriaLiveStatusEvent.Severity;
import org.rstudio.studio.client.application.events.AriaLiveStatusEvent.Timing;
import org.rstudio.studio.client.application.events.CrossWindowEvent;
import org.rstudio.studio.client.application.events.EventBus;
import org.rstudio.studio.client.common.FileDialogs;
import org.rstudio.studio.client.common.FilePathUtils;
import org.rstudio.studio.client.common.GlobalDisplay;
import org.rstudio.studio.client.common.GlobalProgressDelayer;
import org.rstudio.studio.client.common.SimpleRequestCallback;
import org.rstudio.studio.client.common.dependencies.DependencyManager;
import org.rstudio.studio.client.common.filetypes.EditableFileType;
import org.rstudio.studio.client.common.filetypes.FileIcon;
import org.rstudio.studio.client.common.filetypes.FileType;
import org.rstudio.studio.client.common.filetypes.FileTypeRegistry;
import org.rstudio.studio.client.common.filetypes.ObjectExplorerFileType;
import org.rstudio.studio.client.common.filetypes.TextFileType;
import org.rstudio.studio.client.common.filetypes.events.OpenPresentationSourceFileEvent;
import org.rstudio.studio.client.common.filetypes.events.OpenPresentationSourceFileHandler;
import org.rstudio.studio.client.common.filetypes.events.OpenSourceFileEvent;
import org.rstudio.studio.client.common.filetypes.events.OpenSourceFileHandler;
import org.rstudio.studio.client.common.filetypes.model.NavigationMethods;
import org.rstudio.studio.client.common.rnw.RnwWeave;
import org.rstudio.studio.client.common.rnw.RnwWeaveRegistry;
import org.rstudio.studio.client.common.satellite.Satellite;
import org.rstudio.studio.client.common.synctex.Synctex;
import org.rstudio.studio.client.common.synctex.events.SynctexStatusChangedEvent;
import org.rstudio.studio.client.events.GetEditorContextEvent;
import org.rstudio.studio.client.events.GetEditorContextEvent.DocumentSelection;
import org.rstudio.studio.client.events.ReplaceRangesEvent;
import org.rstudio.studio.client.events.ReplaceRangesEvent.ReplacementData;
import org.rstudio.studio.client.events.SetSelectionRangesEvent;
import org.rstudio.studio.client.rmarkdown.model.RmdChosenTemplate;
import org.rstudio.studio.client.rmarkdown.model.RmdFrontMatter;
import org.rstudio.studio.client.rmarkdown.model.RmdOutputFormat;
import org.rstudio.studio.client.rmarkdown.model.RmdTemplateData;
import org.rstudio.studio.client.server.ServerError;
import org.rstudio.studio.client.server.ServerRequestCallback;
import org.rstudio.studio.client.server.VoidServerRequestCallback;
import org.rstudio.studio.client.server.model.RequestDocumentCloseEvent;
import org.rstudio.studio.client.server.model.RequestDocumentSaveEvent;
import org.rstudio.studio.client.workbench.ConsoleEditorProvider;
import org.rstudio.studio.client.workbench.MainWindowObject;
import org.rstudio.studio.client.workbench.FileMRUList;
import org.rstudio.studio.client.workbench.WorkbenchContext;
import org.rstudio.studio.client.workbench.codesearch.model.SearchPathFunctionDefinition;
import org.rstudio.studio.client.workbench.commands.Commands;
import org.rstudio.studio.client.workbench.events.ZoomPaneEvent;
import org.rstudio.studio.client.workbench.model.ClientState;
import org.rstudio.studio.client.workbench.model.RemoteFileSystemContext;
import org.rstudio.studio.client.workbench.model.Session;
import org.rstudio.studio.client.workbench.model.SessionInfo;
import org.rstudio.studio.client.workbench.model.SessionUtils;
import org.rstudio.studio.client.workbench.model.UnsavedChangesItem;
import org.rstudio.studio.client.workbench.model.UnsavedChangesTarget;
import org.rstudio.studio.client.workbench.model.helper.IntStateValue;
import org.rstudio.studio.client.workbench.prefs.model.UserPrefs;
import org.rstudio.studio.client.workbench.prefs.model.UserState;
import org.rstudio.studio.client.workbench.snippets.SnippetHelper;
import org.rstudio.studio.client.workbench.snippets.model.SnippetsChangedEvent;
import org.rstudio.studio.client.workbench.ui.unsaved.UnsavedChangesDialog;
import org.rstudio.studio.client.workbench.views.console.shell.editor.InputEditorDisplay;
import org.rstudio.studio.client.workbench.views.environment.events.DebugModeChangedEvent;
import org.rstudio.studio.client.workbench.views.files.model.DirectoryListing;
import org.rstudio.studio.client.workbench.views.output.find.events.FindInFilesEvent;
import org.rstudio.studio.client.workbench.views.source.NewShinyWebApplication.Result;
import org.rstudio.studio.client.workbench.views.source.SourceWindowManager.NavigationResult;
import org.rstudio.studio.client.workbench.views.source.editors.EditingTarget;
import org.rstudio.studio.client.workbench.views.source.editors.EditingTargetSource;
import org.rstudio.studio.client.workbench.views.source.editors.codebrowser.CodeBrowserEditingTarget;
import org.rstudio.studio.client.workbench.views.source.editors.data.DataEditingTarget;
import org.rstudio.studio.client.workbench.views.source.editors.explorer.ObjectExplorerEditingTarget;
import org.rstudio.studio.client.workbench.views.source.editors.explorer.events.OpenObjectExplorerEvent;
import org.rstudio.studio.client.workbench.views.source.editors.explorer.model.ObjectExplorerHandle;
import org.rstudio.studio.client.workbench.views.source.editors.profiler.OpenProfileEvent;
import org.rstudio.studio.client.workbench.views.source.editors.profiler.model.ProfilerContents;
import org.rstudio.studio.client.workbench.views.source.editors.text.AceEditor;
import org.rstudio.studio.client.workbench.views.source.editors.text.DocDisplay;
import org.rstudio.studio.client.workbench.views.source.editors.text.TextEditingTarget;
import org.rstudio.studio.client.workbench.views.source.editors.text.TextEditingTargetPresentationHelper;
import org.rstudio.studio.client.workbench.views.source.editors.text.TextEditingTargetRMarkdownHelper;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.AceEditorNative;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Position;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Range;
import org.rstudio.studio.client.workbench.views.source.editors.text.ace.Selection;
import org.rstudio.studio.client.workbench.views.source.editors.text.events.FileTypeChangedEvent;
import org.rstudio.studio.client.workbench.views.source.editors.text.events.FileTypeChangedHandler;
import org.rstudio.studio.client.workbench.views.source.editors.text.events.NewWorkingCopyEvent;
import org.rstudio.studio.client.workbench.views.source.editors.text.events.SourceOnSaveChangedEvent;
import org.rstudio.studio.client.workbench.views.source.editors.text.events.SourceOnSaveChangedHandler;
import org.rstudio.studio.client.workbench.views.source.editors.text.ui.NewRMarkdownDialog;
import org.rstudio.studio.client.workbench.views.source.editors.text.ui.NewRdDialog;
import org.rstudio.studio.client.workbench.views.source.events.*;
import org.rstudio.studio.client.workbench.views.source.model.ContentItem;
import org.rstudio.studio.client.workbench.views.source.model.DataItem;
import org.rstudio.studio.client.workbench.views.source.model.DocTabDragParams;
import org.rstudio.studio.client.workbench.views.source.model.RdShellResult;
import org.rstudio.studio.client.workbench.views.source.model.SourceDocument;
import org.rstudio.studio.client.workbench.views.source.model.SourceDocumentResult;
import org.rstudio.studio.client.workbench.views.source.model.SourceNavigation;
import org.rstudio.studio.client.workbench.views.source.model.SourceNavigationHistory;
import org.rstudio.studio.client.workbench.views.source.model.SourcePosition;
import org.rstudio.studio.client.workbench.views.source.model.SourceServerOperations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@Singleton
public class SourceColumnManager implements SourceExtendedTypeDetectedEvent.Handler
{

   public interface CPSEditingTargetCommand
   {
      void execute(EditingTarget editingTarget, Command continuation);
   }
   
   @Inject
   public SourceColumnManager(Source.Display display,
                              SourceServerOperations server,
                              GlobalDisplay globalDisplay,
                              EditingTargetSource editingTargetSource,
                              FileTypeRegistry fileTypeRegistry,
                              EventBus events,
                              DependencyManager dependencyManager,
                              UserPrefs userPrefs,
                              Provider<FileMRUList> pMruList)
   {
      SourceColumn column = GWT.create(SourceColumn.class);
      column.loadDisplay(Source.COLUMN_PREFIX, display, this);
      columnMap_.put(column.getName(), column);
      activeColumn_ = column;

      server_ = server;
      globalDisplay_ = globalDisplay;
      editingTargetSource_ = editingTargetSource;
      fileTypeRegistry_ = fileTypeRegistry;
      events_ = events;
      dependencyManager_ = dependencyManager;
      userPrefs_ = userPrefs;
      pMruList_ = pMruList;

      rmarkdown_ = new TextEditingTargetRMarkdownHelper();

      events_.addHandler(SourceExtendedTypeDetectedEvent.TYPE, this);
      events_.addHandler(SourceFileSavedEvent.TYPE, new SourceFileSavedHandler()
      {
         public void onSourceFileSaved(SourceFileSavedEvent event)
         {
            pMruList_.get().add(event.getPath());
         }
      });
   }

   public String add()
   {
      Source.Display display = GWT.create(SourcePane.class);
      return add(display, false);
   }

   public String add(Source.Display display)
   {
      return add(display, false);
   }
   
   public String add(Source.Display display, boolean activate)
   {
      SourceColumn column = GWT.create(SourceColumn.class);
      column.loadDisplay(Source.COLUMN_PREFIX + StringUtil.makeRandomId(12),
                        display, 
                        this);
      columnMap_.put(column.getName(), column);

      if (activate || activeColumn_ == null)
         activeColumn_ = column;

      return column.getName();
   }

   public void initialSelect(int index)
   {
      activeColumn_.initialSelect(index);
   }

   public void setActive(String name)
   {
      activeColumn_ = columnMap_.get(name);
   }

   public void setActive(EditingTarget target)
   {
      activeEditor_ = target;
      activeColumn_ = findByDocument(target.getId());
      activeColumn_.setActiveEditor(target);
   }

   public void setActiveDocId(String docId)
   {
      for (Map.Entry<String,SourceColumn> entry : columnMap_.entrySet())
      {
         SourceColumn column = entry.getValue();
         EditingTarget target = column.setActiveEditor(docId);
         if (target != null)
         {
            activeEditor_ = target;
            activeColumn_ = column;
            return;
         }
      }
      Debug.logWarning("Attempted to set unknown doc to active " + docId);
   }

   public void activateColumns(final Command afterActivation)
   {
      if (activeEditor_ == null)
      {
         if (activeColumn_ == null)
            activeColumn_ = columnMap_.get(Source.COLUMN_PREFIX);
         newDoc(FileTypeRegistry.R, new ResultCallback<EditingTarget, ServerError>()
         {
            @Override
            public void onSuccess(EditingTarget target)
            {
               setActive(target);
               doActivateSource(afterActivation);
            }
         });
      }
      else
      {
         doActivateSource(afterActivation);
      }
   }

   public SourceColumn getActive()
   {
      if (activeColumn_ != null)
         return activeColumn_;

      if (activeEditor_ != null)
      {
         activeColumn_ = findByDocument(activeEditor_.getId());
         return activeColumn_;
      }
      else
         activeColumn_ = columnMap_.get(Source.COLUMN_PREFIX);
      return activeColumn_;
   }

   public int getTabCount()
   {
      return activeColumn_.getTabCount();
   }
   
   public int getPhysicalTabIndex()
   {
      return activeColumn_.getPhysicalTabIndex();
   }

   public ArrayList<Widget> getWidgets(boolean excludeMain)
   {

      ArrayList<Widget> result = new ArrayList<Widget>();
      columnMap_.forEach((name, column) -> {
         if (!excludeMain || !StringUtil.equals(name, Source.COLUMN_PREFIX))
            result.add(column.asWidget());
      });
      return result;
   }

   public HashMap<String,SourceColumn> getMap()
   {
      return columnMap_;
   }

   public Widget getWidget(String name)
   {
      return columnMap_.get(name).asWidget();
   }

   public int getSize()
   {
      return columnMap_.size();
   }

   // !!! HOW TO HANDLE THESE?
   public EditingTarget addTab(SourceDocument doc, int mode, SourceColumn column)
   {
      if (column == null)
         column = activeColumn_;
      return column.addTab(doc, mode);
   }
   
   public EditingTarget addTab(SourceDocument doc, boolean atEnd, 
         int mode, SourceColumn column)
   {
      if (column == null)
         column = activeColumn_;
      return column.addTab(doc, atEnd, mode);
   }

   public EditingTarget addTab(SourceDocument doc, Integer position, 
         int mode, SourceColumn column)
   {
      if (column == null)
         column = activeColumn_;
      return column.addTab(doc, position, mode);
   }
   
   public EditingTarget findEditor(String docId)
   {
      for (Map.Entry<String,SourceColumn> entry : columnMap_.entrySet())
      {
         SourceColumn column = (SourceColumn)entry.getValue();
         EditingTarget target = column.getDoc(docId);
         if (target != null)
            return target;
      }
      return null;
   }

   public EditingTarget findEditorByPath(String path)
   {
      for (Map.Entry<String,SourceColumn> entry : columnMap_.entrySet())
      {
         SourceColumn column = (SourceColumn)entry.getValue();
         EditingTarget target = column.getEditorWithPath(path);
         if (target != null)
            return target;
      }
      return null;
   }

   public SourceColumn findByDocument(String docId)
   {
      for (Map.Entry<String,SourceColumn> entry : columnMap_.entrySet())
      {
         SourceColumn column = (SourceColumn)entry.getValue();
         if (column.hasDoc(docId))
            return column;
      }
      return null;
   }

   public SourceColumn findByPath(String path)
   {
      for (Map.Entry<String,SourceColumn> entry : columnMap_.entrySet())
      {
         SourceColumn column = (SourceColumn)entry.getValue();
         if (column.hasDocWithPath(path))
            return column;
      }
      return null;
   }

   public SourceColumn findByName(String name)
   {
      return columnMap_.get(name);
   }

   public SourceColumn findByPosition(int x)
   {
      for (Map.Entry<String,SourceColumn> entry : columnMap_.entrySet())
      {
         SourceColumn column = (SourceColumn)entry.getValue();

         Widget w = column.asWidget();
         int left = w.getAbsoluteLeft();
         int right = w.getAbsoluteLeft() + w.getOffsetWidth();

         if (x > left && x < right)
            return column;
      }
      return null;
   }

   public boolean isEmpty(String name)
   {
      return columnMap_.get(name).getTabCount() == 0;
   }

   public void activateCodeBrowser(
         final String codeBrowserPath,
         boolean replaceIfActive,
         final ResultCallback<CodeBrowserEditingTarget,ServerError> callback)
   {
      // first check to see if this request can be fulfilled with an existing
      // code browser tab
      EditingTarget target = selectTabWithDocPath(codeBrowserPath);
      if (target != null)
      {
         callback.onSuccess((CodeBrowserEditingTarget) target);
         return;
      }

      // then check to see if the active editor is a code browser -- if it is,
      // we'll use it as is, replacing its contents
      if (replaceIfActive &&
          activeEditor_ != null &&
          activeEditor_ instanceof CodeBrowserEditingTarget)
      {
         events_.fireEvent(new CodeBrowserCreatedEvent(activeEditor_.getId(),
               codeBrowserPath));
         callback.onSuccess((CodeBrowserEditingTarget) activeEditor_);
         return;
      }

      // create a new one
      newDoc(FileTypeRegistry.CODEBROWSER,
             new ResultCallback<EditingTarget, ServerError>()
             {
               @Override
               public void onSuccess(EditingTarget arg)
               {
                  events_.fireEvent(new CodeBrowserCreatedEvent(
                        arg.getId(), codeBrowserPath));
                  callback.onSuccess( (CodeBrowserEditingTarget)arg);
               }

               @Override
               public void onFailure(ServerError error)
               {
                  callback.onFailure(error);
               }

               @Override
               public void onCancelled()
               {
                  callback.onCancelled();
               }

            });
   }

   public void activateObjectExplorer(ObjectExplorerHandle handle)
   {
      columnMap_.forEach((name, column) -> {
         for (EditingTarget target : column.getEditors())
         {
            // bail if this isn't an object explorer filetype
            FileType fileType = target.getFileType();
            if (!(fileType instanceof ObjectExplorerFileType))
               continue;
   
            // check for identical titles
            if (handle.getTitle() == target.getTitle())
            {
               ((ObjectExplorerEditingTarget) target).update(handle);
               ensureVisible(false);
               column.selectTab(target.asWidget());
               return;
            }
         }
      });

      ensureVisible(true);
      server_.newDocument(
            FileTypeRegistry.OBJECT_EXPLORER.getTypeId(),
            null,
            (JsObject) handle.cast(),
            new SimpleRequestCallback<SourceDocument>("Show Object Explorer")
            {
               @Override
               public void onResponseReceived(SourceDocument response)
               {
                  activeColumn_.addTab(response, Source.OPEN_INTERACTIVE);
               }
            });
   }

   public void showOverflowPopout()
   {
      ensureVisible(false);
      activeColumn_.showOverflowPopout();
   }

   public void showDataItem(DataItem data)
   {
      columnMap_.forEach((name, column) -> {
         for (EditingTarget target : column.getEditors())
         {
            String path = target.getPath();
            if (path != null && path.equals(data.getURI()))
            {
               ((DataEditingTarget)target).updateData(data);
   
               ensureVisible(false);
               column.selectTab(target.asWidget());
               return;
            }
         }
      });

      ensureVisible(true);
      server_.newDocument(
            FileTypeRegistry.DATAFRAME.getTypeId(),
            null,
            (JsObject) data.cast(),
            new SimpleRequestCallback<SourceDocument>("Show Data Frame")
            {
               @Override
               public void onResponseReceived(SourceDocument response)
               {
                  activeColumn_.addTab(response, Source.OPEN_INTERACTIVE);
               }
            });
   }

   public void showUnsavedChangesDialog(
         String title,
         ArrayList<UnsavedChangesTarget> dirtyTargets,
         OperationWithInput<UnsavedChangesDialog.Result> saveOperation,
         Command onCancelled)
   {
      activeColumn_.showUnsavedChangesDialog(title, dirtyTargets, saveOperation, onCancelled);
   }
   
   @Handler
   public void onMoveTabRight()
   {
      activeColumn_.moveTab(activeColumn_.getPhysicalTabIndex(), 1);
   }

   @Handler
   public void onMoveTabLeft()
   {
      activeColumn_.moveTab(activeColumn_.getPhysicalTabIndex(), -1);
   }

   @Handler
   public void onMoveTabToFirst()
   {
      activeColumn_.moveTab(activeColumn_.getPhysicalTabIndex(),
                            activeColumn_.getPhysicalTabIndex() * -1);
   }

   @Handler
   public void onMoveTabToLast()
   {
      activeColumn_.moveTab(activeColumn_.getPhysicalTabIndex(),
              (activeColumn_.getTabCount() -
               activeColumn_.getPhysicalTabIndex()) - 1);
   }

   @Handler
   public void onSwitchToTab()
   {
      if (activeColumn_.getTabCount() == 0)
         return;
      showOverflowPopout();
   }

   @Handler
   public void onFirstTab()
   {
      if (activeColumn_.getTabCount() == 0)
         return;

      ensureVisible(false);
      if (activeColumn_.getTabCount() > 0)
         activeColumn_.setPhysicalTabIndex(0);
   }

   @Handler
   public void onPreviousTab()
   {
      switchToTab(-1, userPrefs_.wrapTabNavigation().getValue());
   }

   @Handler
   public void onNextTab()
   {
      switchToTab(1, userPrefs_.wrapTabNavigation().getValue());
   }

   @Handler
   public void onLastTab()
   {
      if (activeColumn_.getTabCount() == 0)
         return;

      activeColumn_.ensureVisible(false);
      if (activeColumn_.getTabCount() > 0)
         activeColumn_.setPhysicalTabIndex(activeColumn_.getTabCount() - 1);
   }

   @Override
   public void onSourceExtendedTypeDetected(SourceExtendedTypeDetectedEvent e)
   {
      // set the extended type of the specified source file

      EditingTarget target = findEditor(e.getDocId());
      if (target != null)
         target.adaptToExtendedFileType(e.getExtendedType());
   }

   public void nextTabWithWrap()
   {
      switchToTab(1, true);
   }

   public void prevTabWithWrap()
   {
      switchToTab(-1, true);
   }

   private void switchToTab(int delta, boolean wrap)
   {
      if (activeColumn_.getTabCount() == 0)
         return;

      activeColumn_.ensureVisible(false);

      int targetIndex = activeColumn_.getPhysicalTabIndex() + delta;
      if (targetIndex > (activeColumn_.getTabCount() - 1))
      {
         if (wrap)
            targetIndex = 0;
         else
            return;
      }
      else if (targetIndex < 0)
      {
         if (wrap)
            targetIndex = activeColumn_.getTabCount() - 1;
         else
            return;
      }
      activeColumn_.setPhysicalTabIndex(targetIndex);
   }

   private void doActivateSource(final Command afterActivation)
   {
      activeColumn_.ensureVisible(false);
      if (activeEditor_ != null)
      {
         activeEditor_.focus();
         activeEditor_.ensureCursorVisible();
      }

      if (afterActivation != null)
         afterActivation.execute();
   }

   // new doc functions

   public void newRMarkdownV1Doc()
   {
      newSourceDocWithTemplate(FileTypeRegistry.RMARKDOWN,
            "",
            "v1.Rmd",
            Position.create(3, 0));
   }

   public void newRMarkdownV2Doc()
   {
      rmarkdown_.showNewRMarkdownDialog(
         new OperationWithInput<NewRMarkdownDialog.Result>()
         {
            @Override
            public void execute(final NewRMarkdownDialog.Result result)
            {
               if (result == null)
               {
                  // No document chosen, just create an empty one
                  newSourceDocWithTemplate(FileTypeRegistry.RMARKDOWN, "", "default.Rmd");
               }
               else if (result.isNewDocument())
               {
                  NewRMarkdownDialog.RmdNewDocument doc =
                        result.getNewDocument();
                  String author = doc.getAuthor();
                  if (author.length() > 0)
                  {
                     userPrefs_.documentAuthor().setGlobalValue(author);
                     userPrefs_.writeUserPrefs();
                  }
                  newRMarkdownV2Doc(doc);
               }
               else
               {
                  newDocFromRmdTemplate(result);
               }
            }
         });
   }

   private void newDocFromRmdTemplate(final NewRMarkdownDialog.Result result)
   {
      final RmdChosenTemplate template = result.getFromTemplate();
      if (template.createDir())
      {
         rmarkdown_.createDraftFromTemplate(template);
         return;
      }

      rmarkdown_.getTemplateContent(template,
         new OperationWithInput<String>() {
            @Override
            public void execute(final String content)
            {
               if (content.length() == 0)
                  globalDisplay_.showErrorMessage("Template Content Missing",
                        "The template at " + template.getTemplatePath() +
                        " is missing.");
               newDoc(FileTypeRegistry.RMARKDOWN, content, null);
            }
      });
   }

 
   private void newRMarkdownV2Doc(
         final NewRMarkdownDialog.RmdNewDocument doc)
   {
      rmarkdown_.frontMatterToYAML((RmdFrontMatter)doc.getJSOResult().cast(),
            null,
            new CommandWithArg<String>()
      {
         @Override
         public void execute(final String yaml)
         {
            String template = "";
            // select a template appropriate to the document type we're creating
            if (doc.getTemplate().equals(RmdTemplateData.PRESENTATION_TEMPLATE))
               template = "presentation.Rmd";
            else if (doc.isShiny())
            {
               if (doc.getFormat().endsWith(
                     RmdOutputFormat.OUTPUT_PRESENTATION_SUFFIX))
                  template = "shiny_presentation.Rmd";
               else
                  template = "shiny.Rmd";
            }
            else
               template = "document.Rmd";
            newSourceDocWithTemplate(FileTypeRegistry.RMARKDOWN,
                  "",
                  template,
                  Position.create(1, 0),
                  null,
                  new TransformerCommand<String>()
                  {
                     @Override
                     public String transform(String input)
                     {
                        return RmdFrontMatter.FRONTMATTER_SEPARATOR +
                               yaml +
                               RmdFrontMatter.FRONTMATTER_SEPARATOR + "\n" +
                               input;
                     }
                  });
         }
      });
   }

   public void newSourceDocWithTemplate(final TextFileType fileType,
                                         String name,
                                         String template)
   {
      newSourceDocWithTemplate(fileType, name, template, null);
   }

   public void newSourceDocWithTemplate(final TextFileType fileType,
                                        String name,
                                        String template,
                                        final Position cursorPosition)
   {
      newSourceDocWithTemplate(fileType, name, template, cursorPosition, null);
   }

   public void newSourceDocWithTemplate(
                       final TextFileType fileType,
                       String name,
                       String template,
                       final Position cursorPosition,
                       final CommandWithArg<EditingTarget> onSuccess)
   {
      newSourceDocWithTemplate(fileType, name, template, cursorPosition, onSuccess, null);
   }

   public void startDebug()
   {
      activeColumn_.setPendingDebugSelection();
   }

   private EditingTarget selectTabWithDocPath(String path)
   {
      for (Map.Entry<String,SourceColumn> entry : columnMap_.entrySet())
      {
         SourceColumn column = (SourceColumn)entry.getValue();
         EditingTarget editor = column.getEditorWithPath(path);
         if (editor != null)
         {
            column.selectTab(editor.asWidget());
            return editor;
         }
      }
      return null;
   }

   private void newSourceDocWithTemplate(
                       final TextFileType fileType,
                       String name,
                       String template,
                       final Position cursorPosition,
                       final CommandWithArg<EditingTarget> onSuccess,
                       final TransformerCommand<String> contentTransformer)
   {
      final ProgressIndicator indicator = new GlobalProgressDelayer(
            globalDisplay_, 500, "Creating new document...").getIndicator();

      server_.getSourceTemplate(name,
                                template,
                                new ServerRequestCallback<String>() {
         @Override
         public void onResponseReceived(String templateContents)
         {
            indicator.onCompleted();

            if (contentTransformer != null)
               templateContents = contentTransformer.transform(templateContents);

            newDoc(fileType,
                  templateContents,
                  new ResultCallback<EditingTarget, ServerError> () {
               @Override
               public void onSuccess(EditingTarget target)
               {
                  if (cursorPosition != null)
                     target.setCursorPosition(cursorPosition);

                  if (onSuccess != null)
                     onSuccess.execute(target);
               }
            });
         }

         @Override
         public void onError(ServerError error)
         {
            indicator.onError(error.getUserMessage());
         }
      });
   }

   public void newDoc(EditableFileType fileType,
                      ResultCallback<EditingTarget, ServerError> callback)
   {
      activeColumn_.newDoc(fileType, callback);
   }

   public void newDoc(EditableFileType fileType,
                      final String contents,
                      final ResultCallback<EditingTarget, ServerError> resultCallback)
   {
      activeColumn_.newDoc(fileType, contents, resultCallback);
   }

   public void disownDoc(String docId)
   {
      findByDocument(docId).closeTab(docId);
   }

   public void selectTab(EditingTarget target)
   {
      findByDocument(target.getId()).selectTab(target.asWidget());
   }

   public void closeTabs(JsArrayString ids)
   {
      if (ids != null)
      {
         columnMap_.forEach((name, column) -> {
            column.closeTabs(ids);
         });
      }
   }

   public void closeTabWithPath(String path, boolean interactive)
   {
      EditingTarget target = findEditorByPath(path);
      closeTab(target, interactive);
   }

   public void closeTab(boolean interactive)
   {
      closeTab(activeEditor_, interactive);
   }
   
   public void closeTab(EditingTarget target, boolean interactive)
   {
      findByDocument(target.getId()).closeTab(target.asWidget(), interactive, null);
   }

   public void closeTab(EditingTarget target, boolean interactive, Command onClosed)
   {
      findByDocument(target.getId()).closeTab(
         target.asWidget(), interactive, onClosed);
   }

   public void closeAllTabs(boolean excludeActive, boolean excludeMain)
   {
      columnMap_.forEach((name, column) -> {
         if (!excludeMain || name != Source.COLUMN_PREFIX)
         {
            cpsExecuteForEachEditor(column.getEditors(),
                  new CPSEditingTargetCommand()
             {
                @Override
                public void execute(EditingTarget target, Command continuation)
                {
                   if (excludeActive && target == activeEditor_)
                   {
                      continuation.execute();
                      return;
                   }
                   else
                   {
                      column.closeTab(target.asWidget(), false, continuation);
                   }
                }
             });
         }
      });
   }

   public void closeColumn(String name)
   {
      SourceColumn column = findByName(name);
      if (column.getTabCount() > 0)
         return;
      if (column == activeColumn_)
         activeColumn_ = null;

      columnMap_.remove(name);
   }

   public void ensureVisible(boolean newTabPending)
   {
      activeColumn_.ensureVisible(newTabPending);
   }

   public void manageChevronVisibility()
   {
      columnMap_.forEach((name, column) -> {
         column.manageChevronVisibility();
      });
   }

   public static boolean isMainColumn(SourceColumn column)
   {
      if (StringUtil.equals(column.getName(), Source.COLUMN_PREFIX))
         return true;
      return false;
   }

   public void openFile(FileSystemItem file)
   {
      openFile(file, fileTypeRegistry_.getTextTypeForFile(file));
   }

   public void openFile(FileSystemItem file,  TextFileType fileType)
   {
      openFile(file,
               fileType,
               new CommandWithArg<EditingTarget>() {
                  @Override
                  public void execute(EditingTarget arg)
                  {

                  }
               });
   }

   public void openFile(final FileSystemItem file,
                        final TextFileType fileType,
                        final CommandWithArg<EditingTarget> executeOnSuccess)
   {
      // add this work to the queue
      openFileQueue_.add(new OpenFileEntry(file, fileType, executeOnSuccess));

      // begin queue processing if it's the only work in the queue
      if (openFileQueue_.size() == 1)
         processOpenFileQueue();
   }

   public void fireDocTabsChanged()
   {
      activeColumn_.fireDocTabsChanged();
   }
   
   private void vimSetTabIndex(int index)
   {
      int tabCount = activeColumn_.getTabCount();
      if (index >= tabCount)
         return;
      activeColumn_.setPhysicalTabIndex(index);
   }

   private void processOpenFileQueue()
   {
      // no work to do
      if (openFileQueue_.isEmpty())
         return;

      // find the first work unit
      final OpenFileEntry entry = openFileQueue_.peek();

      // define command to advance queue
      final Command processNextEntry = new Command()
            {
               @Override
               public void execute()
               {
                  openFileQueue_.remove();
                  if (!openFileQueue_.isEmpty())
                     processOpenFileQueue();

               }
            };
      openFile(
            entry.file,
            entry.fileType,
            new ResultCallback<EditingTarget, ServerError>() {
               @Override
               public void onSuccess(EditingTarget target)
               {
                  processNextEntry.execute();
                  if (entry.executeOnSuccess != null)
                     entry.executeOnSuccess.execute(target);
               }

               @Override
               public void onCancelled()
               {
                  super.onCancelled();
                  processNextEntry.execute();
               }

               @Override
               public void onFailure(ServerError error)
               {
                  String message = error.getUserMessage();

                  // see if a special message was provided
                  JSONValue errValue = error.getClientInfo();
                  if (errValue != null)
                  {
                     JSONString errMsg = errValue.isString();
                     if (errMsg != null)
                        message = errMsg.stringValue();
                  }

                  globalDisplay_.showMessage(GlobalDisplay.MSG_ERROR,
                                             "Error while opening file",
                                             message);

                  processNextEntry.execute();
               }
            });
   }

   // top-level wrapper for opening files. takes care of:
   //  - making sure the view is visible
   //  - checking whether it is already open and re-selecting its tab
   //  - prohibit opening very large files (>500KB)
   //  - confirmation of opening large files (>100KB)
   //  - finally, actually opening the file from the server
   //    via the call to the lower level openFile method
   public void openFile(final FileSystemItem file,
                        final TextFileType fileType,
                        final ResultCallback<EditingTarget, ServerError> resultCallback)
   {
      activeColumn_.ensureVisible(true);

      if (fileType.isRNotebook())
      {
         openNotebook(file, fileType, resultCallback);
         return;
      }

      if (file == null)
      {
         newDoc(fileType, resultCallback);
         return;
      }

      if (openFileAlreadyOpen(file, resultCallback))
         return;

      EditingTarget target = editingTargetSource_.getEditingTarget(fileType);

      if (file.getLength() > target.getFileSizeLimit())
      {
         if (resultCallback != null)
            resultCallback.onCancelled();
         showFileTooLargeWarning(file, target.getFileSizeLimit());
      }
      else if (file.getLength() > target.getLargeFileSize())
      {
         confirmOpenLargeFile(file, new Operation() {
            public void execute()
            {
               openFileFromServer(file, fileType, resultCallback);
            }
         }, new Operation() {
            public void execute()
            {
               // user (wisely) cancelled
               if (resultCallback != null)
                  resultCallback.onCancelled();
            }
         });
      }
      else
      {
         openFileFromServer(file, fileType, resultCallback);
      }
   }

   public void openNotebook(
         final FileSystemItem rmdFile,
         final SourceDocumentResult doc,
         final ResultCallback<EditingTarget, ServerError> resultCallback)
   {
      if (!StringUtil.isNullOrEmpty(doc.getDocPath()))
      {
         // this happens if we created the R Markdown file, or if the R Markdown
         // file on disk matched the one inside the notebook
         openFileFromServer(rmdFile,
               FileTypeRegistry.RMARKDOWN, resultCallback);
      }
      else if (!StringUtil.isNullOrEmpty(doc.getDocId()))
      {
         // this happens when we have to open an untitled buffer for the the
         // notebook (usually because the of a conflict between the Rmd on disk
         // and the one in the .nb.html file)
         server_.getSourceDocument(doc.getDocId(),
               new ServerRequestCallback<SourceDocument>()
         {
            @Override
            public void onResponseReceived(SourceDocument doc)
            {
               // create the editor
               EditingTarget target = activeColumn_.addTab(doc, Source.OPEN_INTERACTIVE);

               // show a warning bar
               if (target instanceof TextEditingTarget)
               {
                  ((TextEditingTarget) target).showWarningMessage(
                        "This notebook has the same name as an R Markdown " +
                        "file, but doesn't match it.");
               }
               resultCallback.onSuccess(target);
            }

            @Override
            public void onError(ServerError error)
            {
               globalDisplay_.showErrorMessage(
                  "Notebook Open Failed",
                  "This notebook could not be opened. " +
                  "If the error persists, try removing the " +
                  "accompanying R Markdown file. \n\n" +
                  error.getMessage());
               resultCallback.onFailure(error);
            }
         });
      }
   }

   public void beforeShow()
   {
      columnMap_.forEach((name, column) -> {
         column.onBeforeShow();
      });
   }

   public void inEditorForId(String id, OperationWithInput<EditingTarget> onEditorLocated)
   {
      EditingTarget editor = findEditor(id);
      if (editor != null)
         onEditorLocated.execute(editor);
   }

   public void inEditorForPath(String path, OperationWithInput<EditingTarget> onEditorLocated)
   {
      EditingTarget editor = findEditorByPath(path);
      if (editor != null)
         onEditorLocated.execute(editor);
   }

   private void openNotebook(final FileSystemItem rnbFile,
                             final TextFileType fileType,
                             final ResultCallback<EditingTarget, ServerError> resultCallback)
   {
      // construct path to .Rmd
      final String rnbPath = rnbFile.getPath();
      final String rmdPath = FilePathUtils.filePathSansExtension(rnbPath) + ".Rmd";
      final FileSystemItem rmdFile = FileSystemItem.createFile(rmdPath);

      // if we already have associated .Rmd file open, then just edit it
      // TODO: should we perform conflict resolution here as well?
      if (openFileAlreadyOpen(rmdFile, resultCallback))
         return;

      // ask the server to extract the .Rmd, then open that
      Command extractRmdCommand = new Command()
      {
         @Override
         public void execute()
         {
            server_.extractRmdFromNotebook(
                  rnbPath,
                  new ServerRequestCallback<SourceDocumentResult>()
                  {
                     @Override
                     public void onResponseReceived(SourceDocumentResult doc)
                     {
                        openNotebook(rmdFile, doc, resultCallback);
                     }
   
                     @Override
                     public void onError(ServerError error)
                     {
                        globalDisplay_.showErrorMessage("Notebook Open Failed",
                              "This notebook could not be opened. \n\n" +
                              error.getMessage());
                        resultCallback.onFailure(error);
                     }
                  });
         }
      };

      dependencyManager_.withRMarkdown("R Notebook", "Using R Notebooks", extractRmdCommand);
   }

   private void openFileFromServer(
         final FileSystemItem file,
         final TextFileType fileType,
         final ResultCallback<EditingTarget, ServerError> resultCallback)
   {
      final Command dismissProgress = globalDisplay_.showProgress(
                                                         "Opening file...");

      server_.openDocument(
            file.getPath(),
            fileType.getTypeId(),
            userPrefs_.defaultEncoding().getValue(),
            new ServerRequestCallback<SourceDocument>()
            {
               @Override
               public void onError(ServerError error)
               {
                  dismissProgress.execute();
                  pMruList_.get().remove(file.getPath());
                  Debug.logError(error);
                  if (resultCallback != null)
                     resultCallback.onFailure(error);
               }

               @Override
               public void onResponseReceived(SourceDocument document)
               {
                  // if we are opening for a source navigation then we
                  // need to force Rmds into source mode
                  if (openingForSourceNavigation_)
                  {
                     document.getProperties()._setBoolean(
                        TextEditingTarget.RMD_VISUAL_MODE,
                        false
                     );
                  }

                  dismissProgress.execute();
                  pMruList_.get().add(document.getPath());
                  EditingTarget target = activeColumn_.addTab(document, Source.OPEN_INTERACTIVE);
                  if (resultCallback != null)
                     resultCallback.onSuccess(target);
               }
            });
   }

   private boolean openFileAlreadyOpen(final FileSystemItem file,
                                       final ResultCallback<EditingTarget, ServerError> resultCallback)
   {
      for (Map.Entry<String,SourceColumn> entry : columnMap_.entrySet())
      {
         SourceColumn column = (SourceColumn)entry.getValue();
         // check to see if any local editors have the file open
         for (int i = 0; i < column.getEditors().size(); i++)
         {
            EditingTarget target = column.getEditors().get(i);
            String thisPath = target.getPath();
            if (thisPath != null
                && thisPath.equalsIgnoreCase(file.getPath()))
            {
               column.selectTab(target.asWidget());
               pMruList_.get().add(thisPath);
               if (resultCallback != null)
                  resultCallback.onSuccess(target);
               return true;
            }
         }
      };
      return false;
   }

   private void showFileTooLargeWarning(FileSystemItem file,
                                        long sizeLimit)
   {
      StringBuilder msg = new StringBuilder();
      msg.append("The file '" + file.getName() + "' is too ");
      msg.append("large to open in the source editor (the file is ");
      msg.append(StringUtil.formatFileSize(file.getLength()) + " and the ");
      msg.append("maximum file size is ");
      msg.append(StringUtil.formatFileSize(sizeLimit) + ")");

      globalDisplay_.showMessage(GlobalDisplay.MSG_WARNING,
                                 "Selected File Too Large",
                                 msg.toString());
   }

   private void confirmOpenLargeFile(FileSystemItem file,
                                     Operation openOperation,
                                     Operation noOperation)
   {
      StringBuilder msg = new StringBuilder();
      msg.append("The source file '" + file.getName() + "' is large (");
      msg.append(StringUtil.formatFileSize(file.getLength()) + ") ");
      msg.append("and may take some time to open. ");
      msg.append("Are you sure you want to continue opening it?");
      globalDisplay_.showYesNoMessage(GlobalDisplay.MSG_WARNING,
                                      "Confirm Open",
                                      msg.toString(),
                                      false, // Don't include cancel
                                      openOperation,
                                      noOperation,
                                      false);   // 'No' is default
   }
   /**
    * Execute the given command for each editor, using continuation-passing
    * style. When executed, the CPSEditingTargetCommand needs to execute its
    * own Command parameter to continue the iteration.
    * @param command The command to run on each EditingTarget
    */
   public void cpsExecuteForEachEditor(ArrayList<EditingTarget> editors,
                                       final CPSEditingTargetCommand command,
                                       final Command completedCommand)
   {
      SerializedCommandQueue queue = new SerializedCommandQueue();

      // Clone editors_, since the original may be mutated during iteration
      for (final EditingTarget editor : new ArrayList<EditingTarget>(editors))
      {
         queue.addCommand(new SerializedCommand()
         {
            @Override
            public void onExecute(Command continuation)
            {
               command.execute(editor, continuation);
            }
         });
      }
      
      if (completedCommand != null)
      {
         queue.addCommand(new SerializedCommand() {
   
            public void onExecute(Command continuation)
            {
               completedCommand.execute();
               continuation.execute();
            }  
         });
      }
   }
   
   public void cpsExecuteForEachEditor(ArrayList<EditingTarget> editors,
                                       final CPSEditingTargetCommand command)
   {
      cpsExecuteForEachEditor(editors, command, null);
   }

   private class OpenFileEntry
   {
      public OpenFileEntry(FileSystemItem fileIn, TextFileType fileTypeIn,
            CommandWithArg<EditingTarget> executeIn)
      {
         file = fileIn;
         fileType = fileTypeIn;
         executeOnSuccess = executeIn;
      }
      public final FileSystemItem file;
      public final TextFileType fileType;
      public final CommandWithArg<EditingTarget> executeOnSuccess;
   }

   private SourceColumn activeColumn_;
   private EditingTarget activeEditor_;

   private boolean openingForSourceNavigation_ = false;

   final Queue<OpenFileEntry> openFileQueue_ = new LinkedList<OpenFileEntry>();

   private final EventBus events_;
   private final Provider<FileMRUList> pMruList_;
   private final UserPrefs userPrefs_;
   private final GlobalDisplay globalDisplay_;
   private final TextEditingTargetRMarkdownHelper rmarkdown_;
   private final EditingTargetSource editingTargetSource_;
   private final FileTypeRegistry fileTypeRegistry_;

   private HashMap<String,SourceColumn> columnMap_ = new HashMap<String,SourceColumn>();

   private final SourceServerOperations server_;
   private DependencyManager dependencyManager_;
}
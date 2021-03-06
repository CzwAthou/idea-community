/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.ex;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * @author irengrig
 * author: lesya
 */
public class LineStatusTracker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.ex.LineStatusTracker");
  private final Object myLock = new Object();
  // true -> have contents
  private BaseLoadState myBaseLoaded;

  private final Document myDocument;
  private final Document myUpToDateDocument;

  private List<Range> myRanges;

  private final Project myProject;

  private MyDocumentListener myDocumentListener;

  private boolean myBulkUpdate;
  private final Application myApplication;

  private LineStatusTracker(final Document document, final Document upToDateDocument, final Project project) {
    myApplication = ApplicationManager.getApplication();
    myDocument = document;
    myUpToDateDocument = upToDateDocument;
    myUpToDateDocument.putUserData(UndoManager.DONT_RECORD_UNDO, Boolean.TRUE);
    myProject = project;
    myBaseLoaded = BaseLoadState.LOADING;
    myRanges = new ArrayList<Range>();
  }

  public void initialize(@NotNull final String upToDateContent) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    synchronized (myLock) {
      LOG.assertTrue(BaseLoadState.LOADING == myBaseLoaded);
      try {
        myUpToDateDocument.setReadOnly(false);
        myUpToDateDocument.replaceString(0, myUpToDateDocument.getTextLength(), upToDateContent);
        myUpToDateDocument.setReadOnly(true);
        reinstallRanges();

        if (myDocumentListener == null) {
          myDocumentListener = new MyDocumentListener();
          myDocument.addDocumentListener(myDocumentListener);
        }
      }
      finally {
        myBaseLoaded = BaseLoadState.LOADED;
      }
    }
  }

  private void reinstallRanges() {
    myApplication.assertReadAccessAllowed();

    synchronized (myLock) {
      removeHighlightersFromMarkupModel();
      myRanges = new RangesBuilder(myDocument, myUpToDateDocument).getRanges();
      for (final Range range : myRanges) {
        range.setHighlighter(createHighlighter(range));
      }
    }
  }

  @SuppressWarnings({"AutoBoxing"})
  private RangeHighlighter createHighlighter(final Range range) {
    int first =
      range.getOffset1() >= myDocument.getLineCount() ? myDocument.getTextLength() : myDocument.getLineStartOffset(range.getOffset1());

    int second =
      range.getOffset2() >= myDocument.getLineCount() ? myDocument.getTextLength() : myDocument.getLineStartOffset(range.getOffset2());

    final RangeHighlighter highlighter = myDocument.getMarkupModel(myProject)
      .addRangeHighlighter(first, second, HighlighterLayer.FIRST - 1, null, HighlighterTargetArea.LINES_IN_RANGE);
    final TextAttributes attr = LineStatusTrackerDrawing.getAttributesFor(range);
    highlighter.setErrorStripeMarkColor(attr.getErrorStripeColor());
    highlighter.setThinErrorStripeMark(true);
    highlighter.setGreedyToLeft(true);
    highlighter.setGreedyToRight(true);
    highlighter.setLineMarkerRenderer(LineStatusTrackerDrawing.createRenderer(range, this));
    highlighter.setEditorFilter(MarkupEditorFilterFactory.createIsNotDiffFilter());
    final int line1 = myDocument.getLineNumber(first);
    final int line2 = myDocument.getLineNumber(second);
    final String tooltip;
    if (line1 == line2) {
      tooltip = VcsBundle.message("tooltip.text.line.changed", line1);
    }
    else {
      tooltip = VcsBundle.message("tooltip.text.lines.changed", line1, line2);
    }

    highlighter.setErrorStripeTooltip(tooltip);
    return highlighter;
  }

  public void release() {
    synchronized (myLock) {
      if (myDocumentListener != null) {
        myDocument.removeDocumentListener(myDocumentListener);
      }
      removeHighlightersFromMarkupModel();
      myRanges.clear();
    }
  }

  public Document getDocument() {
    return myDocument;
  }

  public VirtualFile getVirtualFile() {
    return FileDocumentManager.getInstance().getFile(getDocument());
  }

  public List<Range> getRanges() {
    myApplication.assertReadAccessAllowed();

    synchronized (myLock) {
      return myRanges;
    }
  }

  public Document getUpToDateDocument() {
    myApplication.assertIsDispatchThread();
    return myUpToDateDocument;
  }

  public void startBulkUpdate() {
    synchronized (myLock) {
      myBulkUpdate = true;
      removeHighlightersFromMarkupModel();
      myRanges.clear();
    }
  }

  private void removeHighlightersFromMarkupModel() {
    final MarkupModel markupModel = myDocument.getMarkupModel(myProject);
    synchronized (myLock) {
      for (Range range : myRanges) {
        markupModel.removeHighlighter(range.getHighlighter());
      }
    }
  }

  public void finishBulkUpdate() {
    synchronized (myLock) {
      myBulkUpdate = false;
      reinstallRanges();
    }
  }

  /**
   * @return true if was cleared and base revision contents load should be started
   * false -> load was already started; after contents is loaded,
   */
  public boolean resetForBaseRevisionLoad() {
    myApplication.assertReadAccessAllowed();

    synchronized (myLock) {
      if (BaseLoadState.LOADING == myBaseLoaded) return false;
      myUpToDateDocument.setReadOnly(false);
      myUpToDateDocument.setText("");
      myUpToDateDocument.setReadOnly(true);
      removeHighlightersFromMarkupModel();
      myRanges.clear();
      myBaseLoaded = BaseLoadState.LOADING;
      return true;
    }
  }

  private class MyDocumentListener extends DocumentAdapter {
    private int myFirstChangedLine;
    private int myUpToDateFirstLine;
    private int myUpToDateLastLine;
    private int myLastChangedLine;
    private int myLinesBeforeChange;

    public void beforeDocumentChange(DocumentEvent e) {
      myApplication.assertWriteAccessAllowed();

      synchronized (myLock) {
        if (myBulkUpdate || (BaseLoadState.LOADED != myBaseLoaded)) return;
        try {
          myFirstChangedLine = myDocument.getLineNumber(e.getOffset());
          myLastChangedLine = myDocument.getLineNumber(e.getOffset() + e.getOldLength());
          if (StringUtil.endsWithChar(e.getOldFragment(), '\n')) myLastChangedLine++;

          myLinesBeforeChange = myDocument.getLineNumber(e.getOffset() + e.getOldLength()) - myDocument.getLineNumber(e.getOffset());

          Range firstChangedRange = getLastRangeBeforeLine(myFirstChangedLine);

          if (firstChangedRange == null) {
            myUpToDateFirstLine = myFirstChangedLine;
          }
          else if (firstChangedRange.containsLine(myFirstChangedLine)) {
            myFirstChangedLine = firstChangedRange.getOffset1();
            myUpToDateFirstLine = firstChangedRange.getUOffset1();
          }
          else {
            myUpToDateFirstLine = firstChangedRange.getUOffset2() + (myFirstChangedLine - firstChangedRange.getOffset2());
          }

          Range myLastChangedRange = getLastRangeBeforeLine(myLastChangedLine);

          if (myLastChangedRange == null) {
            myUpToDateLastLine = myLastChangedLine;
          }
          else if (myLastChangedRange.containsLine(myLastChangedLine)) {
            myUpToDateLastLine = myLastChangedRange.getUOffset2();
            myLastChangedLine = myLastChangedRange.getOffset2();
          }
          else {
            myUpToDateLastLine = myLastChangedRange.getUOffset2() + (myLastChangedLine - myLastChangedRange.getOffset2());
          }
        } catch (ProcessCanceledException ignore) {
        }
      }
    }

    @Nullable
    private Range getLastRangeBeforeLine(int line) {
      Range result = null;
      for (Range range : myRanges) {
        if (range.isMoreThen(line)) return result;
        result = range;
      }
      return result;
    }

    public void documentChanged(DocumentEvent e) {
      myApplication.assertWriteAccessAllowed();

      synchronized (myLock) {
        if (myBulkUpdate || (BaseLoadState.LOADED != myBaseLoaded)) return;
        try {

          int line = myDocument.getLineNumber(e.getOffset() + e.getNewLength());
          int linesAfterChange = line - myDocument.getLineNumber(e.getOffset());
          int linesShift = linesAfterChange - myLinesBeforeChange;

          List<Range> rangesAfterChange = getRangesAfter(myRanges, myLastChangedLine);
          List<Range> rangesBeforeChange = getRangesBefore(myRanges, myFirstChangedLine);

          List<Range> changedRanges = getChangedRanges(myFirstChangedLine, myLastChangedLine);

          int newSize = rangesBeforeChange.size() + changedRanges.size() + rangesAfterChange.size();
          if (myRanges.size() != newSize) {
            LOG.info("Ranges: " + myRanges + "; first changed line: " + myFirstChangedLine + "; last changed line: " + myLastChangedLine);
            LOG.assertTrue(false);
          }


          myLastChangedLine += linesShift;


          List<Range> newChangedRanges = getNewChangedRanges();

          shiftRanges(rangesAfterChange, linesShift);

          if (!changedRanges.equals(newChangedRanges)) {
            replaceRanges(changedRanges, newChangedRanges);

            myRanges = new ArrayList<Range>();

            myRanges.addAll(rangesBeforeChange);
            myRanges.addAll(newChangedRanges);
            myRanges.addAll(rangesAfterChange);

            myRanges = mergeRanges(myRanges);

            for (Range range : myRanges) {
              if (!range.hasHighlighter()) range.setHighlighter(createHighlighter(range));
            }
          }
        } catch (ProcessCanceledException ignore) {
        }
      }
    }

    private List<Range> getNewChangedRanges() {
      List<String> lines = new DocumentWrapper(myDocument).getLines(myFirstChangedLine, myLastChangedLine);
      List<String> uLines = new DocumentWrapper(myUpToDateDocument)
        .getLines(myUpToDateFirstLine, myUpToDateLastLine);
      return new RangesBuilder(lines, uLines, myFirstChangedLine, myUpToDateFirstLine).getRanges();
    }

    private List<Range> mergeRanges(List<Range> ranges) {
      final MarkupModel markupModel = myDocument.getMarkupModel(myProject);

      ArrayList<Range> result = new ArrayList<Range>();
      Iterator<Range> iterator = ranges.iterator();
      if (!iterator.hasNext()) return result;
      Range prev = iterator.next();
      while (iterator.hasNext()) {
        Range range = iterator.next();
        if (prev.canBeMergedWith(range)) {
          markupModel.removeHighlighter(range.getHighlighter());
          markupModel.removeHighlighter(prev.getHighlighter());
          prev = prev.mergeWith(range, LineStatusTracker.this);
        }
        else {
          result.add(prev);
          prev = range;
        }
      }
      result.add(prev);
      return result;
    }

    private void replaceRanges(List<Range> rangesInChange, List<Range> newRangesInChange) {
      final MarkupModel markupModel = myDocument.getMarkupModel(myProject);

      for (Range range : rangesInChange) {
        markupModel.removeHighlighter(range.getHighlighter());
        range.setHighlighter(null);
      }
      for (Range range : newRangesInChange) {
        range.setHighlighter(createHighlighter(range));
      }
    }

    private void shiftRanges(List<Range> rangesAfterChange, int shift) {
      for (final Range aRangesAfterChange : rangesAfterChange) {
        aRangesAfterChange.shift(shift);
      }
    }

  }

  private List<Range> getChangedRanges(int from, int to) {
    return getChangedRanges(myRanges, from, to);
  }

  public static List<Range> getChangedRanges(List<Range> ranges, int from, int to) {
    ArrayList<Range> result = new ArrayList<Range>();
    for (Range range : ranges) {
      if (range.getOffset1() <= to && range.getOffset2() >= from) result.add(range);
//      if (range.getOffset1() > to) break;
    }
    return result;
  }

  @Nullable
  Range getNextRange(final Range range) {
    synchronized (myLock) {
      final int index = myRanges.indexOf(range);
      if (index == myRanges.size() - 1) return null;
      return myRanges.get(index + 1);
    }
  }

  @Nullable
  Range getPrevRange(final Range range) {
    synchronized (myLock) {
      final int index = myRanges.indexOf(range);
      if (index <= 0) return null;
      return myRanges.get(index - 1);
    }
  }

  @Nullable
  public Range getNextRange(final int line) {
    synchronized (myLock) {
      final Range currentRange = getRangeForLine(line);
      if (currentRange != null) {
        return getNextRange(currentRange);
      }

      for (final Range range : myRanges) {
        if (line > range.getOffset1() || line > range.getOffset2()) {
          continue;
        }
        return range;
      }
      return null;
    }
  }

  @Nullable
  public Range getPrevRange(final int line) {
    synchronized (myLock) {
      final Range currentRange = getRangeForLine(line);
      if (currentRange != null) {
        return getPrevRange(currentRange);
      }

      for (ListIterator<Range> iterator = myRanges.listIterator(myRanges.size()); iterator.hasPrevious();) {
        final Range range = iterator.previous();
        if (range.getOffset1() > line) {
          continue;
        }
        return range;
      }
      return null;
    }
  }

  public static List<Range> getRangesBefore(List<Range> ranges, int line) {
    ArrayList<Range> result = new ArrayList<Range>();

    for (Range range : ranges) {
      if (range.getOffset2() < line) result.add(range);
      //if (range.getOffset2() > line) break;
    }
    return result;
  }

  public static List<Range> getRangesAfter(List<Range> ranges, int line) {
    ArrayList<Range> result = new ArrayList<Range>();
    for (Range range : ranges) {
      if (range.getOffset1() > line) result.add(range);
    }
    return result;
  }

  @Nullable
  public Range getRangeForLine(final int line) {
    synchronized (myLock) {
      for (final Range range : myRanges) {
        if (range.getType() == Range.DELETED && line == range.getOffset1()) {
          return range;
        }
        else if (line >= range.getOffset1() && line < range.getOffset2()) {
          return range;
        }
      }
      return null;
    }
  }

  public void rollbackChanges(final Range range) {
    myApplication.assertWriteAccessAllowed();

    synchronized (myLock) {
      TextRange currentTextRange = getCurrentTextRange(range);

      if (range.getType() == Range.INSERTED) {
        myDocument
          .replaceString(currentTextRange.getStartOffset(), Math.min(currentTextRange.getEndOffset() + 1, myDocument.getTextLength()), "");
      }
      else if (range.getType() == Range.DELETED) {
        String upToDateContent = getUpToDateContent(range);
        myDocument.insertString(currentTextRange.getStartOffset(), upToDateContent);
      }
      else {

        String upToDateContent = getUpToDateContent(range);
        myDocument.replaceString(currentTextRange.getStartOffset(), Math.min(currentTextRange.getEndOffset() + 1, myDocument.getTextLength()),
                                 upToDateContent);
      }
    }
  }

  public String getUpToDateContent(Range range) {
    synchronized (myLock) {
      TextRange textRange = getUpToDateRange(range);
      final int startOffset = textRange.getStartOffset();
      final int endOffset = Math.min(textRange.getEndOffset() + 1, myUpToDateDocument.getTextLength());
      return myUpToDateDocument.getCharsSequence().subSequence(startOffset, endOffset).toString();
    }
  }

  Project getProject() {
    return myProject;
  }

  TextRange getCurrentTextRange(Range range) {
    return getRange(range.getType(), range.getOffset1(), range.getOffset2(), Range.DELETED, myDocument, false);
  }

  TextRange getUpToDateRange(Range range) {
    return getRange(range.getType(), range.getUOffset1(), range.getUOffset2(), Range.INSERTED, myUpToDateDocument, false);
  }

  // a hack
  TextRange getUpToDateRangeWithEndSymbol(Range range) {
    return getRange(range.getType(), range.getUOffset1(), range.getUOffset2(), Range.INSERTED, myUpToDateDocument, true);
  }

  private static TextRange getRange(byte rangeType, int offset1, int offset2, byte emptyRangeCondition, Document document,
                                    final boolean keepEnd) {
    if (rangeType == emptyRangeCondition) {
      int lineStartOffset;
      if (offset1 == 0) {
        lineStartOffset = 0;
      }
      else {
        lineStartOffset = document.getLineEndOffset(offset1 - 1);
      }
      //if (lineStartOffset > 0) lineStartOffset--;
      return new TextRange(lineStartOffset, lineStartOffset);

    }
    else {
      int startOffset = document.getLineStartOffset(offset1);
      int endOffset = document.getLineEndOffset(offset2 - 1);
      if (startOffset > 0) {
        -- startOffset;
        if (! keepEnd) {
          -- endOffset;
        }
      }
      return new TextRange(startOffset, endOffset);
    }
  }

  public static LineStatusTracker createOn(final Document doc, final Project project) {
    final Document document = new DocumentImpl(true);
    return new LineStatusTracker(doc, document, project);
  }

  public BaseLoadState getBaseLoaded() {
    return myBaseLoaded;
  }

  public void baseRevisionLoadFailed() {
    synchronized (myLock) {
      myBaseLoaded = BaseLoadState.FAILED;
    }
  }

  public static enum BaseLoadState {
    LOADING,
    FAILED,
    LOADED
  }
}

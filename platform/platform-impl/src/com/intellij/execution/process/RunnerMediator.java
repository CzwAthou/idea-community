/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.execution.process;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;

/**
 * @author traff
 */
public class RunnerMediator {
  public static final Logger LOG = Logger.getInstance("#com.intellij.execution.process.RunnerMediator");

  private static final char IAC = (char)5;
  private static final char BRK = (char)3;
  private static final String STANDARD_RUNNERW = "runnerw.exe";

  /**
   * Creates default runner mediator
   * @return
   */
  public static RunnerMediator getInstance() {
    return new RunnerMediator();
  }

  /**
   * Sends sequence of two chars(codes 5 and 3) to a process output stream
   */
  private static void sendCtrlBreakThroughStream(Process process) {
    OutputStream os = process.getOutputStream();
    PrintWriter pw = new PrintWriter(os);
    try {
      pw.print(IAC);
      pw.print(BRK);
      pw.flush();
    }
    finally {
      pw.close();
    }
  }

  /**
   * In case of windows creates process with runner mediator(runnerw.exe) injected to command line string, which adds a capability
   * to terminate process tree gracefully with ctrl+break.
   *
   * Returns appropriate process handle, which in case of Unix is able to terminate whole process tree by sending sig_kill
   *
   */
  public ProcessHandler createProcess(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    if (isWindows()) {
      injectRunnerCommand(commandLine);
    }

    Process process = commandLine.createProcess();

    return createProcessHandler(process, commandLine);
  }

  /**
   * Creates process handler for process able to be terminated with method RunnerMediator.destroyProcess.
   * You can override this method to customize process handler creation.
   * @return
   */
  protected ProcessHandler createProcessHandler(@NotNull Process process, @NotNull GeneralCommandLine commandLine) {
    return new CustomDestroyProcessHandler(process, commandLine);
  }

  @Nullable
  private String getRunnerPath() {
    if (isWindows()) {
      final String path = System.getenv("IDEA_RUNNERW");
      if (path != null && new File(path).exists()) {
        return path;
      }
      if (new File(STANDARD_RUNNERW).exists()) {
        return STANDARD_RUNNERW;
      }
      return null;
    }
    else {
      throw new IllegalStateException("There is no need of runner under unix based OS");
    }
  }

  private void injectRunnerCommand(@NotNull GeneralCommandLine commandLine) {
    final String path = getRunnerPath();
    if (path != null) {
      commandLine.getParametersList().addAt(0, commandLine.getExePath());
      commandLine.setExePath(path);
    }
  }

  public static boolean isUnix() {
    return SystemInfo.isLinux || SystemInfo.isMac;
  }

  public static boolean isWindows() {
    if (File.separatorChar == '\\') {
      return true;
    }
    return false;
  }

  /**
   * Destroys process tree: in case of windows via imitating ctrl+break, in case of unix via sending sig_kill to every process in tree.
   * @param process to kill with all subprocesses
   */
  public static boolean destroyProcess(Process process) {
    try {
      if (isWindows()) {
        sendCtrlBreakThroughStream(process);
        return true;
      }
      else if (isUnix()) {
        return UnixProcessManager.sendSigKillToProcessTree(process);
      }
      else {
        return false;
      }
    }
    catch (Exception e) {
      LOG.error("Couldn't terminate the process", e);
      return false;
    }
  }

  /**
   *
   */
  public static class CustomDestroyProcessHandler extends ColoredProcessHandler {


    public CustomDestroyProcessHandler(@NotNull Process process,
                                       @NotNull GeneralCommandLine commandLine) {
      super(process, commandLine.getCommandLineString());
    }

    @Override
    protected void destroyProcessImpl() {
      if (!RunnerMediator.destroyProcess(getProcess())) {
        super.destroyProcessImpl();
      }
    }
  }
}

package com.intellij.tasks.lighthouse;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EnumSet;

/**
 * @author Dennis.Ushakov
 */
public class LighthouseRepositoryType extends BaseRepositoryType<LighthouseRepository> {
  static final Icon ICON = IconLoader.getIcon("/icons/lighthouse.gif");

  @NotNull
  @Override
  public String getName() {
    return "Lighthouse";
  }

  @Override
  public Icon getIcon() {
    return ICON;
  }

  @NotNull
  @Override
  public TaskRepository createRepository() {
    return new LighthouseRepository(this);
  }

  @Override
  public Class<LighthouseRepository> getRepositoryClass() {
    return LighthouseRepository.class;
  }

  @Override
  public EnumSet<TaskState> getPossibleTaskStates() {
    return EnumSet.of(TaskState.SUBMITTED, TaskState.OPEN, TaskState.RESOLVED, TaskState.OTHER);
  }

  @NotNull
  @Override
  public TaskRepositoryEditor createEditor(LighthouseRepository repository,
                                           Project project,
                                           Consumer<LighthouseRepository> changeListener) {
    return new LighthouseRepositoryEditor(project, repository, changeListener);
  }
}

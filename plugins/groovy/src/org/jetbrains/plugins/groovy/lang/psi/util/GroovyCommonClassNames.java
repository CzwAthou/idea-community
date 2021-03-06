/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.util;

/**
 * @author Maxim.Medvedev
 */
public final class GroovyCommonClassNames {

  public static final String GROOVY_OBJECT_SUPPORT = "groovy.lang.GroovyObjectSupport";
  public static final String GROOVY_LANG_CLOSURE = "groovy.lang.Closure";
  public static final String DEFAULT_BASE_CLASS_NAME = "groovy.lang.GroovyObject";
  public static final String GROOVY_LANG_GSTRING = "groovy.lang.GString";
  public static final String DEFAULT_GROOVY_METHODS = "org.codehaus.groovy.runtime.DefaultGroovyMethods";
  public static final String GROOVY_LANG_SCRIPT = "groovy.lang.Script";

  private GroovyCommonClassNames() {
  }
}

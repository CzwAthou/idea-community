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
package com.intellij.cvsSupport2.config;

import com.intellij.cvsSupport2.connections.ssh.SshTypesToUse;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * author: lesya
 */
public class SshSettings implements JDOMExternalizable, Cloneable {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.connections.ssh.ui.SshSettings");

  public boolean USE_PPK = false;
  public String PATH_TO_PPK = "";
  public String PORT = "";

  public SshTypesToUse SSH_TYPE = SshTypesToUse.ALLOW_BOTH;
  @NonNls private static final String SSH_TYPE_ATTRIBUTE = "SSH_TYPE";


  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    String sshType = element.getAttributeValue(SSH_TYPE_ATTRIBUTE);
    SSH_TYPE = SshTypesToUse.fromName(sshType);
  }
  
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    element.setAttribute(SSH_TYPE_ATTRIBUTE, SSH_TYPE.toString());
  }

  public SshSettings clone() {
    try {
      return (SshSettings)super.clone();
    }
    catch (CloneNotSupportedException e) {
      LOG.error(e);
      return new SshSettings();
    }
  }

}

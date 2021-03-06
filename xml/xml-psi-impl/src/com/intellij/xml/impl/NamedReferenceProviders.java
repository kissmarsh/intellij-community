// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.xml.XmlNamedReferenceHost;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import com.intellij.xml.XmlNamedReferenceProviderBean;
import gnu.trove.THashMap;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Internal
@Service
public final class NamedReferenceProviders {

  private static final ExtensionPointName<XmlNamedReferenceProviderBean> EP_NAME = ExtensionPointName.create(
    "com.intellij.xml.namedReferenceProvider"
  );

  @NotNull
  static NamedReferenceProviders getInstance() {
    return ServiceManager.getService(NamedReferenceProviders.class);
  }


  // There are 2 XmlNamedReferenceHost inheritors currently.
  private final Map<Class<?>, ByHostClass> myByHostClass = new ConcurrentHashMap<>(2);

  public NamedReferenceProviders() {
    EP_NAME.addExtensionPointListener(() -> myByHostClass.clear(), ApplicationManager.getApplication());
  }


  @NotNull
  Collection<XmlNamedReferenceProviderBean> getNamedReferenceProviderBeans(@NotNull XmlNamedReferenceHost element) {
    final String hostName = element.getHostName();
    if (hostName == null) {
      return Collections.emptyList();
    }
    return byHostClass(element).byHostName(hostName);
  }

  @NotNull
  private ByHostClass byHostClass(@NotNull XmlNamedReferenceHost element) {
    return myByHostClass.computeIfAbsent(element.getClass(), NamedReferenceProviders::byHostClassInner);
  }

  @NotNull
  private static ByHostClass byHostClassInner(@NotNull Class<?> hostClass) {
    List<XmlNamedReferenceProviderBean> result = new SmartList<>();
    for (XmlNamedReferenceProviderBean bean : EP_NAME.getExtensionList()) {
      if (bean.getHostElementClass().isAssignableFrom(hostClass)) {
        result.add(bean);
      }
    }
    return new ByHostClass(result);
  }

  private static final class ByHostClass {

    private final Map<String, List<XmlNamedReferenceProviderBean>> myCaseSensitiveMap;
    private final Map<String, List<XmlNamedReferenceProviderBean>> myCaseInsensitiveMap;

    ByHostClass(@NotNull List<XmlNamedReferenceProviderBean> beans) {
      THashMap<String, List<XmlNamedReferenceProviderBean>> caseSensitiveMap = new THashMap<>();
      THashMap<String, List<XmlNamedReferenceProviderBean>> caseInsensitiveMap = new THashMap<>(
        CaseInsensitiveStringHashingStrategy.INSTANCE
      );

      for (XmlNamedReferenceProviderBean bean : beans) {
        final Map<String, List<XmlNamedReferenceProviderBean>> map = bean.caseSensitive ? caseSensitiveMap
                                                                                        : caseInsensitiveMap;
        for (String hostName : bean.getHostNames()) {
          map.computeIfAbsent(hostName, __ -> new SmartList<>()).add(bean);
        }
      }

      caseSensitiveMap.compact();
      caseInsensitiveMap.compact();

      myCaseSensitiveMap = caseSensitiveMap;
      myCaseInsensitiveMap = caseInsensitiveMap;
    }

    @NotNull
    Collection<XmlNamedReferenceProviderBean> byHostName(@NotNull String hostName) {
      return ContainerUtil.concat(
        ObjectUtils.notNull(myCaseSensitiveMap.get(hostName), Collections.emptyList()),
        ObjectUtils.notNull(myCaseInsensitiveMap.get(hostName), Collections.emptyList())
      );
    }
  }
}

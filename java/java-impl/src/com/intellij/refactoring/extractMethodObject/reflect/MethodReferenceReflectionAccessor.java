// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.psi.*;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import com.intellij.refactoring.extractMethodObject.PsiReflectionAccessUtil;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public class MethodReferenceReflectionAccessor
  extends ReferenceReflectionAccessorBase<MethodReferenceReflectionAccessor.MethodReferenceDescriptor> {
  public MethodReferenceReflectionAccessor(@NotNull PsiClass psiClass,
                                           @NotNull PsiElementFactory elementFactory) {
    super(psiClass, elementFactory);
  }

  @Nullable
  @Override
  protected MethodReferenceDescriptor createDescriptor(@NotNull PsiReferenceExpression expression) {
    if (expression instanceof PsiMethodReferenceExpression) {
      PsiElement resolvedElement = expression.resolve();
      if (resolvedElement instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)resolvedElement;
        if (!PsiReflectionAccessUtil.isAccessibleMember(method)) {
          return new MethodReferenceDescriptor(method, (PsiMethodReferenceExpression)expression);
        }
      }
    }

    return null;
  }

  @Override
  protected void grantAccess(@NotNull MethodReferenceDescriptor descriptor, int order) {
    MethodReflectionAccessor methodAccessor = new MethodReflectionAccessor(getOuterClass(), getElementFactory());
    PsiLambdaExpression lambda = LambdaRefactoringUtil.convertMethodReferenceToLambda(descriptor.expression, false, true);
    if (lambda != null) {
      methodAccessor.accessThroughReflection(lambda);
    }
  }

  public static class MethodReferenceDescriptor implements ItemToReplaceDescriptor {
    public final PsiMethod method;
    public final PsiMethodReferenceExpression expression;

    public MethodReferenceDescriptor(@NotNull PsiMethod method, @NotNull PsiMethodReferenceExpression expression) {
      this.method = method;
      this.expression = expression;
    }
  }
}

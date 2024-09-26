package ee.carlrobert.codegpt.codedown.filters;

import com.intellij.psi.PsiElement;

public interface MethodFilter {
    boolean allow(PsiElement psiElement);
}

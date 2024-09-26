package ee.carlrobert.codegpt.actions.editor;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.concurrency.NonUrgentExecutor;
import ee.carlrobert.codegpt.CodeGPTKeys;
import ee.carlrobert.codegpt.ReferencedFile;
import ee.carlrobert.codegpt.codedown.model.CallStack;
import ee.carlrobert.codegpt.codedown.uast.UastSequenceGenerator;
import ee.carlrobert.codegpt.conversations.message.Message;
import ee.carlrobert.codegpt.toolwindow.chat.ChatToolWindowContentManager;
import ee.carlrobert.codegpt.toolwindow.chat.ChatToolWindowTabPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

public class CodeDownAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(ChatToolWindowTabPanel.class);

    public static String[] UAST_Language = new String[]{"JAVA", "kotlin",};

    private static final String TARGET_METHOD_PLACE_HOLD = "{{targetClass_method}}";
    private static final String TARGET_METHOD_BODY = "{{targetClass_method_body}}";

    private static final String PROMPT = """
            你是一位长期在一线编码的高级程序员，你擅长java、java生态的所有技术栈（如spring、springboot、unit、log4j，apache、google等等框架和工具）以及第三方组件，如mysql、rocketMq、对象存储等等。现在你需要根据要单测的目标代码以及其关联的代码调用信息，写出目标代码可能出现的单测场景。
            
            注意事项：
            1. 目标代码会省略调导包、spring bean注入等繁杂的信息，Service、DAO、DaoService等都会是spring bean实例。
            2. 在给定的代码中可能出现RMB、WEMQ等工具的调用，它是类似rocketMq的消息中间件。FPS是对象存储工具（主要用于存储和下载文件）。
            
            <example>
            用户输入：
            public class Circle {
            
                private double radius;
            
                public Circle(double radius) {
                   if(radius <= 0){
                      throw new Exception("radius需要大于0")
                    }
            
                    this.radius = radius;
                }
            
                // 计算圆的周长
                public double getPerimeter() {
                    return 2 * Math.PI * radius;
                }
            
                // 获得半径
                public double getRadius() {
                    return radius;
                }
            }
            
            输出结果：
            
            1.半径为正值，应该正确计算周长
            2.半径为零或负值，构造函数应抛出异常
            3.半径为极大值，应该正确计算周长并且不会溢出
            4.半径为极小正值，应该正确计算周长
            5.半径为浮点数，应该正确计算周长
            
            </example>
            
            帮我分析以下代码，给我合理的%1$s方法的单测场景：
            
            %2$s
            """.formatted(TARGET_METHOD_PLACE_HOLD, TARGET_METHOD_BODY);

    CodeDownAction(@Nullable @NlsActions.ActionText String text,
                   @Nullable @NlsActions.ActionDescription String description) {
        super(text, description, null);
    }

    public void update(@NotNull AnActionEvent event) {
        super.update(event);

        Presentation presentation = event.getPresentation();

        @Nullable
        PsiElement psiElement = event.getData(CommonDataKeys.PSI_FILE);
        presentation.setEnabled(isEnabled(psiElement));

    }

    private boolean isEnabled(PsiElement psiElement) {
        return psiElement != null
                && Arrays.stream(UAST_Language).anyMatch(p -> p.equals(psiElement.getLanguage().getID()));
    }

    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null)
            return;

        PsiElement psiElement = event.getData(CommonDataKeys.PSI_ELEMENT);
        PsiFile psiFile = event.getData(CommonDataKeys.PSI_FILE);

        if (psiElement == null) {
            final Caret caret = event.getData(CommonDataKeys.CARET);

            if (psiFile != null && caret != null) {
                Class<? extends PsiElement> method = PsiMethod.class;
                // if ("JAVA".equals(psiFile.getLanguage().getID())) {
                // method = PsiMethod.class;
                // }

                psiElement = PsiTreeUtil.findElementOfClassAtOffset(psiFile, caret.getOffset(), method, false);
            }
        }

        if (psiElement != null) {
            LOG.info(String.format("已选择method: %s", psiElement.getText()));

            final BackgroundableProcessIndicator progressIndicator =
                    new BackgroundableProcessIndicator(project, "Drill Down Code Extraction", "Stop", "Stop", true);

            final PsiElement finalPsiElement = psiElement;

            ReadAction.nonBlocking(() -> {
                try {
                    UastSequenceGenerator uastSequenceGenerator = new UastSequenceGenerator();
                    CallStack callStack = uastSequenceGenerator.generate(finalPsiElement, null);

                    if (callStack == null || callStack.getMethod() == null) {
                        return "";
                    }

                    return PROMPT.replace(
                            TARGET_METHOD_PLACE_HOLD, callStack.getMethod().getClassDescription().getClassName() + "." + callStack.getMethod().getMethodName()
                    ).replace(TARGET_METHOD_BODY, String.format("%n```%s%n%s%n```", "java", callStack.getMethod().getMethodBody()));
                } finally {
                    progressIndicator.processFinish();

                }
            }).wrapProgress(progressIndicator).finishOnUiThread(ModalityState.defaultModalityState(), final_prompt -> {
                var message = new Message(final_prompt);
                var toolWindowContentManager = project.getService(ChatToolWindowContentManager.class);
                toolWindowContentManager.getToolWindow().show();

                message.setReferencedFilePaths(Stream.ofNullable(project.getUserData(CodeGPTKeys.SELECTED_FILES))
                        .flatMap(Collection::stream).map(ReferencedFile::getFilePath).toList());
                toolWindowContentManager.sendMessage(message);

            }).inSmartMode(project).submit(NonUrgentExecutor.getInstance());

        } else {
            LOG.warn("未选中method");
        }

    }

}

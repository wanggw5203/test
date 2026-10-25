package io.github.aitestflow.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

public final class ArtifactStore {
    private final Project project;

    public ArtifactStore(Project project) {
        this.project = project;
    }

    public Path save(String projectRelativePath, String content) throws IOException {
        return save(projectRelativePath, content, null);
    }

    public Path save(String projectRelativePath, String content, Revision expectedRevision) throws IOException {
        Path target = resolveProjectPath(project, projectRelativePath);
        String value = clean(content);
        Files.createDirectories(target.getParent());
        AtomicReference<Exception> failure = new AtomicReference<>();
        Runnable write = () -> {
            try {
                WriteCommandAction.runWriteCommandAction(project, () -> {
                    try {
                        if (expectedRevision != null && !expectedRevision.equals(capture(target))) {
                            throw new IOException("产物在生成期间已被其他成员或进程修改，已停止覆盖: " + target);
                        }
                        writeThroughVfs(target, value);
                    } catch (IOException exception) {
                        throw new ArtifactWriteException(exception);
                    }
                });
            } catch (Exception exception) {
                failure.set(exception);
            }
        };
        if (ApplicationManager.getApplication().isDispatchThread()) write.run();
        else ApplicationManager.getApplication().invokeAndWait(write);
        if (failure.get() != null) throw new IOException("写入项目文件失败: " + target, failure.get());
        return target;
    }

    public Path saveBinary(String projectRelativePath, byte[] content) throws IOException {
        Path target = resolveProjectPath(project, projectRelativePath);
        Files.createDirectories(target.getParent());
        AtomicReference<Exception> failure = new AtomicReference<>();
        Runnable write = () -> {
            try {
                WriteCommandAction.runWriteCommandAction(project, () -> writeBinaryThroughVfs(target, content));
            } catch (Exception exception) {
                failure.set(exception);
            }
        };
        if (ApplicationManager.getApplication().isDispatchThread()) write.run();
        else ApplicationManager.getApplication().invokeAndWait(write);
        if (failure.get() != null) throw new IOException("导入项目文件失败: " + target, failure.get());
        return target;
    }

    private static void writeThroughVfs(Path target, String value) {
        try {
            VirtualFile parent = VfsUtil.createDirectories(target.getParent().toString());
            String filename = target.getFileName().toString();
            VirtualFile file = parent.findChild(filename);
            if (file == null) file = parent.createChildData(ArtifactStore.class, filename);
            var document = FileDocumentManager.getInstance().getDocument(file);
            if (document != null) {
                document.setText(value);
                FileDocumentManager.getInstance().saveDocument(document);
            } else {
                file.setBinaryContent(value.getBytes(file.getCharset()));
            }
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target);
        } catch (IOException exception) {
            throw new ArtifactWriteException(exception);
        }
    }

    private static void writeBinaryThroughVfs(Path target, byte[] content) {
        try {
            VirtualFile parent = VfsUtil.createDirectories(target.getParent().toString());
            String filename = target.getFileName().toString();
            VirtualFile file = parent.findChild(filename);
            if (file == null) file = parent.createChildData(ArtifactStore.class, filename);
            file.setBinaryContent(content);
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(target);
        } catch (IOException exception) {
            throw new ArtifactWriteException(exception);
        }
    }

    private static final class ArtifactWriteException extends RuntimeException {
        private ArtifactWriteException(IOException cause) {
            super(cause);
        }
    }

    public Path resolve(String projectRelativePath) throws IOException {
        return resolveProjectPath(project, projectRelativePath);
    }

    public Revision revision(String projectRelativePath) throws IOException {
        return capture(resolve(projectRelativePath));
    }

    public static Revision capture(Path path) throws IOException {
        if (!Files.exists(path)) return new Revision(false, "");
        return new Revision(true, sha256(Files.readAllBytes(path)));
    }

    public static String sha256(String value) {
        return sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 缺少 SHA-256", exception);
        }
    }

    public record Revision(boolean exists, String sha256) {}

    static Path resolveProjectPath(Project project, String projectRelativePath) throws IOException {
        if (project.getBasePath() == null) throw new IOException("当前项目没有本地目录");
        if (projectRelativePath == null || projectRelativePath.isBlank()) throw new IOException("产物路径不能为空");
        Path projectRoot = Path.of(project.getBasePath()).toAbsolutePath().normalize();
        Path target = projectRoot.resolve(projectRelativePath.trim()).normalize();
        if (!target.startsWith(projectRoot)) {
            throw new IOException("产物必须保存在当前项目内: " + projectRelativePath);
        }
        return target;
    }

    public static String clean(String content) {
        if (content == null) return "";
        String value = content.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (value.startsWith("```") && value.endsWith("```")) {
            int firstLine = value.indexOf('\n');
            if (firstLine > 0) value = value.substring(firstLine + 1, value.length() - 3).trim();
        }
        return value + "\n";
    }
}

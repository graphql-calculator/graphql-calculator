package calculator.engine;

import java.util.concurrent.CompletableFuture;

/**
 **/
public class TaskNode {
    private String path;
    private CompletableFuture<Object> task;

    /**
     * 虽然可能有多个子节点，但是但是对于某一个node路径、只可能有一个子节点
     *
     * todo：是否考虑使用 parentNode 或者 List<TaskNode> subNodeList，后者应该肯定是不合适的。
     */
    private TaskNode subNode;
}

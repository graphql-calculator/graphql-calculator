package calculator.engine;

import graphql.execution.instrumentation.InstrumentationState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * fixme 有状态的、保存执行信息
 *
 * @Date 2021/1/7
 **/
public class ScheduleState implements InstrumentationState {


    /**
     * 字段路径，对应的异步任务
     *
     * fixme 刚开始都是 DUMMY_TASK；
     */
    private final Map<String, CompletableFuture<Object>> taskByPath = new ConcurrentHashMap<>();

    /**
     * 一个节点依赖的字段绝对路径、从外到内
     *
     * <p>
     *     通过taskByPath可间接获取到该节点依赖的异步任务。
     */
    private final Map<String, List<String>> sequenceTaskByNode = new HashMap<>();


    public Map<String, CompletableFuture<Object>> getTaskByPath() {
        return taskByPath;
    }


    public Map<String, List<String>> getSequenceTaskByNode() {
        return sequenceTaskByNode;
    }
}

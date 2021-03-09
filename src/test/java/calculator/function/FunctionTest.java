package calculator.function;

import static calculator.engine.metadata.WrapperState.FUNCTION_KEY;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.junit.Test;

import com.googlecode.aviator.AviatorEvaluator;

import calculator.engine.function.NodeFunction;
import calculator.engine.metadata.FutureTask;
import calculator.engine.metadata.WrapperState;

public class FunctionTest {

    @Test
    public void nodeFunction() {
        WrapperState wrapperState = new WrapperState();
        wrapperState.getSequenceTaskByNode().put("itemId", Arrays.asList("itemInfo.itemId"));

        FutureTask task = FutureTask.newBuilder()
                .future(CompletableFuture.completedFuture(1991))
                .isList(false)
                .path("itemInfo.itemId")
                .build();
        wrapperState.getTaskByPath().put("itemInfo.itemId", task);


        Map<String, Object> variable = new HashMap<>();
        variable.put(FUNCTION_KEY, wrapperState);

        AviatorEvaluator.addFunction(new NodeFunction());
        Object funcResult = AviatorEvaluator.execute("node('itemId')", variable, true);

        assert Objects.equals(funcResult, 1991);
    }
}

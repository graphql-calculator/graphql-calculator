package calculator.engine.function;

import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorJavaType;
import com.googlecode.aviator.runtime.type.AviatorNil;
import com.googlecode.aviator.runtime.type.AviatorObject;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * todo：通过threadLocal获取InstrumentState变量？
 */
public class NodeFunction extends AbstractFunction {

    private static final String FUNCTION_NAME = "node";

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    @Override
    public AviatorObject call(Map<String, Object> env, AviatorObject arg) {
        AviatorJavaType javaArg = (AviatorJavaType) arg;

        if (!env.containsKey(javaArg.getName())) {
            return AviatorNil.NIL;
        }

        /**
         * todo：等待任务异常结束或者执行完毕
         */
        CompletableFuture<Object> future = (CompletableFuture)env.get(javaArg.getName());
        return null;
    }

}

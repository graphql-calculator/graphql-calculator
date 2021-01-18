package calculator.engine.function;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorBigInt;
import com.googlecode.aviator.runtime.type.AviatorJavaType;
import com.googlecode.aviator.runtime.type.AviatorNil;
import com.googlecode.aviator.runtime.type.AviatorObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class NodeFunction extends AbstractFunction {


    private static final String FUNCTION_NAME = "node";

    @Override
    public String getName() {
        return FUNCTION_NAME;
    }

    // 只可能有一个参数，比如 node(couponIds)
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
    public static void main(String[] args) {
        AviatorEvaluator.addFunction(new NodeFunction());

        Map<String, Object> env = new HashMap();
        env.put("couponPrice", 1);
        env.put("salePrice", 10);
        // salePrice - node(couponPrice)
        // salePrice - fromNode(couponPrice)
        Object execute = AviatorEvaluator.execute("salePrice - node(couponPrice)", env);
        System.out.println(execute);
    }

}

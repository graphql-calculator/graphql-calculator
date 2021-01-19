package calculator.validate;

import calculator.config.ConfigImpl;
import calculator.engine.CalculateInstrumentation;
import calculator.engine.ScheduleInstrument;
import calculator.engine.Wrapper;
import com.googlecode.aviator.runtime.type.AviatorFunction;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.execution.instrumentation.ChainedInstrumentation;
import graphql.schema.GraphQLSchema;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static calculator.directives.CalculateSchemaHolder.getCalSchema;
import static calculator.engine.CalculateInstrumentation.getCalInstance;
import static calculator.engine.ScheduleInstrument.getScheduleInstrument;

public class CommonToolsForTest {

    public static void sleepBySecond(long sec){
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException e) {

        }
    }

    public static void sleepByMil(long sec){
        try {
            TimeUnit.MILLISECONDS.sleep(sec);
        } catch (InterruptedException e) {

        }
    }


    public static void println(Object object){
        System.out.println(Objects.toString(object));
    }


    public static void main(String[] args) {
        String query = ""
                + "query($userId:Int){\n" +
                "    userInfo(id:$userId){\n" +
                "        age\n" +
                "        name\n" +
                "        preferredItemIdList @node(name:\"itemIds\")\n" +
                "    }\n" +
                "\n" +
                "    itemList(ids:1) @link(argument:\"ids\",node:\"itemIds\"){\n" +
                "        id\n" +
                "        name\n" +
                "        salePrice\n" +
                "        withCouponIdList\n" +
                "    }\n" +
                "}";


        System.out.println(query);

    }
}

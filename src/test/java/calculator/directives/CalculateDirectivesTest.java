package calculator.directives;

import calculator.config.Config;
import calculator.engine.CalculateDirectives;
import calculator.engine.CalculateInstrumentation;
import calculator.engine.ScheduleInstrument;
import calculator.engine.Wrapper;
import calculator.validate.CalValidation;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static calculator.directives.CalculateSchemaHolder.getCalSchema;
import static calculator.engine.CalculateInstrumentation.CAL_INSTANCE;
import static calculator.TestUtil.getFromNestedMap;
import static com.googlecode.aviator.AviatorEvaluator.execute;

// todo 测试校验
public class CalculateDirectivesTest {

    @Test
    public void skipByTest() {
        Config config = () -> Collections.singleton(CalculateDirectives.skipBy);
        GraphQLSchema wrappedSchema = Wrapper.wrap(config, getCalSchema());
        GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema)
                .instrumentation(CAL_INSTANCE)
                .build();

        String query = ""
                + "query($userId:Int) { "
                + "    userInfo(id: $userId) @skipBy(exp:\"id < 0\"){"
                + "        id "
                + "        name"
                + "    }"
                + "}";

        ExecutionInput skipInput = ExecutionInput
                .newExecutionInput(query)
                .variables(Collections.singletonMap("userId", -1))
                .build();
        ExecutionResult skipRes = graphQL.execute(skipInput);
        assert ((Map) skipRes.getData()).get("userInfo") == null;

        ExecutionInput normalInput = ExecutionInput
                .newExecutionInput(query)
                .variables(Collections.singletonMap("userId", 11))
                .build();

        ExecutionResult invokeRes = graphQL.execute(normalInput);
        assert ((Map) invokeRes.getData()).get("userInfo") != null;
    }


    @Test
    public void scheduleTest() {
        Config config = () -> new HashSet<>(Arrays.asList(CalculateDirectives.link, CalculateDirectives.node));
        GraphQLSchema wrappedSchema = Wrapper.wrap(config, getCalSchema());
        GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema)
                .instrumentation(ScheduleInstrument.getInstance()).build();

        String query = ""
                + "query($userId:Int, $couponId:Int){ "
                + "   userInfo(id:$userId){"
                + "       age"
                + "       name"
                + "       preferredItemIdList @node(name:\"itemIds\")" +
                "   }"
                + ""
                + "   coupon(id:$couponId){"
                + "       price"
                + "       couponText"
                + "   }"
                + ""
                //  fixme 使用 @node 标记的 "用户喜欢的商品id"获取商品信息
                + "   itemList(ids:[1]) @link(exp:\"itemIds:ids\"){"
                + "       name"
                + "       salePrice"
                + "       withCouponIdList"
                + "   }"
                + ""
                + "}";

        Map<String, Object> variables = new HashMap<>();
        variables.put("userId", 1);
        variables.put("couponId", 1);
        ExecutionInput input = ExecutionInput.newExecutionInput(query).variables(variables).build();
        ExecutionResult result = graphQL.execute(input);

        assert result != null;
        assert result.getErrors().isEmpty();
        assert result.getData() != null;
    }


    @Test
    public void mockTest() {
        Config config = () -> Collections.singleton(CalculateDirectives.mock);
        GraphQLSchema wrappedSchema = Wrapper.wrap(config, getCalSchema());
        GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema).instrumentation(new CalculateInstrumentation()).build();

        String query = "query{\n" +
                "    userInfo(id:1){\n" +
                "        email @mock(value:\"dugk@foxmail.com\")\n" +
                "    }\n" +
                "}";
        CalValidation.validateQuery(query, getCalSchema());

        ExecutionResult filterRes = graphQL.execute(query);
        assert filterRes.getErrors().isEmpty();
        assert getFromNestedMap(filterRes.getData(), "userInfo.email").equals("dugk@foxmail.com");
    }


    @Test
    public void mapTest() {
        Config config = () -> Collections.singleton(CalculateDirectives.map);
        GraphQLSchema wrappedSchema = Wrapper.wrap(config, getCalSchema());
        GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema).instrumentation(new CalculateInstrumentation()).build();

        String query = "query {\n" +
                "    userInfo(id:5){\n" +
                "        email\n" +
                "        netName: email @map(mapper:\"\'netName:\' + email\")\n" +
                "    }\n" +
                "}";

        ExecutionResult mapResult = graphQL.execute(query);

        assert mapResult != null;
        assert mapResult.getErrors().isEmpty();
        assert getFromNestedMap(mapResult.getData(), "userInfo.email").equals("5dugk@foxmail.com");
        assert getFromNestedMap(mapResult.getData(), "userInfo.netName").equals("netName:5dugk@foxmail.com");
    }

    @Test
    public void filterTest() {
        Config config = () -> Collections.singleton(CalculateDirectives.filter);
        GraphQLSchema wrappedSchema = Wrapper.wrap(config, getCalSchema());
        GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema).instrumentation(CAL_INSTANCE).build();
        String query = "query {\n" +
                "    couponList(ids:[1,2,3,4]) @filter(predicate:\"id>=2\"){\n" +
                "        id\n" +
                "        price\n" +
                "        limitation \n" +
                "    }  \n" +
                "}";

        ExecutionResult result = graphQL.execute(query);
        assert result != null;
        assert result.getErrors().isEmpty();
        assert ((Map<String, List>) result.getData()).get("couponList").size() == 3;

    }

    @Test
    public void sortByDirective() {
        Config config = () -> Collections.singleton(CalculateDirectives.sortBy);
        GraphQLSchema wrappedSchema = Wrapper.wrap(config, getCalSchema());
        GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema).instrumentation(new CalculateInstrumentation()).build();
        String query = "query {\n" +
                "    itemList(ids:[3,2,1,4,5]) @sortBy(key:\"id\"){\n" +
                "        id\n" +
                "        name\n" +
                "    }\n" +
                "}";
        ExecutionResult result = graphQL.execute(query);

        assert result != null;
        assert result.getErrors().isEmpty();
        assert Objects.equals(execute("seq.get(seq.get(itemList,0),'id')",result.getData()),1);
        assert Objects.equals(execute("seq.get(seq.get(itemList,1),'id')",result.getData()),2);
        assert Objects.equals(execute("seq.get(seq.get(itemList,2),'id')",result.getData()),3);
        assert Objects.equals(execute("seq.get(seq.get(itemList,3),'id')",result.getData()),4);
        assert Objects.equals(execute("seq.get(seq.get(itemList,4),'id')",result.getData()),5);

    }
}

package calculator.validate;

import calculator.config.Config;
import calculator.engine.CalculateDirectives;
import calculator.engine.ScheduleInstrument;
import calculator.engine.Wrapper;
import graphql.GraphQL;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static calculator.directives.CalculateSchemaHolder.getCalSchema;

public class CalValidationUtilTest {

    // 验证 不能有同名的node
    // todo 解析片段
    @Test
    public void unusedNodeTest() {
        Config config = () -> Collections.singleton(CalculateDirectives.node);
        GraphQLSchema wrappedSchema = Wrapper.wrap(config, getCalSchema());

        String query = "query { \n" +
                "            userInfo @node(name: \"X\") {\n" +
                "                id\n" +
                "                name\n" +
                "            } \n" +
                "        }";
        ParseAndValidateResult parseAndValidateResult = CalValidation.validateQuery(
                query, wrappedSchema
        );

        assert parseAndValidateResult.getValidationErrors().size() == 1;
        assert parseAndValidateResult.getValidationErrors().get(0).getMessage().equals(
                "Validation error of type null:  unused node: [X]."
        );
    }


    @Test
    public void duplicateNodeTest() {
        Config config = () -> new HashSet<>(Arrays.asList(CalculateDirectives.link, CalculateDirectives.node));
        GraphQLSchema wrappedSchema = Wrapper.wrap(config, getCalSchema());
        String query = "query($userId:Int){\n" +
                "    userInfo(id:$userId){\n" +
                "        preferredItemIdList @node(name:\"ids\")\n" +
                "        acquiredCouponIdList @node(name:\"ids\")\n" +
                "    }\n" +
                "\n" +
                "    itemList(ids: 1) @link(argument:\"ids\",node:\"ids\"){\n" +
                "        name\n" +
                "    }\n" +
                "    \n" +
                "    couponList(ids: 1) @link(argument:\"ids\",node:\"ids\"){\n" +
                "        price\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult parseAndValidateResult = CalValidation.validateQuery(
                query, wrappedSchema
        );

        assert parseAndValidateResult.getValidationErrors().size() == 1;
        assert parseAndValidateResult.getValidationErrors().get(0).getMessage().equals(
                "Validation error of type null: duplicate node name 'ids' for userInfo#preferredItemIdList and userInfo#acquiredCouponIdList."
        );
    }

}

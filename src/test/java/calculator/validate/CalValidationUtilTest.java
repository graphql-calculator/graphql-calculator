package calculator.validate;

import calculator.config.Config;
import calculator.engine.CalculateDirectives;
import calculator.engine.Wrapper;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Collections;

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
        Config config = () -> Collections.singleton(CalculateDirectives.node);
        GraphQLSchema wrappedSchema = Wrapper.wrap(config, getCalSchema());


        ParseAndValidateResult parseAndValidateResult = CalValidation.validateQuery(
                "", wrappedSchema
        );

        System.out.println(parseAndValidateResult.getValidationErrors().get(0).getMessage());
        assert parseAndValidateResult.getValidationErrors().size() == 1;
        assert parseAndValidateResult.getValidationErrors().get(0).getMessage().equals(
                "Validation error of type null: duplicate node name for Query.namedField and Query.intField "
        );
    }


}

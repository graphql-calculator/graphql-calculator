package calculator;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

public class TestUtil {

    public static GraphQLSchema schemaByInputFile(String inputPath, RuntimeWiring runtimeWiring) {
        InputStream inputStream = TestUtil.class.getClassLoader().getResourceAsStream(inputPath);
        InputStreamReader inputReader = new InputStreamReader(inputStream);
        TypeDefinitionRegistry registry = new SchemaParser().parse(inputReader);
        return new SchemaGenerator().makeExecutableSchema(registry, runtimeWiring);
    }

    public static Object getFromNestedMap(Map map, String path) {
        String[] split = path.split("\\.");

        Object tmpRes = null;
        Map tmpMap = map;
        for (String key : split) {
            tmpRes = tmpMap.get(key);
            if (tmpRes == null) {
                return null;
            }

            if (tmpRes instanceof Map) {
                tmpMap = (Map) tmpRes;
            }
        }

        return tmpRes;
    }

}

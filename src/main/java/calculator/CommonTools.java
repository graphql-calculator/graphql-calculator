package calculator;

import graphql.Assert;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.execution.ResultPath;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.Field;
import graphql.language.IntValue;
import graphql.language.NamedNode;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLUnmodifiedType;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilationFailedException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

// todo 名字不好，应该用 组建CommonTools
public class CommonTools {

    public static final String PATH_SEPARATOR = "#";

    public static Object getArgumentFromDirective(Directive directive, String argName) {
        Argument argument = directive.getArgument(argName);
        if (argument == null) {
            return null;
        }

        return parseValue(argument.getValue());
    }

    /**
     * todo 解析Value：这个方法不太安全，因为没新增一种Value类型，这里都要新增一种情况
     *
     * @param value
     * @return
     */
    public static Object parseValue(Value value) {
        if (value instanceof StringValue) {
            return ((StringValue) value).getValue();
        }

        if (value instanceof IntValue) {
            return ((IntValue) value).getValue();
        }

        return null;
    }


    /**
     * 获取字段的别名或者名称
     */
    public static String getAliasOrName(Field field) {
        if (field.getAlias() != null) {
            return field.getAlias();
        }

        return field.getName();
    }


    public static boolean isValidEleName(String name) {
        try {
            Assert.assertValidName(name);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * todo 查询中的坐标不可以使用 类型.字段名称，是可能重复的，比如
     * <p>
     * // person 和 human 都是 Animal类
     * query{
     * person{
     * name
     * }
     * human{
     * name
     * }
     * }
     */
    public static String keyForFieldByQVFEnv(final QueryVisitorFieldEnvironment environment) {
        // 考虑接口、联合类型、片段等因素
        GraphQLUnmodifiedType namedType = GraphQLTypeUtil.unwrapAll(environment.getParentType());
        String aliasOrName = getAliasOrName(environment.getField());
        return namedType.getName() + "." + aliasOrName;
    }

    /**
     * 返回当前字段的查询路径名称，路径segment别名优先
     *
     * <p>
     * todo 确定 list 是否有影响。有，需要确认下具体影响
     */
    public static String fieldPath(final ResultPath stepInfo) {
        StringBuilder sb = new StringBuilder();
        ResultPath tmpEnv = stepInfo;
        while (tmpEnv != null) {
            if (!tmpEnv.isNamedSegment()) {
                tmpEnv = tmpEnv.getParent();
                continue;
            }

            String segmentName = tmpEnv.getSegmentName();
            if (segmentName == null || segmentName.length() == 0) {
                tmpEnv = tmpEnv.getParent();
                continue;
            }

            if (sb.length() == 0) {
                sb.append(segmentName);
            } else {
                sb.insert(0, segmentName + PATH_SEPARATOR);
            }
            tmpEnv = tmpEnv.getParent();
        }

        return sb.toString();
    }


    public static <T extends NamedNode<T>> T findNodeByName(List<T> namedNodes, String name) {
        for (T namedNode : namedNodes) {
            if (Objects.equals(namedNode.getName(), name)) {
                return namedNode;
            }
        }
        return null;
    }

}

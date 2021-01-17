package calculator.validate;

import calculator.CommonTools;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.language.Directive;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.SourceLocation;
import graphql.schema.GraphQLTypeUtil;
import graphql.util.TraverserContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static calculator.CommonTools.isValidEleName;
import static calculator.CommonTools.keyForFieldByQVFEnv;
import static calculator.engine.CalculateDirectives.filter;
import static calculator.engine.CalculateDirectives.mock;
import static calculator.engine.CalculateDirectives.node;
import static calculator.engine.CalculateDirectives.skipBy;
import static calculator.engine.CalculateDirectives.sortBy;
import static calculator.engine.ExpCalculator.isValidExp;


/**
 * 校验逻辑：
 * - skipBy：表达式是否可编译 -
 * - mock：表达式是否为空
 * - filter：表达式是否可编译，是不是放在数组上的
 * - map：
 * - sortBy
 * - node
 * - link
 * <p>
 * todo：表达式返回值类型必须是boolean，但是很难校验、比如 "a"、a可能是任意类型、还必须要分析a表示的字段类型。
 */
public class BaseValidator extends QueryValidationVisitor {

    // 1. @node是否重名;
    private Map<String, String> nodeNameMap;


    // todo 是否需要考虑并发
    public BaseValidator() {
        super();
        this.nodeNameMap = new HashMap<>();
    }


    public Map<String, String> getNodeNameMap() {
        return nodeNameMap;
    }

    public static BaseValidator newInstance() {
        return new BaseValidator();
    }

    @Override
    public void visitField(QueryVisitorFieldEnvironment environment) {
        // 不是进入该节点则返回
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

        // TypeName.aliasOrName
        String keyForField = keyForFieldByQVFEnv(environment);
        SourceLocation location = environment.getField().getSourceLocation();

        for (Directive directive : environment.getField().getDirectives()) {
            String directiveName = directive.getName();
            // 如果是node、则查看是否已经在中保存过

            if (Objects.equals(directiveName, skipBy.getName())) {
                /**
                 * skipBy
                 *
                 * 1. 表达式不为空；
                 * 2. 表达式合法；
                 */

                String exp = (String) CommonTools.parseValue(
                        directive.getArgument("exp").getValue()
                );

                if (exp == null || exp.isEmpty()) {
                    String errorMsg = String.format(" groovy script can't be empty, @%s ", keyForField);
                    addValidError(location, errorMsg);
                }

                if (!isValidExp(exp)) {
                    String errorMsg = String.format(" invalidate groovy script for %s on %s ", exp, keyForField);
                    addValidError(location, errorMsg);
                }


            } else if (Objects.equals(directiveName, mock.getName())) {
                /**
                 * mock：
                 *      fixme：注意，value可以为空串、模拟返回结果为空的情况；
                 */
            } else if (Objects.equals(directiveName, filter.getName())) {

                /**
                 * filter
                 *
                 * 1. 放在的位置是不是非叶子节点；
                 * 2. 表达式不为空；
                 * 3. 表达式合法；
                 */
                boolean isListType = GraphQLTypeUtil.isList(environment.getFieldDefinition().getType());
                if (!isListType) {
                    String errorMsg = String.format(" predicate must define on list type, instead @%s.", keyForField);
                    addValidError(location, errorMsg);
                }

                String predicate = (String) CommonTools.parseValue(
                        directive.getArgument("predicate").getValue()
                );
                if (predicate == null || predicate.isEmpty()) {
                    String errorMsg = String.format(" groovy script can't be empty, @%s ", keyForField);
                    addValidError(location, errorMsg);
                }

                if (!isValidExp(predicate)) {
                    String errorMsg = String.format(" invalidate groovy script for %s on %s ", predicate, keyForField);
                    addValidError(location, errorMsg);
                }


            } else if (Objects.equals(directiveName, sortBy.getName())) {
                /**
                 * filter
                 *
                 * 1. 放在的位置是不是非叶子节点；
                 * 2. key名称不为空；
                 * 3. todo 指定的key是否包含在查询的子列表中；
                 *         考虑到别名；
                 *         key名称和真正执行排序的时候使用的名称是否相同；
                 */

                boolean isListType = GraphQLTypeUtil.isList(environment.getFieldDefinition().getType());
                if (!isListType) {
                    String errorMsg = String.format(" key must define on list type, instead @%s.", keyForField);
                    addValidError(location, errorMsg);
                    continue;
                }

                String key = (String) CommonTools.parseValue(
                        directive.getArgument("key").getValue()
                );
                if (key == null || key.isEmpty()) {
                    String errorMsg = String.format(" key can't be null, @%s ", keyForField);
                    addValidError(location, errorMsg);
                    continue;
                }

                SelectionSet selectionSet = environment.getField().getSelectionSet();
                for (Selection selection : selectionSet.getSelections()) {
                    System.out.println(selection);
                }

            } else if (Objects.equals(directiveName, node.getName())) {
                /**
                 * node
                 *
                 * 1. 名称合法；
                 * 2. 名称不重复；
                 */
                String nodeName = (String) CommonTools.parseValue(
                        directive.getArgument("name").getValue()
                );

                if (nodeNameMap.containsKey(nodeName)) {
                    String errorMsg = String.format("duplicate node name for %s and %s ", nodeNameMap.get(nodeName), keyForField);
                    addValidError(location, errorMsg);
                } else {
                    nodeNameMap.put(nodeName, keyForField);
                    if (!isValidEleName(nodeName)) {
                        String errorMsg = String.format(" invalid node name, @%s .", keyForField);
                        addValidError(location, errorMsg);
                    }
                }
            }
        }

    }
}

package calculator.validate;

import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.language.SourceLocation;
import graphql.util.TraverserContext;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static calculator.CommonTools.getArgumentFromDirective;
import static calculator.CommonTools.keyForFieldByQVFEnv;
import static calculator.engine.CalculateDirectives.link;
import static java.util.stream.Collectors.toSet;


/**
 * link使用的node必须存在； -
 * link指向的参数必须存在； -
 * link不能指向同一个参数；-
 * <p>
 * todo
 * node和参数类型必须兼容；
 * 定义的node是否被使用了；
 * 不能有环、还是dag；
 */
public class LinkValidator extends QueryValidationVisitor {

    private Map<String, String> nodeNameMap;

    // 在link中使用的node
    private Set<String> usedNodeName;

    // todo 是否需要考虑并发
    public LinkValidator() {
        super();
        usedNodeName = new LinkedHashSet<>();
    }

    public static LinkValidator newInstance() {
        return new LinkValidator();
    }

    public void setNodeNameMap(Map<String, String> nodeNameMap) {
        this.nodeNameMap = nodeNameMap;
    }

    public Set<String> getUsedNodeName() {
        return usedNodeName;
    }

    @Override
    public void visitField(QueryVisitorFieldEnvironment environment) {
        // 不是进入该节点则返回
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

        String keyForField = keyForFieldByQVFEnv(environment);

        Set<String> argumentsName = environment.getField().getArguments().stream().map(Argument::getName).collect(toSet());
        for (Directive directive : environment.getField().getDirectives()) {
            String directiveName = directive.getName();
            SourceLocation directiveLocation = directive.getSourceLocation();

            /**
             * 1. 格式错误；
             * 2. node节点不存在；
             * 3. 指向的参数没有在field中定义；
             * 4. 两个node指向同一个参数。
             */
            if (Objects.equals(directiveName, link.getName())) {
                Map<String, String> argByNodeName = new HashMap<>();
                String expString = (String) getArgumentFromDirective(directive, "exp");
                for (String kvStr : expString.split(",")) {
                    // nodeName:argName
                    String[] split = kvStr.split(":");
                    if (split.length != 2) {
                        String errorMsg = String.format("wrong format exp on %s.", keyForField, kvStr);
                        addValidError(directiveLocation, errorMsg);
                        continue;
                    }
                    usedNodeName.add(split[1]);

                    // 如果没有这个node节点
                    if (!nodeNameMap.containsKey(split[0])) {
                        String errorMsg = String.format("the node '%s' used by %s do not exist.", split[0], kvStr);
                        addValidError(directiveLocation, errorMsg);
                        continue;
                    }

                    // 指向的参数没有在Field中显式定义
                    if (argumentsName.contains(split[1])) {
                        String errorMsg = String.format("the argument '%s' on %s do not exist.", split[1], kvStr);
                        addValidError(directiveLocation, errorMsg);
                        continue;
                    }

                    // 两个node指向同一个参数
                    if (argByNodeName.containsKey(split[1])) {
                        String errorMsg = String.format("duplicate argument for same node, %s, exp fragment is '%s'.", keyForField, kvStr);
                        addValidError(directiveLocation, errorMsg);
                        continue;
                    } else {
                        argByNodeName.put(split[1], split[0]);
                    }
                }
            }

        }

    }
}

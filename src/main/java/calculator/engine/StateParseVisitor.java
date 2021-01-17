package calculator.engine;

import graphql.analysis.QueryVisitor;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.QueryVisitorInlineFragmentEnvironment;
import graphql.language.Directive;
import graphql.util.TraverserContext;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static calculator.CommonTools.PATH_SEPARATOR;
import static calculator.CommonTools.getAliasOrName;
import static calculator.CommonTools.getArgumentFromDirective;


public class StateParseVisitor implements QueryVisitor {

    private ScheduleState state;

    private StateParseVisitor(ScheduleState state) {
        this.state = state;
    }

    public static StateParseVisitor newInstanceWithState(ScheduleState state) {
        return new StateParseVisitor(state);
    }

    @Override
    public void visitField(QueryVisitorFieldEnvironment environment) {
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

        /**
         * 该节点被node注解，保存该节点及其父亲节点
         */
        List<Directive> directives = environment.getField().getDirectives(CalculateDirectives.node.getName());
        if (directives != null && !directives.isEmpty()) {
            Directive nodeDir = directives.get(0);
            String nodeName = getArgumentFromDirective(nodeDir, "name");

            Map<String, CompletableFuture<Object>> taskByPath = state.getTaskByPath();

            List<String> pathList = new LinkedList<>();
            handle(environment, pathList);
            state.getSequenceTaskByNode().put(nodeName, pathList);
            for (String path : pathList) {
                taskByPath.putIfAbsent(path, new CompletableFuture());
            }
        }
    }

    /**
     * fixme 直接嵌套便利o(N^2)算法，该优化为o(N)算法(因为一般层级很浅、所以此优化也没必要)
     */
    private String handle(QueryVisitorFieldEnvironment environment, List<String> pathList) {
        if (environment.getParentEnvironment() == null) {
            String resultKey = getAliasOrName(environment.getField());
            pathList.add(resultKey);
            return resultKey;
        }

        String nestedKey = handle(environment.getParentEnvironment(), pathList)
                + PATH_SEPARATOR + getAliasOrName(environment.getField());
        pathList.add(nestedKey);
        return nestedKey;
    }


    @Override
    public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment) {

    }

    @Override
    public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment) {

    }
}

package calculator.validate;

import graphql.analysis.QueryVisitor;
import graphql.analysis.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.QueryVisitorInlineFragmentEnvironment;
import graphql.language.SourceLocation;
import graphql.util.TraverserContext;
import graphql.validation.ValidationError;

import java.util.LinkedList;
import java.util.List;

public abstract class QueryValidationVisitor implements QueryVisitor {

    // fixme 校验结果
    private List<ValidationError> errors;


    public QueryValidationVisitor() {
        this.errors = new LinkedList<>();
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public void addValidError(SourceLocation location, String errorMsg) {
        ValidationError error = ValidationError.newValidationError()
                .sourceLocation(location)
                .description(errorMsg)
                .build();
        errors.add(error);
    }


    @Override
    public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment environment) {
        // 不是进入该节点则返回
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }

    }

    @Override
    public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment environment) {
        // 不是进入该节点则返回
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }
    }

}

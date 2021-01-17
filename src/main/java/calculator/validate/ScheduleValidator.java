package calculator.validate;

import graphql.analysis.QueryVisitorFieldEnvironment;


/**
 * todo
 *      不能有环、还是dag；
 *      node和参数类型必须兼容；
 */
public class ScheduleValidator extends QueryValidationVisitor {


    public static ScheduleValidator newInstance() {
        return new ScheduleValidator();
    }

    @Override
    public void visitField(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {

    }
}

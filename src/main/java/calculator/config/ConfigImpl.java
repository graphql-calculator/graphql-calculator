package calculator.config;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.runtime.type.AviatorFunction;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * 默认配置实现
 */
public class ConfigImpl implements Config {

    private boolean isScheduleEnable;

    private List<AviatorFunction> functionList;

    public ConfigImpl(boolean isScheduleEnable, List<AviatorFunction> functionList) {
        this.isScheduleEnable = isScheduleEnable;
        this.functionList = functionList;
    }

    @Override
    public boolean isScheduleEnable() {
        return isScheduleEnable;
    }

    @Override
    public List<AviatorFunction> calFunctions() {
        return Collections.unmodifiableList(functionList);
    }

    @Override
    public AviatorEvaluator getAviatorEvaluator() {
        // todo
        return null;
    }

    public static Builder newConfig() {
        return new Builder();
    }

    public static class Builder {

        private boolean isScheduleEnable;

        private List<AviatorFunction> functionList = new LinkedList<>();

        public Builder isScheduleEnable(boolean val) {
            isScheduleEnable = val;
            return this;
        }

        public Builder function(AviatorFunction function) {
            Objects.requireNonNull(function, "function can't be null.");
            this.functionList.add(function);
            return this;
        }

        public Builder functionList(List<AviatorFunction> functionList) {
            Objects.requireNonNull(functionList, "functionList can't be null.");
            this.functionList.addAll(Objects.requireNonNull(functionList));
            return this;
        }


        public ConfigImpl build() {
            return new ConfigImpl(isScheduleEnable, functionList);
        }
    }
}

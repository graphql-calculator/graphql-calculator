package calculator.config;


import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.runtime.type.AviatorFunction;

import java.util.List;

public interface Config {

    boolean isScheduleEnable();

    List<AviatorFunction> calFunctions();

    // todo 使用指定的执行器
    AviatorEvaluator getAviatorEvaluator();
}

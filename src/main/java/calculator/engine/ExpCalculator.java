package calculator.engine;

import com.googlecode.aviator.AviatorEvaluator;
import org.codehaus.groovy.control.CompilationFailedException;

import java.util.Map;

public class ExpCalculator {

    public static Object calExp(String exp, Map<String, Object> arguments) {
        return AviatorEvaluator.execute(exp, arguments);
    }


    public static boolean isValidExp(String scriptText) {
        try {
            AviatorEvaluator.compile(scriptText, true);
            return true;
        } catch (CompilationFailedException e) {
            return false;
        }
    }
}

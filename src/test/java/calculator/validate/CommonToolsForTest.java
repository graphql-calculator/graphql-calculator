package calculator.validate;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class CommonToolsForTest {

    public static void sleepBySecond(long sec){
        try {
            TimeUnit.SECONDS.sleep(sec);
        } catch (InterruptedException e) {

        }
    }

    public static void sleepByMil(long sec){
        try {
            TimeUnit.MILLISECONDS.sleep(sec);
        } catch (InterruptedException e) {

        }
    }


    public static void println(Object object){
        System.out.println(Objects.toString(object));
    }
}

package nl.revolution.watchboard.utils;

import java.lang.reflect.Field;

public class ReflectionUtils {

    public static <T> T getStaticValue(Class<?> aClass, String fieldName) {
        Field field = null;
        Boolean isAccessible = null;
        try {
            field = aClass.getDeclaredField(fieldName);
            isAccessible = field.isAccessible();
            field.setAccessible(true);
            return (T) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        } finally {
            if (field != null && isAccessible != null) {
                field.setAccessible(isAccessible);
            }
        }
    }

    public static void setStaticValue(Class aClass, String fieldName, Object value) {
        Field field = null;
        Boolean isAccessible = null;
        try {
            field = aClass.getDeclaredField(fieldName);
            isAccessible = field.isAccessible();
            field.setAccessible(true);

            field.set(null, value);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        } finally {
            if (field != null && isAccessible != null) {
                field.setAccessible(isAccessible);
            }
        }
    }

}

package nl.revolution.watchboard;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.openqa.selenium.remote.HttpCommandExecutor;
import org.openqa.selenium.remote.internal.HttpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

public class WebDriverHttpParamsSetter {

    private static final Logger LOG = LoggerFactory.getLogger(WebDriverHttpParamsSetter.class);

    @SuppressWarnings("deprecation")
    public static void setSoTimeout(int soTimeout) {
        HttpClientFactory factory = getStaticValue(HttpCommandExecutor.class, "httpClientFactory");
        if (factory == null) {
            factory = new HttpClientFactory();
        }
        DefaultHttpClient httpClient = (DefaultHttpClient) factory.getHttpClient();
        HttpParams params = httpClient.getParams();
        LOG.info("Original webDriver http.socket.timeout: {}.", HttpConnectionParams.getSoTimeout(params));
        HttpConnectionParams.setSoTimeout(params, soTimeout);
        httpClient.setParams(params);
        setStaticValue(HttpCommandExecutor.class, "httpClientFactory", factory);
        LOG.info("New webDriver http.socket.timeout: {}.", HttpConnectionParams.getSoTimeout(params));
    }

    private static <T> T getStaticValue(Class<?> aClass, String fieldName) {
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

    private static void setStaticValue(Class<HttpCommandExecutor> aClass, String fieldName, Object value) {
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
package nl.revolution.watchboard;

import nl.revolution.watchboard.utils.ReflectionUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.Configurable;
import org.openqa.selenium.remote.internal.ApacheHttpClient;
import org.openqa.selenium.remote.internal.HttpClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebDriverHttpParamsSetter {

    private static final Logger LOG = LoggerFactory.getLogger(WebDriverHttpParamsSetter.class);

    public static void setSoTimeout(int soTimeout) {
        logWebDriverTimeoutSettings("Old");

        ReflectionUtils.setStaticValue(
                ApacheHttpClient.Factory.class,
                "defaultClientFactory",
                new HttpClientFactory(soTimeout, soTimeout));

        logWebDriverTimeoutSettings("New");
    }


    private static void logWebDriverTimeoutSettings(String prefix) {
        // Trigger initialization of defaultClientFactory.
        new ApacheHttpClient.Factory();

        HttpClientFactory factory = ReflectionUtils.getStaticValue(ApacheHttpClient.Factory.class, "defaultClientFactory");
        RequestConfig config = ((Configurable)factory.getHttpClient()).getConfig();
        LOG.info(prefix + " webDriver connectTimeout: " + config.getConnectTimeout());
        LOG.info(prefix + " webDriver socketTimeout: " + config.getSocketTimeout());
    }

}
package nl.revolution.watchboard.plugins.sonar;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

/**
 * Add -webkit prefixed flex-box directives to css as phantomjs webkit
 * driver does not support standard directives for flexbox yet.
 */
public class FlexboxCssFix {

    static final String FLEX_FIX_CSS =
            ".page {" +
            "   padding 0;" +
            "   margin-left: 0;" +
            "   margin-right: 0;" +
            "}" +
            ".overview-domains-list {" +
            "   position: absolute;" +
            "   left: 0;" +
            "   top: 0;" +
            "   background-color: white;" +
            "   z-index: 9999;" +
            "   padding: 5px 0 0 0;" +
            "   width: 100%" +
            "}" +
            ".overview-card-header {" +
            "   display: -webkit-flex;" +
            "   -webkit-justify-content: space-between;" +
            "}" +
            ".overview-domain-panel {" +
            "   display: -webkit-flex;" +
            "   -webkit-flex-grow: 1;" +
            "   -webkit-flex-shrink: 0;" +
            "   -webkit-flex-basis: 500px;" +
            "}" +
            ".overview-domain-nutshell {" +
            "   display: -webkit-flex;" +
            "   -webkit-flex: 2;" +
            "}" +
            ".overview-domain-leak {" +
            "   display: -webkit-flex;" +
            "   -webkit-flex: 1;" +
            "}" +
            ".overview-domain-measures {" +
            "   display: -webkit-flex;" +
            "   -webkit-flex: 2;" +
            "}" +
            ".overview-domain-measure {" +
            "   display: -webkit-flex;" +
            "   -webkit-flex: 2;" +
            "   -webkit-flex-direction: column;" +
            "}";

    static final String JS_APPLY_SCRIPT = "var css = '" + FLEX_FIX_CSS + "';"+
            "var element = document.createElement('style');\n" +
            "element.setAttribute('type', 'text/css');\n" +
            "\n" +
            "if ('textContent' in element) {\n" +
            "  element.textContent = css;\n" +
            "} else {\n" +
            "  element.styleSheet.cssText = css;\n" +
            "}\n" +
            "\n" +
            "document.getElementsByTagName('head')[0].appendChild(element);";

    public static void flexboxFix(WebDriver driver) {
        JavascriptExecutor executor = (JavascriptExecutor)driver;

        WebElement head = driver.findElement(By.tagName("head"));
        executor.executeScript(JS_APPLY_SCRIPT, head);
    }

}

package nl.revolution.watchboard;

import junit.framework.TestCase;
import org.eclipse.jetty.server.Request;

import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NotFoundHandlerTest extends TestCase {

    public void testHandle() throws Exception {
        Request baseRequest = mock(Request.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        PrintWriter responseWriter = mock(PrintWriter.class);
        when(response.getWriter()).thenReturn(responseWriter);

        new NotFoundHandler().handle("non-existent", baseRequest, null, response);

        verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
        verify(responseWriter).write(NotFoundHandler.RESPONSE_BODY_404);
        verify(baseRequest).setHandled(true);
    }
}
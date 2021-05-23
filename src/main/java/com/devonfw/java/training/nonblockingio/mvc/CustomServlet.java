package com.devonfw.java.training.nonblockingio.mvc;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.AsyncContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.servlet.HttpServletBean;

import com.devonfw.java.training.nonblockingio.service.StorageService;

/**
 * @author MLUZYNA
 *
 */
public class CustomServlet extends HttpServletBean {

  /** Logger instance. */
  private static final Logger LOG = LoggerFactory.getLogger(CustomServlet.class);

  private StorageService storageService;

  @Override
  public void init(ServletConfig config) throws ServletException {

    super.init(config);
    this.storageService = WebApplicationContextUtils.findWebApplicationContext(config.getServletContext())
        .getBean(StorageService.class);
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    final String parameter = request.getParameter("param");
    LOG.info("Serving {}", parameter);
    Resource file = this.storageService.loadAsResource("C:\\Users\\mluzyna\\Desktop\\files\\1.pdf");
    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"");

    ServletOutputStream output = response.getOutputStream();
    AsyncContext context = request.startAsync();
    context.setTimeout(0);

    // context.setTimeout(1000); // TODO does not work, why
    output.setWriteListener(new AsyncWriter(file, parameter, output, context));
  }

  static private class AsyncWriter implements WriteListener {
    private Resource file;

    private String clientId;

    ServletOutputStream output;

    AsyncContext context;

    InputStream input;

    /**
     * The constructor.
     *
     * @param file
     * @param parameter
     * @param output
     * @param context
     * @throws IOException
     */
    public AsyncWriter(Resource file, String parameter, ServletOutputStream output, AsyncContext context)
        throws IOException {

      super();
      this.file = file;
      this.clientId = parameter;
      this.output = output;
      this.context = context;
      this.input = this.file.getInputStream();
    }

    @Override
    public void onWritePossible() throws IOException {

      byte[] buffer = new byte[1024];
      while (this.output.isReady()) {
        int length = this.input.read(buffer);
        if (length > 0) {
          LOG.info("Client {},  writing {} bytes", this.clientId, length);
          this.output.write(buffer, 0, length);
        } else {
          LOG.info("Client {} - flush", this.clientId);
          this.output.flush();
          this.context.complete();
          return;
        }
      }

    }

    @Override
    public void onError(Throwable t) {

      LOG.error("ERROR", t);
      this.context.complete();
    }
  }
}

package com.devonfw.java.training.nonblockingio.mvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.util.stream.Collectors;

import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.devonfw.java.training.nonblockingio.service.StorageService;
import com.devonfw.java.training.nonblockingio.service.exception.StorageFileNotFoundException;

/**
 * curl --limit-rate 250k http://localhost:7777/mvc/files-async/1.pdf?clientId=1 --output 1.pdf <br>
 * curl --limit-rate 250k http://localhost:7777/mvc/files-async/1.pdf?clientId=2 --output 2.pdf <br>
 * curl --limit-rate 250k http://localhost:7777/mvc/files-async/1.pdf?clientId=3 --output 3.pdf <br>
 */
@Controller
@RequestMapping("mvc")
public class StorageControllerAsync {

  /** Logger instance. */
  private static final Logger LOG = LoggerFactory.getLogger(StorageControllerAsync.class);

  @Autowired
  private StorageService storageService;

  @GetMapping("storage-async")
  public String listUploadedFiles(Model model) throws IOException {

    model.addAttribute("files", this.storageService.loadAll()
        .map(path -> "/mvc/files-async/" + path.getFileName().toString()).collect(Collectors.toList()));

    return "storage-async";
  }

  @GetMapping("files-async/{filename:.+}")
  @ResponseBody
  public void serveFile(@PathVariable String filename, HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    String clientId = request.getParameter("clientId");
    Resource file = this.storageService.loadAsResource(filename);
    InputStream fileInput = file.getInputStream();
    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"");

    AsyncContext context = request.startAsync(); // 1
    context.setTimeout(0);
    ServletOutputStream responeOutput = response.getOutputStream(); // 2

    responeOutput.setWriteListener(new WriteListener() { // 3
      @Override
      public void onWritePossible() throws IOException {

        byte[] buffer = new byte[1024];
        while (responeOutput.isReady()) { // 4
          int length = fileInput.read(buffer);
          if (length > 0) {
            LOG.info("Client {},  writing {} bytes", clientId, length);
            responeOutput.write(buffer, 0, length); // 5
          } else {
            LOG.info("Client {} - flush", clientId);
            responeOutput.flush(); // 6
            context.complete(); // 7
            return;
          }
        }

      }

      @Override
      public void onError(Throwable t) { // 7

        LOG.error("ERROR", t);
        context.complete();
      }
    });
  }

  @PostMapping("upload-nio")
  public String handleFileUploadViaStream(HttpServletRequest request, HttpServletResponse response,
      RedirectAttributes redirectAttributes) throws IOException {

    String clientId = request.getParameter("clientId");
    final AsyncContext acontext = request.startAsync();
    long start = System.currentTimeMillis();
    acontext.setTimeout(0);
    final ServletInputStream input = request.getInputStream();
    final ByteArrayOutputStream byteStream = new ByteArrayOutputStream(50000000);
    input.setReadListener(new ReadListener() {
      byte buffer[] = new byte[4 * 1024];

      @Override
      public void onDataAvailable() {

        try {
          do {
            int length = input.read(this.buffer);
            LOG.info("writing {} bytes client {}", length, clientId);
            byteStream.write(this.buffer, 0, length);
          } while (input.isReady());
        } catch (Exception ex) {
          LOG.error("", ex);
        }
      }

      @Override
      public void onAllDataRead() {

        try {
          long duration = System.currentTimeMillis() - start;
          LOG.info("Download finished {} ms", duration);
          byteStream.flush();
          StorageControllerAsync.this.storageService.store(byteStream);
          acontext.getResponse().getWriter()
              .write("...write succed... " + byteStream.size() + "bytes in " + duration + "ms");
        } catch (Exception ex) {
          LOG.error("", ex);
        }
        acontext.complete();
      }

      @Override
      public void onError(Throwable t) {

        LOG.error("", t);
      }
    });

    return "redirect:/mvc/storage-async";

  }

  @PostMapping("upload-block")
  public String uploadBlocking(HttpServletRequest request, HttpServletResponse response,
      RedirectAttributes redirectAttributes) throws IOException {

    String clientId = request.getParameter("clientId");
    long start = System.currentTimeMillis();
    final ServletInputStream input = request.getInputStream();
    final ByteArrayOutputStream byteStream = new ByteArrayOutputStream(50000000);
    byte buffer[] = new byte[4 * 1024];
    int length = 0;

    do {

      length = input.read(buffer);
      if (length > 0) {
        LOG.info("writing {} bytes client {}", length, clientId);
        byteStream.write(buffer, 0, length);
      }
    } while (length > -1);

    long duration = System.currentTimeMillis() - start;
    LOG.info("Download finished {} ms", duration);
    byteStream.flush();
    StorageControllerAsync.this.storageService.store(byteStream);

    PrintWriter writer = response.getWriter();
    writer.print("...write succed... " + byteStream.size() + "bytes in " + duration + "ms");

    return "redirect:/mvc/storage-async";

  }

  public static BigInteger fib(BigInteger n) {

    if (n.compareTo(BigInteger.ONE) == -1 || n.compareTo(BigInteger.ONE) == 0)
      return n;
    else
      return fib(n.subtract(BigInteger.ONE)).add(fib(n.subtract(BigInteger.ONE).subtract(BigInteger.ONE)));
  }

  @PostMapping("upload-async")
  public String handleFileUpload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {

    this.storageService.store(file);
    redirectAttributes.addFlashAttribute("message", "You successfully uploaded " + file.getOriginalFilename() + "!");

    return "redirect:/mvc/storage-async";
  }

  @ExceptionHandler(StorageFileNotFoundException.class)
  public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {

    return ResponseEntity.notFound().build();
  }

}

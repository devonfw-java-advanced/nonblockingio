package com.devonfw.java.training.nonblockingio.mvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.stream.Collectors;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
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
 * curl --limit-rate 250k http://localhost:7777/mvc/files/1.pdf?clientId=1 --output 1.pdf <br>
 * curl --limit-rate 250k http://localhost:7777/mvc/files/1.pdf?clientId=1 --output 2.pdf <br>
 * curl --limit-rate 250k http://localhost:7777/mvc/files/1.pdf?clientId=1 --output 3.pdf <br>
 * curl -X POST -H "Content-Type: application/pdf" --limit-rate 1500k --data-binary @3.pdf http://localhost:7777/mvc/upload-block?clientId=1 <br>
 * curl -X POST -H "Content-Type: application/pdf" --limit-rate 1500k --data-binary @3.pdf http://localhost:7777/mvc/upload-block?clientId=2 <br>
 * curl -X POST -H "Content-Type: application/pdf" --limit-rate 1500k --data-binary @3.pdf http://localhost:7777/mvc/upload-block?clientId=3 <br>
 */
@Controller
@RequestMapping("mvc")
public class StorageController {

  /** Logger instance. */
  private static final Logger LOG = LoggerFactory.getLogger(StorageController.class);

  @Autowired
  private StorageService storageService;

  @GetMapping("storage")
  public String listUploadedFiles(Model model) throws IOException {

    model.addAttribute("files", this.storageService.loadAll().map(path -> "/mvc/files/" + path.getFileName().toString())
        .collect(Collectors.toList()));

    return "storage";
  }

  @GetMapping("files/{filename:.+}")
  @ResponseBody
  public void downloadBlocking(@PathVariable String filename, HttpServletRequest request, HttpServletResponse response)
      throws IOException {

    String clientId = request.getParameter("clientId");
    Resource file = this.storageService.loadAsResource(filename);
    InputStream input = file.getInputStream();
    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"");
    ServletOutputStream output = response.getOutputStream();

    byte[] buffer = new byte[1024];
    while (true) {
      int length = input.read(buffer);
      if (length > 0) {
        LOG.info("Client {},  writing {} bytes", clientId, length);
        output.write(buffer, 0, length);
      } else {
        LOG.info("Client {} - flush", clientId);
        output.flush();
        return;
      }
    }

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
    this.storageService.store(byteStream);

    PrintWriter writer = response.getWriter();
    writer.print("...write succed... " + byteStream.size() + "bytes in " + duration + "ms");

    return "redirect:/mvc/storage-async";

  }

  @PostMapping("upload")
  public String handleFileUpload(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {

    this.storageService.store(file);
    redirectAttributes.addFlashAttribute("message", "You successfully uploaded " + file.getOriginalFilename() + "!");

    return "redirect:/mvc/storage";
  }

  @ExceptionHandler(StorageFileNotFoundException.class)
  public ResponseEntity<?> handleStorageFileNotFound(StorageFileNotFoundException exc) {

    return ResponseEntity.notFound().build();
  }

}

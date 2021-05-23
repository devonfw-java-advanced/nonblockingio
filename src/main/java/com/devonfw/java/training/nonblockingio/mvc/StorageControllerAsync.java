package com.devonfw.java.training.nonblockingio.mvc;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;

import javax.servlet.AsyncContext;
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
    InputStream input = file.getInputStream();
    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"");

    ServletOutputStream output = response.getOutputStream();
    AsyncContext context = request.startAsync();
    context.setTimeout(0);

    output.setWriteListener(new WriteListener() {
      @Override
      public void onWritePossible() throws IOException {

        byte[] buffer = new byte[1024];
        while (output.isReady()) {
          int length = input.read(buffer);
          if (length > 0) {
            LOG.info("Client {},  writing {} bytes", clientId, length);
            output.write(buffer, 0, length);
          } else {
            LOG.info("Client {} - flush", clientId);
            output.flush();
            context.complete();
            return;
          }
        }

      }

      @Override
      public void onError(Throwable t) {

        LOG.error("ERROR", t);
        context.complete();
      }
    });
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

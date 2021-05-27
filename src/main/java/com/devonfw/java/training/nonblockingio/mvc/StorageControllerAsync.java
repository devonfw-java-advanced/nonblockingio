package com.devonfw.java.training.nonblockingio.mvc;

import java.io.IOException;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
  public void downloadNonblocking(@PathVariable String filename, HttpServletRequest request,
      HttpServletResponse response) throws IOException {

  }

  @PostMapping("upload-nio")
  public String uploadNonblocking(HttpServletRequest request, HttpServletResponse response,
      RedirectAttributes redirectAttributes) throws IOException {

    return "redirect:/mvc/storage-async";
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

package com.devonfw.java.training.nonblockingio.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import com.devonfw.java.training.nonblockingio.service.exception.StorageException;
import com.devonfw.java.training.nonblockingio.service.exception.StorageFileNotFoundException;

@Service
public class StorageService {

  private final Path rootLocation = Paths.get("upload-dir");

  @PostConstruct
  private void postConstruct() {

    deleteAll();
    init();
  }

  public void store(MultipartFile file) {

    if (file.isEmpty()) {
      throw new StorageException("Failed to store empty file.");
    }
    Path destinationFile = this.rootLocation.resolve(Paths.get(file.getOriginalFilename())).normalize()
        .toAbsolutePath();
    if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
      // This is a security check
      throw new StorageException("Cannot store file outside current directory.");
    }
    try (InputStream inputStream = file.getInputStream()) {
      Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new StorageException("Failed to store file.", e);
    }
  }

  public Stream<Path> loadAll() {

    try {
      return Files.walk(this.rootLocation, 1).filter(path -> !path.equals(this.rootLocation))
          .map(this.rootLocation::relativize);
    } catch (IOException e) {
      throw new StorageException("Failed to read stored files", e);
    }
  }

  public Path load(String filename) {

    return this.rootLocation.resolve(filename);
  }

  public Resource loadAsResource(String filename) {

    try {
      Path file = load(filename);
      Resource resource = new UrlResource(file.toUri());
      if (resource.exists() || resource.isReadable()) {
        return resource;
      } else {
        throw new StorageFileNotFoundException("Could not read file: " + filename);
      }
    } catch (MalformedURLException e) {
      throw new StorageFileNotFoundException("Could not read file: " + filename, e);
    }
  }

  public void deleteAll() {

    FileSystemUtils.deleteRecursively(this.rootLocation.toFile());
  }

  public void init() {

    try {
      Files.createDirectories(this.rootLocation);
    } catch (IOException e) {
      throw new StorageException("Could not initialize storage", e);
    }
  }

  /**
   * @param byteStream
   */
  public void store(ByteArrayOutputStream byteStream) {

    Path destinationFile = this.rootLocation.resolve(Paths.get(UUID.randomUUID().toString())).normalize()
        .toAbsolutePath();
    if (!destinationFile.getParent().equals(this.rootLocation.toAbsolutePath())) {
      // This is a security check
      throw new StorageException("Cannot store file outside current directory.");
    }

    ByteArrayInputStream byteInputStream = new ByteArrayInputStream(byteStream.toByteArray());
    try {
      Files.copy(byteInputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new StorageException("Could not initialize storage", e);
    }

  }
}

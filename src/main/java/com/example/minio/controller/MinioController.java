package com.example.minio.controller;

import io.minio.*;
import io.minio.messages.Item;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/files")
public class MinioController {

    private final MinioClient minioClient;
    
    @Value("${minio.bucket.name}")
    private String bucketName;

    public MinioController(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    // Создание бакета, если он не существует
    @PostConstruct
    public void init() {
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucketName)
                    .build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucketName)
                        .build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Получение списка файлов
    @GetMapping("/view-files")
    public List<String> listFiles() throws Exception {
        List<String> fileNames = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder().bucket(bucketName).recursive(true).build()
        );
        for (Result<Item> result : results) {
            fileNames.add(result.get().objectName());
        }
        return fileNames;
    }

    // Просмотр файла
    @GetMapping("/view/{objectName}")
    public void getFile(@PathVariable String objectName, HttpServletResponse response) {
        try {
            GetObjectArgs args = GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build();
            try (InputStream stream = minioClient.getObject(args)) {
                // Копируем поток в outputStream ответа
                IOUtils.copy(stream, response.getOutputStream());
                response.flushBuffer();
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    // Получение файла (скачивание)
    @GetMapping("/download/{objectName}")
    public void downloadFile(@PathVariable String objectName, HttpServletResponse response) {
        try {
            // Получаем файл из MinIO
            GetObjectArgs getObjectArgs = GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build();

            try (InputStream inputStream = minioClient.getObject(getObjectArgs)) {
                // Устанавливаем заголовки для скачивания
                response.setHeader("Content-Disposition", "attachment; filename=\"" + objectName + "\"");
                response.setContentType(URLConnection.guessContentTypeFromName(objectName));

                // Копируем поток в HTTP-ответ
                IOUtils.copy(inputStream, response.getOutputStream());
                response.flushBuffer();
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        }
    }

    // Загрузка файла (создание или обновление)
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        
        try {
            String contentType = file.getContentType();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(file.getOriginalFilename())
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
            return "Upload success";
        } catch (Exception e) {
            e.printStackTrace();
            return "Upload fail";
        }
    }

    // Удаление файла
    @DeleteMapping("/{objectName}")
    public ResponseEntity<String> deleteFile(@PathVariable String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return ResponseEntity.ok("File is deleted successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error while deleting file");
        }
    }
}
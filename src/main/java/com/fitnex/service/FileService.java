package com.fitnex.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileService {

    @Value("${file.upload.image.path}")
    private String imageUploadPath;

    public String saveImage(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new RuntimeException("文件为空");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String filename = UUID.randomUUID().toString() + extension;
        
        // 处理路径：如果是相对路径（以 ./ 开头），使用项目根目录
        Path uploadPath;
        if (imageUploadPath.startsWith("./")) {
            // 相对路径，使用项目根目录
            String projectRoot = System.getProperty("user.dir");
            String relativePath = imageUploadPath.substring(2); // 去掉 "./"
            uploadPath = Paths.get(projectRoot, relativePath);
        } else if (imageUploadPath.startsWith("/")) {
            // 绝对路径，直接使用
            uploadPath = Paths.get(imageUploadPath);
        } else {
            // 相对路径，使用项目根目录
            String projectRoot = System.getProperty("user.dir");
            uploadPath = Paths.get(projectRoot, imageUploadPath);
        }

        // 确保目录存在
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 检查目录是否可写
        if (!Files.isWritable(uploadPath)) {
            throw new RuntimeException("上传目录不可写: " + uploadPath.toAbsolutePath());
        }

        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath);

        // 返回相对路径，用于 URL 访问
        return "/uploads/images/" + filename;
    }
}


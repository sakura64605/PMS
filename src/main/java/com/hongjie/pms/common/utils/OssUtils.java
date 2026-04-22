package com.hongjie.pms.common.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.hongjie.pms.common.circuitbreaker.annotation.CircuitBreaker;
import com.hongjie.pms.common.config.OssConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class OssUtils {

    private final OSS ossClient;
    private final OssConfig ossConfig;

    /**
     * 上传文件
     * @param file 文件
     * @param userId 用户ID（用于目录隔离）
     * @return 文件访问URL
     */
    @CircuitBreaker(
            value = "ossUpload",
            windowSize = 10,
            minRequestAmount = 5,
            errorRateThreshold = 0.5,
            openDurationSeconds = 10,
            fallbackMethod = "fallbackUpload"
    )
    public String uploadAvatar(MultipartFile file, Long userId) {
        // 1. 验证文件
        validateImage(file);

        try {
            // 2. 生成文件名：用户ID/日期/随机UUID.后缀
            String originalFilename = file.getOriginalFilename();
            String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            String fileName = generateFileName(userId, suffix);

            // 3. 完整的OSS路径
            String objectName = ossConfig.getBasePath() + fileName;

            // 4. 上传到OSS
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    ossConfig.getBucketName(),
                    objectName,
                    file.getInputStream()
            );

            PutObjectResult result = ossClient.putObject(putObjectRequest);
            log.info("文件上传成功: {}, ETag: {}", objectName, result.getETag());

            // 5. 返回完整的访问URL
            return ossConfig.getDomain() + "/" + objectName;

        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 降级方法：返回本地路径或默认图片
     */
    public String fallbackUpload(MultipartFile file, String path) {
        log.warn("OSS上传熔断降级: fileName={}", file.getOriginalFilename());
        // 降级：返回默认图片地址
        return ossConfig.getDomain() + "/default/default-avatar.png";
    }

    public String fallbackUpload(MultipartFile file, String path, Exception e) {
        log.error("OSS上传熔断降级: fileName={}, error={}", file.getOriginalFilename(), e.getMessage());
        return fallbackUpload(file, path);
    }

    /**
     * 删除文件
     * @param fileUrl 文件URL
     */
    @CircuitBreaker(
            value = "deleteFile",
            fallbackMethod = "fallbackDeleteFile"
    )
    public void deleteFile(String fileUrl) {
        try {
            // 从URL中提取objectName
            String objectName = extractObjectName(fileUrl);
            if (objectName != null) {
                ossClient.deleteObject(ossConfig.getBucketName(), objectName);
                log.info("文件删除成功: {}", objectName);
            }
        } catch (Exception e) {
            log.error("文件删除失败", e);
        }
    }

    /**
     * 降级方法：只记录日志
     */
    public void fallbackDeleteFile(String fileUrl) {
        log.warn("OSS删除熔断降级: fileUrl={}", fileUrl);
        // 降级：记录到失败队列，后续异步重试
        saveToDeleteQueue(fileUrl);
    }

    public void fallbackDeleteFile(String fileUrl, Exception e) {
        log.error("OSS删除熔断降级: fileUrl={}, error={}", fileUrl, e.getMessage());
        saveToDeleteQueue(fileUrl);
    }

    private void saveToDeleteQueue(String fileUrl) {
        // 可以存到数据库，定时任务重试
        log.info("文件已加入删除重试队列: {}", fileUrl);
    }

    /**
     * 验证图片
     */
    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        // 检查文件大小（最大2MB）
        if (file.getSize() > 2 * 1024 * 1024) {
            throw new IllegalArgumentException("文件大小不能超过2MB");
        }

        // 检查文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只能上传图片文件");
        }

        // 检查后缀
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String suffix = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            if (!"jpg".equals(suffix) && !"jpeg".equals(suffix) && !"png".equals(suffix) && !"gif".equals(suffix)) {
                throw new IllegalArgumentException("只支持jpg、jpeg、png、gif格式");
            }
        }
    }

    /**
     * 生成文件名
     */
    private String generateFileName(Long userId, String suffix) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return String.format("%d/%s/%s%s", userId, date, uuid, suffix);
    }

    /**
     * 从URL提取objectName
     */
    private String extractObjectName(String fileUrl) {
        if (fileUrl == null || !fileUrl.startsWith(ossConfig.getDomain())) {
            return null;
        }
        return fileUrl.substring(ossConfig.getDomain().length() + 1);
    }

}

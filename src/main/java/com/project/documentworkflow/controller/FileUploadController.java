package com.project.documentworkflow.controller;

import com.project.documentworkflow.dto.ApiResponse;
import com.project.documentworkflow.dto.UploadResponse;
import com.project.documentworkflow.security.JwtUtil;
import com.project.documentworkflow.service.DocumentWorkflowService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class FileUploadController {

    @Autowired
    private DocumentWorkflowService documentWorkflowService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/upload")
    public ApiResponse<UploadResponse> upload(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("file") MultipartFile file) {

        try {
            // Extract uploader email from JWT token
            String token = authHeader.replace("Bearer ", "");
            String uploaderEmail = jwtUtil.extractEmail(token);

            // Validate file
            if (file == null || file.isEmpty()) {
                return new ApiResponse<>(false, null, "No file provided");
            }

            // Process upload
            UploadResponse response = documentWorkflowService.processUpload(file, uploaderEmail);

            return new ApiResponse<>(true, response, null);

        } catch (Exception e) {
            return new ApiResponse<>(false, null, "Upload failed: " + e.getMessage());
        }
    }
}
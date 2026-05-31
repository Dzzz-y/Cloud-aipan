package net.aipan.dcloud_aipan.controller;

import net.aipan.dcloud_aipan.config.MinioConfig;
import net.aipan.dcloud_aipan.util.CommonUtil;
import net.aipan.dcloud_aipan.util.JsonData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@RestController
@RequestMapping("/ai/test/v1")
public class TestController {
    @Autowired
    private MinioConfig minioConfig;

//    @Autowired
//    private MinioClient minioClient;

    @PostMapping("/upload")
    public JsonData testUpload(@RequestParam("file")MultipartFile  file){
        String fileName= CommonUtil.getFilePath(file.getOriginalFilename());
        //获取文件流，上传minio
        try {
            InputStream inputStream=file.getInputStream();
//            minioClient.putObject(PutObjectArgs.builder().bucket(minioConfig.getBucketName())
//                    .object(fileName)
//                    .stream(inputStream, inputStream.available(), -1)
//                    .build());
        }catch(Exception e){
            e.printStackTrace();
        }
        String url=minioConfig.getEndpoint()+"/"+minioConfig.getBucketName()+"/"+fileName;
        return JsonData.buildSuccess(url);
    }
}

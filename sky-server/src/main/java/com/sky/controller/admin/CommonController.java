package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 通用接口
 */
@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
@Slf4j
public class CommonController {

    @Autowired
    private AliOssUtil aliOssUtil;  //阿里云文件上传工具类
    /**
     * 文件上传
     * @param file
     * @return
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file){
        log.info("文件上传：{}",file);
        //上传文件
        try {
            //获取原始文件名
            String originalFileName = file.getOriginalFilename();
            //截取后缀
            String suffixFileName = originalFileName.substring(originalFileName.lastIndexOf("."));
            //生成UUID随机文件名
            String prefixFileName = UUID.randomUUID().toString();
            //组合成文件名
            String objectFileName = prefixFileName + suffixFileName;

            String filePath = aliOssUtil.upload(file.getBytes(), objectFileName);
            return Result.success(filePath);
        } catch (IOException e) {
            //文件上传失败
            log.error("文件上传失败：{}",e);
        }
        return Result.error(MessageConstant.UPLOAD_FAILED);
    }
}

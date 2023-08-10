package com.sky.handler;

import com.sky.constant.MessageConstant;
import com.sky.exception.BaseException;
import com.sky.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获业务异常
     *
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex) {
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 捕获SQL异常，违反唯一约束异常
     *
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex) {
        //获取异常信息，例如：Duplicate entry 'zhangsan' for key 'idx_username'
        String message = ex.getMessage();

        log.error("异常信息：{}",message);

        if (message.contains("Duplicate entry")) {  //属于违反唯一约束异常
            //获取异常信息中的属性列名，例如：zhangsan
            String entryName = message.split(" ")[2];
            entryName = entryName.replace("'", "");

            log.debug("Duplicate entry: {}", entryName);

            //将属性列名返回给前端页面，例如：zhangsan已存在
            return Result.error(entryName + MessageConstant.ALREADY_EXISTS);
        } else {  //不属于违反唯一约束异常
            return Result.error(MessageConstant.UNKNOWN_ERROR);  //返回未知错误信息
        }
    }

}

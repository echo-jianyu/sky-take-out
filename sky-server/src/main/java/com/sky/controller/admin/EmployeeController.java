package com.sky.controller.admin;

import com.sky.constant.JwtClaimsConstant;
import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.properties.JwtProperties;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.EmployeeService;
import com.sky.utils.JwtUtil;
import com.sky.vo.EmployeeLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import static com.sky.result.Result.success;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Api(tags = "员工相关接口")   //该注解用于描述该类的作用，便于生成接口测试文档
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @PostMapping("/login")
    @ApiOperation("员工登录")  //该注解用于描述该方法的功能，便于生成接口测试文档
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) { //employeeLoginDTO为员工登录数据传输对象，用于前后端数据的传输
        log.info("员工登录：{}", employeeLoginDTO);

        Employee employee = employeeService.login(employeeLoginDTO);  //执行服务层登录方法

        //登录成功后，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());  //仅放入EMP_ID，无其它字段
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),  //密钥
                jwtProperties.getAdminTtl(),  //TTL，jwt令牌过期时间
                claims);  //claims

        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()  //封装要返回的数据
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .build();

        return success(employeeLoginVO);  //使用Result格式再次封装
    }

    /**
     * 退出
     *
     * @return
     */
    @ApiOperation(value = "员工退出")
    @PostMapping("/logout")
    public Result<String> logout() {
        return success();
    }

    /**
     * 新增员工
     *
     * @param employeeDTO
     * @return
     */
    @ApiOperation("新增员工")
    @PostMapping
    public Result save(@RequestBody EmployeeDTO employeeDTO) {

        log.info("新增员工：{}", employeeDTO);

        //调用业务层处理
        employeeService.save(employeeDTO);
        //返回成功结果
        return success();
    }

    /**
     * 员工分页查询
     *
     * @param employeePageQueryDTO
     * @return
     */
    @ApiOperation("分页查询")
    @GetMapping("/page")
    public Result<PageResult> page(EmployeePageQueryDTO employeePageQueryDTO) {
        log.info("员工分页查询，参数为：", employeePageQueryDTO);
        //调用业务层查询
        PageResult pageResult = employeeService.pageQuery(employeePageQueryDTO);
        //返回封装结果
        return Result.success(pageResult);
    }

    /**
     * 启用禁用员工
     *
     * @param status
     * @param id
     * @return
     */
    @PostMapping("/{status}")
    @ApiOperation("启用禁用员工账号")
    public Result startOrStop(@PathVariable Integer status, Long id) {
        log.info("启用禁用员工账号：id={},status={}", id, status);
        employeeService.startOrStop(status, id);
        return Result.success();
    }

}

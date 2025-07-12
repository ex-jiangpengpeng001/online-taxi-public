package com.mashibing.serviceprice.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.util.Collections;

/**
 * 自动生成代码工具类
 */
public class MysqlGenerator {
    public static void main(String[] args) {

        FastAutoGenerator.create("jdbc:mysql://localhost:3306/service-price?characterEncoding=utf-8&serverTimezone=GMT%2B8",
                "root","root")
                .globalConfig(builder -> {
                    builder.author("JPP").fileOverride().outputDir("/Volumes/Work/WorkSpace/online-taxi-public/service-price/src/main/java");
                })
                .packageConfig(builder -> {
                    builder.parent("com.mashibing.serviceprice").pathInfo(Collections.singletonMap(OutputFile.mapperXml,
                            "/Volumes/Work/WorkSpace/online-taxi-public/service-price/src/main/java/com/mashibing/serviceprice/mapper"));
                })
                .strategyConfig(builder -> {
                    // 增加包含的表名
                    builder.addInclude("price_rule");

                })
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();
    }
}
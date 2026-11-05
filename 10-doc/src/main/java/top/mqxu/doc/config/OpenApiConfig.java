package top.mqxu.doc.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 文档配置：文档元信息 + 分组。
 * <p>
 * 分组的意义在于把几十个接口切成人能看的几块。Swagger UI 右上角可以在分组之间切换，
 * 也能按组导出 JSON，方便同步到 Apifox / Postman。
 *
 * @author mqxu
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bookOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("图书管理接口")
                        .version("1.0.0")
                        .description("""
                                这是 10-doc 模块的示例文档。
                                <p>
                                文档由代码注解生成，改代码即改文档，不需要额外维护一份 Markdown。
                                """)
                        .contact(new Contact().name("mqxu").email("mqxu@example.com"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://127.0.0.1:8010").description("本地开发"),
                        new Server().url("https://api.example.com").description("生产环境")))
                .externalDocs(new ExternalDocumentation()
                        .description("模块教程")
                        .url("https://github.com/mqxu/backend-learning"));
    }

    /**
     * 图书业务分组：只收录 /books 下的接口
     */
    @Bean
    public GroupedOpenApi bookGroup() {
        return GroupedOpenApi.builder()
                .group("book")
                .displayName("图书业务")
                .pathsToMatch("/books/**")
                .build();
    }

    /**
     * 运维分组：健康检查、文档自身这类接口单独放一块
     */
    @Bean
    public GroupedOpenApi opsGroup() {
        return GroupedOpenApi.builder()
                .group("ops")
                .displayName("运维接口")
                .pathsToMatch("/ops/**", "/actuator/**")
                .build();
    }
}
